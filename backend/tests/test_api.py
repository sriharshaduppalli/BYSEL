import pytest
from fastapi.testclient import TestClient
import sys
from pathlib import Path
import time

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app import app
from app.database.db import SessionLocal, WalletModel
from app.models.schemas import MarketStatus

client = TestClient(app)


def _unique_user(prefix: str) -> tuple[str, str, str]:
    suffix = str(int(time.time() * 1000))
    username = f"{prefix}_{suffix}"
    email = f"{username}@example.com"
    password = "demo1234"
    return username, email, password


def _seed_trading_wallet(user_id: int = 1, balance: float = 1_000_000.0) -> None:
    db = SessionLocal()
    try:
        db.query(WalletModel).filter(WalletModel.user_id == user_id).delete(synchronize_session=False)
        wallet = WalletModel(user_id=user_id, balance=balance)
        db.add(wallet)
        db.commit()
    finally:
        db.close()


def _mock_live_market(monkeypatch, price: float = 100.0) -> None:
    monkeypatch.setattr(
        "app.routes.trading.is_market_open",
        lambda: MarketStatus(isOpen=True, message="Market is OPEN"),
    )
    monkeypatch.setattr(
        "app.routes.trading.fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": price, "pctChange": 0.0},
    )

def test_health_check():
    """Test health check endpoint"""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "version" in data

def test_get_quotes():
    """Test getting quotes"""
    response = client.get("/quotes?symbols=RELIANCE,TCS")
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 0
    if len(data) > 0:
        assert "symbol" in data[0]
        assert "last" in data[0]
        assert "pctChange" in data[0]

def test_get_holdings_empty():
    """Test getting holdings when empty"""
    response = client.get("/holdings")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)

def test_place_order(monkeypatch):
    """Test placing an order"""
    _seed_trading_wallet(user_id=1)
    _mock_live_market(monkeypatch, price=100.0)

    order_data = {
        "symbol": "TCS",
        "qty": 1,
        "side": "BUY"
    }
    response = client.post("/order", json=order_data)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["order"]["symbol"] == "TCS"
    assert data["order"]["qty"] == 1


def test_logout_all_invalidates_old_access_token():
    username, email, password = _unique_user("logout_all_user")

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200
    tokens = register_response.json()
    access_token = tokens["access_token"]

    sessions_before = client.get(
        "/auth/sessions",
        headers={"Authorization": f"Bearer {access_token}"}
    )
    assert sessions_before.status_code == 200

    logout_all_response = client.post(
        "/auth/logout-all",
        headers={"Authorization": f"Bearer {access_token}"}
    )
    assert logout_all_response.status_code == 200

    sessions_after = client.get(
        "/auth/sessions",
        headers={"Authorization": f"Bearer {access_token}"}
    )
    assert sessions_after.status_code == 401
    assert sessions_after.json()["detail"] == "Session invalidated"


def test_refresh_token_reuse_invalidates_active_sessions():
    username, email, password = _unique_user("reuse_user")

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200
    register_payload = register_response.json()

    first_refresh_token = register_payload["refresh_token"]

    rotate_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token}
    )
    assert rotate_response.status_code == 200
    rotated_payload = rotate_response.json()
    second_refresh_token = rotated_payload["refresh_token"]

    reuse_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token}
    )
    assert reuse_response.status_code == 401
    assert "reuse" in reuse_response.json()["detail"].lower()

    second_refresh_attempt = client.post(
        "/auth/refresh",
        json={"refreshToken": second_refresh_token}
    )
    assert second_refresh_attempt.status_code == 401
    assert second_refresh_attempt.json()["detail"] == "Session invalidated"


def test_login_username_is_case_insensitive():
    username, email, password = _unique_user("CaseUser")

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200

    login_response = client.post(
        "/auth/login",
        json={"username": username.upper(), "password": password}
    )
    assert login_response.status_code == 200
    payload = login_response.json()
    assert payload["status"] == "ok"
    assert "access_token" in payload
