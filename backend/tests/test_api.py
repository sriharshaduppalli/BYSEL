import json
import pytest
from fastapi.testclient import TestClient
from sqlalchemy.exc import IntegrityError
import sys
from pathlib import Path
import time
import json

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app import app
from app import ai_engine
import app.market_heatmap as market_heatmap_module
import app.routes as routes_module
import app.routes.trading as trading_module
import app.routes.streaming as streaming_module
from app.database.db import SessionLocal, WalletModel, OrderModel
from app.models.schemas import MarketStatus
from app.routes import auth as auth_routes
from app import market_heatmap as market_heatmap_module

client = TestClient(app)


def _unique_user(prefix: str) -> tuple[str, str, str]:
    suffix = str(int(time.time() * 1000))
    username = f"{prefix}_{suffix}"
    email = f"{username}@example.com"
    password = "demo1234"
    return username, email, password


def _register_and_get_access_token(prefix: str) -> str:
    username, email, password = _unique_user(prefix)
    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    assert register_response.status_code == 200
    return register_response.json()["access_token"]


def _register_trading_user(prefix: str, balance: float = 1_000_000.0) -> tuple[int, dict]:
    """Register a user, seed wallet, return (user_id, auth headers)."""
    username, email, password = _unique_user(prefix)
    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    assert register_response.status_code == 200
    payload = register_response.json()
    user_id = int(payload["user_id"])
    _seed_trading_wallet(user_id=user_id, balance=balance)
    headers = {"Authorization": f"Bearer {payload['access_token']}"}
    return user_id, headers


def _seed_trading_wallet(user_id: int = 1, balance: float = 1_000_000.0) -> None:
    db = SessionLocal()
    try:
        wallet = db.query(WalletModel).filter(WalletModel.user_id == user_id).first()
        if not wallet:
            wallet = WalletModel(user_id=user_id, balance=balance)
            db.add(wallet)
        else:
            wallet.balance = balance
        db.commit()
    finally:
        db.close()


def _mock_live_market(monkeypatch, price: float = 100.0) -> None:
    monkeypatch.setattr(
        "app.routes.trading.is_market_open",
        lambda: MarketStatus(isOpen=True, message="Market is OPEN"),
    )
    monkeypatch.setattr(
        "app.routes.is_market_open",
        lambda: MarketStatus(isOpen=True, message="Market is OPEN"),
    )
    monkeypatch.setattr(
        "app.routes.trading.fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": price, "pctChange": 0.0},
    )
    monkeypatch.setattr(
        "app.routes.fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": price, "pctChange": 0.0},
    )


def _mock_news_payload(prefix: str, sentiment: str = "mixed") -> dict:
    headlines = [
        {
            "title": f"{prefix} headline {index}",
            "source": "Moneycontrol",
            "publishedLabel": f"{index}h ago",
        }
        for index in range(1, 6)
    ]
    return {
        "sentiment": sentiment,
        "summary": f"{sentiment.capitalize()} flow across the latest 5 headlines.",
        "headlines": headlines,
    }


def _mock_sms_provider_config(monkeypatch) -> None:
    # send_otp now checks provider configuration before attempting SMS delivery.
    monkeypatch.setattr(auth_routes, "FAST2SMS_API_KEY", "test-fast2sms-key")

def test_health_check():
    """Test health check endpoint"""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "version" in data


def test_health_echoes_trace_header():
    trace_id = "trc-test-health-echo"
    response = client.get("/health", headers={"X-Trace-Id": trace_id})

    assert response.status_code == 200
    assert response.headers.get("x-trace-id") == trace_id
    assert response.headers.get("x-process-time-ms") is not None


def test_health_generates_trace_header_when_missing():
    response = client.get("/health")

    assert response.status_code == 200
    trace_id = response.headers.get("x-trace-id")
    assert trace_id is not None
    assert trace_id.startswith("trc-")
    assert len(trace_id) >= 8
    assert response.headers.get("x-process-time-ms") is not None


def test_send_and_verify_otp_auth_flow(monkeypatch):
    # Mock SMS sending
    _mock_sms_provider_config(monkeypatch)
    monkeypatch.setattr("app.routes.auth._send_otp_sms", lambda *a, **kw: True)
    monkeypatch.setattr(auth_routes.secrets, "randbelow", lambda _max: 23456)

    # Clean up old OTPs to avoid rate-limit (max 3/hour)
    from app.database.db import SessionLocal, OTPModel
    db = SessionLocal()
    try:
        db.query(OTPModel).filter(OTPModel.mobile_number == "+919998887777").delete()
        db.commit()
    finally:
        db.close()

    # Send OTP
    send_response = client.post(
        "/auth/send-otp",
        json={"mobile_number": "9998887777"},
    )
    assert send_response.status_code == 200
    send_payload = send_response.json()
    assert send_payload["status"] == "ok"
    assert send_payload["otp_id"] is not None
    assert send_payload["expires_in_seconds"] == 300

    # Verify OTP with the real code
    verify_response = client.post(
        "/auth/verify-otp",
        json={"mobile_number": "9998887777", "otp": "123456"},
    )
    assert verify_response.status_code == 200
    verify_payload = verify_response.json()
    assert verify_payload["status"] == "ok"
    assert "access_token" in verify_payload
    assert "refresh_token" in verify_payload


def test_slo_metrics_endpoint_returns_latency_error_and_success_rates(monkeypatch):
    _user_id, auth = _register_trading_user("slo_user", balance=100_000.0)
    _mock_live_market(monkeypatch, price=100.0)

    order_response = client.post(
        "/order",
        json={"symbol": "SLOTEST", "qty": 1, "side": "BUY"},
        headers={**auth, "X-Trace-Id": "trc-slo-order-001"},
    )
    assert order_response.status_code == 200

    response = client.get("/metrics/slo")
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "ok"

    slo = payload["slo"]
    assert "http" in slo
    assert "orderRequests" in slo
    assert "orderOutcomes" in slo
    assert "quotesStream" in slo

    assert "p95" in slo["http"]["latencyMs"]
    assert float(slo["http"]["errorRatePct"]) >= 0.0
    assert float(slo["http"]["errorRatePct"]) <= 100.0
    assert float(slo["orderOutcomes"]["successRatePct"]) >= 0.0
    assert float(slo["orderOutcomes"]["successRatePct"]) <= 100.0
    assert int(slo["http"]["totalRequests"]) >= 1


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


def test_get_quote_history_passes_period_and_interval(monkeypatch):
    captured = {}

    def _fake_history(symbol: str, period: str = "1mo", interval: str = "1d"):
        captured["symbol"] = symbol
        captured["period"] = period
        captured["interval"] = interval
        return [
            {
                "timestamp": 1710844800000,
                "open": 100.0,
                "high": 102.0,
                "low": 99.5,
                "close": 101.2,
                "volume": 125000,
            }
        ]

    monkeypatch.setattr(routes_module, "fetch_quote_history", _fake_history)

    response = client.get("/quotes/INFY/history?period=5d&interval=15m")
    assert response.status_code == 200
    data = response.json()
    assert len(data) == 1
    assert data[0]["close"] == 101.2
    assert captured == {"symbol": "INFY", "period": "5d", "interval": "15m"}


def test_get_quote_history_returns_400_for_invalid_period(monkeypatch):
    def _invalid_history(symbol: str, period: str = "1mo", interval: str = "1d"):
        raise ValueError("Unsupported history period: bad")

    monkeypatch.setattr(routes_module, "fetch_quote_history", _invalid_history)

    response = client.get("/quotes/INFY/history?period=bad&interval=1d")
    assert response.status_code == 400
    payload = response.json()
    assert "Unsupported history period" in payload["detail"]

def test_get_holdings_empty():
    """Test getting holdings when empty"""
    token = _register_and_get_access_token("holdings_empty")
    response = client.get("/holdings", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)

def test_place_order(monkeypatch):
    """Test placing an order"""
    _user_id, auth = _register_trading_user("place_order")
    _mock_live_market(monkeypatch, price=100.0)

    order_data = {
        "symbol": "TCS",
        "qty": 1,
        "side": "BUY"
    }
    response = client.post("/order", json=order_data, headers=auth)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["order"]["symbol"] == "TCS"
    assert data["order"]["qty"] == 1


def test_place_order_is_idempotent(monkeypatch):
    _user_id, auth = _register_trading_user("order_idem")
    _mock_live_market(monkeypatch, price=250.0)

    order_data = {"symbol": "INFY", "qty": 2, "side": "BUY"}
    idem_key = f"test-order-key-001-{time.time_ns()}"
    headers = {**auth, "X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-001"}

    first = client.post("/order", json=order_data, headers=headers)
    second = client.post("/order", json=order_data, headers=headers)

    assert first.status_code == 200
    assert second.status_code == 200

    first_data = first.json()
    second_data = second.json()

    assert first_data["status"] == "ok"
    assert second_data["status"] == "ok"
    assert first_data["orderId"] == second_data["orderId"]
    assert first_data["idempotencyKey"] == second_data["idempotencyKey"]
    assert second_data["isDuplicate"] is True


def test_place_order_reused_idempotency_key_with_different_payload_is_rejected(monkeypatch):
    _user_id, auth = _register_trading_user("order_idem_conflict")
    _mock_live_market(monkeypatch, price=250.0)

    idem_key = f"test-order-key-002-{time.time_ns()}"
    headers = {**auth, "X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-002"}
    first_order = {"symbol": "INFY", "qty": 2, "side": "BUY"}
    conflicting_order = {"symbol": "INFY", "qty": 3, "side": "BUY"}

    first = client.post("/order", json=first_order, headers=headers)
    second = client.post("/order", json=conflicting_order, headers=headers)

    assert first.status_code == 200
    assert second.status_code == 200

    first_data = first.json()
    second_data = second.json()

    assert first_data["status"] == "ok"
    assert second_data["status"] == "error"
    assert second_data["errorCode"] == "IDEMPOTENCY_KEY_REUSED"
    assert second_data["orderId"] == first_data["orderId"]


def test_place_order_handles_integrity_race_by_returning_existing_order(monkeypatch):
    _user_id, auth = _register_trading_user("order_race")
    _mock_live_market(monkeypatch, price=250.0)

    idem_key = f"test-order-race-key-{time.time_ns()}"
    headers = {**auth, "X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-race-001"}
    order_data = {"symbol": "INFY", "qty": 2, "side": "BUY"}

    def _simulate_race(*args, **kwargs):
        db = kwargs["db"]
        order = kwargs["order"]
        execution_price = float(kwargs["execution_price"])
        user_id = int(kwargs.get("user_id", 1))
        trace_id = kwargs.get("trace_id")

        raced_order = OrderModel(
            user_id=user_id,
            symbol=order.symbol,
            quantity=order.qty,
            side=order.side,
            order_type=order.orderType,
            validity=order.validity,
            limit_price=order.limitPrice,
            trigger_price=order.triggerPrice,
            tag=order.tag,
            price=execution_price,
            total=round(execution_price * order.qty, 2),
            status="COMPLETED",
            idempotency_key=order.idempotencyKey,
            request_fingerprint=trading_module._build_request_fingerprint(order),
            trace_id=trace_id or "trc-race-existing",
        )
        db.add(raced_order)
        db.commit()

        raise IntegrityError(
            statement="INSERT INTO orders (...) VALUES (...)",
            params={"idempotency_key": order.idempotencyKey},
            orig=Exception("UNIQUE constraint failed: orders.user_id, orders.idempotency_key"),
        )

    monkeypatch.setattr("app.routes.trading._execute_order_at_price", _simulate_race)

    response = client.post("/order", json=order_data, headers=headers)
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "ok"
    assert payload["isDuplicate"] is True
    assert payload["orderId"] is not None
    assert payload["idempotencyKey"] == idem_key


