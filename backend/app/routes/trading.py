"""
Trading operations - uses real market data from Yahoo Finance
for prices, with local SQLite for portfolio/orders/alerts.
Enforces market hours and wallet balance checks.
"""

from datetime import datetime, time
import hashlib
import pytz
from uuid import uuid4
from sqlalchemy.orm import Session
from ..database.db import HoldingModel, OrderModel, AlertModel, WalletModel
from ..models.schemas import Order, OrderResponse, Holding, Wallet, WalletResponse, MarketStatus
from ..market_data import fetch_quote

import logging

logger = logging.getLogger(__name__)

IST = pytz.timezone("Asia/Kolkata")
ORDER_STATUS_TRANSITIONS: dict[str, set[str]] = {
    "PENDING": {"COMPLETED", "REJECTED"},
    "COMPLETED": set(),
    "REJECTED": set(),
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
    return Order(symbol=symbol, qty=order.qty, side=side, idempotencyKey=resolved_key)


def _build_request_fingerprint(order: Order) -> str:
    payload = f"{order.symbol}|{order.side}|{order.qty}"
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def _transition_order_status(order_db: OrderModel, next_status: str) -> None:
    current_status = (order_db.status or "PENDING").upper()
    target_status = next_status.upper()
    allowed = ORDER_STATUS_TRANSITIONS.get(current_status, set())
    if target_status not in allowed:
        raise ValueError(f"Invalid transition from {current_status} to {target_status}")
    order_db.status = target_status


def _get_trading_wallet(db: Session) -> WalletModel:
    """Use a dedicated system wallet for non-auth trading endpoints."""
    wallet = db.query(WalletModel).filter(WalletModel.user_id == TRADING_WALLET_USER_ID).first()
    if not wallet:
        wallet = WalletModel(user_id=TRADING_WALLET_USER_ID, balance=DEFAULT_TRADING_WALLET_BALANCE)
        db.add(wallet)
        db.commit()
        db.refresh(wallet)
    return wallet


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



def get_wallet(db: Session, user_id: int) -> Wallet:
    """Get current wallet balance for a user. Creates wallet with ₹100,000 if first time."""
    wallet = db.query(WalletModel).filter(WalletModel.user_id == user_id).first()
    if not wallet:
        wallet = WalletModel(user_id=user_id, balance=DEFAULT_TRADING_WALLET_BALANCE)
        db.add(wallet)
        db.commit()
        db.refresh(wallet)
    return Wallet(balance=round(wallet.balance, 2))



def add_funds(db: Session, user_id: int, amount: float) -> WalletResponse:
    """Add funds to a user's wallet."""
    if amount <= 0:
        return WalletResponse(status="error", balance=0, message="Amount must be positive")

    wallet = db.query(WalletModel).filter(WalletModel.user_id == user_id).first()
    if not wallet:
        wallet = WalletModel(user_id=user_id, balance=amount)
        db.add(wallet)
    else:
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

    wallet = db.query(WalletModel).filter(WalletModel.user_id == user_id).first()
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


def place_order(
    db: Session,
    order: Order,
    idempotency_key: str | None = None,
    trace_id: str | None = None,
) -> OrderResponse:
    """Place a buy or sell order with idempotency and deterministic status handling."""
    normalized_order = _normalize_order_payload(order, idempotency_key=idempotency_key)
    resolved_trace_id = (trace_id or f"trc-{uuid4().hex[:16]}").strip()

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
        duplicate_order = (
            db.query(OrderModel)
            .filter(OrderModel.idempotency_key == normalized_order.idempotencyKey)
            .order_by(OrderModel.id.desc())
            .first()
        )
        if duplicate_order:
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
    live_price = live_quote["last"]

    if live_price <= 0:
        return OrderResponse(
            status="error",
            order=normalized_order,
            message=f"Could not fetch live price for {normalized_order.symbol}",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode="PRICE_UNAVAILABLE",
        )

    wallet = _get_trading_wallet(db)
    existing = db.query(HoldingModel).filter(HoldingModel.symbol == normalized_order.symbol).first()

    try:
        if normalized_order.side == "BUY":
            order_cost = live_price * normalized_order.qty
            if wallet.balance < order_cost:
                return OrderResponse(
                    status="error",
                    order=normalized_order,
                    message=f"Insufficient funds. Need ₹{order_cost:.2f} but wallet has ₹{wallet.balance:.2f}",
                    traceId=resolved_trace_id,
                    idempotencyKey=normalized_order.idempotencyKey,
                    errorCode="INSUFFICIENT_FUNDS",
                )

            wallet.balance -= order_cost

            if existing:
                total_cost = (existing.avg_price * existing.quantity) + (live_price * normalized_order.qty)
                existing.quantity += normalized_order.qty
                existing.avg_price = round(total_cost / existing.quantity, 2)
                existing.last_price = live_price
                existing.pnl = round((live_price - existing.avg_price) * existing.quantity, 2)
            else:
                new_holding = HoldingModel(
                    symbol=normalized_order.symbol,
                    quantity=normalized_order.qty,
                    avg_price=live_price,
                    last_price=live_price,
                    pnl=0.0,
                )
                db.add(new_holding)

        elif normalized_order.side == "SELL":
            if not existing or existing.quantity < normalized_order.qty:
                return OrderResponse(
                    status="error",
                    order=normalized_order,
                    message=f"Insufficient holdings: have {existing.quantity if existing else 0}, trying to sell {normalized_order.qty}",
                    traceId=resolved_trace_id,
                    idempotencyKey=normalized_order.idempotencyKey,
                    errorCode="INSUFFICIENT_HOLDINGS",
                )

            sale_proceeds = live_price * normalized_order.qty
            wallet.balance += sale_proceeds

            existing.quantity -= normalized_order.qty
            existing.last_price = live_price
            if existing.quantity == 0:
                db.delete(existing)
            else:
                existing.pnl = round((live_price - existing.avg_price) * existing.quantity, 2)

        order_db = OrderModel(
            symbol=normalized_order.symbol,
            quantity=normalized_order.qty,
            side=normalized_order.side,
            price=live_price,
            total=round(live_price * normalized_order.qty, 2),
            status="PENDING",
            idempotency_key=normalized_order.idempotencyKey,
            request_fingerprint=_build_request_fingerprint(normalized_order),
            trace_id=resolved_trace_id,
        )
        db.add(order_db)

        _transition_order_status(order_db, "COMPLETED")
        db.commit()
        db.refresh(order_db)

    except Exception:
        db.rollback()
        logger.exception("Order execution failed trace_id=%s", resolved_trace_id)
        return OrderResponse(
            status="error",
            order=normalized_order,
            message="Order could not be completed at this time",
            traceId=resolved_trace_id,
            idempotencyKey=normalized_order.idempotencyKey,
            errorCode="ORDER_EXECUTION_FAILED",
        )

    logger.info(
        "Order executed trace_id=%s: %s %sx %s @ ₹%.2f | Wallet: ₹%.2f",
        resolved_trace_id,
        normalized_order.side,
        normalized_order.qty,
        normalized_order.symbol,
        live_price,
        wallet.balance,
    )

    return OrderResponse(
        status="ok",
        order=normalized_order,
        message=(
            f"{normalized_order.side} {normalized_order.qty} shares of {normalized_order.symbol} "
            f"@ ₹{live_price:.2f} = ₹{live_price * normalized_order.qty:.2f} | Balance: ₹{wallet.balance:.2f}"
        ),
        orderId=order_db.id,
        executedPrice=round(order_db.price or 0.0, 2),
        total=round(order_db.total or 0.0, 2),
        orderStatus=order_db.status,
        traceId=resolved_trace_id,
        idempotencyKey=normalized_order.idempotencyKey,
        isDuplicate=False,
    )
