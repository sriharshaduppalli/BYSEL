"""Earnings calendar is the Nifty board, not only the three names sent by the app."""
from app.routes.ai_v2 import calendar_symbols, upcoming_rows


def test_calendar_adds_the_nifty_board_and_skips_the_index():
    symbols = calendar_symbols(["RELIANCE", "NIFTY50", "TCS"])
    assert symbols[0] == "RELIANCE"
    assert "TCS" in symbols
    assert "HDFCBANK" in symbols
    assert "NIFTY50" not in symbols
    assert len(symbols) >= 50


def test_upcoming_rows_drop_past_and_missing_dates():
    rows = [
        {"symbol": "TCS", "nextEarningsDate": "2026-10-08"},
        {"symbol": "INFY", "nextEarningsDate": "2027-01-13"},
        {"symbol": "WIPRO", "nextEarningsDate": None},
        {"symbol": "SBIN", "nextEarningsDate": "2026-11-06"},
    ]
    kept = [row["symbol"] for row in upcoming_rows(rows, "2026-11-06")]
    assert kept == ["SBIN", "INFY"]