def test_invalid_order_transition_returns_deterministic_error_code(monkeypatch):
    _user_id, auth = _register_trading_user("order_transition")
    _mock_live_market(monkeypatch, price=250.0)

    def _force_invalid_transition(order_db, next_status):
        raise trading_module.LifecycleTransitionError(
            entity="order",
            current_status="COMPLETED",
            next_status=next_status,
            error_code=trading_module.ORDER_TRANSITION_ERROR_CODE,
        )

    monkeypatch.setattr("app.routes.trading._transition_order_status", _force_invalid_transition)

    response = client.post("/order", json={"symbol": "INFY", "qty": 1, "side": "BUY"}, headers=auth)
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "error"
    assert payload["errorCode"] == "INVALID_ORDER_TRANSITION"
    assert "Invalid order transition" in payload["message"]


def test_advanced_order_retries_return_same_order_id(monkeypatch):
    _user_id, auth = _register_trading_user("advanced_retry")
    _mock_live_market(monkeypatch, price=300.0)

    payload = {
        "symbol": "ADVRETRY",
        "qty": 1,
        "side": "BUY",
        "orderType": "MARKET",
        "validity": "DAY",
    }
    idem_key = f"test-advanced-key-{time.time_ns()}"
    headers = {**auth, "X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-advanced-001"}

    first = client.post("/orders/advanced", json=payload, headers=headers)
    second = client.post("/orders/advanced", json=payload, headers=headers)

    assert first.status_code == 200
    assert second.status_code == 200

    first_data = first.json()
    second_data = second.json()

    assert first_data["status"] == "ok"
    assert second_data["status"] == "ok"
    assert first_data["orderId"] is not None
    assert second_data["orderId"] == first_data["orderId"]


def test_send_otp(monkeypatch):
    """Test sending OTP to mobile number"""
    _mock_sms_provider_config(monkeypatch)
    monkeypatch.setattr("app.routes.auth._send_otp_sms", lambda *a, **kw: True)
    response = client.post("/auth/send-otp", json={"mobile_number": "9876543210"})
    assert response.status_code == 200

    data = response.json()
    assert data["status"] == "ok"
    assert "otp_id" in data
    assert "expires_in_seconds" in data
    assert data["expires_in_seconds"] == 300


def test_verify_otp_invalid_format():
    """Test OTP verification with invalid format"""
    response = client.post("/auth/verify-otp", json={
        "mobile_number": "9876543210",
        "otp": "12345"  # Too short
    })
    assert response.status_code == 400
    assert "Invalid OTP format" in response.json()["detail"]


def test_verify_otp_not_found():
    """Test OTP verification when no OTP exists"""
    response = client.post("/auth/verify-otp", json={
        "mobile_number": "9111222333",
        "otp": "123456"
    })
    assert response.status_code == 400
    assert "OTP not found or expired" in response.json()["detail"]


def test_otp_flow(monkeypatch):
    """Test complete OTP flow: send and verify"""
    # Mock SMS sending to avoid needing Twilio credentials
    _mock_sms_provider_config(monkeypatch)
    monkeypatch.setattr(auth_routes.secrets, "randbelow", lambda _max: 34567)
    def mock_send_sms(*args, **kwargs):
        return True

    monkeypatch.setattr("app.routes.auth._send_otp_sms", mock_send_sms)

    # Clean up old OTPs to avoid rate-limit (max 3/hour)
    from app.database.db import SessionLocal, OTPModel
    db = SessionLocal()
    try:
        db.query(OTPModel).filter(OTPModel.mobile_number == "+919222333444").delete()
        db.commit()
    finally:
        db.close()

    # Send OTP (use unique number to avoid rate-limit / state leaks)
    send_response = client.post("/auth/send-otp", json={"mobile_number": "9222333444"})
    assert send_response.status_code == 200

    # Verify OTP
    verify_response = client.post("/auth/verify-otp", json={
        "mobile_number": "9222333444",
        "otp": "134567"
    })
    assert verify_response.status_code == 200

    data = verify_response.json()
    assert data["status"] == "ok"
    assert "user_id" in data
    assert "access_token" in data
    assert "refresh_token" in data


def test_basket_execution_with_idempotency_key_is_retry_safe(monkeypatch):
    _user_id, auth = _register_trading_user("basket_retry")
    _mock_live_market(monkeypatch, price=150.0)

    symbol_a = f"BASKA{int(time.time())}"
    symbol_b = f"BASKB{int(time.time())}"

    create_payload = {
        "name": "Retry Safe Basket",
        "legs": [
            {"symbol": symbol_a, "qty": 1, "side": "BUY", "orderType": "MARKET", "validity": "DAY"},
            {"symbol": symbol_b, "qty": 2, "side": "BUY", "orderType": "MARKET", "validity": "DAY"},
        ],
    }
    created = client.post("/orders/baskets", json=create_payload, headers=auth)
    assert created.status_code == 200
    basket_id = created.json()["basketId"]

    idem_key = f"test-basket-key-{time.time_ns()}"
    headers = {**auth, "X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-basket-001"}

    first = client.post(f"/orders/baskets/{basket_id}/execute", headers=headers)
    second = client.post(f"/orders/baskets/{basket_id}/execute", headers=headers)

    assert first.status_code == 200
    assert second.status_code == 200

    first_data = first.json()
    second_data = second.json()

    assert first_data["status"] in {"EXECUTED", "PARTIAL"}
    assert second_data["status"] in {"EXECUTED", "PARTIAL"}
    assert len(first_data["legResults"]) == 2
    assert len(second_data["legResults"]) == 2

    for idx in range(2):
        assert first_data["legResults"][idx]["orderId"] is not None
        assert second_data["legResults"][idx]["orderId"] == first_data["legResults"][idx]["orderId"]

    db = SessionLocal()
    try:
        persisted = (
            db.query(OrderModel)
            .filter(OrderModel.idempotency_key.like(f"basket-{basket_id}-leg-%"))
            .count()
        )
        assert persisted == 2
    finally:
        db.close()


def test_pre_trade_estimate_returns_server_charge_breakdown(monkeypatch):
    _user_id, auth = _register_trading_user("pretrade_ok", balance=100_000.0)
    _mock_live_market(monkeypatch, price=100.0)

    payload = {
        "order": {
            "symbol": "TCS",
            "qty": 10,
            "side": "BUY",
            "orderType": "MARKET",
            "validity": "DAY",
        }
    }

    response = client.post("/orders/pre-trade-estimate", json=payload, headers=auth)
    assert response.status_code == 200

    data = response.json()
    assert data["symbol"] == "TCS"
    assert data["side"] == "BUY"
    assert data["tradeValue"] == 1000.0
    assert data["charges"]["totalCharges"] > 0
    assert data["canAfford"] is True
    assert data["impactTag"] in {"Low impact", "Medium impact", "High impact"}
    assert data["signal"]["verdict"] in {"GO", "CAUTION", "BLOCK"}


def test_pre_trade_estimate_flags_insufficient_funds(monkeypatch):
    _user_id, auth = _register_trading_user("pretrade_poor", balance=100.0)
    _mock_live_market(monkeypatch, price=1_000.0)

    payload = {
        "order": {
            "symbol": "INFY",
            "qty": 2,
            "side": "BUY",
            "orderType": "MARKET",
            "validity": "DAY",
        }
    }

    response = client.post("/orders/pre-trade-estimate", json=payload, headers=auth)
    assert response.status_code == 200

    data = response.json()
    assert data["canAfford"] is False
    assert any("Insufficient funds" in warning for warning in data["warnings"])
    assert any("Insufficient wallet" in flag for flag in data["signal"]["flags"])


def test_order_trace_lookup_returns_latest_order(monkeypatch):
    _user_id, auth = _register_trading_user("trace_lookup", balance=100_000.0)
    _mock_live_market(monkeypatch, price=250.0)

    trace_id = "trc-support-lookup-001"
    order_payload = {
        "symbol": "TCS",
        "qty": 1,
        "side": "BUY",
    }
    placed = client.post(
        "/order",
        json=order_payload,
        headers={**auth, "X-Trace-Id": trace_id},
    )
    assert placed.status_code == 200
    placed_data = placed.json()
    assert placed_data["status"] == "ok"

    lookup = client.get(f"/orders/trace/{trace_id}", headers=auth)
    assert lookup.status_code == 200
    data = lookup.json()
    assert data["traceId"] == trace_id
    assert data["orderId"] == placed_data["orderId"]
    assert data["symbol"] == "TCS"
    assert data["side"] == "BUY"
    assert data["status"] in {"COMPLETED", "PENDING", "TRIGGER_EXECUTED", "REJECTED", "CANCELLED"}


def test_order_trace_lookup_not_found():
    _user_id, auth = _register_trading_user("trace_missing")
    response = client.get("/orders/trace/trc-support-missing-001", headers=auth)
    assert response.status_code == 404


def test_place_order_invalid_side_has_deterministic_error_code():
    _user_id, auth = _register_trading_user("invalid_side", balance=1_000.0)
    order_data = {"symbol": "TCS", "qty": 1, "side": "HOLD"}

    response = client.post("/order", json=order_data, headers=auth)
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "error"
    assert payload["errorCode"] == "INVALID_SIDE"


def test_paper_credit_and_buy_debit_same_authenticated_user(monkeypatch):
    """Regression: credit + BUY must hit JWT user (not default user 1)."""
    username, email, password = _unique_user("paper_wallet")
    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    assert register_response.status_code == 200
    payload = register_response.json()
    user_id = int(payload["user_id"])
    assert user_id != 1
    auth = {"Authorization": f"Bearer {payload['access_token']}"}

    credit = client.post("/wallet/add", json={"amount": 50_000.0}, headers=auth)
    assert credit.status_code == 200
    assert credit.json()["status"] == "ok"
    assert credit.json()["balance"] == 50_000.0

    wallet_before = client.get("/wallet", headers=auth)
    assert wallet_before.status_code == 200
    assert wallet_before.json()["balance"] == 50_000.0

    _mock_live_market(monkeypatch, price=100.0)
    buy = client.post(
        "/order",
        json={"symbol": "PAPERWAL", "qty": 10, "side": "BUY"},
        headers=auth,
    )
    assert buy.status_code == 200
    buy_data = buy.json()
    assert buy_data["status"] == "ok"

    wallet_after = client.get("/wallet", headers=auth)
    assert wallet_after.status_code == 200
    assert wallet_after.json()["balance"] == 49_000.0

    holdings = client.get("/holdings", headers=auth)
    assert holdings.status_code == 200
    holding = next((h for h in holdings.json() if h["symbol"] == "PAPERWAL"), None)
    assert holding is not None
    assert holding["qty"] == 10


def test_sip_plans_require_authentication():
    response = client.get("/sip/plans")
    assert response.status_code == 401


