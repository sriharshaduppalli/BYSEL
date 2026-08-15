"""Session-aware intraday trading tips (educational process cues — not stock tips)."""
from __future__ import annotations

from datetime import datetime, time
from typing import Any, Optional

from .habits import merge_habit_tips
from .market_session import (
    CASH_OPEN,
    PRE_OPEN_START,
    IST,
    session_close_for_status,
)

DISCLAIMER = (
    "Educational session habits only — not stock tips, not investment advice. "
    "Verify live exchange/broker rules before trading."
)

# tip banks keyed by session phase
_TIP_BANKS: dict[str, list[dict[str, str]]] = {
    "weekend": [
        {
            "id": "wk_journal",
            "title": "Weekend review",
            "body": "Tag last week's paper trades: plan followed? size too large? one fix for Monday.",
            "category": "process",
        },
        {
            "id": "wk_calendar",
            "title": "Scan the week ahead",
            "body": "Note RBI/Fed/earnings dates. Busy event days → smaller size or sit out.",
            "category": "risk",
        },
        {
            "id": "wk_watchlist",
            "title": "Trim the watchlist",
            "body": "Keep 5–8 liquid names with a clear level. Crowded lists cause FOMO entries.",
            "category": "process",
        },
    ],
    "holiday": [
        {
            "id": "hol_gap",
            "title": "Holiday gap risk",
            "body": "Overnight news can gap open. Prefer smaller size on the reopening session.",
            "category": "risk",
        },
        {
            "id": "hol_plan",
            "title": "Prep, don't chase",
            "body": "Write entry/stop/target while the market is shut — decide before the open auction.",
            "category": "process",
        },
    ],
    "pre_market": [
        {
            "id": "pm_levels",
            "title": "Mark key levels",
            "body": "Note prior day high/low and your invalidation before 9:15 — avoid mid-candle decisions.",
            "category": "process",
        },
        {
            "id": "pm_news",
            "title": "Headline check",
            "body": "Scan global cues and stock-specific news. If you can't name the risk, skip the trade.",
            "category": "risk",
        },
        {
            "id": "pm_size",
            "title": "Pre-commit size",
            "body": "Decide max loss in ₹ before the open. Intraday size should survive a bad first hour.",
            "category": "risk",
        },
    ],
    "pre_open": [
        {
            "id": "po_auction",
            "title": "Pre-open is noisy",
            "body": "9:00–9:15 discovery can fake breakouts. Wait for the continuous session to confirm.",
            "category": "session",
        },
        {
            "id": "po_orders",
            "title": "Order discipline",
            "body": "Use limit intent near your level. Market orders into the open often pay a wide spread.",
            "category": "process",
        },
        {
            "id": "po_bias",
            "title": "One bias, not ten",
            "body": "Pick long or short bias for the open. Switching mid-auction is usually emotion, not edge.",
            "category": "psychology",
        },
    ],
    "first_hour": [
        {
            "id": "fh_patience",
            "title": "First-hour volatility",
            "body": "Opening range is often widest. Let a range form (15–30 min) before chasing breakouts.",
            "category": "session",
        },
        {
            "id": "fh_slippage",
            "title": "Respect liquidity",
            "body": "Prefer names with tight spreads. Wide bid-ask eats paper-edge before the thesis plays out.",
            "category": "risk",
        },
        {
            "id": "fh_stop",
            "title": "Stop first, entry second",
            "body": "If you can't place a stop where a thesis dies, you don't have a trade — only a hope.",
            "category": "process",
        },
        {
            "id": "fh_fomo",
            "title": "Skip the gap chase",
            "body": "Stocks already +3–4% at 9:20 often mean-revert. Late FOMO entries have poor R:R.",
            "category": "psychology",
        },
    ],
    "mid_morning": [
        {
            "id": "mm_trend",
            "title": "Trade with breadth",
            "body": "If advances lead, favour pullback longs in leaders. If declines dominate, tighten risk.",
            "category": "session",
        },
        {
            "id": "mm_scale",
            "title": "Scale, don't all-in",
            "body": "Add only if price confirms. Averaging losers mid-morning is the fastest way to blow a day.",
            "category": "risk",
        },
        {
            "id": "mm_news",
            "title": "Corporate actions",
            "body": "Check for results/board meets on your names. Event risk turns a clean chart into a coin flip.",
            "category": "risk",
        },
    ],
    "lunch_lull": [
        {
            "id": "ll_chop",
            "title": "Midday chop zone",
            "body": "12:00–1:30 often ranges. Smaller size or wait — fake breaks are common in thin volume.",
            "category": "session",
        },
        {
            "id": "ll_review",
            "title": "Midday checkpoint",
            "body": "Are you green because of process or luck? Lock a partial if the plan called for it.",
            "category": "process",
        },
        {
            "id": "ll_revenge",
            "title": "No revenge trades",
            "body": "After a stop-out, step away 10 minutes. The next impulse trade is rarely your best idea.",
            "category": "psychology",
        },
    ],
    "afternoon": [
        {
            "id": "af_trend_day",
            "title": "Afternoon continuation",
            "body": "On clean trend days, pullbacks into VWAP/MA often work better than fresh breakouts late.",
            "category": "session",
        },
        {
            "id": "af_time_stop",
            "title": "Time stops matter",
            "body": "If a trade hasn't worked by mid-afternoon, reassess. Dead capital needs a decision.",
            "category": "process",
        },
        {
            "id": "af_size_down",
            "title": "Cut size into the close",
            "body": "New positions after 2:30 need a stronger reason — less time for thesis to play out.",
            "category": "risk",
        },
    ],
    "closing_window": [
        {
            "id": "cw_cas",
            "title": "Know the CAS clock",
            "body": "From 3 Aug 2026: F&O cash continuous ends ~3:15, CAS to ~3:35; derivatives to ~3:40. Broker MIS square-off may be earlier.",
            "category": "session",
        },
        {
            "id": "cw_flat",
            "title": "Intraday → flat",
            "body": "Don't leave MIS hopes overnight. Square off with time buffer — last minutes are chaotic.",
            "category": "risk",
        },
        {
            "id": "cw_no_lottery",
            "title": "No closing lottery",
            "body": "Avoid doubling size in the last 20 minutes to 'make the day back'. That is variance, not skill.",
            "category": "psychology",
        },
        {
            "id": "cw_journal",
            "title": "Close with a note",
            "body": "One line: what worked, what you'll skip tomorrow. Beats scrolling P&L after the bell.",
            "category": "process",
        },
    ],
    "after_hours": [
        {
            "id": "ah_review",
            "title": "After-hours debrief",
            "body": "Grade process, not P&L. A green day with broken rules is still a bad day.",
            "category": "process",
        },
        {
            "id": "ah_gaps",
            "title": "Plan for gaps",
            "body": "If holding overnight (delivery), know next support/resistance before tomorrow's open.",
            "category": "risk",
        },
        {
            "id": "ah_rest",
            "title": "Protect attention",
            "body": "Stop refreshing after close. Fresh decisions need a clear head at 9:15 tomorrow.",
            "category": "psychology",
        },
    ],
}

