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
            from ..habits import wilder_rsi

            ticker = yf.Ticker(symbol if symbol.endswith(".NS") else symbol + ".NS")
            info = ticker.info or {}
            hist = ticker.history(period="1mo")
            if hist is not None and not hist.empty:
                closes = [float(v) for v in hist["Close"].values]
                rsi = wilder_rsi(closes)
                vol_ratio = (
                    (hist["Volume"].iloc[-1] / hist["Volume"].mean())
                    if hist["Volume"].mean() > 0
                    else 1.0
                )
                high_52 = info.get("fiftyTwoWeekHigh")
                near_52w_high = bool(
                    high_52 and price > 0 and price >= (float(high_52) * 0.97)
                )
                context = {
                    "volumeRatio": round(float(vol_ratio), 2),
                    "near52wHigh": near_52w_high,
                    "trailingPE": info.get("trailingPE"),
                    "marketCap": info.get("marketCap"),
                }
                if rsi is not None:
                    context["rsiAtTrade"] = rsi
        except Exception:
            pass

        notes: List[str] = []
        rsi_at = context.get("rsiAtTrade")
        if rsi_at is not None and rsi_at >= 70:
            notes.append("Bought at overbought RSI (>70) — high risk entry")
        elif rsi_at is not None and rsi_at <= 30:
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
    """Behavioral insights from the authenticated user's paper journal + fills."""
    from ..database.db import OrderModel
    from ..habits import MIN_PATTERN_TRADES, score_session_habits

    rows = (
        db.query(TradeJournalModel)
        .filter(TradeJournalModel.user_id == user.id)
        .order_by(TradeJournalModel.created_at.desc())
        .limit(50)
        .all()
    )
    recent = [_entry_to_dict(r) for r in rows]
    orders = (
        db.query(OrderModel)
        .filter(OrderModel.user_id == user.id)
        .order_by(OrderModel.created_at.desc())
        .limit(80)
        .all()
    )
    scored = score_session_habits(orders, journal_entries=rows)
    sample = int(scored.get("sampleSize") or 0)
    if sample < 3 and len(recent) < 3:
        return {
            "hasEnoughData": False,
            "message": (
                f"Need at least {MIN_PATTERN_TRADES} paper fills (or 3 journaled trades) "
                "before these insights describe your habits. Keep using Today's Practice."
            ),
            "insights": [],
            "totalTrades": sample,
            "buys": 0,
            "sells": 0,
            "topSymbols": [],
            "paperNote": scored.get("paperNote") or "",
        }

    buys = [e for e in recent if e.get("side") == "BUY"]
    sells = [e for e in recent if e.get("side") == "SELL"]
    insights: List[Dict[str, Any]] = []
    for habit in scored.get("habits") or []:
        category = habit.get("category") or "process"
        insight_type = "warning" if category in {"risk", "psychology"} else "info"
        detail = habit.get("body") or ""
        evidence = habit.get("evidence")
        if evidence:
            detail = f"{detail} ({evidence}.)"
        insights.append({
            "type": insight_type,
            "title": habit.get("title") or "Habit",
            "detail": detail,
        })

    high_volume_entries = [
        e
        for e in recent
        if _safe_float((e.get("context") or {}).get("volumeRatio")) is not None
        and _safe_float((e.get("context") or {}).get("volumeRatio")) > 2.0
    ]
    if len(high_volume_entries) > 2:
        insights.append({
            "type": "info",
            "title": "Active on high-volume days",
            "detail": (
                f"{len(high_volume_entries)} journaled paper fills landed on days with "
                ">2× average volume — useful context, not a reason to size up."
            ),
        })

    return {
        "hasEnoughData": bool(scored.get("hasEnoughData")) or len(recent) >= 3,
        "totalTrades": sample or len(recent),
        "buys": len(buys) if recent else int((scored.get("stats") or {}).get("buys") or 0),
        "sells": len(sells) if recent else int((scored.get("stats") or {}).get("sells") or 0),
        "insights": insights,
        "topSymbols": _top_symbols(recent) or _top_symbols(
            [{"symbol": o.symbol, "trades": 1} for o in orders if getattr(o, "symbol", None)]
        ),
        "paperNote": scored.get("paperNote") or "",
    }


def _safe_float(value: Any) -> Optional[float]:
    try:
        if value is None:
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def _top_symbols(entries: List[Dict]) -> List[Dict]:
    counts: Dict[str, int] = {}
    for e in entries:
        counts[e["symbol"]] = counts.get(e["symbol"], 0) + 1
    return [{"symbol": s, "trades": c} for s, c in sorted(counts.items(), key=lambda x: -x[1])[:5]]