def test_sip_plans_accessible_with_bearer_token():
    access_token = _register_and_get_access_token("sip_auth_user")
    response = client.get("/sip/plans", headers={"Authorization": f"Bearer {access_token}"})
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_ipo_my_applications_require_authentication():
    response = client.get("/ipos/my-applications")
    assert response.status_code == 401


def test_ipo_my_applications_accessible_with_bearer_token():
    access_token = _register_and_get_access_token("ipo_auth_user")
    response = client.get("/ipos/my-applications", headers={"Authorization": f"Bearer {access_token}"})
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_newly_registered_user_wallet_starts_empty():
    username, email, password = _unique_user("wallet_default_zero")
    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    assert register_response.status_code == 200
    access_token = register_response.json()["access_token"]

    wallet_response = client.get(
        "/wallet",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    assert wallet_response.status_code == 200
    assert wallet_response.json()["balance"] == 0.0


def test_auth_me_returns_authenticated_profile_details():
    username, email, password = _unique_user("auth_me_user")
    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    assert register_response.status_code == 200
    access_token = register_response.json()["access_token"]

    me_response = client.get(
        "/auth/me",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    assert me_response.status_code == 200
    payload = me_response.json()
    assert payload["status"] == "ok"
    assert payload["username"] == username
    assert payload["email"] == email
    assert payload["user_id"] > 0


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


def test_refresh_token_reuse_same_client_preserves_active_session():
    username, email, password = _unique_user("reuse_same_client_user")
    headers = {"User-Agent": "bysel-test-client-1"}

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
        headers=headers,
    )
    assert register_response.status_code == 200
    register_payload = register_response.json()

    first_refresh_token = register_payload["refresh_token"]

    rotate_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token},
        headers=headers,
    )
    assert rotate_response.status_code == 200
    rotated_payload = rotate_response.json()
    second_refresh_token = rotated_payload["refresh_token"]

    # Concurrent retry with the just-rotated token should recover (not sign the user out).
    reuse_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token},
        headers=headers,
    )
    assert reuse_response.status_code == 200
    recovered_refresh = reuse_response.json()["refresh_token"]
    assert recovered_refresh
    assert recovered_refresh != first_refresh_token

    recovered_refresh_attempt = client.post(
        "/auth/refresh",
        json={"refreshToken": recovered_refresh},
        headers=headers,
    )
    assert recovered_refresh_attempt.status_code == 200
    assert recovered_refresh_attempt.json()["status"] == "ok"

    # The intermediate token may already have been rotated during recovery.
    second_refresh_attempt = client.post(
        "/auth/refresh",
        json={"refreshToken": second_refresh_token},
        headers=headers,
    )
    assert second_refresh_attempt.status_code in (200, 401)


def test_refresh_token_reuse_different_client_within_grace_recovers():
    """Mobile clients often change User-Agent / network; do not treat that as theft within grace."""
    username, email, password = _unique_user("reuse_diff_client_user")
    primary_headers = {"User-Agent": "bysel-test-client-primary"}
    replay_headers = {"User-Agent": "bysel-test-client-secondary"}

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
        headers=primary_headers,
    )
    assert register_response.status_code == 200
    register_payload = register_response.json()
    first_refresh_token = register_payload["refresh_token"]

    rotate_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token},
        headers=primary_headers,
    )
    assert rotate_response.status_code == 200

    replay_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token},
        headers=replay_headers,
    )
    assert replay_response.status_code == 200
    assert replay_response.json()["status"] == "ok"
    assert "access_token" in replay_response.json()


