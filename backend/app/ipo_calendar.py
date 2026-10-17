"""NSE issue calendar for the paper IPO screen.

Reads the public NSE JSON feeds for current, forthcoming, and recent past
issues, then stores them on IPOModel. Practice apply stays a paper record.
"""

from __future__ import annotations

import logging
import re
import threading
import time
from datetime import date, datetime, timedelta, timezone

logger = logging.getLogger(__name__)

IST = timezone(timedelta(hours=5, minutes=30))
_CACHE_SECONDS = 15 * 60
_PAST_KEEP_DAYS = 60
_PAST_LIMIT = 25
_LOCK = threading.Lock()
_cache_at = 0.0

_HOME = "https://www.nseindia.com/"
_CURRENT = "https://www.nseindia.com/api/ipo-current-issue"
_UPCOMING = "https://www.nseindia.com/api/all-upcoming-issues?category=ipo"
_PAST = "https://www.nseindia.com/api/public-past-issues"
_PUBLIC_BOARD = "https://webnodejs.chittorgarh.com/cloud/ipo/list-read"

_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/122.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json,text/plain,*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.nseindia.com/market-data/all-upcoming-issues-ipo",
}

_MONTHS = {
    "jan": 1,
    "feb": 2,
    "mar": 3,
    "apr": 4,
    "may": 5,
    "jun": 6,
    "jul": 7,
    "aug": 8,
    "sep": 9,
    "oct": 10,
    "nov": 11,
    "dec": 12,
}

DEMO_IPO_IDS = {
    "IPO-DEMO-OPEN",
    "IPO-DEMO-UPCOMING",
    "IPO-2026-001",
    "IPO-2026-002",
}


def ist_today() -> date:
    return datetime.now(IST).date()


def is_practice_demo(ipo_id: str | None, company_name: str | None = None) -> bool:
    if (ipo_id or "") in DEMO_IPO_IDS:
        return True
    return "(demo)" in (company_name or "").lower()


def parse_nse_date(raw: str | None) -> str | None:
    text = (raw or "").strip()
    if not text or text == "-":
        return None
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", text):
        return text
    match = re.fullmatch(r"(\d{1,2})-([A-Za-z]{3})-(\d{4})", text)
    if not match:
        return None
    month = _MONTHS.get(match.group(2).lower())
    if month is None:
        return None
    try:
        return date(int(match.group(3)), month, int(match.group(1))).isoformat()
    except ValueError:
        return None


def parse_price_band(raw: str | None) -> tuple[float | None, float | None]:
    text = (raw or "").strip()
    if not text or text == "-":
        return None, None
    numbers = re.findall(r"\d+(?:\.\d+)?", text.replace(",", ""))
    if not numbers:
        return None, None
    values = [float(item) for item in numbers]
    if len(values) == 1:
        return values[0], values[0]
    return values[0], values[1]


def classify_issue_status(open_date: str, close_date: str, today: date) -> str:
    opened = date.fromisoformat(open_date)
    closed = date.fromisoformat(close_date)
    if today < opened:
        return "UPCOMING"
    if today > closed:
        return "CLOSED"
    return "OPEN"


def _clean_symbol(raw: str | None) -> str:
    return re.sub(r"\s+", "", (raw or "").strip().upper())


def _issue_row(
    *,
    symbol: str | None,
    company_name: str | None,
    open_raw: str | None,
    close_raw: str | None,
    price_raw: str | None,
    listing_raw: str | None,
    today: date,
) -> dict | None:
    symbol_text = _clean_symbol(symbol)
    name = (company_name or "").strip()
    open_date = parse_nse_date(open_raw)
    close_date = parse_nse_date(close_raw)
    if not symbol_text or not name or not open_date or not close_date:
        return None
    low, high = parse_price_band(price_raw)
    listing = parse_nse_date(listing_raw)
    return {
        "ipo_id": f"NSE-{symbol_text}",
        "company_name": name,
        "symbol": symbol_text,
        "status": classify_issue_status(open_date, close_date, today),
        "issue_open_date": open_date,
        "issue_close_date": close_date,
        "listing_date": listing,
        "price_band_min": low,
        "price_band_max": high,
        "lot_size": None,
    }


