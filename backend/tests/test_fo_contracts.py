"""Lot sizes and expiry days are what a learner carries to a real broker screen."""
import sys
from datetime import date
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).parent.parent))

from app import app, fo_contracts  # noqa: E402


@pytest.fixture(scope="module")
def client():
    return TestClient(app)


def test_index_lots_follow_nse_circular_176_2025():
    assert fo_contracts.lot_size_for("NIFTY", 25_000) == 65
    assert fo_contracts.lot_size_for("banknifty", 56_000) == 30
    assert fo_contracts.lot_size_for("FINNIFTY", 26_000) == 60
    assert fo_contracts.lot_size_for("MIDCPNIFTY", 13_000) == 120


def test_unknown_stock_gets_rounded_teaching_lot():
    assert fo_contracts.lot_size_for("XYZ", 2_400) == 50
    assert fo_contracts.lot_size_for("XYZ", 0) == 100


def test_nifty_weekly_expiry_is_tuesday():
    # Sunday 25 Oct 2026 -> Tuesday 27 Oct 2026
    assert fo_contracts.next_weekly_expiry(date(2026, 10, 25)) == date(2026, 10, 27)
    # On expiry day itself the same day counts.
    assert fo_contracts.next_weekly_expiry(date(2026, 10, 27)) == date(2026, 10, 27)
    # A Tuesday holiday pulls expiry to Monday.
    assert fo_contracts.next_weekly_expiry(date(2026, 10, 25), holidays={"2026-10-27"}) == date(2026, 10, 26)


def test_monthly_expiry_is_last_tuesday():
    assert fo_contracts.monthly_expiry(2026, 10) == date(2026, 10, 27)
    assert fo_contracts.monthly_expiry(2026, 11) == date(2026, 11, 24)
    assert fo_contracts.monthly_expiry(2026, 12) == date(2026, 12, 29)


def test_stock_rolls_to_next_month_after_expiry():
    assert fo_contracts.next_expiry_for("RELIANCE", date(2026, 10, 28)) == date(2026, 11, 24)
    assert fo_contracts.next_expiry_for("NIFTY", date(2026, 10, 28)) == date(2026, 11, 3)


def test_option_chain_reports_lot_size_and_expiry_aware_premiums(client, monkeypatch):
    monkeypatch.setattr("app.derivatives_data.fetch_nse_option_chain", lambda **kwargs: None)
    near = client.get("/derivatives/option-chain", params={"symbol": "NIFTY", "expiry": "2026-10-27"})
    far = client.get("/derivatives/option-chain", params={"symbol": "NIFTY", "expiry": "2026-12-29"})
    assert near.status_code == 200 and far.status_code == 200
    near_body, far_body = near.json(), far.json()
    assert near_body["lotSize"] == 65
    assert near_body["source"] == "synthetic"
    atm_near = min(near_body["contracts"], key=lambda c: abs(c["strike"] - near_body["spot"]))
    atm_far = min(far_body["contracts"], key=lambda c: abs(c["strike"] - far_body["spot"]))
    assert atm_far["callLtp"] > atm_near["callLtp"]
    assert any("Tuesday" in note for note in near_body["notes"])


def test_futures_board_uses_exchange_lot_and_monthly_cycle(client, monkeypatch):
    monkeypatch.setattr("app.derivatives_data.fetch_nse_futures_contracts", lambda *args, **kwargs: None)
    resp = client.get("/derivatives/futures/contracts", params={"symbol": "BANKNIFTY"})
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["contracts"]) == 3
    assert all(c["lotSize"] == 30 for c in body["contracts"])
    expiries = [date.fromisoformat(c["expiry"]) for c in body["contracts"]]
    assert expiries == sorted(expiries)
    # Last Tuesday of the month, or the trading day before it on a holiday.
    assert all(d.weekday() <= 1 for d in expiries)
    assert len({(d.year, d.month) for d in expiries}) == 3
