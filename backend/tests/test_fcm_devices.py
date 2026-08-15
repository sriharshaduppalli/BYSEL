import time

from fastapi.testclient import TestClient

from app import app
from app.alert_push import _symbol_key, _threshold_hit

client = TestClient(app)


def _register(prefix: str) -> dict:
    suffix = str(int(time.time() * 1000))
    username = f"{prefix}_{suffix}"
    response = client.post(
        "/auth/register",
        json={"username": username, "email": f"{username}@example.com", "password": "demo1234"},
    )
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_fcm_token_requires_auth():
    assert client.post("/auth/register-fcm-token", json={"token": "abc"}).status_code == 401
    assert client.post("/auth/devices", json={"token": "abc"}).status_code == 401


def test_fcm_token_register_and_unregister():
    headers = _register("fcm_reg")
    created = client.post(
        "/auth/register-fcm-token",
        headers=headers,
        json={"token": "fcm-test-token-1", "platform": "android"},
    )
    assert created.status_code == 200
    assert created.json()["status"] == "ok"

    alias = client.post(
        "/auth/devices",
        headers=headers,
        json={"token": "fcm-test-token-1", "platform": "android"},
    )
    assert alias.status_code == 200

    removed = client.post(
        "/auth/unregister-fcm-token",
        headers=headers,
        json={"token": "fcm-test-token-1"},
    )
    assert removed.status_code == 200
    assert removed.json()["status"] == "ok"


def test_alert_symbol_and_threshold_helpers():
    assert _symbol_key("reliance.ns") == "RELIANCE"
    assert _symbol_key("TCS") == "TCS"
    assert _threshold_hit("ABOVE", 101.0, 100.0) is True
    assert _threshold_hit("ABOVE", 99.0, 100.0) is False
    assert _threshold_hit("BELOW", 99.0, 100.0) is True
    assert _threshold_hit("BELOW", 101.0, 100.0) is False