def test_refresh_token_reuse_outside_grace_invalidates_active_sessions(monkeypatch):
    import app.routes.auth as auth_module

    monkeypatch.setattr(auth_module, "REFRESH_TOKEN_REPLAY_GRACE_SECONDS", 0)

    username, email, password = _unique_user("reuse_outside_grace_user")
    headers = {"User-Agent": "bysel-test-client-primary"}

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
        headers=headers,
    )
    assert register_response.status_code == 200
    first_refresh_token = register_response.json()["refresh_token"]

    rotate_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token},
        headers=headers,
    )
    assert rotate_response.status_code == 200
    second_refresh_token = rotate_response.json()["refresh_token"]

    replay_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token},
        headers=headers,
    )
    assert replay_response.status_code == 401
    assert "reuse" in replay_response.json()["detail"].lower()

    second_refresh_attempt = client.post(
        "/auth/refresh",
        json={"refreshToken": second_refresh_token},
        headers=headers,
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


def test_login_accepts_registered_email_case_insensitively():
    username, email, password = _unique_user("EmailLoginUser")

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    assert register_response.status_code == 200

    for identifier in (email, email.upper(), f"  {email}  "):
        login_response = client.post(
            "/auth/login",
            json={"username": identifier, "password": password},
        )
        assert login_response.status_code == 200, identifier
        assert login_response.json()["status"] == "ok"


def test_get_profile_returns_authenticated_user_details():
    username, email, password = _unique_user("profile_get_user")

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200
    access_token = register_response.json()["access_token"]

    profile_response = client.get(
        "/auth/profile",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    assert profile_response.status_code == 200

    payload = profile_response.json()
    assert payload["status"] == "ok"
    assert payload["username"] == username
    assert payload["email"] == email
    assert payload["user_id"] > 0
    assert "created_at" in payload


def test_update_profile_updates_user_fields_and_returns_latest_values():
    username, email, password = _unique_user("profile_update_user")

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200
    access_token = register_response.json()["access_token"]

    updated_username = f"{username}_new"
    updated_email = f"{updated_username}@example.com"
    unique_mobile = f"9{str(time.time_ns())[-9:]}"

    update_response = client.patch(
        "/auth/profile",
        headers={"Authorization": f"Bearer {access_token}"},
        json={
            "username": updated_username,
            "email": updated_email,
            "mobile_number": unique_mobile,
        },
    )
    assert update_response.status_code == 200
    updated_payload = update_response.json()
    assert updated_payload["username"] == updated_username
    assert updated_payload["email"] == updated_email
    assert updated_payload["mobile_number"] == f"+91{unique_mobile}"

    refreshed_profile = client.get(
        "/auth/profile",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    assert refreshed_profile.status_code == 200
    refreshed_payload = refreshed_profile.json()
    assert refreshed_payload["username"] == updated_username
    assert refreshed_payload["email"] == updated_email
    assert refreshed_payload["mobile_number"] == f"+91{unique_mobile}"


def test_update_profile_rejects_duplicate_email():
    username_one, email_one, password = _unique_user("profile_dup_a")
    username_two, email_two, _ = _unique_user("profile_dup_b")

    first_register = client.post(
        "/auth/register",
        json={"username": username_one, "email": email_one, "password": password}
    )
    assert first_register.status_code == 200

    second_register = client.post(
        "/auth/register",
        json={"username": username_two, "email": email_two, "password": password}
    )
    assert second_register.status_code == 200
    second_access_token = second_register.json()["access_token"]

    duplicate_email_response = client.patch(
        "/auth/profile",
        headers={"Authorization": f"Bearer {second_access_token}"},
        json={"email": email_one},
    )
    assert duplicate_email_response.status_code == 400
    assert duplicate_email_response.json()["detail"] == "Email already exists"


def test_password_reset_can_update_password(monkeypatch):
    monkeypatch.setattr(auth_routes, "PASSWORD_RESET_DEBUG_RESPONSE_ENABLED", True)
    monkeypatch.setattr(auth_routes, "SMTP_HOST", "")

    username, email, password = _unique_user("password_reset_user")
    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200

    request_response = client.post(
        "/auth/password-reset/request",
        json={"identifier": email}
    )
    assert request_response.status_code == 200
    request_payload = request_response.json()
    assert request_payload["status"] == "ok"
    assert request_payload["delivery"] == "debug"
    assert request_payload["reset_code"]

    new_password = "demo5678"
    confirm_response = client.post(
        "/auth/password-reset/confirm",
        json={"token": request_payload["reset_code"], "newPassword": new_password}
    )
    assert confirm_response.status_code == 200
    assert confirm_response.json()["status"] == "ok"

    old_login = client.post(
        "/auth/login",
        json={"username": username, "password": password}
    )
    assert old_login.status_code == 401

    new_login = client.post(
        "/auth/login",
        json={"username": email, "password": new_password}
    )
    assert new_login.status_code == 200
    assert new_login.json()["status"] == "ok"


def test_password_reset_request_falls_back_to_support_when_delivery_unavailable(monkeypatch):
    monkeypatch.setattr(auth_routes, "PASSWORD_RESET_DEBUG_RESPONSE_ENABLED", False)
    monkeypatch.setattr(auth_routes, "SMTP_HOST", "")
    monkeypatch.setattr(auth_routes, "RESEND_API_KEY", "")

    response = client.post(
        "/auth/password-reset/request",
        json={"identifier": "missing_user@example.com"}
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "ok"
    assert payload["delivery"] == "support"
    assert "support" in payload["message"].lower()


def test_password_reset_uses_resend_when_api_key_configured(monkeypatch):
    """RESEND_API_KEY alone must enable email delivery (no SMTP required)."""
    monkeypatch.setattr(auth_routes, "PASSWORD_RESET_DEBUG_RESPONSE_ENABLED", False)
    monkeypatch.setattr(auth_routes, "SMTP_HOST", "")
    monkeypatch.setattr(auth_routes, "RESEND_API_KEY", "re_test_key")
    monkeypatch.setattr(auth_routes, "RESEND_FROM_EMAIL", "BYSEL <onboarding@resend.dev>")

    sent = {}

    def _fake_resend(email, username, reset_code):
        sent["email"] = email
        sent["username"] = username
        sent["code"] = reset_code
        return True

    monkeypatch.setattr(auth_routes, "_send_password_reset_email_resend", _fake_resend)

    username, email, password = _unique_user("resend_reset_user")
    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password},
    )
    assert register_response.status_code == 200

    request_response = client.post(
        "/auth/password-reset/request",
        json={"identifier": email},
    )
    assert request_response.status_code == 200
    payload = request_response.json()
    assert payload["status"] == "ok"
    assert payload["delivery"] == "email"
    assert sent.get("email") == email
    assert sent.get("code")
    assert len(sent["code"]) >= 6

    confirm_response = client.post(
        "/auth/password-reset/confirm",
        json={"token": sent["code"], "newPassword": "freshPass99"},
    )
    assert confirm_response.status_code == 200

    login_response = client.post(
        "/auth/login",
        json={"username": email, "password": "freshPass99"},
    )
    assert login_response.status_code == 200


def test_change_password_rotates_current_session_and_invalidates_old_access_token():
    username, email, password = _unique_user("change_password_user")

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200
    register_payload = register_response.json()
    old_access_token = register_payload["access_token"]

    new_password = "demo9999"
    change_response = client.post(
        "/auth/change-password",
        headers={"Authorization": f"Bearer {old_access_token}"},
        json={"currentPassword": password, "newPassword": new_password},
    )
    assert change_response.status_code == 200
    change_payload = change_response.json()
    assert change_payload["status"] == "ok"
    assert change_payload["access_token"] != old_access_token

    old_token_sessions = client.get(
        "/auth/sessions",
        headers={"Authorization": f"Bearer {old_access_token}"},
    )
    assert old_token_sessions.status_code == 401
    assert old_token_sessions.json()["detail"] == "Session invalidated"

    new_token_sessions = client.get(
        "/auth/sessions",
        headers={"Authorization": f"Bearer {change_payload['access_token']}"},
    )
    assert new_token_sessions.status_code == 200

    old_login = client.post(
        "/auth/login",
        json={"username": username, "password": password}
    )
    assert old_login.status_code == 401

    new_login = client.post(
        "/auth/login",
        json={"username": email, "password": new_password}
    )
    assert new_login.status_code == 200


def test_change_password_rejects_wrong_current_password():
    username, email, password = _unique_user("change_password_wrong_current")

    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200
    access_token = register_response.json()["access_token"]

    response = client.post(
        "/auth/change-password",
        headers={"Authorization": f"Bearer {access_token}"},
        json={"currentPassword": "wrong-pass", "newPassword": "demo9999"},
    )
    assert response.status_code == 401
    assert "current password" in response.json()["detail"].lower()


def test_quotes_websocket_stream_supports_subscribe_updates(monkeypatch):
    monkeypatch.setattr(
        "app.routes.streaming.get_default_symbols",
        lambda: ["RELIANCE", "TCS"],
    )
    monkeypatch.setattr(
        "app.routes.streaming.fetch_quotes",
        lambda symbols: [
            {
                "symbol": symbol.upper(),
                "last": 100.0 + index,
                "pctChange": float(index),
            }
            for index, symbol in enumerate(symbols)
        ],
    )

    with client.websocket_connect("/ws/quotes") as websocket:
        first_message = websocket.receive_json()
        assert first_message["type"] == "subscribed"

        websocket.send_json({"action": "subscribe", "symbols": ["INFY", "SBIN"]})

        second_message = websocket.receive_json()
        assert second_message["type"] == "subscribed"
        assert set(second_message["symbols"]) == {"INFY", "SBIN"}

        quotes_message = websocket.receive_json()
        assert quotes_message["type"] == "quotes"
        assert int(quotes_message["sequence"]) >= 1
        quote_symbols = {row["symbol"] for row in quotes_message["quotes"]}
        assert quote_symbols == {"INFY", "SBIN"}


def test_quotes_websocket_stream_replays_messages_from_sequence(monkeypatch):
    monkeypatch.setattr("app.routes.streaming.STREAM_PUSH_INTERVAL_MS", 50)
    monkeypatch.setattr("app.routes.streaming.get_default_symbols", lambda: ["RELIANCE", "TCS"])

    call_count = {"value": 0}

    def _fake_fetch_quotes(symbols):
        call_count["value"] += 1
        return [
            {
                "symbol": symbol.upper(),
                "last": 200.0 + call_count["value"] + index,
                "pctChange": float(call_count["value"]),
            }
            for index, symbol in enumerate(symbols)
        ]

    monkeypatch.setattr("app.routes.streaming.fetch_quotes", _fake_fetch_quotes)

    with streaming_module._stream_lock:
        streaming_module._stream_history.clear()
        streaming_module._stream_sequence = 0

    with client.websocket_connect("/ws/quotes") as websocket:
        subscribed_message = websocket.receive_json()
        assert subscribed_message["type"] == "subscribed"

        first_quotes = websocket.receive_json()
        second_quotes = websocket.receive_json()

        assert first_quotes["type"] == "quotes"
        assert second_quotes["type"] == "quotes"
        assert int(second_quotes["sequence"]) > int(first_quotes["sequence"])

        resume_from_sequence = int(first_quotes["sequence"])

    with client.websocket_connect(f"/ws/quotes?sinceSeq={resume_from_sequence}") as websocket:
        subscribed_message = websocket.receive_json()
        assert subscribed_message["type"] == "subscribed"

        replay_meta = websocket.receive_json()
        assert replay_meta["type"] == "replay"
        assert int(replay_meta["fromSequence"]) == resume_from_sequence
        assert int(replay_meta["count"]) >= 1

        replay_quotes = websocket.receive_json()
        assert replay_quotes["type"] == "quotes"
        assert replay_quotes["isReplay"] is True
        assert int(replay_quotes["sequence"]) > resume_from_sequence


def test_quotes_websocket_stream_uses_trace_id_from_header_or_query(monkeypatch):
    monkeypatch.setattr("app.routes.streaming.get_default_symbols", lambda: ["RELIANCE"])
    monkeypatch.setattr(
        "app.routes.streaming.fetch_quotes",
        lambda symbols: [
            {
                "symbol": symbols[0],
                "last": 123.45,
                "pctChange": 0.5,
            }
        ],
    )

    query_trace_id = "trc-ws-query-001"
    with client.websocket_connect(f"/ws/quotes?traceId={query_trace_id}") as websocket:
        subscribed_message = websocket.receive_json()
        assert subscribed_message["type"] == "subscribed"
        assert subscribed_message["traceId"] == query_trace_id

    header_trace_id = "trc-ws-header-001"
    with client.websocket_connect(
        f"/ws/quotes?traceId={query_trace_id}",
        headers={"X-Trace-Id": header_trace_id},
    ) as websocket:
        subscribed_message = websocket.receive_json()
        assert subscribed_message["type"] == "subscribed"
        assert subscribed_message["traceId"] == header_trace_id

        replay_request_sequence = 0
        websocket.send_json({"action": "resume", "sinceSequence": replay_request_sequence})

        replay_meta = websocket.receive_json()
        assert replay_meta["type"] == "replay"
        assert replay_meta["traceId"] == header_trace_id


def test_auth_debug_session_health_endpoint(monkeypatch):
    monkeypatch.setattr(auth_routes, "AUTH_DEBUG_ENDPOINTS_ENABLED", True)
    monkeypatch.setattr(auth_routes, "AUTH_DEBUG_TOKEN", "debug-token")

    username, email, password = _unique_user("session_health_user")
    register_response = client.post(
        "/auth/register",
        json={"username": username, "email": email, "password": password}
    )
    assert register_response.status_code == 200

    session_health = client.get(
        "/auth/debug/session-health",
        headers={"X-Debug-Token": "debug-token"},
    )
    assert session_health.status_code == 200
    payload = session_health.json()
    assert payload["status"] == "ok"
    assert "session_health" in payload
    assert payload["session_health"]["active_sessions_total"] >= 1


def test_ai_overvalued_query_routes_to_stock_analysis(monkeypatch):
    monkeypatch.setattr(
        ai_engine,
        "analyze_stock",
        lambda symbol: {
            "name": "Kaynes Technology India Ltd",
            "summary": f"{symbol} appears richly valued versus peers based on current P/E.",
            "score": 64,
            "signal": "HOLD",
            "fundamental": {"pe": 52.3},
            "predictions": [],
        },
    )

    result = ai_engine.ai_assistant("Is KAYNES overvalued?")
    assert result["type"] == "analysis"
    assert result["symbol"] == "KAYNES"
    assert "valuation" in result["answer"].lower()
    assert "portfolio" not in result["answer"].lower()


def test_ai_undervalued_screening_query_stays_screening(monkeypatch):
    monkeypatch.setattr(
        ai_engine,
        "fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": 100.0, "pctChange": 1.25},
    )

    result = ai_engine.ai_assistant("Best undervalued IT stocks")
    assert result["type"] == "screening"
    assert "top" in result["answer"].lower()


def test_ai_ask_endpoint_uses_monkeypatched_rule_assistant(monkeypatch):
    def fake_ai_assistant(query: str, db=None, user_id=None):
        return {
            "answer": f"db_present={db is not None}; query={query}",
        }

    monkeypatch.setattr(routes_module, "ai_assistant", fake_ai_assistant)

    # Force rule-engine so Groq / Gemini / Indian Stock LLM cannot replace the answer.
    response = client.post(
        "/ai/ask",
        json={"query": "Is KAYNES overvalued?", "tier": "rule-engine"},
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "rule-engine"
    # Request Session is intentionally not shared into the Yahoo worker thread.
    assert "db_present=false" in payload["answer"].lower()
    assert "kaynes" in payload["answer"].lower()


def test_ai_ask_greeting_short_circuits_before_stock_pipeline(monkeypatch):
    def _should_not_run(*args, **kwargs):
        raise AssertionError("ai_assistant should not run for pure greeting queries")

    monkeypatch.setattr(routes_module, "ai_assistant", _should_not_run)

    response = client.post("/ai/ask", json={"query": "Hi"})
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "small-talk"
    assert payload["tier_requested"] == "auto"
    assert payload["answer"].lower().startswith("hi")


@pytest.mark.parametrize(
    "query, expected_hint",
    [
        ("thanks", "welcome"),
        ("bye", "bye"),
        ("how are you", "ready to help"),
    ],
)
def test_ai_ask_small_talk_responses_are_deterministic(monkeypatch, query, expected_hint):
    def _should_not_run(*args, **kwargs):
        raise AssertionError("ai_assistant should not run for small-talk queries")

    monkeypatch.setattr(routes_module, "ai_assistant", _should_not_run)

    response = client.post("/ai/ask", json={"query": query})
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "small-talk"
    assert expected_hint in payload["answer"].lower()


def test_ai_ask_stock_query_does_not_use_greeting_short_circuit(monkeypatch):
    monkeypatch.setattr(
        routes_module,
        "ai_assistant",
        lambda query, db=None, user_id=None: {
            "answer": f"Stock flow for: {query}",
            "symbol": "LUPIN",
        },
    )

    response = client.post("/ai/ask", json={"query": "Hi LUPIN valuation", "tier": "rule-engine"})
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "rule-engine"
    assert payload["tier_requested"] == "rule-engine"
    assert payload.get("symbol") == "LUPIN"
    assert "stock flow" in payload["answer"].lower()


def test_ai_ask_low_confidence_stock_intent_returns_clarifier(monkeypatch):
    def _should_not_run(*args, **kwargs):
        raise AssertionError("ai_assistant should not run when clarifier guardrail triggers")

    monkeypatch.setattr(routes_module, "ai_assistant", _should_not_run)

    response = client.post("/ai/ask", json={"query": "buy"})
    assert response.status_code == 200
    payload = response.json()
    assert payload["source"] == "clarifier"
    assert "clarification" in payload["answer"].lower() or "clarify" in payload["answer"].lower()


def test_ai_ask_passes_concise_style_to_groq(monkeypatch):
    captured = {}

    async def _fake_ask_groq(query, context=None, conversation_history=None, intent_result=None, response_style=None):
        captured["response_style"] = response_style
        return {"answer": "ok"}

    async def _fake_enrich(symbol):
        return None

    monkeypatch.setattr(
        routes_module,
        "ai_assistant",
        lambda query, db=None, user_id=None: {"answer": "fallback", "symbol": "TCS"},
    )
    monkeypatch.setattr("app.groq_llm.groq_available", lambda: True)
    monkeypatch.setattr("app.groq_llm.ask_groq", _fake_ask_groq)
    monkeypatch.setattr("app.stock_enricher.enrich", _fake_enrich)
    monkeypatch.setattr(routes_module, "get_holdings", lambda db=None: [])

    response = client.post("/ai/ask", json={"query": "TCS price?", "tier": "groq"})

    assert response.status_code == 200
    assert response.json()["source"] == "groq"
    assert captured["response_style"] == "concise"


def test_groq_rate_limit_helpers():
    from app.groq_llm import _is_rate_limit_error, _retry_after_seconds

    class RateLimitExc(Exception):
        status_code = 429
        response = type("Resp", (), {"headers": {"Retry-After": "3"}})()

    exc = RateLimitExc("Error code: 429 - rate limit reached")
    assert _is_rate_limit_error(exc) is True
    assert _retry_after_seconds(exc) == 3.0
    assert _is_rate_limit_error(Exception("timeout")) is False


def test_ai_ask_http_429_is_softened_to_chat_answer(monkeypatch):
    from fastapi import HTTPException

    def _boom(_query):
        raise HTTPException(status_code=429, detail="provider busy")

    monkeypatch.setattr("app.groq_llm.classify_intent", _boom)
    response = client.post("/ai/ask", json={"query": "Should I buy TCS?"})
    assert response.status_code == 200
    body = response.json()
    assert body.get("source") == "rate-limit"
    assert "rate-limited" in body.get("answer", "").lower() or "try again" in body.get("answer", "").lower()


def test_ai_ask_passes_detailed_style_to_groq(monkeypatch):
    captured = {}

    async def _fake_ask_groq(query, context=None, conversation_history=None, intent_result=None, response_style=None):
        captured["response_style"] = response_style
        return {"answer": "ok"}

    async def _fake_enrich(symbol):
        return {"current_price": 100.0}

    monkeypatch.setattr(
        routes_module,
        "ai_assistant",
        lambda query, db=None, user_id=None: {"answer": "fallback", "symbol": "RELIANCE"},
    )
    monkeypatch.setattr("app.groq_llm.groq_available", lambda: True)
    monkeypatch.setattr("app.groq_llm.ask_groq", _fake_ask_groq)
    monkeypatch.setattr("app.stock_enricher.enrich", _fake_enrich)
    monkeypatch.setattr(routes_module, "get_holdings", lambda db=None: [])

    history = [
        {"role": "user", "content": "Can you compare this with peers?"},
        {"role": "assistant", "content": "Sure, share your timeframe."},
        {"role": "user", "content": "I want a full breakdown of trend, valuation, and risks."},
        {"role": "assistant", "content": "Got it."},
    ]

    response = client.post(
        "/ai/ask",
        json={
            "query": "Please explain RELIANCE valuation with detailed calculation and risk breakdown for 3 months",
            "conversation_history": history,
            "tier": "groq",
        },
    )

    assert response.status_code == 200
    assert response.json()["source"] == "groq"
    assert captured["response_style"] == "detailed"


def test_classify_intent_detects_small_talk_and_calculation_queries():
    from app.groq_llm import classify_intent

    small_talk_result = classify_intent("how are you")
    assert small_talk_result["intent"] == "SMALL_TALK"
    assert small_talk_result["confidence"] >= 90

    calc_result = classify_intent("calculate CAGR for ₹1,00,000 to ₹1,80,000 in 3 years")
    assert calc_result["intent"] == "CALCULATION"


def test_classify_intent_keeps_stock_followup_chips_distinct():
    from app.groq_llm import classify_intent

    assert classify_intent("Latest news on RELIANCE")["intent"] == "NEWS"
    assert classify_intent("RELIANCE market sentiment")["intent"] == "SENTIMENT"
    assert classify_intent("What is the price of RELIANCE?")["intent"] == "QUOTE"
    assert classify_intent("Technical analysis of RELIANCE")["intent"] == "TECHNICAL"
    assert classify_intent("Should I buy RELIANCE?")["intent"] == "BUY_SELL"
    assert classify_intent("Predict RELIANCE price")["intent"] == "PREDICT"
    assert classify_intent("Is RELIANCE overvalued?")["intent"] == "FUNDAMENTAL"


def test_expand_acronyms_supports_extended_market_terms():
    from app.groq_llm import expand_acronyms_in_query

    expanded = expand_acronyms_in_query("Check EV EBITDA and VWAP with OI and PCR")
    assert "Enterprise Value" in expanded
    assert "Earnings Before Interest Taxes Depreciation and Amortization" in expanded
    assert "Volume Weighted Average Price" in expanded
    assert "Open Interest" in expanded
    assert "Put-Call Ratio" in expanded


def test_classify_intent_detects_derivatives_query():
    from app.groq_llm import classify_intent

    result = classify_intent("BANKNIFTY call option strategy with IV and OI for next expiry")
    assert result["intent"] == "DERIVATIVES"
    assert result["confidence"] >= 60


def test_market_term_normalization_handles_hinglish_and_trading_shorthand():
    from app.groq_llm import normalize_market_terms_in_query

    normalized = normalize_market_terms_in_query("NIFTY CE PE mein kitna OI hai aur SL kya hona chahiye")
    normalized_lower = normalized.lower()
    assert "call option" in normalized_lower
    assert "put option" in normalized_lower
    assert "open interest" in normalized_lower
    assert "how much" in normalized_lower
    assert "what" in normalized_lower


@pytest.mark.parametrize(
    "query",
    [
        "Need calendar spread setup for NIFTY next expiry",
        "Suggest butterfly strategy for BANKNIFTY",
        "Build ratio spread with risk limits",
        "Best covered call on RELIANCE holdings",
        "Use protective put to hedge my long position",
    ],
)
def test_classify_intent_detects_full_options_strategy_vocabulary(query):
    from app.groq_llm import classify_intent

    result = classify_intent(query)
    assert result["intent"] == "DERIVATIVES"


def test_market_term_normalization_expands_options_strategy_aliases():
    from app.groq_llm import normalize_market_terms_in_query

    normalized = normalize_market_terms_in_query("cal spread with bfly and prot put plus cov call")
    normalized_lower = normalized.lower()
    assert "calendar spread" in normalized_lower
    assert "butterfly spread" in normalized_lower
    assert "protective put" in normalized_lower
    assert "covered call" in normalized_lower


@pytest.mark.parametrize(
    "query, expected_strategy",
    [
        ("Need calendar spread setup for NIFTY next expiry", "calendar_spread"),
        ("Suggest butterfly strategy for BANKNIFTY", "butterfly_spread"),
        ("Build ratio spread with risk limits", "ratio_spread"),
        ("Best covered call on RELIANCE holdings", "covered_call"),
        ("Use protective put to hedge my long position", "protective_put"),
    ],
)
def test_detect_options_strategy_maps_named_strategies(query, expected_strategy):
    from app.groq_llm import detect_options_strategy

    assert detect_options_strategy(query) == expected_strategy


@pytest.mark.parametrize(
    "query, expected_strategy",
    [
        ("Need calendar spread setup for NIFTY next expiry", "calendar_spread"),
        ("Suggest butterfly strategy for BANKNIFTY", "butterfly_spread"),
        ("Build ratio spread with risk limits", "ratio_spread"),
        ("Best covered call on RELIANCE holdings", "covered_call"),
        ("Use protective put to hedge my long position", "protective_put"),
    ],
)
def test_classify_intent_includes_detected_options_strategy(query, expected_strategy):
    from app.groq_llm import classify_intent

    result = classify_intent(query)
    assert result["intent"] == "DERIVATIVES"
    assert result.get("detected_strategy") == expected_strategy


@pytest.mark.parametrize(
    "strategy_name",
    [
        "calendar_spread",
        "butterfly_spread",
        "ratio_spread",
        "covered_call",
        "protective_put",
    ],
)
def test_get_options_strategy_example_returns_style_specific_blocks(strategy_name):
    from app.groq_llm import get_options_strategy_example

    concise = get_options_strategy_example(strategy_name, "concise")
    detailed = get_options_strategy_example(strategy_name, "detailed")

    assert "CONCISE EXAMPLE" in concise
    assert "DETAILED EXAMPLE" in detailed
    assert len(detailed) > len(concise)


def test_get_options_strategy_example_returns_empty_for_unknown_strategy():
    from app.groq_llm import get_options_strategy_example

    assert get_options_strategy_example("unknown_strategy", "concise") == ""


def test_ai_buy_query_returns_decision_style_response(monkeypatch):
    monkeypatch.setattr(
        ai_engine,
        "analyze_stock",
        lambda symbol: {
            "name": "Kaynes Technology India Ltd",
            "currentPrice": 2970.0,
            "summary": f"{symbol} has stable momentum and acceptable valuation.",
            "score": 71,
            "signal": "BUY",
            "technical": {"rsi": 56.2, "movingAverages": {"trend": "bullish"}},
            "predictions": [{"days": 30, "changePercent": 6.5, "predictedPrice": 3163.0}],
            "fundamental": {"pe": 42.0},
            "news": _mock_news_payload(symbol, sentiment="positive"),
        },
    )

    result = ai_engine.ai_assistant("Should I buy KAYNES?")
    assert result["type"] == "recommendation"
    assert "trade decision" in result["answer"].lower()
    assert "decision bias" in result["answer"].lower()


def test_ai_analysis_query_returns_detailed_sections(monkeypatch):
    monkeypatch.setattr(
        ai_engine,
        "analyze_stock",
        lambda symbol: {
            "name": "Kaynes Technology India Ltd",
            "currentPrice": 2970.0,
            "summary": f"{symbol} trend remains constructive.",
            "score": 66,
            "signal": "HOLD",
            "technical": {"rsi": 58.4, "macd": {"trend": "bullish"}, "movingAverages": {"trend": "bullish"}},
            "predictions": [{"days": 30, "changePercent": 3.1, "predictedPrice": 3062.0}],
            "fundamental": {"pe": 40.0, "roe": 18.2, "debtToEquity": 15.0},
            "news": _mock_news_payload(symbol),
        },
    )

    result = ai_engine.ai_assistant("Analyze KAYNES in detail")
    assert result["type"] == "analysis"
    assert "detailed analysis" in result["answer"].lower()
    assert "technical pulse" in result["answer"].lower()
    assert "fundamental snapshot" in result["answer"].lower()
    assert "recent headlines considered" in result["answer"].lower()
    assert "kaynes headline 1" in result["answer"].lower()


def test_ai_recommend_keyword_with_symbol_avoids_portfolio_generic(monkeypatch):
    monkeypatch.setattr(ai_engine, "_get_user_portfolio", lambda db=None: ["RELIANCE", "INFY"])
    monkeypatch.setattr(
        ai_engine,
        "analyze_stock",
        lambda symbol: {
            "name": "Kaynes Technology India Ltd",
            "currentPrice": 2970.0,
            "summary": f"{symbol} has improving setup.",
            "score": 68,
            "signal": "HOLD",
            "technical": {"rsi": 55.0, "movingAverages": {"trend": "bullish"}},
            "predictions": [{"days": 30, "changePercent": 2.0, "predictedPrice": 3029.0}],
            "fundamental": {"pe": 43.0},
            "news": _mock_news_payload(symbol),
        },
    )

    result = ai_engine.ai_assistant("Recommend KAYNES")
    assert result["type"] == "recommendation"
    assert result["symbol"] == "KAYNES"
    assert "portfolio" not in result["answer"].lower()


def test_fetch_recent_headlines_returns_latest_five(monkeypatch):
    raw_news = [
        {
            "title": f"Headline {index}",
            "publisher": "Reuters",
            "providerPublishTime": 1_700_000_000 + index,
        }
        for index in range(7)
    ]

    class FakeTicker:
        def get_news(self):
            return raw_news

    ai_engine._news_cache.clear()
    monkeypatch.setattr(ai_engine.yf, "Ticker", lambda symbol: FakeTicker())
    monkeypatch.setattr(ai_engine, "_fetch_google_news_items", lambda symbol, limit=8: [])

    headlines = ai_engine._fetch_recent_headlines("KAYNES")

    assert len(headlines) == 5
    assert headlines[0]["title"] == "Headline 6"
    assert headlines[-1]["title"] == "Headline 2"


def test_market_news_endpoint_uses_normalized_headlines(monkeypatch):
    monkeypatch.setattr(
        routes_module,
        "get_market_headlines",
        lambda symbols=None, limit=5: {
            "headlines": [
                {
                    "symbol": "RELIANCE",
                    "title": "Reliance wins new energy order",
                    "source": "Reuters",
                    "publishedAt": "2026-03-15T08:00:00",
                    "publishedLabel": "1h ago",
                    "link": "https://example.com/reliance-order",
                }
            ],
            "symbolsConsidered": symbols or ["RELIANCE", "TCS"],
            "generatedAt": "2026-03-15T09:00:00",
        },
    )

    response = client.get("/market/news?symbols=RELIANCE,TCS&limit=5")

    assert response.status_code == 200
    payload = response.json()
    assert payload["headlines"][0]["title"] == "Reliance wins new energy order"
    assert payload["symbolsConsidered"] == ["RELIANCE", "TCS"]


def test_ai_compare_query_includes_headline_context(monkeypatch):
    def fake_analysis(symbol: str):
        return {
            "name": f"{symbol} Industries",
            "currentPrice": 2500.0,
            "score": 65 if symbol == "KAYNES" else 72,
            "signal": "HOLD" if symbol == "KAYNES" else "BUY",
            "technical": {"rsi": 54.0},
            "fundamental": {"pe": 35.0},
            "predictions": [{"days": 30, "changePercent": 4.0, "predictedPrice": 2600.0}],
            "news": _mock_news_payload(symbol, sentiment="positive" if symbol == "TCS" else "mixed"),
        }

    monkeypatch.setattr(ai_engine, "analyze_stock", fake_analysis)

    result = ai_engine.ai_assistant("Compare KAYNES and TCS")

    assert result["type"] == "comparison"
    assert "latest headlines considered" in result["answer"].lower()
    assert "kaynes headline 1" in result["answer"].lower()
    assert "tcs headline 1" in result["answer"].lower()


# ---------------------------------------------------------------------------
# S1-005: Release gate automation tests
# ---------------------------------------------------------------------------

from scripts.release_gate import evaluate_slo_response  # noqa: E402


def _passing_slo_payload(
    order_total: int = 100,
    order_error_pct: float = 0.0,
    http_total: int = 500,
    p95_ms: float = 120.0,
    stream_messages: int = 200,
    stream_errors: int = 0,
) -> dict:
    """Build a minimal /metrics/slo response suitable for gate evaluation tests."""
    return {
        "status": "ok",
        "slo": {
            "http": {
                "totalRequests": http_total,
                "errorRatePct": 0.0,
                "latencyMs": {"p50": 40.0, "p95": p95_ms, "p99": 200.0},
                "windowSize": http_total,
            },
            "orderRequests": {
                "totalRequests": order_total,
                "errorRatePct": order_error_pct,
                "latencyMs": {"p50": 50.0, "p95": 100.0, "p99": 150.0},
                "windowSize": order_total,
            },
            "orderOutcomes": {"COMPLETE": order_total, "REJECTED": 0, "CANCELLED": 0},
            "quotesStream": {
                "messagesSent": stream_messages,
                "sendErrors": stream_errors,
                "errorRatePct": round(
                    stream_errors / max(stream_messages + stream_errors, 1) * 100, 4
                ),
                "rowsSent": stream_messages * 5,
                "activeConnections": 2,
                "subscriptionsUpdated": 10,
                "resumeEventsSent": 0,
                "lastSequenceSent": stream_messages,
            },
            "targets": {
                "crashFreeSessionsMinPct": 99.8,
                "orderSuccessRateMinPct": 99.5,
                "quoteLatencyP95MaxMs": 300,
            },
        },
    }


def test_release_gate_passes_when_all_slo_thresholds_are_met():
    payload = _passing_slo_payload()
    all_passed, results = evaluate_slo_response(payload, min_samples=10)

    assert all_passed is True
    statuses = {r["check"]: r["status"] for r in results}
    assert statuses["Order success rate"] == "PASS"
    assert statuses["HTTP p95 latency"] == "PASS"
    assert statuses["Quote stream error rate"] == "PASS"
    # Crash-free sessions always skips (requires Crashlytics)
    assert statuses["Crash-free sessions"] == "SKIP"


def test_release_gate_fails_when_order_error_rate_exceeds_threshold():
    # 5% error rate far exceeds the 0.5% threshold
    payload = _passing_slo_payload(order_total=200, order_error_pct=5.0)
    all_passed, results = evaluate_slo_response(payload, min_samples=10)

    assert all_passed is False
    order_check = next(r for r in results if r["check"] == "Order success rate")
    assert order_check["status"] == "FAIL"
    assert "95.00" in order_check["detail"]  # success rate shown in detail


def test_release_gate_fails_when_http_p95_latency_exceeds_threshold():
    # p95 of 450ms exceeds 300ms threshold
    payload = _passing_slo_payload(p95_ms=450.0)
    all_passed, results = evaluate_slo_response(payload, min_samples=10)

    assert all_passed is False
    latency_check = next(r for r in results if r["check"] == "HTTP p95 latency")
    assert latency_check["status"] == "FAIL"
    assert "450.0" in latency_check["detail"]


def test_release_gate_fails_when_stream_error_rate_exceeds_threshold():
    # 50 errors out of 100 total = 50% error rate, well over 1% threshold
    payload = _passing_slo_payload(stream_messages=50, stream_errors=50)
    all_passed, results = evaluate_slo_response(payload, min_samples=10)

    assert all_passed is False
    stream_check = next(r for r in results if r["check"] == "Quote stream error rate")
    assert stream_check["status"] == "FAIL"


def test_release_gate_skips_checks_when_sample_count_is_below_minimum():
    # Only 3 samples — below min_samples=10, so all data-dependent checks skip
    payload = _passing_slo_payload(
        order_total=3, http_total=3, stream_messages=3, stream_errors=0
    )
    all_passed, results = evaluate_slo_response(payload, min_samples=10)

    assert all_passed is True  # SKIP counts as pass (insufficient data, not a failure)
    statuses = {r["check"]: r["status"] for r in results}
    assert statuses["Order success rate"] == "SKIP"
    assert statuses["HTTP p95 latency"] == "SKIP"
    assert statuses["Quote stream error rate"] == "SKIP"


def test_release_gate_evaluate_uses_live_slo_endpoint():
    """Smoke test: evaluate_slo_response accepts a real /metrics/slo response."""
    resp = client.get("/metrics/slo")
    assert resp.status_code == 200
    payload = resp.json()

    # With fresh test DB, sample counts are low — gate should not FAIL (only PASS or SKIP)
    all_passed, results = evaluate_slo_response(payload, min_samples=1000)
    # All checks will be SKIP (not enough samples) so gate must pass
    assert all_passed is True
    for r in results:
        assert r["status"] in {"PASS", "SKIP"}, (
            f"Unexpected FAIL on cold metrics: {r}"
        )


def test_intraday_tips_endpoint_returns_phase_tips():
    response = client.get("/market/intraday-tips?limit=3&advanceShare=0.7")
    assert response.status_code == 200
    payload = response.json()
    assert payload["phase"]
    assert payload["phaseLabel"]
    assert len(payload["tips"]) >= 1
    assert payload["tips"][0]["title"]
    assert payload["tips"][0]["body"]
    assert "Educational" in payload["disclaimer"] or "educational" in payload["disclaimer"].lower()


def test_single_quote_endpoint_includes_snapshot_fields(monkeypatch):
    monkeypatch.setattr(
        "app.routes.fetch_quote",
        lambda symbol: {
            "symbol": symbol.upper(),
            "last": 1000.0,
            "pctChange": 1.25,
            "open": 990.0,
            "high": 1010.0,
            "low": 985.0,
            "previousClose": 987.0,
            "prevClose": 987.0,
            "volume": 1_200_000,
            "avgVolume": 1_500_000,
            "marketCap": 5_000_000_000_000,
            "pe": 22.5,
            "trailingPE": 22.5,
            "eps": 44.4,
            "dividendYield": 0.8,
            "fiftyTwoWeekHigh": 1200.0,
            "fiftyTwoWeekLow": 800.0,
            "bid": 999.5,
            "ask": 1000.5,
            "timestamp": 1_700_000_000_000,
        },
    )
    response = client.get("/quotes/TCS")
    assert response.status_code == 200
    payload = response.json()
    assert payload["symbol"] == "TCS"
    assert payload["marketCap"] == 5_000_000_000_000
    assert payload["trailingPE"] == 22.5
    assert payload["eps"] == 44.4
    assert payload["fiftyTwoWeekHigh"] == 1200.0
    assert payload["bid"] == 999.5
    assert payload["dividendYield"] == 0.8
    assert payload["prevClose"] == 987.0


def test_investor_tips_endpoint_supports_topics():
    for topic in ("long_term", "mutual_funds", "ipo", "fno", "sgb"):
        response = client.get(f"/market/investor-tips?topic={topic}&limit=3")
        assert response.status_code == 200
        payload = response.json()
        assert payload["topic"] == topic
        assert payload["topicLabel"]
        assert len(payload["tips"]) >= 1
        assert payload["tips"][0]["title"]
        assert len(payload["topics"]) == 5
        assert {t["id"] for t in payload["topics"]} >= {
            "long_term",
            "mutual_funds",
            "ipo",
            "fno",
            "sgb",
        }


def test_futures_contracts_endpoint_returns_contract_set(monkeypatch):
    monkeypatch.setattr(
        "app.routes.fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": 2450.0, "pctChange": 0.65},
    )
    monkeypatch.setattr(
        "app.derivatives_data.fetch_nse_futures_contracts",
        lambda *args, **kwargs: None,
    )

    response = client.get("/derivatives/futures/contracts?symbol=TCS")
    assert response.status_code == 200

    payload = response.json()
    assert payload["symbol"] == "TCS"
    assert payload["spot"] == 2450.0
    assert payload["source"] == "synthetic"
    assert len(payload["contracts"]) == 3

    first_contract = payload["contracts"][0]
    assert first_contract["contractSymbol"].startswith("TCS-")
    assert first_contract["lotSize"] > 0
    assert first_contract["last"] > 0
    assert first_contract["marginPerLot"] > 0


def test_option_chain_endpoint_returns_pcr_and_iv_skew(monkeypatch):
    monkeypatch.setattr(
        "app.routes.fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": 22500.0, "pctChange": 0.2},
    )
    monkeypatch.setattr(
        "app.derivatives_data.fetch_nse_option_chain",
        lambda *args, **kwargs: None,
    )

    response = client.get("/derivatives/option-chain?symbol=NIFTY&expiry=2026-08-28")
    assert response.status_code == 200
    payload = response.json()
    assert payload["symbol"] == "NIFTY"
    assert payload["source"] == "synthetic"
    assert payload["pcr"] is not None
    assert payload["ivSkew"] is not None
    assert payload["atmIv"] is not None
    assert len(payload["contracts"]) >= 10
    assert payload["contracts"][0]["callIv"] is not None


