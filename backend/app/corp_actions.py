"""Dated corporate-action lines from the local pack plus Yahoo actions.

Never scrape nseindia.com. Never invent an ex-date, record date, or ratio.
Yahoo rows are kept only when a real YYYY-MM-DD can be parsed.
"""
from __future__ import annotations

import logging
import re
import time
from datetime import datetime, timezone
from threading import Lock, Thread
from typing import Any

logger = logging.getLogger(__name__)

_DATE_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})")
_YAHOO_TTL_SECONDS = 6 * 3600
_yahoo_cache: dict[str, tuple[float, list[dict[str, Any]]]] = {}
_yahoo_lock = Lock()


def parse_action_date(value: Any) -> str | None:
    """Return YYYY-MM-DD only when the source gave a real date."""
    if value is None:
        return None
    if hasattr(value, "strftime"):
        try:
            return value.strftime("%Y-%m-%d")
        except Exception:
            return None
    if isinstance(value, (int, float)):
        ts = float(value)
        if ts > 1e12:
            ts /= 1000.0
        if 1_000_000_000 <= ts <= 20_000_000_000:
            try:
                return datetime.fromtimestamp(ts, tz=timezone.utc).strftime("%Y-%m-%d")
            except Exception:
                return None
        return None
    text = str(value).strip()
    match = _DATE_RE.match(text)
    return match.group(1) if match else None


def _row_key(row: dict[str, Any]) -> str:
    return (
        f"{str(row.get('symbol') or '').upper()}|"
        f"{str(row.get('action') or '').strip()}|"
        f"{str(row.get('effective_date') or '').strip()}"
    )


def dated_pack_rows(rows: list[dict[str, Any]] | None, symbol: str | None = None) -> list[dict[str, Any]]:
    """Keep pack/Yahoo rows that already have a parseable date. Drop the rest."""
    want = (symbol or "").strip().upper()
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in rows or []:
        if not isinstance(item, dict):
            continue
        sym = str(item.get("symbol") or "").strip().upper()
        if want and sym != want:
            continue
        when = parse_action_date(item.get("effective_date") or item.get("ex_date"))
        if not when:
            continue
        cleaned = {
            "symbol": sym,
            "action": str(item.get("action") or "Event").strip() or "Event",
            "effective_date": when,
            "source": str(item.get("source") or "pack").strip() or "pack",
        }
        if item.get("amount") not in (None, "", 0, 0.0):
            cleaned["amount"] = item.get("amount")
        if item.get("ratio") not in (None, "", 0, 0.0):
            cleaned["ratio"] = item.get("ratio")
        key = _row_key(cleaned)
        if key in seen:
            continue
        seen.add(key)
        out.append(cleaned)
    out.sort(key=lambda row: str(row.get("effective_date") or ""), reverse=True)
    return out


def _yahoo_dated_actions(symbol: str) -> list[dict[str, Any]]:
    """Historical Yahoo dividends/splits + calendar ex-date when dated."""
    from .market_data import _yf_ticker

    import yfinance as yf

    ticker = yf.Ticker(_yf_ticker(symbol))
    rows: list[dict[str, Any]] = []

    actions = getattr(ticker, "actions", None)
    if actions is not None and hasattr(actions, "iterrows"):
        try:
            tail = actions.tail(12)
        except Exception:
            tail = actions
        try:
            iterator = tail.iterrows()
        except Exception:
            iterator = []
        for idx, row in iterator:
            when = parse_action_date(idx)
            if not when:
                continue
            try:
                div = float(row.get("Dividends") or 0.0)
            except Exception:
                div = 0.0
            try:
                split = float(row.get("Stock Splits") or 0.0)
            except Exception:
                split = 0.0
            if div > 0:
                rows.append(
                    {
                        "symbol": symbol,
                        "action": "Dividend",
                        "effective_date": when,
                        "source": "yahoo_actions",
                        "amount": div,
                    }
                )
            if split > 0:
                rows.append(
                    {
                        "symbol": symbol,
                        "action": "Split",
                        "effective_date": when,
                        "source": "yahoo_actions",
                        "ratio": split,
                    }
                )

    calendar = getattr(ticker, "calendar", None)
    cal_date = None
    if isinstance(calendar, dict):
        cal_date = calendar.get("Ex-Dividend Date") or calendar.get("exDividendDate")
    elif calendar is not None:
        try:
            cal_date = calendar.get("Ex-Dividend Date")  # type: ignore[union-attr]
        except Exception:
            cal_date = None
    when = parse_action_date(cal_date)
    if when:
        rows.append(
            {
                "symbol": symbol,
                "action": "Dividend",
                "effective_date": when,
                "source": "yahoo_calendar",
            }
        )

    # Skip ticker.info — that extra hop is slow and often empty.
    return dated_pack_rows(rows, symbol)


def yahoo_dated_actions(symbol: str) -> list[dict[str, Any]]:
    """Cached Yahoo dated lines. Empty on any fetch error — never invent."""
    want = (symbol or "").strip().upper()
    if not want:
        return []
    now = time.time()
    with _yahoo_lock:
        cached = _yahoo_cache.get(want)
        if cached and (now - cached[0]) < _YAHOO_TTL_SECONDS:
            return list(cached[1])

    rows: list[dict[str, Any]] = []
    box: list[list[dict[str, Any]]] = []

    def _run() -> None:
        try:
            box.append(_yahoo_dated_actions(want))
        except Exception as exc:
            logger.debug("yahoo corporate actions skipped for %s: %s", want, exc)

    worker = Thread(target=_run, daemon=True, name=f"corp-actions-{want}")
    worker.start()
    worker.join(4.0)
    if box:
        rows = box[0]
    elif worker.is_alive():
        logger.debug("yahoo corporate actions timed out for %s", want)
    with _yahoo_lock:
        _yahoo_cache[want] = (now, list(rows))
    return rows


def merge_yahoo_dated_actions(
    symbol: str,
    pack_rows: list[dict[str, Any]] | None = None,
) -> list[dict[str, Any]]:
    """Union of pack + Yahoo dated lines. Pack dates win on the same key."""
    merged = dated_pack_rows(pack_rows, symbol)
    seen = {_row_key(row) for row in merged}
    for row in dated_pack_rows(yahoo_dated_actions(symbol), symbol):
        key = _row_key(row)
        if key in seen:
            continue
        seen.add(key)
        merged.append(row)
    merged.sort(key=lambda row: str(row.get("effective_date") or ""), reverse=True)
    return merged[:12]
