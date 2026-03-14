"""
Trading operations - uses real market data from Yahoo Finance
for prices, with local SQLite for portfolio/orders/alerts.
Enforces market hours and wallet balance checks.
"""

from datetime import datetime, time
from typing import Iterable
import hashlib
import pytz
from uuid import uuid4
from sqlalchemy.orm import Session
from ..database.db import (
    HoldingModel,
    OrderModel,
    AlertModel,
    WalletModel,
    TriggerOrderModel,
)
from ..models.schemas import Order, OrderResponse, Holding, Wallet, WalletResponse, MarketStatus
from ..market_data import fetch_quote

import logging

logger = logging.getLogger(__name__)

IST = pytz.timezone("Asia/Kolkata")
ORDER_STATUS_TRANSITIONS: dict[str, set[str]] = {
    "PENDING": {"COMPLETED", "REJECTED", "TRIGGER_EXECUTED", "CANCELLED"},
    "COMPLETED": set(),
    "REJECTED": set(),
    "TRIGGER_EXECUTED": set(),
    "CANCELLED": set(),
}
TRIGGER_STATUS_TRANSITIONS: dict[str, set[str]] = {
    "PENDING": {"EXECUTED", "FAILED", "CANCELLED"},
    "EXECUTED": set(),
    "FAILED": set(),
    "CANCELLED": set(),
}
MAX_IDEMPOTENCY_KEY_LENGTH = 128
TRADING_WALLET_USER_ID = 0
DEFAULT_TRADING_WALLET_BALANCE = 100000.0

# NSE market holidays 2026 (approximate - major holidays)
NSE_HOLIDAYS_2026 = {
    "2026-01-26",  # Republic Day
    "2026-03-10",  # Maha Shivaratri
    "2026-03-17",  # Holi
    "2026-03-30",  # Id-Ul-Fitr (Ramadan)
    "2026-04-02",  # Thursday before Good Friday
    "2026-04-03",  # Good Friday
    "2026-04-06",  # Shri Ram Navami
    "2026-04-14",  # Dr. Ambedkar Jayanti
    "2026-05-01",  # Maharashtra Day
    "2026-06-05",  # Eid ul-Adha (Bakrid)
    "2026-07-06",  # Muharram
    "2026-08-15",  # Independence Day
    "2026-08-18",  # Parsi New Year
    "2026-09-04",  # Milad-un-Nabi
    "2026-10-02",  # Mahatma Gandhi Jayanti
    "2026-10-20",  # Dussehra
    "2026-11-09",  # Diwali (Laxmi Pujan)
    "2026-11-10",  # Diwali Balipratipada
    "2026-11-27",  # Guru Nanak Jayanti
    "2026-12-25",  # Christmas
}


def _normalize_order_payload(order: Order, idempotency_key: str | None = None) -> Order:
    """Normalize order payload for deterministic processing."""
    symbol = (order.symbol or "").strip().upper()
    side = (order.side or "").strip().upper()
    resolved_key = (idempotency_key or order.idempotencyKey or "").strip() or None
    return Order(
        symbol=symbol,
        qty=order.qty,
        side=side,
        orderType=getattr(order, "orderType", "MARKET"),
        validity=getattr(order, "validity", "DAY"),
        limitPrice=getattr(order, "limitPrice", None),
        triggerPrice=getattr(order, "triggerPrice", None),
        tag=getattr(order, "tag", None),
        idempotencyKey=resolved_key,
    )


