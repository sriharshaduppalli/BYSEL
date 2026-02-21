from fastapi import APIRouter, Depends, Query, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from ..database.db import get_db, AlertModel, OrderModel, HoldingModel
from .dependencies import get_current_user
from ..models.schemas import (
    Quote, Holding, Order, OrderResponse, Alert, AlertCreate,
    AlertResponse, HealthCheck, TradeHistory, PortfolioSummary, PortfolioValue,
    Wallet, WalletTransaction, WalletResponse, MarketStatus
)
from .trading import (
    get_holdings, get_holding, place_order,
    is_market_open, get_wallet, add_funds, withdraw_funds
)
from ..market_data import (
    fetch_quote, fetch_quotes, get_all_symbols, get_default_symbols,
    search_stocks, get_symbols_with_names, get_stock_name, INDIAN_STOCKS
)
from ..ai_engine import analyze_stock, predict_price, ai_assistant
from ..portfolio_scorer import calculate_portfolio_health
from ..market_heatmap import get_market_heatmap, get_sector_detail

router = APIRouter()


# ==================== QUOTES (LIVE DATA) ====================

@router.get("/quotes", response_model=list[Quote])
async def get_quotes_endpoint(
    symbols: str = Query(""),
    db: Session = Depends(get_db)
):
    """Get live quotes for specified symbols (comma-separated) or defaults."""
    if symbols:
        sym_list = [s.strip().upper() for s in symbols.split(",") if s.strip()]
    else:
        sym_list = get_default_symbols()

    raw_quotes = fetch_quotes(sym_list)
    return [Quote(
        symbol=q["symbol"],
        last=q["last"],
        pctChange=q["pctChange"],
    ) for q in raw_quotes]


@router.get("/quotes/all", response_model=list[Quote])
async def get_all_quotes_endpoint():
    """Get live quotes for ALL supported NSE symbols."""
    raw_quotes = fetch_quotes(get_all_symbols())
    return [Quote(
        symbol=q["symbol"],
        last=q["last"],
        pctChange=q["pctChange"],
    ) for q in raw_quotes]


@router.get("/quotes/{symbol}", response_model=Quote)
async def get_single_quote_endpoint(symbol: str):
    """Get a live quote for a single stock symbol."""
    q = fetch_quote(symbol.upper())
    if q["last"] == 0:
        raise HTTPException(status_code=404, detail=f"Quote not found for {symbol}")
    return Quote(
        symbol=q["symbol"],
        last=q["last"],
        pctChange=q["pctChange"],
    )


# ==================== HOLDINGS ====================

@router.get("/holdings", response_model=list[Holding])
async def get_holdings_endpoint(db: Session = Depends(get_db)):
    """Get all holdings with live prices."""
    return get_holdings(db)


@router.get("/holdings/{symbol}", response_model=Holding)
async def get_holding_endpoint(symbol: str, db: Session = Depends(get_db)):
    """Get a single holding by symbol."""
    holding = get_holding(db, symbol.upper())
    if not holding:
        raise HTTPException(status_code=404, detail=f"No holding found for {symbol}")
    return holding


# ==================== TRADING ====================

@router.post("/order", response_model=OrderResponse)
async def place_order_endpoint(order: Order, db: Session = Depends(get_db)):
    """Place a buy or sell order at live market price."""
    return place_order(db, order)


@router.post("/trade/buy", response_model=OrderResponse)
async def buy_stock_endpoint(order: Order, db: Session = Depends(get_db)):
    """Buy stock at live market price."""
    order.side = "BUY"
    return place_order(db, order)


@router.post("/trade/sell", response_model=OrderResponse)
async def sell_stock_endpoint(order: Order, db: Session = Depends(get_db)):
    """Sell stock at live market price."""
    order.side = "SELL"
    return place_order(db, order)


@router.get("/trades/history", response_model=list[TradeHistory])
async def get_trade_history_endpoint(db: Session = Depends(get_db)):
    """Get all trade history."""
    orders = db.query(OrderModel).order_by(OrderModel.created_at.desc()).all()
    return [TradeHistory(
        id=o.id,
        symbol=o.symbol,
        side=o.side,
        quantity=o.quantity,
        price=o.price or 0,
        total=o.total or 0,
        timestamp=int(o.created_at.timestamp() * 1000) if o.created_at else 0
    ) for o in orders]


