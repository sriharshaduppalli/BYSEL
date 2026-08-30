"""Corporate-action feed: dated lines only, no invented dates, no NSE scrape."""
from __future__ import annotations

from datetime import datetime, timezone

from app.corp_actions import dated_pack_rows, merge_yahoo_dated_actions, parse_action_date


def test_parse_action_date_accepts_real_dates_only():
    assert parse_action_date("2026-05-15") == "2026-05-15"
    assert parse_action_date("soon") is None
    assert parse_action_date("") is None
    assert parse_action_date(None) is None
    ts = datetime(2026, 5, 15, tzinfo=timezone.utc).timestamp()
    assert parse_action_date(ts) == "2026-05-15"


def test_dated_pack_rows_drop_undated():
    rows = dated_pack_rows(
        [
            {"symbol": "INFY", "action": "Dividend", "effective_date": "2026-05-15", "source": "pack"},
            {"symbol": "INFY", "action": "Bonus", "effective_date": "", "source": "pack"},
            {"symbol": "RELIANCE", "action": "Dividend", "effective_date": "tba", "source": "guess"},
        ],
        "INFY",
    )
    assert len(rows) == 1
    assert rows[0]["effective_date"] == "2026-05-15"


def test_merge_yahoo_keeps_pack_and_adds_dated_only(monkeypatch):
    pack = [
        {"symbol": "INFY", "action": "Dividend", "effective_date": "2026-05-15", "source": "nse_corporate_actions"}
    ]

    def _fake_yahoo(_symbol):
        return [
            {"symbol": "INFY", "action": "Split", "effective_date": "2018-09-04", "source": "yahoo_actions"},
            {"symbol": "INFY", "action": "Dividend", "effective_date": "", "source": "yahoo_actions"},
        ]

    monkeypatch.setattr("app.corp_actions.yahoo_dated_actions", _fake_yahoo)
    merged = merge_yahoo_dated_actions("INFY", pack)
    dates = {row["effective_date"] for row in merged}
    assert "2026-05-15" in dates
    assert "2018-09-04" in dates
    assert all(row["effective_date"] for row in merged)
