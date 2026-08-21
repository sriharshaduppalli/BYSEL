"""
Extra quality-screen fields Yahoo quotes omit.

Yahoo income/balance history → 3Y sales CAGR, multi-year profit CAGR, ROCE.
NSE shareholding + quote-equity → promoter %, pledged %, sector PE.

Missing years or filings stay None. Nothing is invented.
"""

from __future__ import annotations

import logging
import threading
import time
from typing import Any, Dict, List, Optional, Sequence, Tuple

logger = logging.getLogger(__name__)

NSE_QUALITY_TTL_SECONDS = 12 * 3600
_NSE_CACHE_LOCK = threading.Lock()
_NSE_CACHE: Dict[str, Tuple[float, Dict[str, Any]]] = {}
_NSE_INFLIGHT: set[str] = set()

_NSE_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json,text/plain,*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.nseindia.com/",
}

_PROMOTER_KEYS = (
    "promoterandpromotergroup",
    "promoterholding",
    "promotersholding",
    "totalpromoter",
    "promotergroup",
    "prmtr",
    "promoter",
)
_PLEDGE_KEYS = (
    "pledgedshares",
    "promoterpledged",
    "percentpledged",
    "pledgeperc",
    "pledgedperc",
    "prmtrpldg",
    "pledged",
    "pledge",
)


def _raw_number(value: Any) -> Optional[float]:
    if value is None or value == "":
        return None
    if isinstance(value, dict):
        if "raw" in value:
            return _raw_number(value.get("raw"))
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if number != number:
        return None
    return number


def _pct(value: Optional[float]) -> Optional[float]:
    if value is None:
        return None
    if abs(value) <= 1.5:
        return round(value * 100.0, 2)
    return round(value, 2)


def _cagr(start: Optional[float], end: Optional[float], years: float) -> Optional[float]:
    if start is None or end is None or years <= 0:
        return None
    if start <= 0 or end <= 0:
        return None
    try:
        return round(((end / start) ** (1.0 / years) - 1.0) * 100.0, 2)
    except (OverflowError, ValueError, ZeroDivisionError):
        return None