@router.get("/trades/history/{symbol}", response_model=list[TradeHistory])
async def get_trade_history_for_symbol(symbol: str, db: Session = Depends(get_db)):
    """Get trade history for a specific symbol."""
    orders = db.query(OrderModel).filter(
        OrderModel.symbol == symbol.upper()
    ).order_by(OrderModel.created_at.desc()).all()
    return [TradeHistory(
        id=o.id,
        symbol=o.symbol,
        side=o.side,
        quantity=o.quantity,
        price=o.price or 0,
        total=o.total or 0,
        timestamp=int(o.created_at.timestamp() * 1000) if o.created_at else 0
    ) for o in orders]


# ==================== PORTFOLIO ====================

@router.get("/portfolio", response_model=PortfolioSummary)
async def get_portfolio_endpoint(db: Session = Depends(get_db)):
    """Get portfolio summary with live values."""
    holdings = get_holdings(db)

    total_value = sum(h.last * h.qty for h in holdings)
    total_invested = sum(h.avgPrice * h.qty for h in holdings)
    total_pnl = total_value - total_invested
    total_pnl_pct = round((total_pnl / total_invested) * 100, 2) if total_invested > 0 else 0.0

    return PortfolioSummary(
        totalValue=round(total_value, 2),
        totalInvested=round(total_invested, 2),
        totalPnL=round(total_pnl, 2),
        totalPnLPercent=total_pnl_pct,
        holdingsCount=len(holdings)
    )


@router.get("/portfolio/value", response_model=PortfolioValue)
async def get_portfolio_value_endpoint(db: Session = Depends(get_db)):
    """Get portfolio current value with live prices."""
    holdings = get_holdings(db)

    total_value = sum(h.last * h.qty for h in holdings)
    total_invested = sum(h.avgPrice * h.qty for h in holdings)
    total_pnl = total_value - total_invested
    total_pnl_pct = round((total_pnl / total_invested) * 100, 2) if total_invested > 0 else 0.0

    return PortfolioValue(
        value=round(total_value, 2),
        invested=round(total_invested, 2),
        pnl=round(total_pnl, 2),
        pnlPercent=total_pnl_pct
    )


# ==================== WALLET ====================