def _build_request_fingerprint(order: Order) -> str:
    payload = "|".join(
        [
            (order.symbol or "").strip().upper(),
            _normalize_side(order.side),
            str(int(order.qty)),
            _normalize_order_type(getattr(order, "orderType", "MARKET")),
            _normalize_validity(getattr(order, "validity", "DAY")),
            (
                f"{float(getattr(order, 'limitPrice', None)):.4f}"
                if getattr(order, "limitPrice", None) is not None
                else ""
            ),
            (
                f"{float(getattr(order, 'triggerPrice', None)):.4f}"
                if getattr(order, "triggerPrice", None) is not None
                else ""
            ),
            (getattr(order, "tag", None) or "").strip(),
        ]
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _transition_order_status(order_db: OrderModel, next_status: str) -> None:
    current_status = (order_db.status or "PENDING").upper()
    target_status = next_status.upper()
    allowed = ORDER_STATUS_TRANSITIONS.get(current_status, set())
    if target_status not in allowed:
        raise ValueError(f"Invalid transition from {current_status} to {target_status}")
    order_db.status = target_status


def _transition_trigger_status(trigger_db: TriggerOrderModel, next_status: str) -> None:
    current_status = (trigger_db.status or "PENDING").upper()
    target_status = next_status.upper()
    allowed = TRIGGER_STATUS_TRANSITIONS.get(current_status, set())
    if target_status not in allowed:
        raise ValueError(f"Invalid trigger transition from {current_status} to {target_status}")
    trigger_db.status = target_status


def is_market_open() -> MarketStatus:
    """Check if NSE is currently open. Market hours: 9:15 AM - 3:30 PM IST, Mon-Fri."""
    now_ist = datetime.now(IST)
    today_str = now_ist.strftime("%Y-%m-%d")
    weekday = now_ist.weekday()  # 0=Monday, 6=Sunday

    market_open = time(9, 15)
    market_close = time(15, 30)

    if weekday >= 5:  # Saturday or Sunday
        next_monday = now_ist.date()
        days_until_monday = 7 - weekday
        from datetime import timedelta
        next_open_date = now_ist.date() + timedelta(days=days_until_monday)
        return MarketStatus(
            isOpen=False,
            message="Market closed - Weekend",
            nextOpen=f"{next_open_date} 09:15 IST"
        )

    if today_str in NSE_HOLIDAYS_2026:
        return MarketStatus(
            isOpen=False,
            message="Market closed - Holiday",
            nextOpen="Next trading day 09:15 IST"
        )

    current_time = now_ist.time()
    if current_time < market_open:
        return MarketStatus(
            isOpen=False,
            message=f"Market opens at 9:15 AM IST",
            nextOpen=f"{today_str} 09:15 IST",
            nextClose=f"{today_str} 15:30 IST"
        )
    elif current_time > market_close:
        return MarketStatus(
            isOpen=False,
            message="Market closed for today (3:30 PM IST)",
            nextOpen="Next trading day 09:15 IST"
        )
    else:
        return MarketStatus(
            isOpen=True,
            message="Market is OPEN",
            nextClose=f"{today_str} 15:30 IST"
        )


def _normalize_side(value: str) -> str:
    side = (value or "").strip().upper()
    if side not in {"BUY", "SELL"}:
        raise ValueError("side must be BUY or SELL")
    return side


def _normalize_order_type(value: str | None) -> str:
    token = (value or "MARKET").strip().upper()
    if token not in {"MARKET", "LIMIT", "SL", "SLM"}:
        raise ValueError("orderType must be one of MARKET, LIMIT, SL, SLM")
    return token


def _normalize_validity(value: str | None) -> str:
    token = (value or "DAY").strip().upper()
    if token not in {"DAY", "IOC", "GTC"}:
        raise ValueError("validity must be one of DAY, IOC, GTC")
    return token


def _wallet_for_user(db: Session, user_id: int) -> WalletModel:
    wallet = db.query(WalletModel).filter(WalletModel.user_id == user_id).first()
    if not wallet:
        wallet = WalletModel(user_id=user_id, balance=DEFAULT_TRADING_WALLET_BALANCE)
        db.add(wallet)
        db.commit()
        db.refresh(wallet)
    return wallet


def _should_trigger(order: Order, live_price: float) -> bool:
    side = _normalize_side(order.side)
    order_type = _normalize_order_type(getattr(order, "orderType", "MARKET"))
    limit_price = getattr(order, "limitPrice", None)
    trigger_price = getattr(order, "triggerPrice", None)

    if order_type == "MARKET":
        return True

    if order_type == "LIMIT":
        if limit_price is None:
            return False
        if side == "BUY":
            return live_price <= float(limit_price)
        return live_price >= float(limit_price)

    if order_type in {"SL", "SLM"}:
        if trigger_price is None:
            return False
        if side == "BUY":
            return live_price >= float(trigger_price)
        return live_price <= float(trigger_price)

    return False


def _execution_price(order: Order, live_price: float) -> float:
    order_type = _normalize_order_type(getattr(order, "orderType", "MARKET"))
    limit_price = getattr(order, "limitPrice", None)

    if order_type == "LIMIT" and limit_price is not None:
        return float(limit_price)
    return float(live_price)


def build_pretrade_signal(
    order: Order,
    live_price: float,
    wallet_balance: float,
    market_open: bool,
) -> dict:
    side = _normalize_side(order.side)
    order_type = _normalize_order_type(getattr(order, "orderType", "MARKET"))
    validity = _normalize_validity(getattr(order, "validity", "DAY"))

    notional = float(order.qty) * float(_execution_price(order, live_price))
    flags: list[str] = []
    guidance: list[str] = []

    if not market_open:
        flags.append("Market is currently closed")
    if order.qty <= 0:
        flags.append("Quantity must be greater than zero")
    if side == "BUY" and wallet_balance < notional:
        flags.append("Insufficient wallet for projected debit")
    if order_type in {"LIMIT", "SL", "SLM"} and validity == "IOC":
        guidance.append("IOC on trigger/limit can cancel quickly if price does not match")
    if notional >= 200_000:
        flags.append("High notional exposure")
    if order_type == "MARKET":
        guidance.append("Market order prioritizes execution over price certainty")
    elif order_type == "LIMIT":
        guidance.append("Limit order protects entry/exit price")
    else:
        guidance.append("Stop orders are useful for breakout/risk-control workflows")

    if any("Quantity" in item for item in flags):
        verdict = "BLOCK"
        confidence = 92
    elif flags:
        verdict = "CAUTION"
        confidence = 72
    else:
        verdict = "GO"
        confidence = 81

    return {
        "verdict": verdict,
        "confidence": confidence,
        "flags": flags,
        "guidance": guidance,
    }



def get_wallet(db: Session, user_id: int) -> Wallet:
    """Get current wallet balance for a user. Creates wallet with ₹100,000 if first time."""
    wallet = _wallet_for_user(db, user_id)
    return Wallet(balance=round(wallet.balance, 2))



def add_funds(db: Session, user_id: int, amount: float) -> WalletResponse:
    """Add funds to a user's wallet."""
    if amount <= 0:
        return WalletResponse(status="error", balance=0, message="Amount must be positive")

    wallet = _wallet_for_user(db, user_id)
    wallet.balance += amount
    db.commit()
    db.refresh(wallet)

    return WalletResponse(
        status="ok",
        balance=round(wallet.balance, 2),
        message=f"Added ₹{amount:.2f}. New balance: ₹{wallet.balance:.2f}"
    )



def withdraw_funds(db: Session, user_id: int, amount: float) -> WalletResponse:
    """Withdraw funds from a user's wallet."""
    if amount <= 0:
        return WalletResponse(status="error", balance=0, message="Amount must be positive")

    wallet = _wallet_for_user(db, user_id)
    if not wallet or wallet.balance < amount:
        current = round(wallet.balance, 2) if wallet else 0
        return WalletResponse(
            status="error",
            balance=current,
            message=f"Insufficient balance. Available: ₹{current:.2f}"
        )

    wallet.balance -= amount
    db.commit()
    db.refresh(wallet)

    return WalletResponse(
        status="ok",
        balance=round(wallet.balance, 2),
        message=f"Withdrew ₹{amount:.2f}. Remaining: ₹{wallet.balance:.2f}"
    )


def get_holdings(db: Session) -> list[Holding]:
    """Get all holdings with live prices."""
    holdings_db = db.query(HoldingModel).all()
    holdings = []
    for h in holdings_db:
        # Fetch live price for each holding
        live_quote = fetch_quote(h.symbol)
        live_price = live_quote["last"] if live_quote["last"] > 0 else h.last_price

        # Update stored price
        h.last_price = live_price
        h.pnl = round((live_price - h.avg_price) * h.quantity, 2)
        db.commit()

        holdings.append(Holding(
            symbol=h.symbol,
            qty=h.quantity,
            avgPrice=round(h.avg_price, 2),
            last=round(live_price, 2),
            pnl=round(h.pnl, 2)
        ))
    return holdings


def get_holding(db: Session, symbol: str) -> Holding | None:
    """Get a single holding by symbol with live price."""
    h = db.query(HoldingModel).filter(HoldingModel.symbol == symbol).first()
    if not h:
        return None

    live_quote = fetch_quote(symbol)
    live_price = live_quote["last"] if live_quote["last"] > 0 else h.last_price
    h.last_price = live_price
    h.pnl = round((live_price - h.avg_price) * h.quantity, 2)
    db.commit()

    return Holding(
        symbol=h.symbol,
        qty=h.quantity,
        avgPrice=round(h.avg_price, 2),
        last=round(live_price, 2),
        pnl=round(h.pnl, 2)
    )


def _execute_order_at_price(
    db: Session,
    order: Order,
    execution_price: float,
    user_id: int = 1,
    basket_id: int | None = None,
    status: str = "COMPLETED",
    idempotency_key: str | None = None,
    trace_id: str | None = None,
) -> tuple[OrderResponse, OrderModel]:
    normalized_order = _normalize_order_payload(order, idempotency_key=idempotency_key)
    resolved_trace_id = (trace_id or f"trc-{uuid4().hex[:16]}").strip()
    side = _normalize_side(normalized_order.side)
    order_type = _normalize_order_type(getattr(normalized_order, "orderType", "MARKET"))
    validity = _normalize_validity(getattr(normalized_order, "validity", "DAY"))
    execution_price = float(execution_price)
    if execution_price <= 0:
        raise ValueError("execution_price must be positive")

    wallet = _wallet_for_user(db, user_id)
    existing = db.query(HoldingModel).filter(HoldingModel.symbol == normalized_order.symbol).first()

    if side == "BUY":
        order_cost = execution_price * normalized_order.qty
        if wallet.balance < order_cost:
            response = OrderResponse(
                status="error",
                order=normalized_order,
                message=f"Insufficient funds. Need ₹{order_cost:.2f} but wallet has ₹{wallet.balance:.2f}",
                traceId=resolved_trace_id,
                idempotencyKey=normalized_order.idempotencyKey,
                errorCode="INSUFFICIENT_FUNDS",
            )
            failed_order = OrderModel(
                user_id=user_id,
                symbol=normalized_order.symbol,
                quantity=normalized_order.qty,
                side=side,
                order_type=order_type,
                validity=validity,
                limit_price=getattr(normalized_order, "limitPrice", None),
                trigger_price=getattr(normalized_order, "triggerPrice", None),
                basket_id=basket_id,
                tag=getattr(normalized_order, "tag", None),
                price=execution_price,
                total=round(order_cost, 2),
                status="REJECTED",
                idempotency_key=normalized_order.idempotencyKey,
                request_fingerprint=_build_request_fingerprint(normalized_order),
                trace_id=resolved_trace_id,
            )
            db.add(failed_order)
            db.commit()
            db.refresh(failed_order)
            return response, failed_order

        wallet.balance -= order_cost
        if existing:
            total_cost = (existing.avg_price * existing.quantity) + (execution_price * normalized_order.qty)
            existing.quantity += normalized_order.qty
            existing.avg_price = round(total_cost / existing.quantity, 2)
            existing.last_price = execution_price
            existing.pnl = round((execution_price - existing.avg_price) * existing.quantity, 2)
        else:
            db.add(
                HoldingModel(
                    symbol=normalized_order.symbol,
                    quantity=normalized_order.qty,
                    avg_price=execution_price,
                    last_price=execution_price,
                    pnl=0.0,
                )
            )
    else:
        if not existing or existing.quantity < normalized_order.qty:
            response = OrderResponse(
                status="error",
                order=normalized_order,
                message=f"Insufficient holdings: have {existing.quantity if existing else 0}, trying to sell {normalized_order.qty}",
                traceId=resolved_trace_id,
                idempotencyKey=normalized_order.idempotencyKey,
                errorCode="INSUFFICIENT_HOLDINGS",
            )
            failed_order = OrderModel(
                user_id=user_id,
                symbol=normalized_order.symbol,
                quantity=normalized_order.qty,
                side=side,
                order_type=order_type,
                validity=validity,
                limit_price=getattr(normalized_order, "limitPrice", None),
                trigger_price=getattr(normalized_order, "triggerPrice", None),
                basket_id=basket_id,
                tag=getattr(normalized_order, "tag", None),
                price=execution_price,
                total=round(execution_price * normalized_order.qty, 2),
                status="REJECTED",
                idempotency_key=normalized_order.idempotencyKey,
                request_fingerprint=_build_request_fingerprint(normalized_order),
                trace_id=resolved_trace_id,
            )
            db.add(failed_order)
            db.commit()
            db.refresh(failed_order)
            return response, failed_order

        sale_proceeds = execution_price * normalized_order.qty
        wallet.balance += sale_proceeds
        existing.quantity -= normalized_order.qty
        existing.last_price = execution_price
        if existing.quantity == 0:
            db.delete(existing)
        else:
            existing.pnl = round((execution_price - existing.avg_price) * existing.quantity, 2)

    order_db = OrderModel(
        user_id=user_id,
        symbol=normalized_order.symbol,
        quantity=normalized_order.qty,
        side=side,
        order_type=order_type,
        validity=validity,
        limit_price=getattr(normalized_order, "limitPrice", None),
        trigger_price=getattr(normalized_order, "triggerPrice", None),
        basket_id=basket_id,
        tag=getattr(normalized_order, "tag", None),
        price=execution_price,
        total=round(execution_price * normalized_order.qty, 2),
        status="PENDING",
        idempotency_key=normalized_order.idempotencyKey,
        request_fingerprint=_build_request_fingerprint(normalized_order),
        trace_id=resolved_trace_id,
    )
    db.add(order_db)
    _transition_order_status(order_db, status.upper())
    db.commit()
    db.refresh(order_db)

    logger.info(
        "Order executed trace_id=%s: %s %sx %s @ ₹%.2f | Wallet: ₹%.2f | user=%s",
        resolved_trace_id,
        side,
        normalized_order.qty,
        normalized_order.symbol,
        execution_price,
        wallet.balance,
        user_id,
    )

    return (
        OrderResponse(
            status="ok",
            order=normalized_order,
            message=f"{side} {normalized_order.qty} shares of {normalized_order.symbol} @ ₹{execution_price:.2f} = ₹{execution_price * normalized_order.qty:.2f} | Balance: ₹{wallet.balance:.2f}",
            orderId=order_db.id,
            executedPrice=round(order_db.price or 0.0, 2),
            total=round(order_db.total or 0.0, 2),
            orderStatus=order_db.status,
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            isDuplicate=False,
        ),
        order_db,
    )


def _create_trigger_entry(db: Session, order: Order, user_id: int = 1) -> TriggerOrderModel:
    trigger = TriggerOrderModel(
        user_id=user_id,
        symbol=order.symbol,
        quantity=order.qty,
        side=_normalize_side(order.side),
        order_type=_normalize_order_type(getattr(order, "orderType", "LIMIT")),
        validity=_normalize_validity(getattr(order, "validity", "GTC")),
        limit_price=getattr(order, "limitPrice", None),
        trigger_price=getattr(order, "triggerPrice", None),
        status="PENDING",
        tag=getattr(order, "tag", None),
    )
    db.add(trigger)
    db.commit()
    db.refresh(trigger)
    return trigger


def evaluate_pending_triggers(
    db: Session,
    user_id: int | None = None,
    symbols: Iterable[str] | None = None,
) -> list[dict]:
    query = db.query(TriggerOrderModel).filter(TriggerOrderModel.status == "PENDING")
    if user_id is not None:
        query = query.filter(TriggerOrderModel.user_id == user_id)

    pending = query.order_by(TriggerOrderModel.created_at.asc()).all()
    symbol_filter = {symbol.strip().upper() for symbol in (symbols or []) if symbol and symbol.strip()}

    results: list[dict] = []
    for entry in pending:
        if symbol_filter and entry.symbol.upper() not in symbol_filter:
            continue

        quote = fetch_quote(entry.symbol)
        live_price = float(quote.get("last") or 0.0)
        if live_price <= 0:
            continue

        simulated_order = Order(
            symbol=entry.symbol,
            qty=entry.quantity,
            side=entry.side,
            orderType=entry.order_type,
            validity=entry.validity,
            limitPrice=entry.limit_price,
            triggerPrice=entry.trigger_price,
            tag=entry.tag,
        )

        if _should_trigger(simulated_order, live_price):
            exec_price = _execution_price(simulated_order, live_price)
            response, order_row = _execute_order_at_price(
                db=db,
                order=simulated_order,
                execution_price=exec_price,
                user_id=entry.user_id,
                status="TRIGGER_EXECUTED",
            )
            next_status = "EXECUTED" if response.status == "ok" else "FAILED"
            _transition_trigger_status(entry, next_status)
            db.commit()
            results.append(
                {
                    "triggerId": entry.id,
                    "symbol": entry.symbol,
                    "status": entry.status,
                    "orderId": order_row.id,
                    "message": response.message,
                }
            )
        elif entry.validity == "IOC":
            _transition_trigger_status(entry, "CANCELLED")
            db.commit()
            results.append(
                {
                    "triggerId": entry.id,
                    "symbol": entry.symbol,
                    "status": entry.status,
                    "orderId": None,
                    "message": "IOC trigger not met and was cancelled",
                }
            )

    return results


def place_order(
    db: Session,
    order: Order,
    user_id: int = 1,
    idempotency_key: str | None = None,
    trace_id: str | None = None,
) -> OrderResponse:
    """Place a buy or sell order with idempotency and deterministic status handling."""
    normalized_order = _normalize_order_payload(order, idempotency_key=idempotency_key)
    resolved_trace_id = (trace_id or f"trc-{uuid4().hex[:16]}").strip()

    try:
        normalized_order.side = _normalize_side(normalized_order.side)
        normalized_order.orderType = _normalize_order_type(getattr(normalized_order, "orderType", "MARKET"))
        normalized_order.validity = _normalize_validity(getattr(normalized_order, "validity", "DAY"))
    except ValueError as exc:
        message = str(exc)
        error_code = "INVALID_SIDE"
        if "orderType" in message:
            error_code = "INVALID_ORDER_TYPE"
        elif "validity" in message:
            error_code = "INVALID_VALIDITY"
        return OrderResponse(
            status="error",
            order=normalized_order,
            message=message,
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode=error_code,
        )

    if not normalized_order.symbol:
        return OrderResponse(
            status="error",
            order=normalized_order,
            message="Symbol is required",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode="INVALID_SYMBOL",
        )

    if normalized_order.qty <= 0:
        return OrderResponse(
            status="error",
            order=normalized_order,
            message="Quantity must be greater than zero",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode="INVALID_QUANTITY",
        )

    if normalized_order.side not in {"BUY", "SELL"}:
        return OrderResponse(
            status="error",
            order=normalized_order,
            message="Side must be BUY or SELL",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode="INVALID_SIDE",
        )

    if (
        normalized_order.idempotencyKey
        and len(normalized_order.idempotencyKey) > MAX_IDEMPOTENCY_KEY_LENGTH
    ):
        return OrderResponse(
            status="error",
            order=normalized_order,
            message=f"Idempotency key exceeds {MAX_IDEMPOTENCY_KEY_LENGTH} characters",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode="INVALID_IDEMPOTENCY_KEY",
        )

    if normalized_order.idempotencyKey:
        current_fingerprint = _build_request_fingerprint(normalized_order)
        duplicate_order = (
            db.query(OrderModel)
            .filter(
                OrderModel.idempotency_key == normalized_order.idempotencyKey,
                OrderModel.user_id == user_id,
            )
            .order_by(OrderModel.id.desc())
            .first()
        )
        if duplicate_order:
            existing_fingerprint = (duplicate_order.request_fingerprint or "").strip()
            if existing_fingerprint:
                same_payload = existing_fingerprint == current_fingerprint
            else:
                # Backward compatibility for older rows that do not carry fingerprints.
                same_payload = (
                    (duplicate_order.symbol or "").strip().upper() == normalized_order.symbol
                    and int(duplicate_order.quantity or 0) == int(normalized_order.qty)
                    and (duplicate_order.side or "").strip().upper() == normalized_order.side
                )

            if not same_payload:
                return OrderResponse(
                    status="error",
                    order=normalized_order,
                    message="Idempotency key already used with a different order payload",
                    orderId=duplicate_order.id,
                    orderStatus=duplicate_order.status,
                    traceId=duplicate_order.trace_id or resolved_trace_id,
                    idempotencyKey=duplicate_order.idempotency_key,
                    errorCode="IDEMPOTENCY_KEY_REUSED",
                    isDuplicate=False,
                )

            return OrderResponse(
                status="ok",
                order=normalized_order,
                message="Duplicate order request acknowledged. Returning existing execution result.",
                orderId=duplicate_order.id,
                executedPrice=round(duplicate_order.price or 0.0, 2),
                total=round(duplicate_order.total or 0.0, 2),
                orderStatus=duplicate_order.status,
                traceId=duplicate_order.trace_id or resolved_trace_id,
                idempotencyKey=duplicate_order.idempotency_key,
                isDuplicate=True,
            )
    market = is_market_open()
    if not market.isOpen:
        return OrderResponse(
            status="error",
            order=normalized_order,
            message=f"Cannot trade: {market.message}",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode="MARKET_CLOSED",
        )

    live_quote = fetch_quote(normalized_order.symbol)
    live_price = float(live_quote.get("last") or 0.0)

    if live_price <= 0:
        return OrderResponse(
            status="error",
            order=normalized_order,
            message=f"Could not fetch live price for {normalized_order.symbol}",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode="PRICE_UNAVAILABLE",
        )

    if normalized_order.orderType in {"LIMIT", "SL", "SLM"} and not _should_trigger(normalized_order, live_price):
        if normalized_order.validity == "IOC":
            return OrderResponse(
                status="error",
                order=normalized_order,
                message="IOC order cancelled because trigger/limit condition was not met",
                traceId=resolved_trace_id,
                idempotencyKey=normalized_order.idempotencyKey,
                errorCode="TRIGGER_NOT_MET",
            )

        trigger = _create_trigger_entry(db, normalized_order, user_id=user_id)
        return OrderResponse(
            status="ok",
            order=normalized_order,
            message=f"Order accepted as server-side trigger (id={trigger.id}) and will execute when conditions are met",
            orderStatus="PENDING",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
        )

    exec_price = _execution_price(normalized_order, live_price)
    response, _ = _execute_order_at_price(
        db=db,
        order=normalized_order,
        execution_price=exec_price,
        user_id=user_id,
        idempotency_key=normalized_order.idempotencyKey,
        trace_id=resolved_trace_id,
    )
    return response
