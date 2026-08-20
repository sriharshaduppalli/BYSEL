"""Fast market headlines for Home / GET /market/news.

Never call yfinance on this path. Yahoo Ticker.get_news() has no timeout and
ThreadPoolExecutor shutdown used to wait on hung workers — that is the 28s
GET /market/news (and the blocked event-loop /wallet 32s).

Hard budget is ~5s. Prefer Economic Times / Mint if Google RSS is slow
from Cloud Run, then stale cache, then empty.
"""

from __future__ import annotations

import logging
import os
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, wait, FIRST_COMPLETED
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from html import unescape
from threading import Lock
from typing import Dict, List, Optional
from xml.etree import ElementTree as ET

logger = logging.getLogger(__name__)

MARKET_NEWS_TIMEOUT_SECONDS = float(os.getenv("MARKET_NEWS_TIMEOUT_SECONDS", "5.0"))
MARKET_NEWS_RSS_TIMEOUT_SECONDS = float(os.getenv("MARKET_NEWS_RSS_TIMEOUT_SECONDS", "2.5"))
MARKET_NEWS_CACHE_TTL_SECONDS = int(os.getenv("MARKET_NEWS_CACHE_TTL_SECONDS", "90"))
MARKET_NEWS_STALE_TTL_SECONDS = int(os.getenv("MARKET_NEWS_STALE_TTL_SECONDS", str(30 * 60)))
MARKET_NEWS_MAX_SYMBOLS = 12
_BROWSER_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
# Google News from Cloud Run (europe-west1) often exceeds the old 1s budget.
# These India market feeds are the first fill so Home is not empty.
_FALLBACK_FEEDS = (
    ("https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms", "Economic Times"),
    ("https://www.livemint.com/rss/markets", "Mint"),
)

_DEFAULT_SYMBOLS = [
    "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK",
    "SBIN", "BHARTIARTL", "ITC", "LT", "TMPV",
    "AXISBANK", "KOTAKBANK",
]

_POOL = ThreadPoolExecutor(max_workers=6, thread_name_prefix="bysel-news")
_CACHE_LOCK = Lock()
# key → (fetched_at, payload)
_FEED_CACHE: Dict[str, tuple[float, Dict]] = {}
_REFRESHING: set[str] = set()


def _utc_now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def _parse_pub_date(raw: Optional[str]) -> Optional[datetime]:
    text = str(raw or "").strip()
    if not text:
        return None
    try:
        parsed = parsedate_to_datetime(text)
        if parsed.tzinfo is not None:
            parsed = parsed.astimezone(timezone.utc).replace(tzinfo=None)
        return parsed
    except Exception:
        return None


def _age_label(published_at: Optional[datetime]) -> str:
    if not published_at:
        return ""
    seconds = max((_utc_now() - published_at).total_seconds(), 0)
    if seconds < 90:
        return "just now"
    if seconds < 3600:
        return f"{int(seconds // 60)}m ago"
    if seconds < 86400:
        return f"{int(seconds // 3600)}h ago"
    return f"{int(seconds // 86400)}d ago"


def _normalize_symbols(symbols: Optional[List[str]]) -> List[str]:
    out: List[str] = []
    seen: set[str] = set()
    for raw in symbols or _DEFAULT_SYMBOLS:
        symbol = str(raw or "").strip().upper()
        if not symbol or symbol in seen:
            continue
        seen.add(symbol)
        out.append(symbol)
        if len(out) >= MARKET_NEWS_MAX_SYMBOLS:
            break
    return out


def _cache_key(symbols: List[str], limit: int) -> str:
    return f"{','.join(symbols)}|{int(limit)}"


def _empty_payload(symbols: List[str], *, generated_at: Optional[str] = None) -> Dict:
    return {
        "headlines": [],
        "symbolsConsidered": list(symbols),
        "generatedAt": generated_at or _utc_now().isoformat(),
    }


def peek_stale_news(symbols: Optional[List[str]], limit: int) -> Optional[Dict]:
    """Return cached feed even if stale (used by the route on hard timeout)."""
    key = _cache_key(_normalize_symbols(symbols), max(1, min(int(limit or 10), 20)))
    with _CACHE_LOCK:
        hit = _FEED_CACHE.get(key)
    if not hit:
        return None
    fetched_at, payload = hit
    if time.time() - fetched_at > MARKET_NEWS_STALE_TTL_SECONDS:
        return None
    return dict(payload)


def empty_market_news(symbols: Optional[List[str]], limit: int) -> Dict:
    return _empty_payload(_normalize_symbols(symbols))


