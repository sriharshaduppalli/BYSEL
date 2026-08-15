from datetime import datetime, timedelta

from app.habits import (
    MIN_PATTERN_TRADES,
    merge_habit_tips,
    score_investor_habits,
    score_session_habits,
    session_bucket,
    wilder_rsi,
)
from app.intraday_tips import build_intraday_tips, session_phase
from app.investor_tips import build_investor_tips
from app.market_session import IST


def _ist(year, month, day, hour, minute=0):
    return datetime(year, month, day, hour, minute, tzinfo=IST)


def _trade(symbol, side, when, **extra):
    row = {
        "symbol": symbol,
        "side": side,
        "qty": extra.get("qty", 1),
        "price": extra.get("price", 100.0),
        "createdAt": when,
    }
    row.update({k: v for k, v in extra.items() if k not in {"qty", "price"}})
    return row


def test_session_bucket_uses_ist_windows():
    # Tuesday 4 Aug 2026 — CAS weekday
    assert session_bucket(_ist(2026, 8, 4, 9, 20)) == "first_hour"
    assert session_bucket(_ist(2026, 8, 4, 11, 0)) == "mid_morning"
    assert session_bucket(_ist(2026, 8, 4, 12, 30)) == "lunch_lull"
    assert session_bucket(_ist(2026, 8, 4, 14, 0)) == "afternoon"
    assert session_bucket(_ist(2026, 8, 4, 15, 0)) == "closing_window"
    assert session_bucket(_ist(2026, 8, 4, 16, 0)) == "after_hours"
    assert session_bucket(_ist(2026, 8, 15, 11, 0)) == "weekend"


def test_session_phase_holiday_not_after_hours():
    holiday = session_phase(_ist(2026, 8, 14, 11, 0), is_holiday=True)
    assert holiday["phase"] == "holiday"
    assert holiday["isOpen"] is False


def test_not_enough_paper_trades_is_honest():
    now = _ist(2026, 8, 14, 12, 0)
    scored = score_session_habits(
        [_trade("TCS", "BUY", now - timedelta(days=1))],
        now=now,
    )
    assert scored["hasEnoughData"] is False
    assert scored["sampleSize"] == 1
    assert scored["habits"][0]["id"] == "need_more_paper"
    assert "Not enough" in scored["habits"][0]["title"]


def test_open_cluster_and_single_name_and_no_stop():
    now = _ist(2026, 8, 14, 16, 0)
    trades = []
    for day in range(5):
        when = _ist(2026, 8, 10 + day, 9, 25)
        trades.append(_trade("RELIANCE", "BUY", when))
        trades.append(_trade("RELIANCE", "BUY", when.replace(minute=40)))
    scored = score_session_habits(trades, trigger_count=0, now=now)
    assert scored["hasEnoughData"] is True
    assert scored["sampleSize"] == 10
    ids = {h["id"] for h in scored["habits"]}
    assert "open_cluster" in ids
    assert "single_name" in ids
    assert "no_stop" in ids
    assert "paper" in scored["paperNote"].lower()
    open_habit = next(h for h in scored["habits"] if h["id"] == "open_cluster")
    assert "09:15" in open_habit["body"]
    assert "buy" not in open_habit["body"].lower() or "paper" in open_habit["body"].lower()


def test_naive_utc_timestamp_converts_to_ist_first_hour():
    # 04:00 UTC = 09:30 IST
    naive_utc = datetime(2026, 8, 4, 4, 0, 0)
    trades = [_trade("INFY", "BUY", naive_utc) for _ in range(MIN_PATTERN_TRADES)]
    scored = score_session_habits(trades, now=_ist(2026, 8, 4, 16, 0))
    assert scored["stats"]["buckets"].get("first_hour", 0) == MIN_PATTERN_TRADES