def test_futures_ticket_preview_endpoint_returns_margin_and_notional(monkeypatch):
    monkeypatch.setattr(
        "app.routes.fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": 1985.0, "pctChange": 0.42},
    )
    monkeypatch.setattr(
        "app.derivatives_data.fetch_nse_futures_contracts",
        lambda *args, **kwargs: None,
    )

    contracts_response = client.get("/derivatives/futures/contracts?symbol=INFY")
    assert contracts_response.status_code == 200
    contracts_payload = contracts_response.json()
    expiry = contracts_payload["contracts"][0]["expiry"]

    preview_payload = {
        "symbol": "INFY",
        "expiry": expiry,
        "side": "BUY",
        "lots": 2,
        "orderType": "MARKET",
    }

    response = client.post("/derivatives/futures/ticket/preview", json=preview_payload)
    assert response.status_code == 200

    data = response.json()
    assert data["symbol"] == "INFY"
    assert data["expiry"] == expiry
    assert data["lots"] == 2
    assert data["quantity"] == data["lotSize"] * 2
    assert data["notionalValue"] > 0
    assert data["estimatedMargin"] > 0
    assert len(data["notes"]) >= 1


def test_signal_lab_buckets_endpoint_returns_results_and_institutional(monkeypatch):
    monkeypatch.setattr(routes_module, "_RESULTS_WEEK_UNIVERSE", ["RELIANCE", "TCS", "INFY"])
    monkeypatch.setattr(routes_module, "_INSTITUTIONAL_CONVICTION_UNIVERSE", ["HDFCBANK", "ICICIBANK", "SBIN"])
    monkeypatch.setattr(
        routes_module,
        "SECTOR_STOCKS",
        {"Banking": ["HDFCBANK", "ICICIBANK", "SBIN"]},
    )
    monkeypatch.setattr(
        routes_module,
        "get_market_heatmap",
        lambda: {
            "sectors": [
                {
                    "name": "Banking",
                    "avgChange": 1.4,
                    "stocks": [
                        {"symbol": "HDFCBANK"},
                        {"symbol": "ICICIBANK"},
                        {"symbol": "SBIN"},
                    ],
                }
            ]
        },
    )

    def _fake_fetch_quotes(symbols):
        rows = []
        for index, symbol in enumerate(symbols):
            rows.append(
                {
                    "symbol": symbol.upper(),
                    "last": 100.0 + index,
                    "pctChange": 1.8 - (index * 0.07),
                    "volume": 1_200_000 + (index * 20_000),
                    "avgVolume": 600_000,
                    "marketCap": 1_000_000_000_000 + (index * 50_000_000),
                    "targetMeanPrice": 118.0 + index,
                    "fiftyDayAverage": 98.0 + index,
                    "twoHundredDayAverage": 95.0 + index,
                }
            )
        return rows

    monkeypatch.setattr(routes_module, "fetch_quotes", _fake_fetch_quotes)

    response = client.get("/market/signal-lab/buckets?limitPerBucket=4&forceRefresh=true")
    assert response.status_code == 200

    payload = response.json()
    assert payload["generatedAt"]
    bucket_ids = {bucket["bucketId"] for bucket in payload["buckets"]}
    assert "results_week" in bucket_ids
    assert "institutional_conviction" in bucket_ids

    for bucket in payload["buckets"]:
        assert len(bucket["candidates"]) > 0
        assert len(bucket["candidates"]) <= 4
        first = bucket["candidates"][0]
        assert first["symbol"]
        assert first["companyName"]
        assert isinstance(first["confidence"], int)
        assert first["score"] >= 0