def _http_get(url: str, timeout: float) -> bytes:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": _BROWSER_UA,
            "Accept": "application/rss+xml, application/xml, text/xml, */*",
        },
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=max(0.25, float(timeout))) as resp:
        return resp.read()


def _parse_rss_items(
    xml_bytes: bytes,
    *,
    symbol: str,
    limit: int,
    default_source: str = "Google News",
) -> List[Dict]:
    root = ET.fromstring(xml_bytes)
    items: List[Dict] = []
    seen: set[str] = set()
    for node in root.findall(".//item")[:24]:
        raw_title = unescape((node.findtext("title") or "").strip())
        if not raw_title:
            continue
        source = ""
        if " - " in raw_title:
            title, source = raw_title.rsplit(" - ", 1)
            title = title.strip()
            source = source.strip()
        else:
            title = raw_title
        title_key = title.lower()
        if not title_key or title_key in seen:
            continue
        seen.add(title_key)
        published_at = _parse_pub_date(node.findtext("pubDate"))
        items.append({
            "symbol": symbol,
            "title": title,
            "source": source or default_source,
            "publishedAt": published_at.isoformat() if published_at else "",
            "publishedLabel": _age_label(published_at),
            "link": (node.findtext("link") or "").strip(),
        })
        if len(items) >= limit:
            break
    return items


def _google_rss_search(query: str, *, symbol: str, limit: int, timeout: float) -> List[Dict]:
    if timeout <= 0.05:
        return []
    encoded = urllib.parse.quote_plus(query)
    url = (
        f"https://news.google.com/rss/search"
        f"?q={encoded}&hl=en-IN&gl=IN&ceid=IN:en"
    )
    try:
        raw = _http_get(url, timeout=timeout)
        return _parse_rss_items(raw, symbol=symbol, limit=limit)
    except Exception as exc:
        logger.warning("market_news.google_rss_failed symbol=%s reason=%s", symbol, exc)
        return []


def _fetch_fallback_feeds(limit: int, timeout: float) -> List[Dict]:
    if timeout <= 0.05:
        return []
    per_feed = max(0.4, min(timeout, MARKET_NEWS_RSS_TIMEOUT_SECONDS))
    items: List[Dict] = []
    for url, source in _FALLBACK_FEEDS:
        if len(items) >= limit:
            break
        try:
            raw = _http_get(url, timeout=per_feed)
            items.extend(
                _parse_rss_items(
                    raw,
                    symbol="MARKET",
                    limit=max(4, limit),
                    default_source=source,
                )
            )
        except Exception as exc:
            logger.warning("market_news.fallback_rss_failed source=%s reason=%s", source, exc)
    return items[:limit]


def _company_name(symbol: str) -> str:
    try:
        from .market_data import INDIAN_STOCKS

        return str(INDIAN_STOCKS.get(symbol, (None, ""))[1] or "")
    except Exception:
        return ""


def _fetch_overview(limit: int, timeout: float) -> List[Dict]:
    return _google_rss_search(
        "Nifty OR Sensex OR NSE stock market India when:2d",
        symbol="MARKET",
        limit=limit,
        timeout=timeout,
    )


def _fetch_symbol_rss(symbol: str, limit: int, timeout: float) -> List[Dict]:
    company = _company_name(symbol)
    subject = f"\"{company}\"" if company else symbol
    return _google_rss_search(
        f"{subject} ({symbol}) (stock OR shares OR NSE) when:3d",
        symbol=symbol,
        limit=limit,
        timeout=timeout,
    )


def _select_headlines(aggregated: List[Dict], limit: int, max_per_symbol: int = 2) -> List[Dict]:
    sorted_items = sorted(
        aggregated,
        key=lambda item: str(item.get("publishedAt") or ""),
        reverse=True,
    )
    selected: List[Dict] = []
    counts: Dict[str, int] = {}
    seen_titles: set[str] = set()
    for item in sorted_items:
        title_key = str(item.get("title") or "").strip().lower()
        if not title_key or title_key in seen_titles:
            continue
        symbol = str(item.get("symbol") or "").upper()
        if counts.get(symbol, 0) >= max_per_symbol:
            continue
        counts[symbol] = counts.get(symbol, 0) + 1
        seen_titles.add(title_key)
        selected.append(item)
        if len(selected) >= limit:
            return selected
    for item in sorted_items:
        if len(selected) >= limit:
            break
        title_key = str(item.get("title") or "").strip().lower()
        if not title_key or title_key in seen_titles:
            continue
        seen_titles.add(title_key)
        selected.append(item)
    return selected


