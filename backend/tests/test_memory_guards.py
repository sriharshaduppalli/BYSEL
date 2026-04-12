import sys
from datetime import datetime, timedelta
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app import ai_engine
from app import market_data
from app.routes import auth as auth_routes


def test_quote_cache_evicts_oldest_when_capacity_exceeded(monkeypatch):
    clock = {"now": 1000.0}
    monkeypatch.setattr(market_data.time, "time", lambda: clock["now"])

    cache = market_data.QuoteCache(ttl_seconds=60, max_entries=2)
    cache.put("AAA", {"symbol": "AAA"})
    clock["now"] += 1
    cache.put("BBB", {"symbol": "BBB"})
    clock["now"] += 1
    cache.put("CCC", {"symbol": "CCC"})

    assert cache.get("AAA") is None
    assert cache.get("BBB") is not None
    assert cache.get("CCC") is not None
    assert cache.size() == 2


def test_quote_cache_evicts_expired_entries(monkeypatch):
    clock = {"now": 2000.0}
    monkeypatch.setattr(market_data.time, "time", lambda: clock["now"])

    cache = market_data.QuoteCache(ttl_seconds=2, max_entries=10)
    cache.put("AAA", {"symbol": "AAA"})
    clock["now"] += 3

    assert cache.get("AAA") is None
    assert cache.size() == 0


def test_news_cache_pruning_removes_expired_and_caps_size(monkeypatch):
    now = datetime(2026, 3, 16, 0, 0, 0)
    monkeypatch.setattr(ai_engine, "_NEWS_CACHE_MAX_SYMBOLS", 2)

    ai_engine._news_cache.clear()
    ai_engine._news_cache["OLD"] = (
        now - ai_engine._NEWS_CACHE_TTL - timedelta(minutes=1),
        [{"title": "old"}],
    )
    ai_engine._news_cache["ONE"] = (now - timedelta(seconds=20), [{"title": "one"}])
    ai_engine._news_cache["TWO"] = (now - timedelta(seconds=10), [{"title": "two"}])
    ai_engine._news_cache["THREE"] = (now, [{"title": "three"}])

    with ai_engine._news_cache_lock:
        ai_engine._prune_news_cache(now)

    assert "OLD" not in ai_engine._news_cache
    assert len(ai_engine._news_cache) == 2
    assert "ONE" not in ai_engine._news_cache
    assert "TWO" in ai_engine._news_cache
    assert "THREE" in ai_engine._news_cache


def test_auth_rate_limit_buckets_are_capped(monkeypatch):
    now = {"ts": 10_000.0}
    monkeypatch.setattr(auth_routes.time, "time", lambda: now["ts"])
    monkeypatch.setattr(auth_routes, "RATE_LIMIT_BUCKET_MAX_KEYS", 10)
    monkeypatch.setattr(auth_routes, "RATE_LIMIT_PRUNE_INTERVAL_SECONDS", 0)

    auth_routes._reset_debug_state()
    for index in range(120):
        auth_routes._enforce_rate_limit(
            key=f"ip-{index}",
            buckets=auth_routes._login_rate_buckets,
            max_attempts=5,
            window_seconds=60,
            message="rate-limited",
        )
        now["ts"] += 0.5

    assert len(auth_routes._login_rate_buckets) <= 10
    auth_routes._reset_debug_state()


def test_auth_rate_limit_prunes_stale_keys_and_expired_lockouts(monkeypatch):
    now = {"ts": 20_000.0}
    monkeypatch.setattr(auth_routes.time, "time", lambda: now["ts"])
    monkeypatch.setattr(auth_routes, "RATE_LIMIT_BUCKET_MAX_KEYS", 100)
    monkeypatch.setattr(auth_routes, "RATE_LIMIT_PRUNE_INTERVAL_SECONDS", 0)

    auth_routes._reset_debug_state()
    auth_routes._login_rate_buckets["stale-login"].append(now["ts"] - 200)
    auth_routes._refresh_rate_buckets["stale-refresh"].append(now["ts"] - 200)
    auth_routes._login_failure_buckets["stale-lockout"].append(now["ts"] - 200)
    auth_routes._login_lockouts["stale-lockout"] = now["ts"] - 1

    auth_routes._enforce_rate_limit(
        key="fresh-login",
        buckets=auth_routes._login_rate_buckets,
        max_attempts=5,
        window_seconds=60,
        message="rate-limited",
    )

    assert "stale-login" not in auth_routes._login_rate_buckets
    assert "stale-refresh" not in auth_routes._refresh_rate_buckets
    assert "stale-lockout" not in auth_routes._login_failure_buckets
    assert "stale-lockout" not in auth_routes._login_lockouts
    auth_routes._reset_debug_state()
