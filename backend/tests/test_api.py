import pytest
from fastapi.testclient import TestClient
import sys
from pathlib import Path
import time

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app import app
from app import ai_engine
import app.routes as routes_module
from app.database.db import SessionLocal, WalletModel
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
        quote_symbols = {row["symbol"] for row in quotes_message["quotes"]}
        assert quote_symbols == {"INFY", "SBIN"}


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