@router.get("/wallet", response_model=Wallet)
async def get_wallet_endpoint(db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Get current wallet balance for the authenticated user."""
    return get_wallet(db, user.id)



@router.post("/wallet/add", response_model=WalletResponse)
async def add_funds_endpoint(txn: WalletTransaction, db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Add funds to the authenticated user's wallet."""
    return add_funds(db, user.id, txn.amount)



@router.post("/wallet/withdraw", response_model=WalletResponse)
async def withdraw_funds_endpoint(txn: WalletTransaction, db: Session = Depends(get_db), user=Depends(get_current_user)):
    """Withdraw funds from the authenticated user's wallet."""
    return withdraw_funds(db, user.id, txn.amount)


# ==================== MARKET STATUS ====================

@router.get("/market/status", response_model=MarketStatus)
async def market_status_endpoint():
    """Check if NSE market is currently open (9:15 AM - 3:30 PM IST, Mon-Fri)."""
    return is_market_open()


# ==================== ALERTS ====================

@router.get("/alerts", response_model=list[Alert])
async def get_alerts_endpoint(db: Session = Depends(get_db)):
    """Get all alerts."""
    alerts = db.query(AlertModel).all()
    return [Alert(
        id=a.id,
        symbol=a.symbol,
        thresholdPrice=a.threshold_price,
        alertType=a.alert_type,
        isActive=a.is_active,
        createdAt=a.created_at
    ) for a in alerts]


@router.get("/alerts/active", response_model=list[Alert])
async def get_active_alerts_endpoint(db: Session = Depends(get_db)):
    """Get active alerts only."""
    alerts = db.query(AlertModel).filter(AlertModel.is_active == True).all()
    return [Alert(
        id=a.id,
        symbol=a.symbol,
        thresholdPrice=a.threshold_price,
        alertType=a.alert_type,
        isActive=a.is_active,
        createdAt=a.created_at
    ) for a in alerts]


@router.post("/alerts", response_model=Alert)
async def create_alert_endpoint(alert: AlertCreate, db: Session = Depends(get_db)):
    """Create a new price alert."""
    alert_db = AlertModel(
        symbol=alert.symbol.upper(),
        threshold_price=alert.thresholdPrice,
        alert_type=alert.alertType,
        is_active=True
    )
    db.add(alert_db)
    db.commit()
    db.refresh(alert_db)
    return Alert(
        id=alert_db.id,
        symbol=alert_db.symbol,
        thresholdPrice=alert_db.threshold_price,
        alertType=alert_db.alert_type,
        isActive=alert_db.is_active,
        createdAt=alert_db.created_at
    )


@router.put("/alerts/{alert_id}", response_model=Alert)
async def update_alert_endpoint(alert_id: int, alert: AlertCreate, db: Session = Depends(get_db)):
    """Update an existing alert."""
    alert_db = db.query(AlertModel).filter(AlertModel.id == alert_id).first()
    if not alert_db:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")

    alert_db.symbol = alert.symbol.upper()
    alert_db.threshold_price = alert.thresholdPrice
    alert_db.alert_type = alert.alertType
    db.commit()
    db.refresh(alert_db)
    return Alert(
        id=alert_db.id,
        symbol=alert_db.symbol,
        thresholdPrice=alert_db.threshold_price,
        alertType=alert_db.alert_type,
        isActive=alert_db.is_active,
        createdAt=alert_db.created_at
    )


@router.delete("/alert/{alert_id}", response_model=AlertResponse)
async def delete_alert_endpoint(alert_id: int, db: Session = Depends(get_db)):
    """Delete/deactivate an alert."""
    alert_db = db.query(AlertModel).filter(AlertModel.id == alert_id).first()
    if not alert_db:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")

    alert_db.is_active = False
    db.commit()
    return AlertResponse(status="ok", message=f"Alert {alert_id} deactivated", id=alert_id)


# ==================== SEARCH ====================

@router.get("/search")
async def search_stocks_endpoint(
    q: str = Query("", description="Search query (symbol or company name)"),
    limit: int = Query(50, description="Max results"),
):
    """Search for Indian stocks by symbol or company name.
    Covers NIFTY 500+ stocks. Unknown symbols are tried on Yahoo Finance."""
    results = search_stocks(q, limit=limit)
    return results


@router.get("/symbols")
async def get_symbols_endpoint():
    """Get all available stock symbols with company names.
    Returns 500+ Indian stocks (NSE & BSE)."""
    return get_symbols_with_names()


@router.get("/symbols/count")
async def get_symbols_count():
    """Get count of available symbols."""
    return {"count": len(INDIAN_STOCKS), "exchange": "NSE/BSE"}


# ==================== HEALTH ====================

@router.get("/health", response_model=HealthCheck)
async def health_check():
    """Health check endpoint."""
    return HealthCheck(status="healthy", version="2.0.0")


# ==================== AI STOCK ASSISTANT ====================

class AiQuery(BaseModel):
    query: str

@router.post("/ai/ask")
async def ai_ask_endpoint(body: AiQuery):
    """Natural language AI stock assistant.
    Examples: 'Should I buy RELIANCE?', 'Predict TCS price', 'Compare INFY and TCS'"""
    result = ai_assistant(body.query)
    return result


@router.get("/ai/analyze/{symbol}")
async def ai_analyze_endpoint(symbol: str):
    """Get comprehensive AI analysis for a stock including technical,
    fundamental analysis, score, prediction, and plain-English summary."""
    result = analyze_stock(symbol.upper())
    if "error" in result and "predictions" not in result:
        raise HTTPException(status_code=404, detail=result["error"])
    return result


@router.get("/ai/predict/{symbol}")
async def ai_predict_endpoint(symbol: str):
    """Get AI price predictions for 1-week, 1-month, and 3-month horizons
    with confidence intervals and direction signals."""
    result = predict_price(symbol.upper())
    if "error" in result and not result.get("predictions"):
        raise HTTPException(status_code=404, detail=result["error"])
    return result


# ==================== PORTFOLIO HEALTH SCORE ====================

@router.get("/portfolio/health")
async def portfolio_health_endpoint(db: Session = Depends(get_db)):
    """Get portfolio health score (0-100) with breakdown and suggestions.
    Analyzes diversification, risk, quality, and balance."""
    holdings_db = db.query(HoldingModel).all()
    holdings_list = []
    for h in holdings_db:
        holdings_list.append({
            "symbol": h.symbol,
            "quantity": h.quantity,
            "avgPrice": h.avg_price,
        })
    result = calculate_portfolio_health(holdings_list)
    return result


# ==================== MARKET HEATMAP ====================

@router.get("/market/heatmap")
async def market_heatmap_endpoint():
    """Get real-time market heatmap with sector-wise performance,
    market breadth, mood indicator, and individual stock data."""
    result = get_market_heatmap()
    return result


@router.get("/market/sector/{sector_name}")
async def sector_detail_endpoint(sector_name: str):
    """Get detailed data for a specific sector."""
    result = get_sector_detail(sector_name)
    if not result:
        raise HTTPException(status_code=404, detail=f"Sector '{sector_name}' not found")
    return result