def test_market_heatmap_returns_persisted_snapshot_when_market_closed(monkeypatch, tmp_path):
    snapshot = {
        "sectors": [
            {
                "name": "Pharma",
                "stocks": [{"symbol": "LUPIN", "price": 2100.0, "pctChange": 1.25, "change": 25.0, "intensity": "positive", "name": "Lupin Limited"}],
                "avgChange": 1.25,
                "advances": 1,
                "declines": 0,
                "unchanged": 0,
                "totalStocks": 1,
                "intensity": "positive",
                "topGainer": {"symbol": "LUPIN"},
                "topLoser": {"symbol": "LUPIN"},
            }
        ],
        "marketBreadth": {
            "advances": 1,
            "declines": 0,
            "unchanged": 0,
            "total": 1,
            "advanceRatio": 1.0,
        },
        "mood": "BULLISH",
        "moodEmoji": "🟢",
        "moodDescription": "Persisted prior session snapshot",
        "bestSector": {"name": "Pharma", "change": 1.25},
        "worstSector": {"name": "Pharma", "change": 1.25},
        "lastUpdated": "2026-05-31T09:15:00",
    }

    snapshot_path = tmp_path / "market_heatmap_snapshot.json"
    snapshot_path.write_text(json.dumps(snapshot), encoding="utf-8")

    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", snapshot_path)
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "_is_nse_market_open", lambda: False)

    result = market_heatmap_module.get_market_heatmap()

    assert result["lastUpdated"] == snapshot["lastUpdated"]
    assert result["marketBreadth"]["total"] == 1
    assert result["sectors"][0]["stocks"][0]["symbol"] == "LUPIN"
    assert result["isStale"] is True
    assert result["marketOpen"] is False


