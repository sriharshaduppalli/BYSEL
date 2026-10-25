"""Exchange contract facts the F&O practice desks must agree on.

Lot sizes and expiry weekdays are the two numbers a learner carries from a
paper drill to a real broker screen, so they live in one place. Keep this in
step with NSE circulars; the last review is recorded below.

- Index lots: NSE/FAOP/70616 (3 Oct 2025), in force for all contracts since
  31 Dec 2025: NIFTY 65, BANKNIFTY 30, FINNIFTY 60, MIDCPNIFTY 120, NIFTYNXT50 25.
- Weekly expiry: NSE index weeklies expire on Tuesday (since 1 Sep 2025);
  monthly index and all stock contracts expire on the last Tuesday of the month.
  A holiday moves the expiry to the previous trading day.
"""
from __future__ import annotations

from datetime import date, timedelta
from typing import Iterable, Optional

LOT_SIZES_REVIEWED = "2026-10"

# Lot per contract as published in the NSE market-lot file for October 2026
# expiries. Stocks get revised roughly twice a year; unknown names fall back to
# an approximate ₹1.2 lakh contract value, rounded to a multiple of 5.
KNOWN_LOT_SIZES: dict[str, int] = {
    "NIFTY": 65,
    "NIFTY50": 65,
    "BANKNIFTY": 30,
    "NIFTYBANK": 30,
    "FINNIFTY": 60,
    "MIDCPNIFTY": 120,
    "NIFTYNXT50": 25,
    "SENSEX": 20,
    "RELIANCE": 500,
    "TCS": 225,
    "INFY": 400,
    "SBIN": 750,
    "HDFCBANK": 650,
    "AXISBANK": 625,
    "BAJFINANCE": 750,
    "BHARTIARTL": 475,
    "ADANIENT": 309,
    "ASIANPAINT": 250,
    "BEL": 1425,
    "BPCL": 1975,
    "CIPLA": 425,
    "COALINDIA": 1350,
    "DLF": 950,
    "DRREDDY": 625,
    "EICHERMOT": 100,
    "GOLDM": 10,
}

WEEKLY_INDEXES = {"NIFTY", "NIFTY50"}
_EXPIRY_WEEKDAY = 1  # Tuesday (Monday == 0)


def lot_size_for(symbol: str, spot: float) -> int:
    """Known exchange lot, else a ₹1.2 lakh-ish teaching lot rounded to 5."""
    normalized = (symbol or "").strip().upper()
    known = KNOWN_LOT_SIZES.get(normalized)
    if known:
        return known
    if spot <= 0:
        return 100
    raw = int(round(120_000.0 / spot))
    return max(10, ((raw + 4) // 5) * 5)


def _previous_trading_day(day: date, holidays: Iterable[str]) -> date:
    blocked = set(holidays)
    while day.weekday() >= 5 or day.isoformat() in blocked:
        day -= timedelta(days=1)
    return day


def next_weekly_expiry(today: Optional[date] = None, holidays: Iterable[str] = ()) -> date:
    """Next NIFTY weekly expiry (Tuesday), today included if today is expiry."""
    today = today or date.today()
    ahead = (_EXPIRY_WEEKDAY - today.weekday()) % 7
    candidate = _previous_trading_day(today + timedelta(days=ahead), holidays)
    if candidate < today:
        candidate = _previous_trading_day(today + timedelta(days=ahead + 7), holidays)
    return candidate


def monthly_expiry(year: int, month: int, holidays: Iterable[str] = ()) -> date:
    """Last Tuesday of the month, pulled earlier when that Tuesday is a holiday."""
    last_day = (date(year + (month == 12), (month % 12) + 1, 1) - timedelta(days=1))
    back = (last_day.weekday() - _EXPIRY_WEEKDAY) % 7
    return _previous_trading_day(last_day - timedelta(days=back), holidays)


def next_expiry_for(symbol: str, today: Optional[date] = None, holidays: Iterable[str] = ()) -> date:
    """Weekly Tuesday for NIFTY; current or next month's last Tuesday otherwise."""
    today = today or date.today()
    if (symbol or "").strip().upper() in WEEKLY_INDEXES:
        return next_weekly_expiry(today, holidays)
    this_month = monthly_expiry(today.year, today.month, holidays)
    if this_month >= today:
        return this_month
    year, month = (today.year + 1, 1) if today.month == 12 else (today.year, today.month + 1)
    return monthly_expiry(year, month, holidays)


def expiry_note(symbol: str) -> str:
    if (symbol or "").strip().upper() in WEEKLY_INDEXES:
        return "NIFTY weekly contracts expire on Tuesday; monthly on the last Tuesday."
    return "This contract expires on the last Tuesday of its month (earlier if that is a holiday)."
