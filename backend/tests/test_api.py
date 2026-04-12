import pytest
from fastapi.testclient import TestClient
from sqlalchemy.exc import IntegrityError
import sys
from pathlib import Path
import time

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app import app
from app import ai_engine
import app.routes as routes_module
import app.routes.trading as trading_module
import app.routes.streaming as streaming_module
from app.database.db import SessionLocal, WalletModel, OrderModel
from app.models.schemas import MarketStatus
from app.routes import auth as auth_routes

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
    monkeypatch.setattr("app.routes.auth._send_otp_sms", lambda *a, **kw: True)

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

    # Read the actual OTP from the database
    from app.database.db import SessionLocal, OTPModel
    db = SessionLocal()
    try:
        otp_record = db.query(OTPModel).filter(
            OTPModel.mobile_number == "+919998887777"
        ).order_by(OTPModel.created_at.desc()).first()
        assert otp_record is not None
        otp_code = otp_record.otp_code
    finally:
        db.close()

    # Verify OTP with the real code
    verify_response = client.post(
        "/auth/verify-otp",
        json={"mobile_number": "9998887777", "otp": otp_code},
    )
    assert verify_response.status_code == 200
    verify_payload = verify_response.json()
    assert verify_payload["status"] == "ok"
    assert "access_token" in verify_payload
    assert "refresh_token" in verify_payload


def test_slo_metrics_endpoint_returns_latency_error_and_success_rates(monkeypatch):
    _seed_trading_wallet(user_id=1, balance=100_000.0)
    _mock_live_market(monkeypatch, price=100.0)

    order_response = client.post(
        "/order",
        json={"symbol": "SLOTEST", "qty": 1, "side": "BUY"},
        headers={"X-Trace-Id": "trc-slo-order-001"},
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


def test_place_order_is_idempotent(monkeypatch):
    _seed_trading_wallet()
    _mock_live_market(monkeypatch, price=250.0)

    order_data = {"symbol": "INFY", "qty": 2, "side": "BUY"}
    idem_key = f"test-order-key-001-{time.time_ns()}"
    headers = {"X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-001"}

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
    _seed_trading_wallet()
    _mock_live_market(monkeypatch, price=250.0)

    idem_key = f"test-order-key-002-{time.time_ns()}"
    headers = {"X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-002"}
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
    _seed_trading_wallet()
    _mock_live_market(monkeypatch, price=250.0)

    idem_key = f"test-order-race-key-{time.time_ns()}"
    headers = {"X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-race-001"}
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
    _seed_trading_wallet()
    _mock_live_market(monkeypatch, price=250.0)

    def _force_invalid_transition(order_db, next_status):
        raise trading_module.LifecycleTransitionError(
            entity="order",
            current_status="COMPLETED",
            next_status=next_status,
            error_code=trading_module.ORDER_TRANSITION_ERROR_CODE,
        )

    monkeypatch.setattr("app.routes.trading._transition_order_status", _force_invalid_transition)

    response = client.post("/order", json={"symbol": "INFY", "qty": 1, "side": "BUY"})
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "error"
    assert payload["errorCode"] == "INVALID_ORDER_TRANSITION"
    assert "Invalid order transition" in payload["message"]


def test_advanced_order_retries_return_same_order_id(monkeypatch):
    _seed_trading_wallet()
    _mock_live_market(monkeypatch, price=300.0)

    payload = {
        "symbol": "ADVRETRY",
        "qty": 1,
        "side": "BUY",
        "orderType": "MARKET",
        "validity": "DAY",
    }
    idem_key = f"test-advanced-key-{time.time_ns()}"
    headers = {"X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-advanced-001"}

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


def test_send_otp():
    """Test sending OTP to mobile number"""
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

    # Read the actual OTP from the database
    db = SessionLocal()
    try:
        otp_record = db.query(OTPModel).filter(
            OTPModel.mobile_number == "+919222333444"
        ).order_by(OTPModel.created_at.desc()).first()

        assert otp_record is not None
        otp_code = otp_record.otp_code

        # Verify OTP
        verify_response = client.post("/auth/verify-otp", json={
            "mobile_number": "9222333444",
            "otp": otp_code
        })
        assert verify_response.status_code == 200

        data = verify_response.json()
        assert data["status"] == "ok"
        assert "user_id" in data
        assert "access_token" in data
        assert "refresh_token" in data

    finally:
        db.close()


def test_basket_execution_with_idempotency_key_is_retry_safe(monkeypatch):
    _seed_trading_wallet()
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
    created = client.post("/orders/baskets", json=create_payload)
    assert created.status_code == 200
    basket_id = created.json()["basketId"]

    idem_key = f"test-basket-key-{time.time_ns()}"
    headers = {"X-Idempotency-Key": idem_key, "X-Trace-Id": "trace-test-basket-001"}

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
    _seed_trading_wallet(user_id=1, balance=100_000.0)
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

    response = client.post("/orders/pre-trade-estimate", json=payload)
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
    _seed_trading_wallet(user_id=1, balance=100.0)
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

    response = client.post("/orders/pre-trade-estimate", json=payload)
    assert response.status_code == 200

    data = response.json()
    assert data["canAfford"] is False
    assert any("Insufficient funds" in warning for warning in data["warnings"])
    assert any("Insufficient wallet" in flag for flag in data["signal"]["flags"])


def test_order_trace_lookup_returns_latest_order(monkeypatch):
    _seed_trading_wallet(user_id=1, balance=100_000.0)
    _mock_live_market(monkeypatch, price=250.0)

    trace_id = "trc-support-lookup-001"
    order_payload = {
        "symbol": "TCS",
        "qty": 1,
        "side": "BUY",
    }
    placed = client.post("/order", json=order_payload, headers={"X-Trace-Id": trace_id})
    assert placed.status_code == 200
    placed_data = placed.json()
    assert placed_data["status"] == "ok"

    lookup = client.get(f"/orders/trace/{trace_id}")
    assert lookup.status_code == 200
    data = lookup.json()
    assert data["traceId"] == trace_id
    assert data["orderId"] == placed_data["orderId"]
    assert data["symbol"] == "TCS"
    assert data["side"] == "BUY"
    assert data["status"] in {"COMPLETED", "PENDING", "TRIGGER_EXECUTED", "REJECTED", "CANCELLED"}


def test_order_trace_lookup_not_found():
    response = client.get("/orders/trace/trc-support-missing-001")
    assert response.status_code == 404


def test_place_order_invalid_side_has_deterministic_error_code():
    order_data = {"symbol": "TCS", "qty": 1, "side": "HOLD"}

    response = client.post("/order", json=order_data)
    assert response.status_code == 200

    payload = response.json()
    assert payload["status"] == "error"
    assert payload["errorCode"] == "INVALID_SIDE"


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

    reuse_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token},
        headers=headers,
    )
    assert reuse_response.status_code == 401
    assert "rotated" in reuse_response.json()["detail"].lower()

    second_refresh_attempt = client.post(
        "/auth/refresh",
        json={"refreshToken": second_refresh_token},
        headers=headers,
    )
    assert second_refresh_attempt.status_code == 200
    assert second_refresh_attempt.json()["status"] == "ok"


