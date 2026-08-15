from datetime import date, datetime
from zoneinfo import ZoneInfo

from app.market_session import (
    CAS_EFFECTIVE_DATE,
    cas_regime_active,
    is_within_equity_session,
    session_close_for_status,
    session_timeline,
)

IST = ZoneInfo("Asia/Kolkata")


def test_cas_regime_active_from_aug_3_2026():
    assert cas_regime_active(date(2026, 8, 2)) is False
    assert cas_regime_active(CAS_EFFECTIVE_DATE) is True
    assert cas_regime_active(date(2026, 8, 4)) is True


def test_session_close_shifts_to_1540_after_cas():
    assert session_close_for_status(date(2026, 8, 2)).hour == 15
    assert session_close_for_status(date(2026, 8, 2)).minute == 30
    assert session_close_for_status(date(2026, 8, 3)).minute == 40


def test_within_session_includes_cas_window():
    # Tuesday 4 Aug 2026 15:32 IST — CAS still running for F&O cash.
    mid_cas = datetime(2026, 8, 4, 15, 32, tzinfo=IST)
    assert is_within_equity_session(mid_cas) is True
    after_fo = datetime(2026, 8, 4, 15, 41, tzinfo=IST)
    assert is_within_equity_session(after_fo) is False


def test_timeline_mentions_cas():
    tl = session_timeline(date(2026, 8, 4))
    assert tl["regime"] == "cas_v1"
    assert tl["closingAuctionEnd"] == "15:35 IST"
    assert tl["foDerivativesClose"] == "15:40 IST"


def test_heatmap_open_flag_follows_equity_session(monkeypatch):
    from app import market_heatmap

    monkeypatch.setattr("app.market_session.is_within_equity_session", lambda now=None: True)
    assert market_heatmap._is_nse_market_open() is True
    monkeypatch.setattr("app.market_session.is_within_equity_session", lambda now=None: False)
    assert market_heatmap._is_nse_market_open() is False
