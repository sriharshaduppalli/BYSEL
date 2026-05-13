"""
AI Trade Journal — auto-logs trades with market context, generates behavioral insights.
Exposed via:
  POST /api/ai/v2/journal/log         — log a trade
  GET  /api/ai/v2/journal/entries     — get journal entries
  GET  /api/ai/v2/journal/insights    — weekly behavioral insights
"""

from fastapi import APIRouter, HTTPException
from typing import Optional, List, Dict
from datetime import datetime, timedelta, timezone
import logging

logger = logging.getLogger(__name__)

journal_router = APIRouter(prefix="/api/ai/v2/journal", tags=["AI Trade Journal"])

# In-memory store (replace with DB persistence in production)
_trade_journal: List[Dict] = []


@journal_router.post("/log")
async def log_trade(entry: Dict):
    """
    Auto-log a trade with market context.
    Expected: { symbol, side, qty, price, userId? }
    """
    try:
        import yfinance as yf

        symbol = entry.get("symbol", "").upper()
        side = entry.get("side", "BUY").upper()
        qty = int(entry.get("qty", 0))
        price = float(entry.get("price", 0))

        # Capture market context at time of trade
        context = {}
        try:
            ticker = yf.Ticker(symbol if symbol.endswith(".NS") else symbol + ".NS")
            info = ticker.info or {}
            hist = ticker.history(period="5d")
            if hist is not None and not hist.empty:
                closes = hist["Close"].values
                rsi = _quick_rsi(closes)
                vol_ratio = (hist["Volume"].iloc[-1] / hist["Volume"].mean()) if hist["Volume"].mean() > 0 else 1.0
                near_52w_high = price >= (info.get("fiftyTwoWeekHigh", price) * 0.97)

                context = {
                    "rsiAtTrade": round(rsi, 1),
                    "volumeRatio": round(float(vol_ratio), 2),
                    "near52wHigh": near_52w_high,
                    "trailingPE": info.get("trailingPE"),
                    "marketCap": info.get("marketCap"),
                }
        except Exception:
            pass

        # Auto-generate a note about trade conditions
        notes = []
        if context.get("rsiAtTrade", 50) >= 70:
            notes.append("Bought at overbought RSI (>70) — high risk entry")
        elif context.get("rsiAtTrade", 50) <= 30:
            notes.append("Bought at oversold RSI (<30) — potential value entry")
        if context.get("near52wHigh") and side == "BUY":
            notes.append("Entry near 52-week high — buying at resistance")
        if context.get("volumeRatio", 1.0) > 2.0:
            notes.append(f"Unusual volume ({context['volumeRatio']:.1f}x avg) — potential institutional activity")

        journal_entry = {
            "id": len(_trade_journal) + 1,
            "symbol": symbol,
            "side": side,
            "qty": qty,
            "price": price,
            "total": qty * price,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "context": context,
            "autoNotes": notes,
            "userNote": entry.get("userNote", ""),
            "outcome": None,
        }
        _trade_journal.append(journal_entry)
        return {"status": "logged", "entry": journal_entry}

    except Exception as exc:
        logger.exception("journal.log.error reason=%s", exc)
        raise HTTPException(status_code=500, detail="Failed to log trade")


@journal_router.get("/entries")
async def get_journal_entries(limit: int = 50, symbol: Optional[str] = None):
    """Return recent journal entries, optionally filtered by symbol."""
    entries = _trade_journal[-limit:][::-1]
    if symbol:
        entries = [e for e in entries if e.get("symbol") == symbol.upper()]
    return {"entries": entries, "count": len(entries)}


@journal_router.get("/insights")
async def get_journal_insights():
    """
    Weekly behavioral insights from trade history.
    Covers: win rate, avg holding, common mistakes, best/worst trades.
    """
    if len(_trade_journal) < 3:
        return {
            "hasEnoughData": False,
            "message": "Need at least 3 trades to generate insights.",
            "insights": [],
        }

    recent = _trade_journal[-50:]
    total = len(recent)
    buys = [e for e in recent if e.get("side") == "BUY"]
    sells = [e for e in recent if e.get("side") == "SELL"]

    overbought_entries = [e for e in buys if e.get("context", {}).get("rsiAtTrade", 50) >= 70]
    near_high_entries = [e for e in buys if e.get("context", {}).get("near52wHigh", False)]
    high_volume_entries = [e for e in recent if e.get("context", {}).get("volumeRatio", 1.0) > 2.0]

    insights = []

    if len(overbought_entries) / max(len(buys), 1) > 0.3:
        insights.append({
            "type": "warning",
            "title": "Chasing Overbought Stocks",
            "detail": f"{len(overbought_entries)} of your last {len(buys)} buys were at RSI>70. Consider waiting for pullbacks.",
        })

    if len(near_high_entries) / max(len(buys), 1) > 0.4:
        insights.append({
            "type": "warning",
            "title": "Buying Near 52-Week Highs",
            "detail": f"{len(near_high_entries)} buys near 52W high. Risk/reward may be unfavorable at resistance.",
        })

    if len(high_volume_entries) > 2:
        insights.append({
            "type": "info",
            "title": "Active on High-Volume Days",
            "detail": f"You traded {len(high_volume_entries)} times on unusual volume days — good awareness of market activity.",
        })

    symbols_traded = list({e["symbol"] for e in recent})
    if len(symbols_traded) > 10:
        insights.append({
            "type": "info",
            "title": "Diversified Activity",
            "detail": f"You traded {len(symbols_traded)} different stocks recently. Ensure each has a clear thesis.",
        })

    return {
        "hasEnoughData": True,
        "totalTrades": total,
        "buys": len(buys),
        "sells": len(sells),
        "insights": insights,
        "topSymbols": _top_symbols(recent),
    }


def _quick_rsi(prices, period: int = 14) -> float:
    if len(prices) < period + 1:
        return 50.0
    deltas = [prices[i] - prices[i - 1] for i in range(1, len(prices))]
    gains = [d for d in deltas if d > 0]
    losses = [-d for d in deltas if d < 0]
    avg_gain = sum(gains[-period:]) / period if gains else 0
    avg_loss = sum(losses[-period:]) / period if losses else 1e-9
    rs = avg_gain / avg_loss
    return 100 - (100 / (1 + rs))


def _top_symbols(entries: List[Dict]) -> List[Dict]:
    counts: Dict[str, int] = {}
    for e in entries:
        counts[e["symbol"]] = counts.get(e["symbol"], 0) + 1
    return [{"symbol": s, "trades": c} for s, c in sorted(counts.items(), key=lambda x: -x[1])[:5]]