_MOOD_TIPS: dict[str, dict[str, str]] = {
    "bullish": {
        "id": "mood_bull",
        "title": "Breadth is supportive",
        "body": "Favour strength on dips over bottom-fishing weak names. Trail winners; don't enlarge losers.",
        "category": "session",
    },
    "bearish": {
        "id": "mood_bear",
        "title": "Breadth is heavy",
        "body": "Tighten risk. Bounces can fail fast — prefer confirmation over catching knives.",
        "category": "session",
    },
    "mixed": {
        "id": "mood_mixed",
        "title": "Mixed tape",
        "body": "Stock-picking day: follow individual levels. Index noise is higher when breadth splits.",
        "category": "session",
    },
}


def session_phase(now: datetime | None = None, *, is_holiday: bool = False) -> dict[str, Any]:
    """Return phase id + human label for the current IST clock."""
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    t = now_ist.time()
    weekday = now_ist.weekday()

    if weekday >= 5:
        return {"phase": "weekend", "label": "Weekend", "isOpen": False}
    if is_holiday:
        return {"phase": "holiday", "label": "Market holiday", "isOpen": False}

    close = session_close_for_status(now_ist.date())
    if t < PRE_OPEN_START:
        return {"phase": "pre_market", "label": "Pre-market", "isOpen": False}
    if PRE_OPEN_START <= t < CASH_OPEN:
        return {"phase": "pre_open", "label": "Pre-open auction", "isOpen": False}
    if t > close:
        return {"phase": "after_hours", "label": "After hours", "isOpen": False}
    if t < time(10, 15):
        return {"phase": "first_hour", "label": "First hour", "isOpen": True}
    if t < time(12, 0):
        return {"phase": "mid_morning", "label": "Mid-morning", "isOpen": True}
    if t < time(13, 30):
        return {"phase": "lunch_lull", "label": "Midday lull", "isOpen": True}
    if t < time(14, 45):
        return {"phase": "afternoon", "label": "Afternoon", "isOpen": True}
    return {"phase": "closing_window", "label": "Closing window", "isOpen": True}


def _rotate(tips: list[dict[str, str]], seed: int, count: int) -> list[dict[str, str]]:
    if not tips:
        return []
    n = len(tips)
    start = seed % n
    ordered = tips[start:] + tips[:start]
    return ordered[: max(1, min(count, n))]


def build_intraday_tips(
    *,
    limit: int = 3,
    advance_share: Optional[float] = None,
    is_holiday: bool = False,
    now: datetime | None = None,
    activity: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    phase_info = session_phase(now_ist, is_holiday=is_holiday)
    phase = phase_info["phase"]
    bank = list(_TIP_BANKS.get(phase) or _TIP_BANKS["after_hours"])

    seed = now_ist.year * 10_000 + now_ist.timetuple().tm_yday * 100 + now_ist.hour
    educational = [dict(tip, source="session", evidence=None) for tip in _rotate(bank, seed, max(limit, 3))]

    mood = None
    if advance_share is not None and phase_info.get("isOpen"):
        if advance_share >= 0.58:
            mood = "bullish"
        elif advance_share <= 0.42:
            mood = "bearish"
        else:
            mood = "mixed"
        mood_tip = dict(_MOOD_TIPS[mood])
        mood_tip.setdefault("source", "session")
        mood_tip.setdefault("evidence", "Live advance/decline share this session")
        educational = [mood_tip] + [t for t in educational if t["id"] != mood_tip["id"]]

    personalized = list((activity or {}).get("habits") or [])
    has_enough = bool((activity or {}).get("hasEnoughData"))
    sample_size = int((activity or {}).get("sampleSize") or 0)
    paper_note = str((activity or {}).get("paperNote") or "")
    tips = merge_habit_tips(
        personalized,
        educational,
        limit=limit,
        has_enough_data=has_enough,
    )

    return {
        "phase": phase,
        "phaseLabel": phase_info["label"],
        "isOpen": bool(phase_info.get("isOpen")),
        "mood": mood,
        "tips": tips,
        "disclaimer": DISCLAIMER,
        "generatedAt": now_ist.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "sampleSize": sample_size,
        "hasEnoughData": has_enough,
        "paperNote": paper_note,
    }