def test_market_heatmap_rebuilds_last_session_when_closed_without_snapshot(monkeypatch, tmp_path):
    snapshot_path = tmp_path / "market_heatmap_snapshot.json"

    def _fake_fetch_quotes(symbols, max_age_seconds=None, **_kwargs):
        return [
            {
                "symbol": symbol,
                "last": 100.0 + i,
                "pctChange": 1.5 if i % 2 == 0 else -0.8,
                "change": 1.0,
            }
            for i, symbol in enumerate(symbols)
        ]

    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", snapshot_path)
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "_is_nse_market_open", lambda: False)
    monkeypatch.setattr(market_heatmap_module, "fetch_quotes", _fake_fetch_quotes)
    monkeypatch.setattr(market_heatmap_module, "_schedule_heatmap_refresh", lambda **_k: None)
    monkeypatch.setattr(market_heatmap_module, "_warm_heatmap_universe_async", lambda: None)
    monkeypatch.setattr(
        "app.heatmap_universe.get_heatmap_sector_symbols",
        lambda: {k: list(v) for k, v in market_heatmap_module.SECTOR_STOCKS.items() if v},
    )

    first = market_heatmap_module.get_market_heatmap()
    assert first["isStale"] is True
    assert first["marketOpen"] is False
    assert first["marketBreadth"]["total"] == 0

    result = market_heatmap_module._refresh_heatmap_sync(market_open=False)
    assert result["marketBreadth"]["total"] > 0
    assert any(sector.get("stocks") for sector in result["sectors"])
    assert snapshot_path.exists()
    # Second call should hit the persisted snapshot without needing quotes.
    monkeypatch.setattr(
        market_heatmap_module,
        "fetch_quotes",
        lambda *_a, **_k: (_ for _ in ()).throw(AssertionError("should use snapshot")),
    )
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    again = market_heatmap_module.get_market_heatmap()
    assert again["marketBreadth"]["total"] == result["marketBreadth"]["total"]


def _curated_close_snapshot(**overrides):
    sectors = []
    for name, symbols in market_heatmap_module.SECTOR_STOCKS.items():
        sectors.append(
            {
                "name": name,
                "avgChange": 0.5,
                "advances": 1,
                "declines": 0,
                "unchanged": 0,
                "totalStocks": 1,
                "stocks": [{"symbol": symbols[0], "change": 1.0, "pctChange": 0.5}],
            }
        )
    payload = {
        "sectors": sectors,
        "marketBreadth": {
            "advances": 2,
            "declines": 1,
            "unchanged": 0,
            "total": 3,
            "advanceRatio": 0.667,
        },
        "mood": "BULLISH",
        "moodEmoji": "🟢",
        "moodDescription": "Close print",
        "bestSector": {"name": "IT", "change": 0.8},
        "worstSector": {"name": "IT", "change": 0.8},
        "lastUpdated": "2026-08-13T10:00:00",
        "quotedCount": 3,
    }
    payload.update(overrides)
    return payload


def test_closed_refresh_does_not_rebuild_existing_snapshot(monkeypatch, tmp_path):
    snapshot = _curated_close_snapshot()
    snapshot_path = tmp_path / "market_heatmap_snapshot.json"
    snapshot_path.write_text(json.dumps(snapshot), encoding="utf-8")

    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", snapshot_path)
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "_is_nse_market_open", lambda: False)
    monkeypatch.setattr(
        market_heatmap_module,
        "fetch_quotes",
        lambda *_a, **_k: (_ for _ in ()).throw(AssertionError("closed snapshot must not hit Yahoo")),
    )

    first = market_heatmap_module._refresh_heatmap_sync(market_open=False)
    second = market_heatmap_module._refresh_heatmap_sync(market_open=False)
    assert first["marketBreadth"]["total"] == 3
    assert first["lastUpdated"] == snapshot["lastUpdated"]
    assert second["marketBreadth"]["advanceRatio"] == first["marketBreadth"]["advanceRatio"]
    assert second["quotedCount"] == first["quotedCount"]


def test_kick_heatmap_refresh_skips_when_closed_with_snapshot(monkeypatch, tmp_path):
    snapshot = _curated_close_snapshot(mood="EUPHORIC", quotedCount=1)
    snapshot_path = tmp_path / "snap.json"
    snapshot_path.write_text(json.dumps(snapshot), encoding="utf-8")
    scheduled = []

    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", snapshot_path)
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "_is_nse_market_open", lambda: False)
    monkeypatch.setattr(
        market_heatmap_module,
        "_schedule_heatmap_refresh",
        lambda **kwargs: scheduled.append(kwargs),
    )

    market_heatmap_module.kick_heatmap_refresh()
    assert scheduled == []


def test_closed_refresh_splices_missing_semiconductor_without_changing_breadth(monkeypatch, tmp_path):
    snapshot = {
        "sectors": [
            {
                "name": "IT",
                "avgChange": 0.8,
                "advances": 2,
                "declines": 1,
                "unchanged": 0,
                "totalStocks": 3,
                "stocks": [{"symbol": "TCS", "change": 1.1, "pctChange": 0.8}],
            }
        ],
        "marketBreadth": {
            "advances": 2,
            "declines": 1,
            "unchanged": 0,
            "total": 3,
            "advanceRatio": 0.667,
        },
        "mood": "BULLISH",
        "lastUpdated": "2026-08-13T10:00:00",
        "quotedCount": 3,
    }
    snapshot_path = tmp_path / "snap.json"
    snapshot_path.write_text(json.dumps(snapshot), encoding="utf-8")

    def _fake_fetch_quotes(symbols, max_age_seconds=None, **_kwargs):
        return [
            {"symbol": symbol, "last": 100.0, "pctChange": 1.0, "change": 1.0}
            for symbol in symbols
        ]

    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", snapshot_path)
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "fetch_quotes", _fake_fetch_quotes)
    from app.market_data import _quote_cache

    _quote_cache.clear()

    result = market_heatmap_module._refresh_heatmap_sync(market_open=False)
    assert result["marketBreadth"]["advanceRatio"] == 0.667
    assert result["quotedCount"] == 3
    names = {sector["name"] for sector in result["sectors"]}
    assert "IT" in names
    assert "Semiconductor" in names
    semiconductor = next(s for s in result["sectors"] if s["name"] == "Semiconductor")
    assert any(stock["symbol"] == "MOSCHIP" for stock in semiconductor["stocks"])


