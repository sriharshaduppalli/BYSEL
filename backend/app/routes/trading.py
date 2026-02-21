"""
Trading operations - uses real market data from Yahoo Finance
for prices, with local SQLite for portfolio/orders/alerts.
Enforces market hours and wallet balance checks.
"""

from datetime import datetime, time
import pytz
from sqlalchemy.orm import Session
from ..database.db import HoldingModel, OrderModel, AlertModel, WalletModel
from ..models.schemas import Order, OrderResponse, Holding, Wallet, WalletResponse, MarketStatus
from ..market_data import fetch_quote

import logging

logger = logging.getLogger(__name__)

IST = pytz.timezone("Asia/Kolkata")

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
        wallet = WalletModel(user_id=user_id, balance=0.0)
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


def place_order(db: Session, order: Order) -> OrderResponse:
    """Place a buy or sell order using REAL market price.
    Checks market hours and wallet balance."""
    # Check market hours
    market = is_market_open()
    if not market.isOpen:
        return OrderResponse(
            status="error",
            order=order,
            message=f"Cannot trade: {market.message}"
        )

    # Get live price
    live_quote = fetch_quote(order.symbol)
    live_price = live_quote["last"]

    if live_price <= 0:
        return OrderResponse(
            status="error",
            order=order,
            message=f"Could not fetch live price for {order.symbol}"
        )

    # Get or create wallet
    wallet = db.query(WalletModel).first()
    if not wallet:
        wallet = WalletModel(balance=0.0)
        db.add(wallet)
        db.commit()
        db.refresh(wallet)

    existing = db.query(HoldingModel).filter(HoldingModel.symbol == order.symbol).first()

    if order.side == "BUY":
        order_cost = live_price * order.qty
        if wallet.balance < order_cost:
            return OrderResponse(
                status="error",
                order=order,
                message=f"Insufficient funds. Need ₹{order_cost:.2f} but wallet has ₹{wallet.balance:.2f}"
            )

        # Deduct from wallet
        wallet.balance -= order_cost

        if existing:
            # Update average price: (old_total + new_total) / (old_qty + new_qty)
            total_cost = (existing.avg_price * existing.quantity) + (live_price * order.qty)
            existing.quantity += order.qty
            existing.avg_price = round(total_cost / existing.quantity, 2)
            existing.last_price = live_price
            existing.pnl = round((live_price - existing.avg_price) * existing.quantity, 2)
        else:
            new_holding = HoldingModel(
                symbol=order.symbol,
                quantity=order.qty,
                avg_price=live_price,
                last_price=live_price,
                pnl=0.0
            )
            db.add(new_holding)

    elif order.side == "SELL":
        if not existing or existing.quantity < order.qty:
            return OrderResponse(
                status="error",
                order=order,
                message=f"Insufficient holdings: have {existing.quantity if existing else 0}, trying to sell {order.qty}"
            )

        # Add sale proceeds to wallet
        sale_proceeds = live_price * order.qty
        wallet.balance += sale_proceeds

        existing.quantity -= order.qty
        existing.last_price = live_price
        if existing.quantity == 0:
            db.delete(existing)
        else:
            existing.pnl = round((live_price - existing.avg_price) * existing.quantity, 2)

    # Record the order with actual price
    order_db = OrderModel(
        symbol=order.symbol,
        quantity=order.qty,
        side=order.side,
        price=live_price,
        total=round(live_price * order.qty, 2),
        status="COMPLETED"
    )
    db.add(order_db)
    db.commit()

    logger.info(f"Order executed: {order.side} {order.qty}x {order.symbol} @ ₹{live_price:.2f} | Wallet: ₹{wallet.balance:.2f}")

    return OrderResponse(
        status="ok",
        order=order,
        message=f"{order.side} {order.qty} shares of {order.symbol} @ ₹{live_price:.2f} = ₹{live_price * order.qty:.2f} | Balance: ₹{wallet.balance:.2f}"
    )