def _fetch_live_headlines(symbols: List[str], limit: int, deadline: float) -> Dict:
    """Yahoo-free fetch. Must return before `deadline` (monotonic)."""
    aggregated: List[Dict] = []
    seen_titles: set[str] = set()

    def _absorb(rows: List[Dict]) -> None:
        for headline in rows or []:
            title_key = str(headline.get("title") or "").strip().lower()
            if not title_key or title_key in seen_titles:
                continue
            seen_titles.add(title_key)
            aggregated.append(headline)

    remaining = deadline - time.monotonic()
    if remaining > 0.15:
        _absorb(_fetch_fallback_feeds(limit=max(6, limit), timeout=min(MARKET_NEWS_RSS_TIMEOUT_SECONDS, remaining)))

    remaining = deadline - time.monotonic()
    if remaining > 0.4:
        _absorb(_fetch_overview(limit=4, timeout=min(MARKET_NEWS_RSS_TIMEOUT_SECONDS, remaining)))

    remaining = deadline - time.monotonic()
    if remaining > 0.4 and symbols:
        per_symbol_limit = max(2, min(4, limit))
        rss_timeout = min(MARKET_NEWS_RSS_TIMEOUT_SECONDS, remaining)
        # Cap per-request Google searches so europe-west1 latency cannot empty Home.
        symbol_batch = list(symbols)[:4]
        futures = {
            _POOL.submit(_fetch_symbol_rss, symbol, per_symbol_limit, rss_timeout): symbol
            for symbol in symbol_batch
        }
        pending = set(futures)
        while pending and time.monotonic() < deadline:
            done, pending = wait(
                pending,
                timeout=max(0.05, deadline - time.monotonic()),
                return_when=FIRST_COMPLETED,
            )
            if not done:
                break
            for future in done:
                try:
                    _absorb(future.result() or [])
                except Exception:
                    continue
        # Do not cancel/wait for leftover workers — they may fill cache later.

    selected = _select_headlines(aggregated, limit=limit)
    return {
        "headlines": selected,
        "symbolsConsidered": list(symbols),
        "generatedAt": _utc_now().isoformat(),
    }


def _store_cache(key: str, payload: Dict) -> None:
    with _CACHE_LOCK:
        _FEED_CACHE[key] = (time.time(), dict(payload))
        overflow = len(_FEED_CACHE) - 32
        if overflow > 0:
            oldest = sorted(_FEED_CACHE.items(), key=lambda item: item[1][0])[:overflow]
            for old_key, _ in oldest:
                _FEED_CACHE.pop(old_key, None)


def _refresh_async(key: str, symbols: List[str], limit: int) -> None:
    with _CACHE_LOCK:
        if key in _REFRESHING:
            return
        _REFRESHING.add(key)

    def _run() -> None:
        try:
            deadline = time.monotonic() + MARKET_NEWS_TIMEOUT_SECONDS
            payload = _fetch_live_headlines(symbols, limit, deadline)
            if payload.get("headlines"):
                _store_cache(key, payload)
        except Exception as exc:
            logger.warning("market_news.background_refresh_failed reason=%s", exc)
        finally:
            with _CACHE_LOCK:
                _REFRESHING.discard(key)

    _POOL.submit(_run)


def get_market_headlines(symbols: Optional[List[str]] = None, limit: int = 5) -> Dict:
    """Aggregate headlines with a hard ~2.5s budget. Prefer stale cache over waiting."""
    limit = max(1, min(int(limit or 10), 20))
    normalized = _normalize_symbols(symbols)
    key = _cache_key(normalized, limit)
    now = time.time()

    with _CACHE_LOCK:
        hit = _FEED_CACHE.get(key)
    if hit:
        fetched_at, payload = hit
        age = now - fetched_at
        if age < MARKET_NEWS_CACHE_TTL_SECONDS:
            return dict(payload)
        if age < MARKET_NEWS_STALE_TTL_SECONDS:
            _refresh_async(key, normalized, limit)
            return dict(payload)

    deadline = time.monotonic() + MARKET_NEWS_TIMEOUT_SECONDS
    future = _POOL.submit(_fetch_live_headlines, normalized, limit, deadline)
    done, _pending = wait([future], timeout=MARKET_NEWS_TIMEOUT_SECONDS)
    payload: Optional[Dict] = None
    if done:
        try:
            payload = future.result()
        except Exception as exc:
            logger.warning("market_news.fetch_failed reason=%s", exc)
            payload = None

    if payload and payload.get("headlines"):
        _store_cache(key, payload)
        return payload

    stale = peek_stale_news(normalized, limit)
    if stale:
        return stale
    empty = payload if payload else _empty_payload(normalized)
    return empty
