"""NSE/BSE session timings (IST), including Aug 2026 Closing Auction Session (CAS).

Effective Monday 3 August 2026 (SEBI/NSE/BSE):
  • Cash open unchanged: 09:15
  • F&O-eligible cash: continuous till 15:15, then CAS till 15:35
  • Non-F&O cash: continuous till 15:30 (unchanged)
  • Equity derivatives (index/stock F&O): till 15:40 (+10 min)
  • Pre-open 09:00–09:15; post-close window ~15:50–16:00 (approx)

BYSEL uses a single isOpen flag for live UI / paper-trading context:
  open from 09:15 through the latest equity-related close (15:40 from 2026-08-03).
"""
from __future__ import annotations

from datetime import date, datetime, time, timedelta
from typing import Any
from zoneinfo import ZoneInfo

IST = ZoneInfo("Asia/Kolkata")

# Phase-1 CAS / F&O close extension go-live.
CAS_EFFECTIVE_DATE = date(2026, 8, 3)

CASH_OPEN = time(9, 15)
PRE_OPEN_START = time(9, 0)

# Legacy single close (pre-CAS).
LEGACY_CASH_CLOSE = time(15, 30)

# From CAS_EFFECTIVE_DATE onward.
FO_CASH_CONTINUOUS_END = time(15, 15)
CAS_END = time(15, 35)
NON_FO_CASH_CLOSE = time(15, 30)
FO_DERIVATIVES_CLOSE = time(15, 40)
POST_CLOSE_START = time(15, 50)
POST_CLOSE_END = time(16, 0)


def cas_regime_active(on: date | None = None) -> bool:
    """True when Closing Auction Session / F&O 15:40 close rules apply."""
    day = on or datetime.now(IST).date()
    return day >= CAS_EFFECTIVE_DATE


def session_close_for_status(on: date | None = None) -> time:
    """Latest time BYSEL treats the equity market as 'open' for UI/live refresh."""
    return FO_DERIVATIVES_CLOSE if cas_regime_active(on) else LEGACY_CASH_CLOSE


def is_within_equity_session(now: datetime | None = None) -> bool:
    """Weekday continuous/CAS/F&O window (holiday checks left to caller)."""
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    if now_ist.weekday() >= 5:
        return False
    t = now_ist.time()
    close = session_close_for_status(now_ist.date())
    return CASH_OPEN <= t <= close


def session_timeline(on: date | None = None) -> dict[str, Any]:
    """Structured timings for API / AI literacy."""
    day = on or datetime.now(IST).date()
    if cas_regime_active(day):
        return {
            "regime": "cas_v1",
            "effectiveFrom": CAS_EFFECTIVE_DATE.isoformat(),
            "preOpen": "09:00-09:15 IST",
            "cashOpen": "09:15 IST",
            "foCashContinuousEnd": "15:15 IST",
            "closingAuctionEnd": "15:35 IST",
            "nonFoCashClose": "15:30 IST",
            "foDerivativesClose": "15:40 IST",
            "postClose": "15:50-16:00 IST (approx)",
            "uiOpenUntil": "15:40 IST",
            "note": (
                "From 3 Aug 2026: F&O stocks end continuous cash at 15:15 then CAS to 15:35; "
                "non-F&O cash still 15:30; equity derivatives trade to 15:40."
            ),
        }
    return {
        "regime": "legacy",
        "effectiveFrom": None,
        "preOpen": "09:00-09:15 IST",
        "cashOpen": "09:15 IST",
        "cashClose": "15:30 IST",
        "postClose": "15:40-16:00 IST (approx)",
        "uiOpenUntil": "15:30 IST",
        "note": "Legacy single cash close 15:30 IST (pre Closing Auction Session).",
    }


def open_message(now: datetime | None = None) -> str:
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    if cas_regime_active(now_ist.date()):
        return (
            "Market is OPEN — cash from 9:15 IST; F&O stocks continuous till 3:15 then CAS "
            "till 3:35; non-F&O till 3:30; equity derivatives till 3:40 IST"
        )
    return "Market is OPEN"


def closed_after_hours_message(now: datetime | None = None) -> str:
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    if cas_regime_active(now_ist.date()):
        return (
            "Market closed for today — cash/CAS ended by 3:35 IST; "
            "equity derivatives by 3:40 IST"
        )
    return "Market closed for today (3:30 PM IST)"


def next_close_label(today_str: str, on: date | None = None) -> str:
    close = session_close_for_status(on)
    return f"{today_str} {close.strftime('%H:%M')} IST"


def literacy_blurb() -> str:
    """Short educational copy for custom LLM / primers."""
    return (
        "**Indian market timings (from 3 Aug 2026)**\n\n"
        "• **Open:** 9:15 AM IST (pre-open ~9:00–9:15)\n"
        "• **F&O stocks (cash):** continuous till **3:15 PM**, then **Closing Auction Session "
        "(CAS)** till **3:35 PM** — closing price from the auction, not the old VWAP window\n"
        "• **Non-F&O cash:** continuous till **3:30 PM** (unchanged)\n"
        "• **Equity derivatives (index/stock F&O):** till **3:40 PM** (+10 minutes)\n"
        "• **Post-close:** short order window around **3:50–4:00 PM** (exchange rules apply)\n\n"
        "There is no longer one single '3:30 close' for every segment. "
        "Intraday/MIS square-off times may differ by broker for CAS stocks.\n"
        "_Educational summary of NSE/BSE/SEBI session changes — verify live circulars._"
    )