def _from_current_or_upcoming(item: dict, today: date, *, require_total: bool) -> dict | None:
    if not isinstance(item, dict):
        return None
    if require_total:
        category = str(item.get("category") or "Total").strip().lower()
        if category != "total":
            return None
    return _issue_row(
        symbol=item.get("symbol"),
        company_name=item.get("companyName"),
        open_raw=item.get("issueStartDate"),
        close_raw=item.get("issueEndDate"),
        price_raw=item.get("issuePrice"),
        listing_raw=None,
        today=today,
    )


def _from_past(item: dict, today: date) -> dict | None:
    if not isinstance(item, dict):
        return None
    return _issue_row(
        symbol=item.get("symbol"),
        company_name=item.get("company") or item.get("companyName"),
        open_raw=item.get("ipoStartDate") or item.get("issueStartDate"),
        close_raw=item.get("ipoEndDate") or item.get("issueEndDate"),
        price_raw=item.get("priceRange") or item.get("issuePrice"),
        listing_raw=item.get("listingDate"),
        today=today,
    )


def merge_nse_issues(
    current: list | None,
    upcoming: list | None,
    past: list | None,
    today: date,
) -> list[dict]:
    """Current issues win over the upcoming feed. Recent past fills Closed."""
    by_symbol: dict[str, dict] = {}
    for item in upcoming or []:
        row = _from_current_or_upcoming(item, today, require_total=False)
        if row:
            by_symbol[row["symbol"]] = row
    for item in current or []:
        row = _from_current_or_upcoming(item, today, require_total=True)
        if row:
            by_symbol[row["symbol"]] = row

    cutoff = today - timedelta(days=_PAST_KEEP_DAYS)
    past_rows: list[dict] = []
    for item in past or []:
        row = _from_past(item, today)
        if not row or row["symbol"] in by_symbol:
            continue
        if date.fromisoformat(row["issue_close_date"]) < cutoff:
            continue
        past_rows.append(row)
    past_rows.sort(key=lambda row: row["issue_close_date"], reverse=True)
    for row in past_rows[:_PAST_LIMIT]:
        by_symbol[row["symbol"]] = row
    return list(by_symbol.values())


def parse_issue_period(period: str | None, close_hint: str | None) -> tuple[str, str] | None:
    """Turn '21 Oct - 23 Oct' plus a close-date hint into ISO open and close dates."""
    match = re.fullmatch(
        r"(\d{1,2})\s+([A-Za-z]{3})\s*-\s*(\d{1,2})\s+([A-Za-z]{3})",
        (period or "").strip(),
    )
    if not match:
        return None
    hint = (close_hint or "").strip()
    year = int(hint[:4]) if len(hint) >= 4 and hint[:4].isdigit() else ist_today().year
    open_month = _MONTHS.get(match.group(2).lower())
    close_month = _MONTHS.get(match.group(4).lower())
    if open_month is None or close_month is None:
        return None
    open_year = year - 1 if open_month > close_month else year
    try:
        opened = date(open_year, open_month, int(match.group(1)))
        closed = date(year, close_month, int(match.group(3)))
    except ValueError:
        return None
    if closed < opened:
        return None
    return opened.isoformat(), closed.isoformat()


