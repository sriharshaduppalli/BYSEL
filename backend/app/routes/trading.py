"""
Trading operations - uses real market data from Yahoo Finance
for prices, with local SQLite for portfolio/orders/alerts.
"""

from datetime import datetime
from sqlalchemy.orm import Session
from ..database.db import HoldingModel, OrderModel, AlertModel
from ..models.schemas import Order, OrderResponse, Holding
from ..market_data import fetch_quote

import logging

logger = logging.getLogger(__name__)


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
    """Place a buy or sell order using REAL market price."""
    # Get live price
    live_quote = fetch_quote(order.symbol)
    live_price = live_quote["last"]

    if live_price <= 0:
        return OrderResponse(
            status="error",
            order=order,
            message=f"Could not fetch live price for {order.symbol}"
        )

    existing = db.query(HoldingModel).filter(HoldingModel.symbol == order.symbol).first()

    if order.side == "BUY":
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

    logger.info(f"Order executed: {order.side} {order.qty}x {order.symbol} @ ₹{live_price:.2f}")

    return OrderResponse(
        status="ok",
        order=order,
        message=f"{order.side} {order.qty} shares of {order.symbol} @ ₹{live_price:.2f} = ₹{live_price * order.qty:.2f}"
    )
