"""Hot-path timeouts: news must not run 28s; wallet must not wait on Yahoo."""

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from fastapi.testclient import TestClient

from app import app
from app import market_news

client = TestClient(app)


def _register_token(prefix: str) -> str:
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
    return response.json()["access_token"]


def test_market_news_hard_timeout_does_not_wait_on_provider(monkeypatch):
    market_news._FEED_CACHE.clear()

    def _hang(*_args, **_kwargs):
        time.sleep(2.5)
        return {
            "headlines": [{"symbol": "RELIANCE", "title": "too late"}],
            "symbolsConsidered": ["RELIANCE"],
            "generatedAt": "2026-08-18T00:00:00",
        }

    monkeypatch.setattr(market_news, "MARKET_NEWS_TIMEOUT_SECONDS", 0.4)
    monkeypatch.setattr(market_news, "_fetch_live_headlines", _hang)

    started = time.perf_counter()
    response = client.get("/market/news?symbols=RELIANCE,TCS,INFY&limit=10")
    elapsed = time.perf_counter() - started

    assert response.status_code == 200
    assert elapsed < 3.5
    payload = response.json()
    assert payload["headlines"] == []
    assert "RELIANCE" in payload["symbolsConsidered"]


def test_market_news_returns_stale_cache_when_provider_hangs(monkeypatch):
    market_news._FEED_CACHE.clear()
    stale = {
        "headlines": [
            {
                "symbol": "TCS",
                "title": "TCS wins deal",
                "source": "Reuters",
                "publishedAt": "2026-08-18T08:00:00",
                "publishedLabel": "1h ago",
                "link": "https://example.com/tcs",
            }
        ],
        "symbolsConsidered": ["TCS"],
        "generatedAt": "2026-08-18T08:00:00",
    }
    key = market_news._cache_key(["TCS"], 10)
    market_news._FEED_CACHE[key] = (time.time() - 5.0, stale)

    def _hang(*_args, **_kwargs):
        time.sleep(2.5)
        return stale

    monkeypatch.setattr(market_news, "MARKET_NEWS_CACHE_TTL_SECONDS", 1)
    monkeypatch.setattr(market_news, "MARKET_NEWS_TIMEOUT_SECONDS", 0.4)
    monkeypatch.setattr(market_news, "_fetch_live_headlines", _hang)

    started = time.perf_counter()
    response = client.get("/market/news?symbols=TCS&limit=10")
    elapsed = time.perf_counter() - started

    assert response.status_code == 200
    assert elapsed < 2.0
    assert response.json()["headlines"][0]["title"] == "TCS wins deal"


def test_wallet_does_not_wait_on_yahoo(monkeypatch):
    def _hang(*_args, **_kwargs):
        time.sleep(8)
        return []

    monkeypatch.setattr("app.market_data.fetch_quotes", _hang)
    monkeypatch.setattr("app.market_data.yf.download", _hang)

    token = _register_token("wallet_hotpath")
    started = time.perf_counter()
    response = client.get("/wallet", headers={"Authorization": f"Bearer {token}"})
    elapsed = time.perf_counter() - started

    assert response.status_code == 200
    assert "balance" in response.json()
    assert elapsed < 2.0