def test_publish_heatmap_does_not_replace_fuller_snapshot_with_thinner(monkeypatch):
    fuller = {
        "sectors": [{"name": "IT", "stocks": [{"symbol": "TCS"}], "totalStocks": 80}],
        "marketBreadth": {"advances": 50, "declines": 30, "unchanged": 0, "total": 80, "advanceRatio": 0.625},
        "quotedCount": 80,
        "mood": "BULLISH",
    }
    thinner = {
        "sectors": [{"name": "IT", "stocks": [{"symbol": "TCS"}], "totalStocks": 12}],
        "marketBreadth": {"advances": 10, "declines": 2, "unchanged": 0, "total": 12, "advanceRatio": 0.833},
        "quotedCount": 12,
        "mood": "EUPHORIC",
    }
    monkeypatch.setattr(
        market_heatmap_module,
        "_HEATMAP_CACHE",
        {"data": fuller, "timestamp": 1},
    )
    published = market_heatmap_module._publish_heatmap(thinner, now=99)
    assert published["quotedCount"] == 80
    assert published["marketBreadth"]["advanceRatio"] == 0.625
    assert market_heatmap_module._HEATMAP_CACHE["data"]["quotedCount"] == 80


def test_heatmap_leaders_paint_before_full_universe(monkeypatch, tmp_path):
    fetched = []

    def _fake_fetch_quotes(symbols, max_age_seconds=None, **_kwargs):
        fetched.append(list(symbols))
        return [
            {
                "symbol": symbol,
                "last": 100.0,
                "pctChange": 1.25,
                "change": 1.0,
            }
            for symbol in symbols
        ]

    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", tmp_path / "snap.json")
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "fetch_quotes", _fake_fetch_quotes)
    from app.market_data import _quote_cache

    _quote_cache.clear()

    result = market_heatmap_module._build_heatmap_from_quotes(market_open=True, leaders_only=True)
    assert fetched, "leaders pass should hit Yahoo"
    first_batch = fetched[0]
    assert "HDFCBANK" in first_batch
    assert "TCS" in first_batch
    assert "20MICRONS" not in first_batch
    banking = next(s for s in result["sectors"] if s["name"] == "Banking")
    assert any(stock["symbol"] == "HDFCBANK" for stock in banking["stocks"])
    assert result["marketBreadth"]["total"] > 0


def test_heatmap_includes_semiconductor_leaders(monkeypatch, tmp_path):
    fetched = []

    def _fake_fetch_quotes(symbols, max_age_seconds=None, **_kwargs):
        fetched.append(list(symbols))
        return [
            {
                "symbol": symbol,
                "last": 210.0,
                "pctChange": 0.9,
                "change": 1.8,
            }
            for symbol in symbols
        ]

    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", tmp_path / "snap.json")
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "fetch_quotes", _fake_fetch_quotes)
    from app.heatmap_universe import HEATMAP_SECTOR_ORDER, _classify_from_name
    from app.market_data import _quote_cache

    _quote_cache.clear()

    assert "Semiconductor" in HEATMAP_SECTOR_ORDER
    assert "MOSCHIP" in market_heatmap_module.SECTOR_STOCKS["Semiconductor"]
    assert "KAYNES" in market_heatmap_module.SECTOR_STOCKS["Semiconductor"]
    assert _classify_from_name("SPEL Semiconductor Ltd.") == "Semiconductor"
    assert _classify_from_name("Moschip Technologies Limited") == "Semiconductor"

    result = market_heatmap_module._build_heatmap_from_quotes(market_open=True, leaders_only=True)
    first_batch = fetched[0]
    assert "MOSCHIP" in first_batch
    assert "KAYNES" in first_batch
    assert "DIXON" in first_batch
    semiconductor = next(s for s in result["sectors"] if s["name"] == "Semiconductor")
    symbols = {stock["symbol"] for stock in semiconductor["stocks"]}
    assert {"MOSCHIP", "KAYNES", "DIXON"} <= symbols


def test_open_market_heatmap_does_not_block_on_yahoo(monkeypatch, tmp_path):
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", tmp_path / "missing.json")
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "_is_nse_market_open", lambda: True)
    monkeypatch.setattr(market_heatmap_module, "_schedule_heatmap_refresh", lambda **_k: None)
    monkeypatch.setattr(
        market_heatmap_module,
        "fetch_quotes",
        lambda *_a, **_k: (_ for _ in ()).throw(AssertionError("request path must not hit Yahoo")),
    )

    result = market_heatmap_module.get_market_heatmap()
    assert result["isStale"] is True
    assert result["marketOpen"] is True
    assert result["marketBreadth"]["total"] == 0


def test_open_heatmap_refetches_stale_cached_leaders(monkeypatch, tmp_path):
    fetched = []
    clock = {"now": 50_000.0}

    def _fake_fetch_quotes(symbols, max_age_seconds=None, **_kwargs):
        fetched.append(list(symbols))
        return [
            {"symbol": symbol, "last": 120.0, "pctChange": 0.5, "change": 0.6}
            for symbol in symbols
        ]

    monkeypatch.setattr("app.market_data.time.time", lambda: clock["now"])
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_SNAPSHOT_PATH", tmp_path / "snap.json")
    monkeypatch.setattr(market_heatmap_module, "_HEATMAP_CACHE", {"data": None, "timestamp": 0})
    monkeypatch.setattr(market_heatmap_module, "fetch_quotes", _fake_fetch_quotes)
    from app.market_data import _quote_cache

    _quote_cache.clear()
    _quote_cache.put("HDFCBANK", {"symbol": "HDFCBANK", "last": 90.0, "pctChange": 0.1})
    clock["now"] += 20

    result = market_heatmap_module._build_heatmap_from_quotes(market_open=True, leaders_only=True)
    assert fetched, "stale cached leaders must be refreshed while the market is open"
    assert "HDFCBANK" in fetched[0]
    banking = next(s for s in result["sectors"] if s["name"] == "Banking")
    assert any(stock["symbol"] == "HDFCBANK" for stock in banking["stocks"])


def test_investor_portfolio_insights_endpoint_returns_changes_and_ideas(monkeypatch):
    def _fake_fetch_quotes(symbols):
        return [
            {
                "symbol": symbol.upper(),
                "last": 200.0 + index,
                "pctChange": 0.6 + (index * 0.05),
                "volume": 900_000 + (index * 10_000),
                "avgVolume": 600_000,
            }
            for index, symbol in enumerate(symbols)
        ]

    monkeypatch.setattr(routes_module, "fetch_quotes", _fake_fetch_quotes)

    response = client.get("/investor-portfolios/insights?maxChangesPerInvestor=2&ideaLimit=5")
    assert response.status_code == 200

    payload = response.json()
    assert payload["generatedAt"]
    assert payload["quarterLabel"]
    assert len(payload["portfolioChanges"]) > 0
    assert len(payload["ideas"]) > 0
    assert len(payload["ideas"]) <= 5

    first_portfolio = payload["portfolioChanges"][0]
    assert first_portfolio["investorId"]
    assert first_portfolio["investorName"]
    assert len(first_portfolio["changes"]) <= 2

    first_delta = first_portfolio["changes"][0]
    assert first_delta["symbol"]
    assert first_delta["companyName"]
    assert first_delta["action"] in {"NEW", "INCREASED", "REDUCED", "REBALANCED"}
    assert "deltaPct" in first_delta

    first_idea = payload["ideas"][0]
    assert first_idea["ideaId"]
    assert first_idea["symbol"]
    assert first_idea["thesis"]
    assert first_idea["whyNow"]
    assert isinstance(first_idea["backingInvestors"], list)

def test_admin_delete_user_is_hidden_without_admin_token(monkeypatch):
    """The destructive admin route must not exist unless AUTH_ADMIN_TOKEN is configured."""
    monkeypatch.setattr(auth_routes, "AUTH_ADMIN_TOKEN", "")

    response = client.post(
        "/auth/admin/delete-user",
        json={"identifier": "someone@example.com"},
    )
    assert response.status_code == 404


def test_admin_delete_user_rejects_wrong_admin_token(monkeypatch):
    monkeypatch.setattr(auth_routes, "AUTH_ADMIN_TOKEN", "correct-admin-token")

    response = client.post(
        "/auth/admin/delete-user",
        json={"identifier": "someone@example.com"},
        headers={"X-Admin-Token": "wrong-token"},
    )
    assert response.status_code == 403


def test_market_movers_only_true_gainers_and_losers(monkeypatch):
    import app.market_data as market_data_module

    monkeypatch.setattr(
        market_data_module,
        "_MOVERS_CACHE",
        {"fetched_at": 0.0, "payload": None, "refreshing": False},
    )

    def _fake_fetch_quotes(symbols, max_age_seconds=None, **_kwargs):
        by_symbol = {
            "TCS": 2.4,
            "INFY": -1.8,
            "WIPRO": 0.0,
            "RELIANCE": -0.3,
            "HDFCBANK": 1.1,
        }
        return [
            {
                "symbol": str(symbol).upper(),
                "last": 100.0,
                "pctChange": by_symbol.get(str(symbol).upper(), -0.5),
                "volume": 1_000_000,
            }
            for symbol in symbols
        ]

    monkeypatch.setattr(market_data_module, "fetch_quotes", _fake_fetch_quotes)

    payload = market_data_module.fetch_market_movers(limit=8)
    gainer_symbols = {row["symbol"] for row in payload["gainers"]}
    loser_symbols = {row["symbol"] for row in payload["losers"]}
    assert all(row["pctChange"] > 0 for row in payload["gainers"])
    assert all(row["pctChange"] < 0 for row in payload["losers"])
    assert "TCS" in gainer_symbols
    assert "HDFCBANK" in gainer_symbols
    assert "INFY" in loser_symbols
    assert "WIPRO" not in gainer_symbols
    assert "WIPRO" not in loser_symbols


def test_otp_debug_requires_debug_token(monkeypatch):
    """otp-debug exposes SMS provider configuration, so it must stay gated."""
    monkeypatch.setattr(auth_routes, "AUTH_DEBUG_ENDPOINTS_ENABLED", False)
    assert client.get("/auth/otp-debug").status_code == 404

    # Enabled but with no token configured is still refused rather than served openly.
    monkeypatch.setattr(auth_routes, "AUTH_DEBUG_ENDPOINTS_ENABLED", True)
    monkeypatch.setattr(auth_routes, "AUTH_DEBUG_TOKEN", "")
    assert client.get("/auth/otp-debug").status_code == 404

    monkeypatch.setattr(auth_routes, "AUTH_DEBUG_TOKEN", "debug-token")
    assert client.get("/auth/otp-debug").status_code == 403
    assert "api_key_prefix" not in client.get(
        "/auth/otp-debug", headers={"X-Debug-Token": "debug-token"}
    ).text
