"""Closed-session paper fills use last print and never invent a price."""
from __future__ import annotations

import sys
import time
from pathlib import Path

from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).parent.parent))

from app import app
from app.database.db import SessionLocal, WalletModel
from app.models.schemas import MarketStatus
from app.routes import trading as trading_module

client = TestClient(app)


def _auth_with_wallet(prefix: str, balance: float = 1_000_000.0) -> dict:
    suffix = str(int(time.time() * 1000))
    username = f"{prefix}_{suffix}"
    response = client.post(
        "/auth/register",
        json={
            "username": username,
            "email": f"{username}@example.com",
            "password": "demo1234",
        },
    )
    assert response.status_code == 200
    payload = response.json()
    user_id = int(payload["user_id"])
    db = SessionLocal()
    try:
        wallet = db.query(WalletModel).filter(WalletModel.user_id == user_id).first()
        if not wallet:
            db.add(WalletModel(user_id=user_id, balance=balance))
        else:
            wallet.balance = balance
        db.commit()
    finally:
        db.close()
    return {"Authorization": f"Bearer {payload['access_token']}"}


def test_closed_session_uses_last_print_without_yahoo(monkeypatch):
    fetched = {"count": 0}

    def _boom(_symbol):
        fetched["count"] += 1
        return {"symbol": _symbol, "last": 0.0}

    monkeypatch.setattr(
        trading_module,
        "is_market_open",
        lambda: MarketStatus(isOpen=False, message="Market closed - Weekend"),
    )
    monkeypatch.setattr(
        trading_module,
        "last_known_print",
        lambda symbol, max_age_seconds=None: {"symbol": symbol, "last": 1620.5},
    )
    monkeypatch.setattr(trading_module, "fetch_quote", _boom)

    auth = _auth_with_wallet("closed_fill")
    response = client.post("/order", json={"symbol": "INFY", "qty": 1, "side": "BUY"}, headers=auth)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["fillKind"] == "last_session"
    assert data["sessionPhase"] == "weekend"
    assert data["executedPrice"] == 1620.5
    assert "last session" in (data.get("message") or "").lower()
    assert fetched["count"] == 0


def test_closed_session_does_not_invent_price(monkeypatch):
    monkeypatch.setattr(
        trading_module,
        "is_market_open",
        lambda: MarketStatus(isOpen=False, message="Market closed - Weekend"),
    )
    monkeypatch.setattr(trading_module, "last_known_print", lambda *_args, **_kwargs: None)
    monkeypatch.setattr(
        trading_module,
        "fetch_quote",
        lambda symbol: {"symbol": symbol, "last": 0.0},
    )
    auth = _auth_with_wallet("closed_noprice")
    response = client.post("/order", json={"symbol": "INFY", "qty": 1, "side": "BUY"}, headers=auth)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "error"
    assert data["errorCode"] == "PRICE_UNAVAILABLE"


def test_last_known_print_does_not_invent():
    from app import market_data

    market_data._last_print.clear()
    assert market_data.last_known_print("NOSUCH") is None
    market_data.remember_last_print("TCS", {"symbol": "TCS", "last": 0.0})
    assert market_data.last_known_print("TCS") is None
    market_data.remember_last_print("TCS", {"symbol": "TCS", "last": 3550.0})
    print_ = market_data.last_known_print("TCS")
    assert print_ is not None
    assert print_["last"] == 3550.0
    assert print_["fillKind"] == "last_session"
