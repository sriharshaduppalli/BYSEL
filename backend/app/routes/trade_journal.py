"""
AI Trade Journal — logs trades with market context, generates behavioral insights.
Persisted per authenticated user in Postgres/SQLite.
Exposed via:
  POST /api/ai/v2/journal/log
  GET  /api/ai/v2/journal/entries
  GET  /api/ai/v2/journal/insights
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database.db import TradeJournalModel
from .dependencies import get_current_user, get_db

logger = logging.getLogger(__name__)

journal_router = APIRouter(prefix="/api/ai/v2/journal", tags=["AI Trade Journal"])


def _entry_to_dict(row: TradeJournalModel) -> Dict[str, Any]:
    context: Dict[str, Any] = {}
    auto_notes: List[str] = []
    try:
        if row.context_json:
            context = json.loads(row.context_json)
    except Exception:
        context = {}
    try:
        if row.auto_notes_json:
            auto_notes = json.loads(row.auto_notes_json)
    except Exception:
        auto_notes = []
    created = row.created_at or datetime.now(timezone.utc)
    return {
        "id": row.id,
        "userId": row.user_id,
        "symbol": row.symbol,
        "side": row.side,
        "qty": row.qty,
        "price": row.price,
        "total": row.total,
        "orderId": row.order_id,
        "timestamp": created.isoformat() if hasattr(created, "isoformat") else str(created),
        "context": context,
        "autoNotes": auto_notes,
        "userNote": row.user_note or "",
        "outcome": row.outcome,
    }


@journal_router.post("/log")
async def log_trade(
    entry: Dict[str, Any],
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Auto-log a trade with market context for the authenticated user."""
    try:
        import yfinance as yf

        symbol = str(entry.get("symbol", "")).upper().strip()
        side = str(entry.get("side", "BUY")).upper().strip() or "BUY"
        qty = int(entry.get("qty", 0) or 0)
        price = float(entry.get("price", 0) or 0.0)
        order_id = entry.get("orderId") or entry.get("order_id")
        if not symbol or qty <= 0:
            raise HTTPException(status_code=400, detail="symbol and qty are required")

        context: Dict[str, Any] = {}
        try:
            ticker = yf.Ticker(symbol if symbol.endswith(".NS") else symbol + ".NS")
            info = ticker.info or {}
            hist = ticker.history(period="5d")
            if hist is not None and not hist.empty:
                closes = hist["Close"].values
                rsi = _quick_rsi(closes)
                vol_ratio = (
                    (hist["Volume"].iloc[-1] / hist["Volume"].mean())
                    if hist["Volume"].mean() > 0
                    else 1.0
                )
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

        notes: List[str] = []
        if context.get("rsiAtTrade", 50) >= 70:
            notes.append("Bought at overbought RSI (>70) — high risk entry")
        elif context.get("rsiAtTrade", 50) <= 30:
            notes.append("Bought at oversold RSI (<30) — potential value entry")
        if context.get("near52wHigh") and side == "BUY":
            notes.append("Entry near 52-week high — buying at resistance")
        if context.get("volumeRatio", 1.0) > 2.0:
            notes.append(
                f"Unusual volume ({context['volumeRatio']:.1f}x avg) — potential institutional activity"
            )

        row = TradeJournalModel(
            user_id=user.id,
            symbol=symbol,
            side=side,
            qty=qty,
            price=price,
            total=qty * price,
            order_id=int(order_id) if order_id is not None else None,
            context_json=json.dumps(context),
            auto_notes_json=json.dumps(notes),
            user_note=str(entry.get("userNote", "") or ""),
            outcome=None,
            created_at=datetime.now(timezone.utc),
        )
        db.add(row)
        db.commit()
        db.refresh(row)

        # Feed anonymised interaction into Indian Stock LLM learning loop (best-effort).
        try:
            from ..llm_integration import record_chat_feedback

            record_chat_feedback(
                query=f"paper {side} {qty} {symbol} @ {price}",
                answer="; ".join(notes) if notes else "trade logged",
                helpful=True,
            )
        except Exception:
            pass

        return {"status": "logged", "entry": _entry_to_dict(row)}

    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("journal.log.error reason=%s", exc)
        raise HTTPException(status_code=500, detail="Failed to log trade")


@journal_router.get("/entries")
async def get_journal_entries(
    limit: int = 50,
    symbol: Optional[str] = None,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Return recent journal entries for the authenticated user."""
    q = db.query(TradeJournalModel).filter(TradeJournalModel.user_id == user.id)
    if symbol:
        q = q.filter(TradeJournalModel.symbol == symbol.upper())
    rows = (
        q.order_by(TradeJournalModel.created_at.desc(), TradeJournalModel.id.desc())
        .limit(max(1, min(limit, 200)))
        .all()
    )
    entries = [_entry_to_dict(r) for r in rows]
    return {"entries": entries, "count": len(entries)}


@journal_router.get("/insights")
async def get_journal_insights(
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    """Weekly behavioral insights from the authenticated user's trade history."""
    rows = (
        db.query(TradeJournalModel)
        .filter(TradeJournalModel.user_id == user.id)
        .order_by(TradeJournalModel.created_at.desc())
        .limit(50)
        .all()
    )
    recent = [_entry_to_dict(r) for r in rows]
    if len(recent) < 3:
        return {
            "hasEnoughData": False,
            "message": "Need at least 3 trades to generate insights.",
            "insights": [],
        }

    total = len(recent)
    buys = [e for e in recent if e.get("side") == "BUY"]
    sells = [e for e in recent if e.get("side") == "SELL"]

    overbought_entries = [e for e in buys if e.get("context", {}).get("rsiAtTrade", 50) >= 70]
    near_high_entries = [e for e in buys if e.get("context", {}).get("near52wHigh", False)]
    high_volume_entries = [e for e in recent if e.get("context", {}).get("volumeRatio", 1.0) > 2.0]

    insights: List[Dict[str, Any]] = []

    if len(overbought_entries) / max(len(buys), 1) > 0.3:
        insights.append({
            "type": "warning",
            "title": "Chasing Overbought Stocks",
            "detail": (
                f"{len(overbought_entries)} of your last {len(buys)} buys were at RSI>70. "
                "Consider waiting for pullbacks."
            ),
        })

    if len(near_high_entries) / max(len(buys), 1) > 0.4:
        insights.append({
            "type": "warning",
            "title": "Buying Near 52-Week Highs",
            "detail": (
                f"{len(near_high_entries)} buys near 52W high. "
                "Risk/reward may be unfavorable at resistance."
            ),
        })

    if len(high_volume_entries) > 2:
        insights.append({
            "type": "info",
            "title": "Active on High-Volume Days",
            "detail": (
                f"You traded {len(high_volume_entries)} times on unusual volume days — "
                "good awareness of market activity."
            ),
        })

    symbols_traded = list({e["symbol"] for e in recent})
    if len(symbols_traded) > 10:
        insights.append({
            "type": "info",
            "title": "Diversified Activity",
            "detail": (
                f"You traded {len(symbols_traded)} different stocks recently. "
                "Ensure each has a clear thesis."
            ),
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
