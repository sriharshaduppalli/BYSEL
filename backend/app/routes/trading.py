import random
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from ..database.db import QuoteModel, HoldingModel, OrderModel
from ..models.schemas import Quote, Holding, Order, OrderResponse

SYMBOLS = ["RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN", "WIPRO", "BAJAJFINSV", "HDFC", "LT", "MARUTI"]

def generate_mock_quote(symbol: str) -> Quote:
    """Generate a mock quote for a symbol"""
    price = round(random.uniform(1000, 5000), 2)
    pct_change = round(random.uniform(-2, 2), 2)
    return Quote(
        symbol=symbol,
        last=price,
        pctChange=pct_change,
        timestamp=datetime.utcnow()
    )

def get_quotes(db: Session, symbols: str = "") -> list[Quote]:
    """Get quotes for specified symbols or all default symbols"""
    syms = symbols.split(",") if symbols else SYMBOLS
    quotes = []
    for sym in syms:
        quote = generate_mock_quote(sym.strip())
        quotes.append(quote)
    return quotes

def get_holdings(db: Session) -> list[Holding]:
    """Get all holdings"""
    holdings_db = db.query(HoldingModel).all()
    holdings = []
    for h in holdings_db:
        holdings.append(Holding(
            symbol=h.symbol,
            qty=h.quantity,
            avgPrice=h.avg_price,
            last=h.last_price,
            pnl=h.pnl
        ))
    return holdings

def place_order(db: Session, order: Order) -> OrderResponse:
    """Place an order (BUY or SELL)"""
    existing = db.query(HoldingModel).filter(HoldingModel.symbol == order.symbol).first()
    
    if order.side == "BUY":
        if existing:
            existing.quantity += order.qty
        else:
            new_holding = HoldingModel(
                symbol=order.symbol,
                quantity=order.qty,
                avg_price=2000.0,
                last_price=2000.0,
                pnl=0.0
            )
            db.add(new_holding)
    elif order.side == "SELL":
        if existing and existing.quantity >= order.qty:
            existing.quantity -= order.qty
            if existing.quantity == 0:
                db.delete(existing)
    
    # Record the order
    order_db = OrderModel(
        symbol=order.symbol,
        quantity=order.qty,
        side=order.side,
        status="COMPLETED"
    )
    db.add(order_db)
    db.commit()
    
    return OrderResponse(
        status="ok",
        order=order,
        message=f"{order.side} order for {order.qty} shares of {order.symbol} completed"
    )