def test_unused_practice_and_concentration():
    now = _ist(2026, 8, 14, 12, 0)
    empty = score_investor_habits([], holdings=[], wallet_balance=50_000, now=now)
    assert empty["habits"][0]["id"] == "unused_practice"
    assert empty["hasEnoughData"] is False

    holdings = [
        {"symbol": "TCS", "qty": 10, "last": 4000},
        {"symbol": "INFY", "qty": 1, "last": 1500},
    ]
    concentrated = score_investor_habits([], holdings=holdings, now=now)
    ids = {h["id"] for h in concentrated["habits"]}
    assert "book_concentration" in ids


def test_horizon_mismatch_same_day_round_trips():
    now = _ist(2026, 8, 14, 16, 0)
    trades = []
    for day in range(5):
        d = _ist(2026, 8, 10 + day, 11, 0)
        trades.append(_trade("HDFCBANK", "BUY", d))
        trades.append(_trade("HDFCBANK", "SELL", d.replace(hour=14)))
    scored = score_investor_habits(trades, topic="long_term", now=now)
    ids = {h["id"] for h in scored["habits"]}
    assert "horizon_mismatch" in ids


def test_risk_profile_mismatch():
    holdings = [{"symbol": "RELIANCE", "qty": 20, "last": 1400}, {"symbol": "ITC", "qty": 2, "last": 400}]
    scored = score_investor_habits(
        [],
        holdings=holdings,
        goals=[{"riskProfile": "CONSERVATIVE"}],
        now=_ist(2026, 8, 14, 12, 0),
    )
    ids = {h["id"] for h in scored["habits"]}
    assert "risk_mismatch" in ids


def test_merge_prefers_paper_habits_then_education():
    paper = [{"id": "open_cluster", "title": "P", "body": "b", "category": "session", "source": "paper"}]
    edu = [
        {"id": "fh_patience", "title": "E1", "body": "b", "category": "session", "source": "session"},
        {"id": "fh_stop", "title": "E2", "body": "b", "category": "process", "source": "session"},
    ]
    merged = merge_habit_tips(paper, edu, limit=3, has_enough_data=True)
    assert [t["id"] for t in merged] == ["open_cluster", "fh_patience", "fh_stop"]


def test_build_intraday_tips_blends_activity():
    now = _ist(2026, 8, 4, 9, 30)
    activity = {
        "habits": [
            {
                "id": "open_cluster",
                "title": "Paper fills clustered at the open",
                "body": "6 of 8 in-session paper fills landed in 09:15–10:15 IST.",
                "category": "session",
                "source": "paper",
                "evidence": "6/8",
            }
        ],
        "sampleSize": 8,
        "hasEnoughData": True,
        "paperNote": "Based on 8 paper fills",
    }
    payload = build_intraday_tips(limit=3, now=now, activity=activity)
    assert payload["phase"] == "first_hour"
    assert payload["tips"][0]["id"] == "open_cluster"
    assert payload["tips"][0]["source"] == "paper"
    assert payload["sampleSize"] == 8
    assert any(t.get("source") == "session" for t in payload["tips"])


def test_build_investor_tips_keeps_topic_and_paper_note():
    payload = build_investor_tips(
        "mutual_funds",
        limit=3,
        now=_ist(2026, 8, 14, 12, 0),
        activity={
            "habits": [
                {
                    "id": "unused_practice",
                    "title": "Paper trading is unused",
                    "body": "No paper fills.",
                    "category": "process",
                    "source": "paper",
                    "evidence": "0 fills",
                }
            ],
            "sampleSize": 0,
            "hasEnoughData": False,
            "paperNote": "Based on 0 paper fills",
        },
    )
    assert payload["topic"] == "mutual_funds"
    assert payload["tips"][0]["id"] == "unused_practice"
    assert payload["paperNote"].startswith("Based on")


def test_wilder_rsi_none_when_short_and_not_fake_50():
    assert wilder_rsi([100, 101, 102]) is None
    # Strong uptrend over 15 closes should be well above 50, not a dummy.
    closes = [100 + i for i in range(20)]
    rsi = wilder_rsi(closes)
    assert rsi is not None
    assert rsi > 70