def _unwrap_quote_summary(raw: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(raw, dict):
        return {}
    summary = raw.get("quoteSummary")
    if isinstance(summary, dict):
        results = summary.get("result") or []
        if results and isinstance(results[0], dict):
            return results[0]
    return raw


def _statement_rows(module: Any, inner_key: str) -> List[Dict[str, Any]]:
    if not isinstance(module, dict):
        return []
    rows = module.get(inner_key) or module.get("statements") or []
    if isinstance(rows, list):
        return [row for row in rows if isinstance(row, dict)]
    return []


def _year_series(rows: Sequence[Dict[str, Any]], *keys: str) -> List[Tuple[int, float]]:
    series: List[Tuple[int, float]] = []
    for row in rows:
        value = None
        for key in keys:
            value = _raw_number(row.get(key))
            if value is not None:
                break
        if value is None:
            continue
        end = row.get("endDate") or row.get("asOfDate") or {}
        stamp = _raw_number(end) if not isinstance(end, dict) else _raw_number(end.get("raw"))
        year = int(stamp) if stamp and stamp > 10_000 else 0
        if stamp and stamp > 10_000_000:
            year = int(time.gmtime(stamp).tm_year)
        series.append((year, float(value)))
    series.sort(key=lambda item: item[0])
    return series


def _capital_employed(row: Dict[str, Any]) -> Optional[float]:
    equity = _raw_number(
        row.get("totalStockholderEquity")
        or row.get("stockholdersEquity")
        or row.get("commonStockEquity")
    )
    debt = _raw_number(row.get("totalDebt"))
    if debt is None:
        long_debt = _raw_number(row.get("longTermDebt")) or 0.0
        short_debt = _raw_number(row.get("shortLongTermDebt") or row.get("shortTermDebt")) or 0.0
        if long_debt or short_debt:
            debt = long_debt + short_debt
    cash = _raw_number(
        row.get("cash")
        or row.get("cashAndCashEquivalents")
        or row.get("cashCashEquivalentsAndShortTermInvestments")
    )
    if equity is not None:
        capital = equity + max(debt or 0.0, 0.0) - max(cash or 0.0, 0.0)
        if capital > 0:
            return capital
    assets = _raw_number(row.get("totalAssets"))
    current = _raw_number(row.get("totalCurrentLiabilities"))
    if assets is not None and current is not None and assets > current:
        return assets - current
    return None


def _statement_year(row: Dict[str, Any]) -> Optional[int]:
    end = row.get("endDate") or row.get("asOfDate") or {}
    stamp = _raw_number(end.get("raw") if isinstance(end, dict) else end)
    if not stamp:
        return None
    if stamp > 10_000_000:
        return int(time.gmtime(stamp).tm_year)
    return int(stamp)


def _yearly_roce(
    income_rows: Sequence[Dict[str, Any]],
    balance_rows: Sequence[Dict[str, Any]],
) -> List[float]:
    balances: Dict[int, Dict[str, Any]] = {}
    for row in balance_rows:
        year = _statement_year(row)
        if year:
            balances[year] = row

    values: List[Tuple[int, float]] = []
    for row in income_rows:
        year = _statement_year(row)
        if not year:
            continue
        ebit = _raw_number(row.get("ebit") or row.get("operatingIncome") or row.get("ebitda"))
        capital = _capital_employed(balances.get(year) or {})
        if ebit is None or capital is None or capital <= 0:
            continue
        values.append((year, (ebit / capital) * 100.0))
    values.sort(key=lambda item: item[0])
    return [item[1] for item in values]


def statements_from_yahoo_quote(raw: Dict[str, Any]) -> Dict[str, Any]:
    """Compute CAGR / ROCE from Yahoo statement modules. Empty if history is short."""
    root = _unwrap_quote_summary(raw)
    income_rows = _statement_rows(root.get("incomeStatementHistory"), "incomeStatementHistory")
    if not income_rows:
        income_rows = _statement_rows(root.get("incomeStatementHistoryQuarterly"), "incomeStatementHistory")
    balance_rows = _statement_rows(root.get("balanceSheetHistory"), "balanceSheetHistory")

    sales = _year_series(income_rows, "totalRevenue", "operatingRevenue")
    profits = _year_series(income_rows, "netIncome", "netIncomeApplicableToCommonShares")
    out: Dict[str, Any] = {"statementsChecked": 1}

    if len(sales) >= 4:
        years = max(sales[-1][0] - sales[0][0], len(sales) - 1)
        cagr = _cagr(sales[0][1], sales[-1][1], float(years))
        if cagr is not None and years >= 3:
            out["salesCagr"] = cagr
            out["salesCagrYears"] = int(years)

    if len(profits) >= 4:
        years = max(profits[-1][0] - profits[0][0], len(profits) - 1)
        cagr = _cagr(profits[0][1], profits[-1][1], float(years))
        if cagr is not None:
            out["profitCagr"] = cagr
            out["profitCagrYears"] = int(years)

    roce_years = _yearly_roce(income_rows, balance_rows)
    if roce_years:
        out["roce"] = round(roce_years[-1], 2)
        sample = roce_years[-5:] if len(roce_years) >= 5 else roce_years
        if len(sample) >= 3:
            out["roceAvg"] = round(sum(sample) / len(sample), 2)
            out["roceAvgYears"] = len(sample)
    return out


def _looks_like(key: str, names: Sequence[str]) -> bool:
    compact = "".join(ch for ch in key.lower() if ch.isalnum())
    return any(name in compact for name in names)


def _walk_shareholding(node: Any, promoter: List[float], pledge: List[float]) -> None:
    if isinstance(node, dict):
        category = str(node.get("category") or node.get("categoryName") or node.get("name") or "")
        is_promoter_row = "promoter" in category.lower()
        for key, value in node.items():
            number = _raw_number(value)
            if number is None:
                _walk_shareholding(value, promoter, pledge)
                continue
            if _looks_like(str(key), _PLEDGE_KEYS):
                if number is not None and 0 <= number <= 100:
                    pledge.append(round(number, 2))
            elif is_promoter_row or _looks_like(str(key), _PROMOTER_KEYS):
                pct = _pct(number)
                if pct is not None and 0 <= pct <= 100:
                    promoter.append(pct)
        return
    if isinstance(node, list):
        for item in node:
            _walk_shareholding(item, promoter, pledge)


def parse_nse_shareholding(payload: Any) -> Dict[str, Any]:
    promoter: List[float] = []
    pledge: List[float] = []
    _walk_shareholding(payload, promoter, pledge)
    out: Dict[str, Any] = {"shareholdingChecked": 1}
    if promoter:
        out["promoter"] = round(promoter[0], 2)
    if pledge:
        picked = pledge[0]
        if promoter:
            distinct = [item for item in pledge if item != promoter[0]]
            if distinct:
                picked = distinct[0]
        out["pledge"] = round(picked, 2)
    return out


def parse_nse_quote_equity(payload: Any) -> Dict[str, Any]:
    if not isinstance(payload, dict):
        return {}
    meta = payload.get("metadata") or {}
    sector_pe = _raw_number(meta.get("pdSectorPe") or meta.get("pdsectorpe"))
    out: Dict[str, Any] = {}
    if sector_pe is not None and sector_pe > 0:
        out["nseSectorPe"] = round(float(sector_pe), 2)
    return out


def _nse_session():
    import requests

    session = requests.Session()
    session.headers.update(_NSE_HEADERS)
    try:
        session.get("https://www.nseindia.com/", timeout=6)
    except Exception as exc:
        logger.debug("nse quality cookie warm failed: %s", exc)
    return session


def _nse_json(session, url: str, timeout: float = 6.0) -> Any:
    try:
        resp = session.get(url, timeout=timeout)
        if resp.status_code != 200:
            logger.debug("nse quality %s -> %s", url, resp.status_code)
            return None
        return resp.json()
    except Exception as exc:
        logger.debug("nse quality fetch failed url=%s reason=%s", url, exc)
        return None


def fetch_nse_quality_overlay(symbol: str) -> Dict[str, Any]:
    """Promoter / pledge / NSE sector PE. Cached 12h. Never invents."""
    key = (symbol or "").strip().upper()
    if not key:
        return {}
    now = time.time()
    with _NSE_CACHE_LOCK:
        cached = _NSE_CACHE.get(key)
        if cached and (now - cached[0]) < NSE_QUALITY_TTL_SECONDS:
            return dict(cached[1])

    session = _nse_session()
    merged: Dict[str, Any] = {"shareholdingChecked": 1}
    quote = _nse_json(session, f"https://www.nseindia.com/api/quote-equity?symbol={key}")
    if quote:
        merged.update(parse_nse_quote_equity(quote))

    share_urls = (
        f"https://www.nseindia.com/api/corp-info?symbol={key}&corpType=shareholdings&market=equities",
        f"https://www.nseindia.com/api/corporates-shareholding?symbol={key}&index=equities",
        f"https://www.nseindia.com/api/corporate-share-holdings-master?index=equities&symbol={key}",
    )
    for url in share_urls:
        payload = _nse_json(session, url)
        if payload in (None, {}, []):
            continue
        parsed = parse_nse_shareholding(payload)
        if parsed.get("promoter") is not None or parsed.get("pledge") is not None:
            merged.update(parsed)
            break
        merged.update(parsed)

    with _NSE_CACHE_LOCK:
        _NSE_CACHE[key] = (time.time(), dict(merged))
    return merged


def needs_nse_quality(fund: Optional[Dict[str, Any]]) -> bool:
    if not fund:
        return True
    if fund.get("shareholdingChecked"):
        return False
    return fund.get("promoter") is None and fund.get("pledge") is None


def fill_nse_quality_batch(symbols: Sequence[str], limit: int = 12) -> None:
    """Background NSE overlay — cap per run so Cloud Run is not blocked."""
    pending: List[str] = []
    with _NSE_CACHE_LOCK:
        for raw in symbols:
            key = str(raw or "").strip().upper()
            if not key or key in _NSE_INFLIGHT:
                continue
            cached = _NSE_CACHE.get(key)
            if cached and (time.time() - cached[0]) < NSE_QUALITY_TTL_SECONDS:
                continue
            _NSE_INFLIGHT.add(key)
            pending.append(key)
            if len(pending) >= max(1, int(limit)):
                break
    try:
        for symbol in pending:
            try:
                fetch_nse_quality_overlay(symbol)
            except Exception as exc:
                logger.debug("nse quality overlay failed symbol=%s reason=%s", symbol, exc)
    finally:
        with _NSE_CACHE_LOCK:
            for symbol in pending:
                _NSE_INFLIGHT.discard(symbol)


def cached_nse_quality(symbol: str) -> Dict[str, Any]:
    key = (symbol or "").strip().upper()
    with _NSE_CACHE_LOCK:
        cached = _NSE_CACHE.get(key)
    if not cached:
        return {}
    return dict(cached[1])


def schedule_nse_quality_fill(symbols: Sequence[str], limit: int = 12) -> None:
    pending = [str(sym or "").strip().upper() for sym in symbols if str(sym or "").strip()]
    if not pending:
        return
    thread = threading.Thread(
        target=fill_nse_quality_batch,
        args=(pending, limit),
        daemon=True,
        name="nse-quality-fill",
    )
    thread.start()