def rows_from_public_board(items: list | None, today: date) -> list[dict]:
    """Map a public issue board into IPO rows. No grey-market premium."""
    cutoff = today - timedelta(days=_PAST_KEEP_DAYS)
    rows: list[dict] = []
    for item in items or []:
        if not isinstance(item, dict):
            continue
        parsed = parse_issue_period(item.get("ipo_period"), str(item.get("orderdate") or ""))
        if parsed is None:
            continue
        open_date, close_date = parsed
        if date.fromisoformat(close_date) < cutoff:
            continue
        title = str(item.get("ipo_news_title") or "").strip()
        if title.lower().endswith(" ipo"):
            title = title[:-4].strip()
        folder = str(item.get("urlrewrite_folder_name") or "")
        if folder.lower().endswith("-ipo"):
            folder = folder[:-4]
        symbol = re.sub(r"[^A-Z0-9]", "", folder.upper())[:18]
        raw_id = item.get("id")
        if not title or not symbol or raw_id is None:
            continue
        status = classify_issue_status(open_date, close_date, today)
        category = str(item.get("issue_category") or "").strip().upper()
        if category == "SME" and status == "CLOSED":
            continue
        rows.append(
            {
                "ipo_id": f"CAL-{int(raw_id)}",
                "company_name": title,
                "symbol": symbol,
                "status": status,
                "issue_open_date": open_date,
                "issue_close_date": close_date,
                "listing_date": None,
                "price_band_min": None,
                "price_band_max": None,
                "lot_size": None,
            }
        )
    return rows


def fetch_public_issue_board(today: date | None = None) -> list[dict]:
    import requests

    today = today or ist_today()
    response = requests.get(
        _PUBLIC_BOARD,
        headers={"User-Agent": _HEADERS["User-Agent"], "Accept": "application/json"},
        timeout=12,
    )
    response.raise_for_status()
    payload = response.json()
    items = payload.get("ipoDropDownList") if isinstance(payload, dict) else None
    return rows_from_public_board(items if isinstance(items, list) else [], today)


def fetch_nse_issue_lists(today: date | None = None) -> tuple[list, list, list]:
    import requests

    today = today or ist_today()
    start = today - timedelta(days=_PAST_KEEP_DAYS)
    session = requests.Session()
    session.headers.update(_HEADERS)
    home = session.get(_HOME, timeout=8)
    home.raise_for_status()

    def _get_json(url: str) -> list:
        response = session.get(url, timeout=8)
        response.raise_for_status()
        payload = response.json()
        return payload if isinstance(payload, list) else []

    past_url = (
        f"{_PAST}?from_date={start.strftime('%d-%m-%Y')}"
        f"&to_date={today.strftime('%d-%m-%Y')}&security_type=Equity"
    )
    return _get_json(_CURRENT), _get_json(_UPCOMING), _get_json(past_url)


def _cache_is_fresh() -> bool:
    return _cache_at > 0 and (time.monotonic() - _cache_at) < _CACHE_SECONDS


def refresh_ipo_calendar(db, *, force: bool = False, today: date | None = None) -> bool:
    """Upsert NSE issues. Returns True when live rows were written or the cache is fresh."""
    global _cache_at
    today = today or ist_today()
    with _LOCK:
        if not force and _cache_is_fresh():
            return True
        rows: list[dict] = []
        try:
            current, upcoming, past = fetch_nse_issue_lists(today)
            rows = merge_nse_issues(current, upcoming, past, today)
        except Exception:
            logger.exception("NSE IPO calendar refresh failed")
        if not rows:
            try:
                rows = fetch_public_issue_board(today)
            except Exception:
                logger.exception("Public IPO calendar refresh failed")
                return False
        if not rows:
            logger.warning("IPO calendar returned no issues")
            return False
        _upsert_rows(db, rows, today)
        _cache_at = time.monotonic()
        return True


def _upsert_rows(db, rows: list[dict], today: date) -> None:
    from .database.db import IPOModel

    seen = {row["ipo_id"] for row in rows}
    for row in rows:
        existing = db.query(IPOModel).filter(IPOModel.ipo_id == row["ipo_id"]).first()
        if existing is None:
            db.add(IPOModel(**row))
            continue
        for key, value in row.items():
            setattr(existing, key, value)
    for existing in db.query(IPOModel).all():
        if is_practice_demo(existing.ipo_id, existing.company_name):
            continue
        if existing.ipo_id in seen:
            continue
        close_date = parse_nse_date(existing.issue_close_date)
        if close_date and date.fromisoformat(close_date) < today and existing.status != "CLOSED":
            existing.status = "CLOSED"
    db.commit()
