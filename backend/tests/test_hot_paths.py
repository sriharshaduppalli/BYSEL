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


def test_market_news_fallback_feeds_fill_when_google_empty(monkeypatch):
    market_news._FEED_CACHE.clear()
    sample_xml = b"""<?xml version="1.0"?>
    <rss><channel>
      <item><title>Sensex jumps on bank buying</title><pubDate>Thu, 20 Aug 2026 10:00:00 GMT</pubDate><link>https://example.com/1</link></item>
    </channel></rss>"""

    def _fake_http(url, timeout):
        if "economictimes" in url or "livemint" in url:
            return sample_xml
        raise TimeoutError("google blocked")

    monkeypatch.setattr(market_news, "_http_get", _fake_http)
    monkeypatch.setattr(market_news, "MARKET_NEWS_TIMEOUT_SECONDS", 1.5)
    monkeypatch.setattr(market_news, "MARKET_NEWS_RSS_TIMEOUT_SECONDS", 0.8)

    payload = market_news.get_market_headlines(["RELIANCE"], limit=5)
    assert payload["headlines"]
    assert payload["headlines"][0]["title"] == "Sensex jumps on bank buying"
    assert payload["headlines"][0]["source"] == "Economic Times"


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


def test_yahoo_v8_chart_parser_skips_null_closes():
    from app.market_data import _parse_yahoo_v8_chart

    payload = {
        "chart": {
            "result": [
                {
                    "timestamp": [1, 2, 3],
                    "indicators": {
                        "quote": [
                            {
                                "open": [10.0, None, 12.0],
                                "high": [11.0, None, 13.0],
                                "low": [9.0, None, 11.5],
                                "close": [10.5, None, 12.5],
                                "volume": [100, None, 200],
                            }
                        ]
                    },
                }
            ]
        }
    }
    candles = _parse_yahoo_v8_chart(payload)
    assert [c["close"] for c in candles] == [10.5, 12.5]
    assert candles[0]["timestamp"] == 1000