def test_refresh_token_reuse_different_client_invalidates_active_sessions():
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
    second_refresh_token = rotate_response.json()["refresh_token"]

    replay_response = client.post(
        "/auth/refresh",
        json={"refreshToken": first_refresh_token},
        headers=replay_headers,
    )
    assert replay_response.status_code == 401
    assert "reuse" in replay_response.json()["detail"].lower()

    second_refresh_attempt = client.post(
        "/auth/refresh",
        json={"refreshToken": second_refresh_token},
        headers=primary_headers,
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

    response = client.post(
        "/auth/password-reset/request",
        json={"identifier": "missing_user@example.com"}
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "ok"
    assert payload["delivery"] == "support"
    assert "support" in payload["message"].lower()


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


def test_ai_ask_endpoint_passes_db_context(monkeypatch):
    def fake_ai_assistant(query: str, db=None):
        return {
            "type": "test",
            "db_present": db is not None,
            "query": query,
        }

    monkeypatch.setattr(routes_module, "ai_assistant", fake_ai_assistant)

    response = client.post("/ai/ask", json={"query": "Is KAYNES overvalued?"})
    assert response.status_code == 200
    payload = response.json()
    assert payload["type"] == "test"
    assert payload["db_present"] is True


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


def test_futures_contracts_endpoint_returns_contract_set(monkeypatch):
    monkeypatch.setattr(
        "app.routes.fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": 2450.0, "pctChange": 0.65},
    )

    response = client.get("/derivatives/futures/contracts?symbol=TCS")
    assert response.status_code == 200

    payload = response.json()
    assert payload["symbol"] == "TCS"
    assert payload["spot"] == 2450.0
    assert len(payload["contracts"]) == 3

    first_contract = payload["contracts"][0]
    assert first_contract["contractSymbol"].startswith("TCS-")
    assert first_contract["lotSize"] > 0
    assert first_contract["last"] > 0
    assert first_contract["marginPerLot"] > 0


def test_futures_ticket_preview_endpoint_returns_margin_and_notional(monkeypatch):
    monkeypatch.setattr(
        "app.routes.fetch_quote",
        lambda symbol: {"symbol": symbol.upper(), "last": 1985.0, "pctChange": 0.42},
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
