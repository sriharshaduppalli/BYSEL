"""
Real-time stock context enricher.
Sources (all free, no API key):
  1. NSE equity CSV   — company names for ALL 2000+ NSE-listed stocks
  2. BSE List of Scrips API — active BSE equities (scrip id + 6-digit code)
  3. NSE India API    — P/E, sector P/E avg, 52-week range, live price
  4. Yahoo Finance API— P/E, sector, dividend yield (direct HTTP, not yfinance lib)
  5. Google News RSS  — headlines from ET, MoneyControl, Business Standard
  6. yfinance fast_info — market cap, fallback price
  7. yf.download()    — OHLCV history for RSI, Bollinger, SMAs, S/R levels
  8. Hardcoded maps   — sector + company name fallback for top 200 stocks
"""
from __future__ import annotations

import asyncio
import csv
import io
import logging
import re
import threading
from time import time
from typing import Any, Optional
from urllib.parse import quote_plus
from xml.etree import ElementTree as ET

logger = logging.getLogger(__name__)

_cache: dict[str, tuple[float, dict]] = {}
_CACHE_TTL = 180  # seconds — chat reuse within a session; still fresh enough for AI context

# Listing masters refresh periodically so new IPO / listing changes show up.
_LISTING_TTL_SECONDS = 12 * 3600

# Loaded from NSE archives CSV — covers ALL ~2000+ NSE-listed stocks
_NSE_EQUITY_MAP: dict[str, str] = {}   # symbol -> full company name
_NSE_EQUITY_LOADED = False
_NSE_EQUITY_LOADED_AT = 0.0
_NSE_EQUITY_LOCK = threading.Lock()

# BSE active equity master (scrip_id / code / ISIN)
_BSE_BY_ID: dict[str, dict[str, Any]] = {}
_BSE_BY_CODE: dict[str, dict[str, Any]] = {}
_BSE_BY_ISIN: dict[str, dict[str, Any]] = {}
_BSE_EQUITY_LOADED = False
_BSE_EQUITY_LOADED_AT = 0.0
_BSE_EQUITY_LOCK = threading.Lock()


def _listings_stale(loaded: bool, loaded_at: float) -> bool:
    if not loaded:
        return True
    if loaded_at <= 0:
        return False  # loaded once with failure / no clock — don't spin
    return (time() - loaded_at) >= _LISTING_TTL_SECONDS


def _invalidate_search_catalog() -> None:
    """Mark catalog dirty without taking its lock (avoids deadlock during merge)."""
    try:
        from .market_data import mark_stock_catalog_dirty

        mark_stock_catalog_dirty()
    except Exception:
        pass


def _load_nse_equity_map(*, force: bool = False) -> None:
    """
    Downloads NSE EQUITY_L.csv and builds symbol→company name map.
    Refreshes about every 12 hours so new listings appear without restart.
    """
    global _NSE_EQUITY_MAP, _NSE_EQUITY_LOADED, _NSE_EQUITY_LOADED_AT
    with _NSE_EQUITY_LOCK:
        if not force and _NSE_EQUITY_LOADED and not _listings_stale(
            _NSE_EQUITY_LOADED, _NSE_EQUITY_LOADED_AT
        ):
            return
        try:
            import requests
            resp = requests.get(
                "https://archives.nseindia.com/content/equities/EQUITY_L.csv",
                headers={
                    "User-Agent": "Mozilla/5.0",
                    "Referer": "https://www.nseindia.com/",
                },
                timeout=15,
            )
            if resp.status_code == 200:
                fresh: dict[str, str] = {}
                reader = csv.DictReader(io.StringIO(resp.text))
                for row in reader:
                    sym = row.get("SYMBOL", "").strip().upper()
                    name = row.get("NAME OF COMPANY", "").strip()
                    if sym and name:
                        fresh[sym] = name
                if fresh:
                    _NSE_EQUITY_MAP = fresh
                    _NSE_EQUITY_LOADED_AT = time()
                    _invalidate_search_catalog()
                    logger.info("NSE equity list loaded: %d symbols", len(_NSE_EQUITY_MAP))
                else:
                    logger.warning("NSE equity CSV empty — keeping prior map (%d)", len(_NSE_EQUITY_MAP))
            else:
                logger.warning("NSE equity CSV returned %s", resp.status_code)
        except Exception as e:
            logger.warning("NSE equity list load failed: %s", e)
        finally:
            _NSE_EQUITY_LOADED = True


def get_nse_equity_map() -> dict[str, str]:
    """Public accessor for the NSE EQUITY_L symbol→name map (~2000+ equities)."""
    _load_nse_equity_map()
    return dict(_NSE_EQUITY_MAP)


def _load_bse_equity_map(*, force: bool = False) -> None:
    """
    Fetch active BSE Equity scrips from BSE ListofScripData API.
    Indexes by scrip_id (e.g. RELIANCE), 6-digit code (500325), and ISIN.
    """
    global _BSE_BY_ID, _BSE_BY_CODE, _BSE_BY_ISIN, _BSE_EQUITY_LOADED, _BSE_EQUITY_LOADED_AT
    with _BSE_EQUITY_LOCK:
        if not force and _BSE_EQUITY_LOADED and not _listings_stale(
            _BSE_EQUITY_LOADED, _BSE_EQUITY_LOADED_AT
        ):
            return
        try:
            import requests

            url = (
                "https://api.bseindia.com/BseIndiaAPI/api/ListofScripData/w"
                "?Group=&Scripcode=&industry=&segment=Equity&status=Active"
            )
            resp = requests.get(
                url,
                headers={
                    "User-Agent": (
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/120.0.0.0 Safari/537.36"
                    ),
                    "Referer": "https://www.bseindia.com/",
                    "Origin": "https://www.bseindia.com",
                    "Accept": "application/json, text/plain, */*",
                },
                timeout=30,
            )
            if resp.status_code != 200:
                logger.warning("BSE equity API returned %s", resp.status_code)
                return
            rows = resp.json()
            if not isinstance(rows, list) or not rows:
                logger.warning("BSE equity API empty payload")
                return

            by_id: dict[str, dict[str, Any]] = {}
            by_code: dict[str, dict[str, Any]] = {}
            by_isin: dict[str, dict[str, Any]] = {}
            nse_syms = set(_NSE_EQUITY_MAP.keys()) if _NSE_EQUITY_MAP else set()
            if not nse_syms:
                # Best-effort: may still be empty if NSE load failed.
                nse_syms = set()

            for row in rows:
                if not isinstance(row, dict):
                    continue
                status = str(row.get("Status") or "").strip().lower()
                segment = str(row.get("Segment") or "").strip().lower()
                if status and status != "active":
                    continue
                if segment and segment != "equity":
                    continue
                code = str(row.get("SCRIP_CD") or "").strip()
                if not (len(code) == 6 and code.isdigit()):
                    continue
                sid = str(row.get("scrip_id") or "").strip().upper()
                name = (
                    str(row.get("Issuer_Name") or "").strip()
                    or str(row.get("Scrip_Name") or "").strip()
                    or sid
                    or code
                )
                isin = str(row.get("ISIN_NUMBER") or "").strip().upper()
                group = str(row.get("GROUP") or "").strip().upper()
                rec = {
                    "code": code,
                    "scrip_id": sid,
                    "name": name,
                    "isin": isin,
                    "group": group,
                    "yahoo": f"{code}.BO",
                    "dual_listed": bool(sid and sid in nse_syms),
                }
                by_code[code] = rec
                if sid and re.fullmatch(r"[A-Z][A-Z0-9-]{0,14}", sid):
                    # Prefer first / dual-aware record; keep richer name if empty.
                    prev = by_id.get(sid)
                    if prev is None or (not prev.get("name") and name):
                        by_id[sid] = rec
                if isin.startswith("IN"):
                    by_isin[isin] = rec

            if by_code:
                _BSE_BY_ID = by_id
                _BSE_BY_CODE = by_code
                _BSE_BY_ISIN = by_isin
                _BSE_EQUITY_LOADED_AT = time()
                _invalidate_search_catalog()
                logger.info(
                    "BSE equity list loaded: %d codes, %d scrip_ids (%d dual-ish vs NSE cache)",
                    len(by_code),
                    len(by_id),
                    sum(1 for r in by_id.values() if r.get("dual_listed")),
                )
        except Exception as e:
            logger.warning("BSE equity list load failed: %s", e)
        finally:
            _BSE_EQUITY_LOADED = True


def get_bse_equity_records() -> list[dict[str, Any]]:
    """All active BSE equity records (one per scrip code)."""
    # Prefer NSE names loaded first so dual_listed flags are accurate.
    _load_nse_equity_map()
    _load_bse_equity_map()
    return list(_BSE_BY_CODE.values())


def get_bse_equity_map() -> dict[str, str]:
    """scrip_id and 6-digit code → company name (active BSE equities)."""
    _load_nse_equity_map()
    _load_bse_equity_map()
    out: dict[str, str] = {}
    for sid, rec in _BSE_BY_ID.items():
        if sid and rec.get("name"):
            out[sid] = str(rec["name"])
    for code, rec in _BSE_BY_CODE.items():
        if code and rec.get("name"):
            out[code] = str(rec["name"])
    return out


def lookup_bse_listing(symbol: str) -> Optional[dict[str, Any]]:
    """Resolve a symbol/code/ISIN to a BSE equity record."""
    _load_bse_equity_map()
    raw = (symbol or "").strip().upper()
    if not raw:
        return None
    if raw.endswith(".BO"):
        raw = raw[:-3]
    if raw.startswith("BSE:"):
        raw = raw.split(":", 1)[1].strip()
    if raw in _BSE_BY_CODE:
        return dict(_BSE_BY_CODE[raw])
    if raw in _BSE_BY_ID:
        return dict(_BSE_BY_ID[raw])
    if raw in _BSE_BY_ISIN:
        return dict(_BSE_BY_ISIN[raw])
    return None


def is_bse_only_symbol(symbol: str) -> bool:
    """True when listed on BSE but not present in the NSE equity master."""
    _load_nse_equity_map()
    rec = lookup_bse_listing(symbol)
    if not rec:
        return False
    sid = str(rec.get("scrip_id") or "").upper()
    if sid and sid in _NSE_EQUITY_MAP:
        return False
    return True


def resolve_analysis_symbol(symbol: str) -> str:
    """Prefer NSE scrip_id for dual-listed BSE codes (reliable quotes/OHLCV).

    Example: 500325 / BSE:500325 → RELIANCE. Pure BSE-only names stay unchanged.
    """
    raw = (symbol or "").strip().upper()
    if not raw:
        return raw
    if raw.endswith(".BO") or raw.endswith(".NS"):
        raw = raw.rsplit(".", 1)[0]
    if raw.startswith("BSE:") or raw.startswith("NSE:"):
        raw = raw.split(":", 1)[1].strip()
    _load_nse_equity_map()
    if raw in _NSE_EQUITY_MAP:
        return raw
    rec = lookup_bse_listing(raw)
    if not rec:
        return raw
    sid = str(rec.get("scrip_id") or "").upper()
    if sid and sid in _NSE_EQUITY_MAP:
        return sid
    return raw


def format_symbol_display(asked: str, analysis_symbol: str | None = None) -> str:
    """Human label that keeps BSE code context when remapped to NSE."""
    asked_u = (asked or "").strip().upper()
    if asked_u.startswith("BSE:"):
        asked_u = asked_u.split(":", 1)[1].strip()
    analysis = (analysis_symbol or resolve_analysis_symbol(asked_u) or asked_u).upper()
    if asked_u and analysis and asked_u != analysis:
        if len(asked_u) == 6 and asked_u.isdigit():
            return f"{analysis} (BSE:{asked_u})"
        return f"{analysis} (asked {asked_u})"
    return analysis or asked_u


def listings_are_stale() -> bool:
    """True when NSE or BSE masters should be refreshed (or never loaded)."""
    nse_stale = _listings_stale(_NSE_EQUITY_LOADED, _NSE_EQUITY_LOADED_AT) or not _NSE_EQUITY_MAP
    bse_stale = _listings_stale(_BSE_EQUITY_LOADED, _BSE_EQUITY_LOADED_AT) or not _BSE_BY_CODE
    return nse_stale or bse_stale


def get_listing_coverage() -> dict[str, Any]:
    """Counts for /symbols/count and diagnostics."""
    nse = get_nse_equity_map()
    _load_bse_equity_map()
    dual = sum(1 for r in _BSE_BY_ID.values() if r.get("scrip_id") in nse)
    bse_only = sum(1 for r in _BSE_BY_ID.values() if r.get("scrip_id") not in nse)
    return {
        "nseCount": len(nse),
        "bseCodeCount": len(_BSE_BY_CODE),
        "bseScripIdCount": len(_BSE_BY_ID),
        "dualListedApprox": dual,
        "bseOnlyApprox": bse_only,
        "nseLoadedAt": _NSE_EQUITY_LOADED_AT,
        "bseLoadedAt": _BSE_EQUITY_LOADED_AT,
        "listingTtlSeconds": _LISTING_TTL_SECONDS,
    }
_NAME_TO_SYMBOL: dict[str, str] = {
    "reliance": "RELIANCE", "ril": "RELIANCE",
    "tcs": "TCS", "tata consultancy": "TCS",
    "infosys": "INFY", "infy": "INFY",
    "hdfc bank": "HDFCBANK", "hdfc": "HDFCBANK",
    "icici bank": "ICICIBANK", "icici": "ICICIBANK",
    "wipro": "WIPRO",
    "hcl": "HCLTECH", "hcltech": "HCLTECH",
    "sbi": "SBIN", "state bank": "SBIN",
    "bajaj finance": "BAJFINANCE", "bajaj fin": "BAJFINANCE",
    "kotak": "KOTAKBANK", "kotak bank": "KOTAKBANK",
    "axis bank": "AXISBANK", "axis": "AXISBANK",
    "maruti": "MARUTI", "maruti suzuki": "MARUTI",
    "adani enterprises": "ADANIENT", "adanient": "ADANIENT",
    "adani ports": "ADANIPORTS",
    "titan": "TITAN",
    "ultratech": "ULTRACEMCO", "ultratech cement": "ULTRACEMCO",
    "nestle": "NESTLEIND",
    "ltimindtree": "LTIM", "lti": "LTIM",
    "tech mahindra": "TECHM",
    "sun pharma": "SUNPHARMA", "sun pharmaceutical": "SUNPHARMA",
    "asian paints": "ASIANPAINT", "asianpaint": "ASIANPAINT",
    "bharti airtel": "BHARTIARTL", "airtel": "BHARTIARTL",
    "ongc": "ONGC",
    "power grid": "POWERGRID",
    "ntpc": "NTPC",
    "coal india": "COALINDIA",
    "jio financial": "JIOFIN", "jio fin": "JIOFIN",
    # TATAMOTORS is not listed after demerger — map common names to TMPV (PV).
    "tata motors": "TMPV",
    "tatamotors": "TMPV",
    "tmpv": "TMPV",
    "tata motors passenger": "TMPV",
    "tata motors pv": "TMPV",
    "tata motors passenger vehicles": "TMPV",
    "tmcv": "TMCV",
    "tata motors cv": "TMCV",
    "tata motors commercial": "TMCV",
    "tata motors commercial vehicles": "TMCV",
    "tata steel": "TATASTEEL",
    "hindalco": "HINDALCO",
    "jsw steel": "JSWSTEEL",
    "grasim": "GRASIM",
    "bpcl": "BPCL", "bharat petroleum": "BPCL",
    "hero motocorp": "HEROMOTOCO", "hero moto": "HEROMOTOCO",
    "bajaj auto": "BAJAJ-AUTO",
    "divis": "DIVISLAB", "divis lab": "DIVISLAB",
    "cipla": "CIPLA",
    "dr reddy": "DRREDDY", "dr reddys": "DRREDDY",
    "eicher": "EICHERMOT",
    "shriram": "SHRIRAMFIN", "shriram finance": "SHRIRAMFIN",
    "upl": "UPL",
    "britannia": "BRITANNIA",
    "indusind": "INDUSINDBK", "indusind bank": "INDUSINDBK",
    # Extended coverage
    "lupin": "LUPIN",
    "zomato": "ETERNAL", "eternal": "ETERNAL",
    "irctc": "IRCTC",
    "havells": "HAVELLS",
    "pidilite": "PIDILITIND",
    "apollo hospital": "APOLLOHOSP", "apollo hospitals": "APOLLOHOSP",
    "sbi life": "SBILIFE", "sbi life insurance": "SBILIFE",
    "hdfc life": "HDFCLIFE", "hdfc life insurance": "HDFCLIFE",
    "bajaj finserv": "BAJAJFINSV",
    "chola": "CHOLAFIN", "cholamandalam": "CHOLAFIN",
    "trent": "TRENT",
    "dmart": "DMART", "avenue supermarts": "DMART",
    "muthoot": "MUTHOOTFIN", "muthoot finance": "MUTHOOTFIN",
    "bandhan": "BANDHANBNK", "bandhan bank": "BANDHANBNK",
    "biocon": "BIOCON",
    "auro pharma": "AUROPHARMA", "aurobindo": "AUROPHARMA",
    "torrent pharma": "TORNTPHARM", "torrent": "TORNTPHARM",
    "godrej": "GODREJCP", "godrej consumer": "GODREJCP",
    "polycab": "POLYCAB",
    "dixon": "DIXON",
    "vedanta": "VEDL",
    "sail": "SAIL",
    "nmdc": "NMDC",
    "rec": "RECLTD", "rec limited": "RECLTD",
    "pfc": "PFC", "power finance": "PFC",
    "irfc": "IRFC",
    "lici": "LICI", "lic": "LICI", "life insurance corporation": "LICI",
    "icici prudential": "ICICIPRULI",
    "icici lombard": "ICICIGI",
    "max healthcare": "MAXHEALTH",
    "fortis": "FORTIS",
    "page industries": "PAGEIND",
    "varun beverages": "VBL",
    "voltas": "VOLTAS",
    "abbott": "ABBOTINDIA",
    "zydus": "ZYDUSLIFE",
    "alkem": "ALKEM",
    "motherson": "MOTHERSON", "mother sumi": "MOTHERSON", "mothersumi": "MOTHERSON",
    "tata communication": "TATACOMM", "tata comm": "TATACOMM",
    "tata elxsi": "TATAELXSI",
    "tata chemicals": "TATACHEM",
    "jubilant food": "JUBLFOOD", "dominos": "JUBLFOOD",
    "star health": "STARHEALTH",
    "naukri": "NAUKRI", "info edge": "NAUKRI",
    "indigo": "INDIGO", "interglobe": "INDIGO",
    "spicejet": "SPICEJET",
    "sula": "SULA",
    "emami": "EMAMILTD",
    "colgate": "COLPAL",
    "marico": "MARICO",
    "dabur": "DABUR",
    "hul": "HINDUNILVR", "hindustan unilever": "HINDUNILVR",
    "ioc": "IOC", "indian oil": "IOC",
    "hpcl": "HINDPETRO", "hindustan petroleum": "HINDPETRO",
    "adani transmission": "ADANIENSOL", "adani energy solutions": "ADANIENSOL",
    "gmr infra": "GMRAIRPORT", "gmr airports": "GMRAIRPORT",
    "uno minda": "UNOMINDA", "minda": "UNOMINDA",
    "l&t finance": "LTF", "lt finance": "LTF",
    "360 one": "360ONE", "iifl wam": "360ONE",
    "sammaan": "SAMMAANCAP", "indiabulls housing": "SAMMAANCAP",
    "adani wilmar": "AWL", "awl": "AWL",
    "canara bank": "CANBK", "canara": "CANBK",
    "dalmia": "DALBHARAT", "dalmia bharat": "DALBHARAT",
    "indigo paints": "INDIGOPNTS",
    "lemon tree": "LEMONTREE",
    "concor": "CONCOR", "container corporation": "CONCOR",
    "bhel": "BHEL",
    "abb": "ABB",
    "siemens": "SIEMENS",
    "l&t": "LT", "larsen": "LT", "larsen and toubro": "LT",
    "ltts": "LTTS", "l&t technology": "LTTS",
    "persistent": "PERSISTENT",
    "coforge": "COFORGE",
    "mphasis": "MPHASIS",
    "ofss": "OFSS", "oracle financial": "OFSS",
    "delhivery": "DELHIVERY",
    "paytm": "PAYTM", "one97": "PAYTM",
    "nykaa": "NYKAA", "fsn": "NYKAA",
    "policybazaar": "POLICYBZR",
    "cartrade": "CARTRADE",
    "anand rathi": "ANANDRATHI",
    "5paisa": "5PAISA",
    "angel": "ANGELONE", "angel one": "ANGELONE",
    "central bank": "CENTRALBK",
    "canara bank": "CANBK",
    "bank of baroda": "BANKBARODA", "bob": "BANKBARODA",
    "punjab national bank": "PNB", "pnb": "PNB",
    "union bank": "UNIONBANK",
    "federal bank": "FEDERALBNK",
    "idfc first": "IDFCFIRSTB", "idfc": "IDFCFIRSTB",
    "au small finance": "AUBANK", "au bank": "AUBANK",
    "equitas": "EQUITASBNK",
    "ujjivan": "UJJIVANSFB",
    "karnataka bank": "KTKBANK",
    "south indian bank": "SOUTHBANK",
    "city union bank": "CUB",
    # LAKSHVILAS amalgamated into DBS — do not map as a live equity.
}

_POS_WORDS = {
    "surge", "rally", "growth", "profit", "strong", "beat", "record",
    "gain", "rise", "bull", "upgrade", "positive", "wins", "boost",
    "outperform", "buy", "overweight", "robust", "better", "recovery",
    "rebound", "momentum", "expansion", "milestone", "breakout", "bullish",
    "upbeat", "optimistic", "soar", "jumps", "climbs", "partnership",
    "deal", "approval", "dividend", "buyback", "bonus", "inflow",
    "raise", "raised", "exceed", "improves", "highest", "award",
}
_NEG_WORDS = {
    "fall", "decline", "loss", "weak", "miss", "cut", "drop", "bear",
    "downgrade", "risk", "concern", "warning", "crash", "slump", "worry",
    "underperform", "sell", "underweight", "poor", "disappointing",
    "slowdown", "contraction", "pressure", "debt", "fraud", "probe",
    "penalty", "default", "pledge", "ban", "suspend", "layoff", "bearish",
    "selloff", "plunge", "tumbles", "slides", "outflow", "scam", "delay",
    "rejected", "impairment", "writedown", "litigation",
}

_NSE_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.nseindia.com/",
}

# Hardcoded fallback for sector (never shows "N/A" for major stocks)
_SYMBOL_SECTOR: dict[str, str] = {
    "RELIANCE": "Oil & Gas / Refineries",
    "TCS": "Information Technology",
    "INFY": "Information Technology",
    "HDFCBANK": "Banking & Financial Services",
    "ICICIBANK": "Banking & Financial Services",
    "WIPRO": "Information Technology",
    "HCLTECH": "Information Technology",
    "SBIN": "Banking & Financial Services",
    "BAJFINANCE": "Financial Services / NBFC",
    "KOTAKBANK": "Banking & Financial Services",
    "AXISBANK": "Banking & Financial Services",
    "MARUTI": "Automobiles",
    "ADANIENT": "Infrastructure / Diversified",
    "ADANIPORTS": "Infrastructure / Ports",
    "TITAN": "Consumer Goods / Jewellery",
    "ULTRACEMCO": "Cement",
    "NESTLEIND": "FMCG",
    "LTIM": "Information Technology",
    "TECHM": "Information Technology",
    "SUNPHARMA": "Pharmaceuticals",
    "ASIANPAINT": "Paints / Consumer Goods",
    "BHARTIARTL": "Telecom",
    "ONGC": "Oil & Gas",
    "POWERGRID": "Power / Utilities",
    "NTPC": "Power / Utilities",
    "COALINDIA": "Mining / Energy",
    "JIOFIN": "Financial Services",
    "TMPV": "Automobiles",
    "TMCV": "Automobiles",
    "TATASTEEL": "Steel / Metals",
    "HINDALCO": "Metals & Mining",
    "JSWSTEEL": "Steel / Metals",
    "GRASIM": "Diversified / Chemicals",
    "BPCL": "Oil & Gas",
    "HEROMOTOCO": "Automobiles",
    "BAJAJ-AUTO": "Automobiles",
    "DIVISLAB": "Pharmaceuticals",
    "CIPLA": "Pharmaceuticals",
    "DRREDDY": "Pharmaceuticals",
    "EICHERMOT": "Automobiles",
    "SHRIRAMFIN": "Financial Services / NBFC",
    "UPL": "Agrochemicals",
    "BRITANNIA": "FMCG",
    "INDUSINDBK": "Banking & Financial Services",
    # Extended
    "LUPIN": "Pharmaceuticals",
    "ZOMATO": "Consumer Internet / Food Tech",
    "IRCTC": "Tourism / Railways",
    "HAVELLS": "Consumer Electricals",
    "PIDILITIND": "Adhesives / Specialty Chemicals",
    "APOLLOHOSP": "Healthcare / Hospitals",
    "SBILIFE": "Life Insurance",
    "HDFCLIFE": "Life Insurance",
    "BAJAJFINSV": "Financial Services / Diversified",
    "CHOLAFIN": "Financial Services / NBFC",
    "TRENT": "Retail",
    "DMART": "Retail / Supermarkets",
    "MUTHOOTFIN": "Financial Services / Gold NBFC",
    "BANDHANBNK": "Banking & Financial Services",
    "BIOCON": "Pharmaceuticals / Biotechnology",
    "AUROPHARMA": "Pharmaceuticals",
    "TORNTPHARM": "Pharmaceuticals",
    "GODREJCP": "FMCG",
    "POLYCAB": "Cables & Wiring",
    "DIXON": "Electronics Manufacturing",
    "VEDL": "Metals & Mining",
    "SAIL": "Steel",
    "NMDC": "Mining / Iron Ore",
    "RECLTD": "Power Finance / NBFC",
    "PFC": "Power Finance / NBFC",
    "IRFC": "Infrastructure Finance",
    "LICI": "Life Insurance",
    "ICICIPRULI": "Life Insurance",
    "ICICIGI": "General Insurance",
    "MAXHEALTH": "Healthcare / Hospitals",
    "FORTIS": "Healthcare / Hospitals",
    "PAGEIND": "Apparel / Textiles",
    "VBL": "Beverages / FMCG",
    "VOLTAS": "Consumer Appliances",
    "ABBOTINDIA": "Pharmaceuticals",
    "ZYDUSLIFE": "Pharmaceuticals",
    "ALKEM": "Pharmaceuticals",
    "MOTHERSON": "Auto Ancillaries",
    "UNOMINDA": "Auto Ancillaries",
    "ADANIENSOL": "Power",
    "GMRAIRPORT": "Infrastructure",
    "LTF": "Financial Services",
    "360ONE": "Financial Services",
    "SAMMAANCAP": "Financial Services",
    "ETERNAL": "Consumer",
    "CANBK": "Banking",
    "DALBHARAT": "Cement",
    "INDIGOPNTS": "Paints",
    "EIH": "Hotels",
    "LEMONTREE": "Hotels",
    "TATACOMM": "Telecom / IT Services",
    "TATAELXSI": "Information Technology",
    "TATACHEM": "Chemicals",
    "JUBLFOOD": "Quick Service Restaurants / Food",
    "STARHEALTH": "General Insurance",
    "NAUKRI": "Online Classifieds / Internet",
    "INDIGO": "Aviation",
    "HINDUNILVR": "FMCG",
    "IOC": "Oil & Gas",
    "HINDPETRO": "Oil & Gas",
    "CONCOR": "Logistics / Railways",
    "BHEL": "Capital Goods / Engineering",
    "ABB": "Capital Goods / Engineering",
    "SIEMENS": "Capital Goods / Engineering",
    "LT": "Infrastructure / Engineering",
    "LTTS": "Information Technology / Engineering",
    "PERSISTENT": "Information Technology",
    "COFORGE": "Information Technology",
    "MPHASIS": "Information Technology",
    "OFSS": "Information Technology / BFSI",
    "DELHIVERY": "Logistics",
    "PAYTM": "Fintech / Digital Payments",
    "NYKAA": "Beauty / E-commerce",
    "POLICYBZR": "Insurance Aggregator / Fintech",
    "ANGELONE": "Broking / Financial Services",
    "CANBK": "Banking & Financial Services",
    "BANKBARODA": "Banking & Financial Services",
    "PNB": "Banking & Financial Services",
    "UNIONBANK": "Banking & Financial Services",
    "FEDERALBNK": "Banking & Financial Services",
    "IDFCFIRSTB": "Banking & Financial Services",
    "AUBANK": "Banking & Financial Services",
    "EMAMILTD": "FMCG",
    "COLPAL": "FMCG",
    "MARICO": "FMCG",
    "DABUR": "FMCG",
}

# Hardcoded full company names (fallback when all APIs return nothing)
_SYMBOL_COMPANY: dict[str, str] = {
    "RELIANCE": "Reliance Industries Ltd",
    "TCS": "Tata Consultancy Services Ltd",
    "INFY": "Infosys Ltd",
    "HDFCBANK": "HDFC Bank Ltd",
    "ICICIBANK": "ICICI Bank Ltd",
    "WIPRO": "Wipro Ltd",
    "HCLTECH": "HCL Technologies Ltd",
    "SBIN": "State Bank of India",
    "BAJFINANCE": "Bajaj Finance Ltd",
    "KOTAKBANK": "Kotak Mahindra Bank Ltd",
    "AXISBANK": "Axis Bank Ltd",
    "MARUTI": "Maruti Suzuki India Ltd",
    "ADANIENT": "Adani Enterprises Ltd",
    "ADANIPORTS": "Adani Ports and SEZ Ltd",
    "TITAN": "Titan Company Ltd",
    "ULTRACEMCO": "UltraTech Cement Ltd",
    "NESTLEIND": "Nestle India Ltd",
    "LTIM": "LTIMindtree Ltd",
    "TECHM": "Tech Mahindra Ltd",
    "SUNPHARMA": "Sun Pharmaceutical Industries Ltd",
    "ASIANPAINT": "Asian Paints Ltd",
    "BHARTIARTL": "Bharti Airtel Ltd",
    "ONGC": "Oil and Natural Gas Corporation Ltd",
    "POWERGRID": "Power Grid Corporation of India Ltd",
    "NTPC": "NTPC Ltd",
    "COALINDIA": "Coal India Ltd",
    "JIOFIN": "Jio Financial Services Ltd",
    "TMPV": "Tata Motors Passenger Vehicles Ltd",
    "TMCV": "Tata Motors Ltd (Commercial Vehicles)",
    "TATASTEEL": "Tata Steel Ltd",
    "HINDALCO": "Hindalco Industries Ltd",
    "JSWSTEEL": "JSW Steel Ltd",
    "GRASIM": "Grasim Industries Ltd",
    "BPCL": "Bharat Petroleum Corporation Ltd",
    "HEROMOTOCO": "Hero MotoCorp Ltd",
    "BAJAJ-AUTO": "Bajaj Auto Ltd",
    "DIVISLAB": "Divi's Laboratories Ltd",
    "CIPLA": "Cipla Ltd",
    "DRREDDY": "Dr. Reddy's Laboratories Ltd",
    "EICHERMOT": "Eicher Motors Ltd",
    "SHRIRAMFIN": "Shriram Finance Ltd",
    "UPL": "UPL Ltd",
    "BRITANNIA": "Britannia Industries Ltd",
    "INDUSINDBK": "IndusInd Bank Ltd",
    # Extended
    "LUPIN": "Lupin Ltd",
    "ZOMATO": "Zomato Ltd",
    "IRCTC": "Indian Railway Catering and Tourism Corporation Ltd",
    "HAVELLS": "Havells India Ltd",
    "PIDILITIND": "Pidilite Industries Ltd",
    "APOLLOHOSP": "Apollo Hospitals Enterprise Ltd",
    "SBILIFE": "SBI Life Insurance Company Ltd",
    "HDFCLIFE": "HDFC Life Insurance Company Ltd",
    "BAJAJFINSV": "Bajaj Finserv Ltd",
    "CHOLAFIN": "Cholamandalam Investment and Finance Company Ltd",
    "TRENT": "Trent Ltd",
    "DMART": "Avenue Supermarts Ltd",
    "MUTHOOTFIN": "Muthoot Finance Ltd",
    "BANDHANBNK": "Bandhan Bank Ltd",
    "BIOCON": "Biocon Ltd",
    "AUROPHARMA": "Aurobindo Pharma Ltd",
    "TORNTPHARM": "Torrent Pharmaceuticals Ltd",
    "GODREJCP": "Godrej Consumer Products Ltd",
    "POLYCAB": "Polycab India Ltd",
    "DIXON": "Dixon Technologies (India) Ltd",
    "VEDL": "Vedanta Ltd",
    "SAIL": "Steel Authority of India Ltd",
    "NMDC": "NMDC Ltd",
    "RECLTD": "REC Ltd",
    "PFC": "Power Finance Corporation Ltd",
    "IRFC": "Indian Railway Finance Corporation Ltd",
    "LICI": "Life Insurance Corporation of India",
    "ICICIPRULI": "ICICI Prudential Life Insurance Company Ltd",
    "ICICIGI": "ICICI Lombard General Insurance Company Ltd",
    "MAXHEALTH": "Max Healthcare Institute Ltd",
    "FORTIS": "Fortis Healthcare Ltd",
    "PAGEIND": "Page Industries Ltd",
    "VBL": "Varun Beverages Ltd",
    "VOLTAS": "Voltas Ltd",
    "ABBOTINDIA": "Abbott India Ltd",
    "ZYDUSLIFE": "Zydus Lifesciences Ltd",
    "ALKEM": "Alkem Laboratories Ltd",
    "MOTHERSON": "Samvardhana Motherson International Ltd",
    "UNOMINDA": "UNO Minda Ltd",
    "ADANIENSOL": "Adani Energy Solutions Ltd",
    "GMRAIRPORT": "GMR Airports Ltd",
    "LTF": "L&T Finance Ltd",
    "360ONE": "360 ONE WAM Ltd",
    "SAMMAANCAP": "Sammaan Capital Ltd",
    "ETERNAL": "Eternal Ltd (Zomato)",
    "CANBK": "Canara Bank",
    "DALBHARAT": "Dalmia Bharat Ltd",
    "INDIGOPNTS": "Indigo Paints Ltd",
    "EIH": "EIH Ltd",
    "LEMONTREE": "Lemon Tree Hotels Ltd",
    "TATACOMM": "Tata Communications Ltd",
    "TATAELXSI": "Tata Elxsi Ltd",
    "TATACHEM": "Tata Chemicals Ltd",
    "JUBLFOOD": "Jubilant FoodWorks Ltd",
    "STARHEALTH": "Star Health and Allied Insurance Company Ltd",
    "NAUKRI": "Info Edge (India) Ltd",
    "INDIGO": "InterGlobe Aviation Ltd",
    "HINDUNILVR": "Hindustan Unilever Ltd",
    "IOC": "Indian Oil Corporation Ltd",
    "HINDPETRO": "Hindustan Petroleum Corporation Ltd",
    "CONCOR": "Container Corporation of India Ltd",
    "BHEL": "Bharat Heavy Electricals Ltd",
    "ABB": "ABB India Ltd",
    "SIEMENS": "Siemens Ltd",
    "LT": "Larsen & Toubro Ltd",
    "LTTS": "L&T Technology Services Ltd",
    "PERSISTENT": "Persistent Systems Ltd",
    "COFORGE": "Coforge Ltd",
    "MPHASIS": "Mphasis Ltd",
    "OFSS": "Oracle Financial Services Software Ltd",
    "DELHIVERY": "Delhivery Ltd",
    "PAYTM": "One 97 Communications Ltd",
    "NYKAA": "FSN E-Commerce Ventures Ltd",
    "POLICYBZR": "PB Fintech Ltd",
    "ANGELONE": "Angel One Ltd",
    "CANBK": "Canara Bank",
    "BANKBARODA": "Bank of Baroda",
    "PNB": "Punjab National Bank",
    "UNIONBANK": "Union Bank of India",
    "FEDERALBNK": "The Federal Bank Ltd",
    "IDFCFIRSTB": "IDFC First Bank Ltd",
    "AUBANK": "AU Small Finance Bank Ltd",
    "EMAMILTD": "Emami Ltd",
    "COLPAL": "Colgate-Palmolive (India) Ltd",
    "MARICO": "Marico Ltd",
    "DABUR": "Dabur India Ltd",
}


def normalize_hinglish(query: str) -> str:
    """
    Normalize Hinglish (Hindi-English mix) queries to English for NLP processing.
    Preserves stock symbols and financial terms.
    Examples:
    • "RELIANCE ka P/E kya hai?" → "RELIANCE of P/E what is?"
    • "₹1500 par buy karna chahiye?" → "₹1500 at buy should?"
    • "NIFTY me RELIANCE best hai?" → "NIFTY in RELIANCE best is?"
    """
    q = query

    # Hindi question words (placed at start for clarity)
    hinglish_words = {
        # Question words
        r'\bkya\b': 'what',
        r'\bkaun\b': 'who',
        r'\bkaise\b': 'how',
        r'\bkyun\b': 'why',
        r'\bkab\b': 'when',
        r'\bkahan\b': 'where',
        # Common postpositions / suffixes
        r'\bka\b': 'of',  # possession
        r'\bke\b': 'of',
        r'\bki\b': 'of',
        r'\bpar\b': 'at',  # location/price
        r'\bpe\b': 'at',
        r'\bme\b': 'in',  # location
        r'\bko\b': 'to',  # object marker
        r'\bse\b': 'from',  # ablative
        r'\btak\b': 'till',
        # Common verbs
        r'\bhona\b': 'is',
        r'\bhai\b': 'is',
        r'\bhain\b': 'are',
        r'\btha\b': 'was',
        r'\bthe\b': 'were',
        r'\bchahunga\b': 'want',
        r'\bchaiye\b': 'should',
        r'\bchahiye\b': 'should',
        r'\bsakte\b': 'can',
        r'\bsakte\s+ho\b': 'can',
        r'\bbata\b': 'tell',
        r'\bdo\b': 'give',
        r'\blena\b': 'take',
        r'\bkarna\b': 'do',
        # Interjections / common words
        r'\bji\b': '',  # respectful marker, remove
        r'\bna\b': 'not',  # negation
        r'\bnahi\b': 'no',
        r'\baur\b': 'and',
        r'\bya\b': 'or',
        r'\bto\b': 'then',
    }

    # Apply case-insensitive replacements
    for hindi, english in hinglish_words.items():
        q = re.sub(hindi, english, q, flags=re.IGNORECASE)

    # Clean up extra whitespace
    q = re.sub(r'\s+', ' ', q).strip()
    return q


def extract_all_symbols_from_query(query: str) -> list[str]:
    """
    Extract ALL symbols/stock names from query (not just first one).
    Returns list of symbols, empty list if none found.
    Used for multi-stock comparisons: "RELIANCE vs TCS vs INFY" → ["RELIANCE", "TCS", "INFY"]
    """
    q_lower = query.lower()
    symbols = []

    # Step 1: Extract via name lookups (most reliable — runs on longest matches first)
    for name, sym in sorted(_NAME_TO_SYMBOL.items(), key=lambda x: -len(x[0])):
        resolved = _resolve_listed_symbol(sym)
        if name in q_lower and resolved not in symbols:
            symbols.append(resolved)

    # Step 2: Extract via regex (uppercase tokens not in skip list)
    _SKIP = {
        "A", "AN", "BY", "TO", "OF", "ON", "AS", "IS", "IN", "AT", "OR", "AND", "THE", "FOR",
        "HOW", "WHY", "WHAT", "WHEN", "WHERE", "WHICH", "WHO",
        "IT", "ITS", "THIS", "THAT", "THEY", "THEM",
        "BUY", "SELL", "GET", "CAN", "WILL", "HAS", "HIT", "ARE", "WAS", "GIVE", "SHOW", "TELL", "LIST", "FIND",
        "UP", "DOWN", "NOW", "NEW", "TOP", "BEST", "HIGH", "LOW", "BIG", "MID", "MOST", "LESS", "MORE", "ALL", "ANY",
        "STOCK", "STOCKS", "SHARE", "SHARES", "MARKET", "SECTOR", "SECTORS", "BANK", "BANKS", "FUND", "FUNDS",
        "INDEX", "NIFTY", "NSE", "BSE", "SENSEX", "IPO", "FII", "DII", "NIFTY50", "NIFTY100",
        "MOMENTUM", "SWING", "TRADING", "TRADE", "TRADER", "INTRADAY", "SCALP", "SCALPING", "BREAKOUT", "PULLBACK",
        "RALLY", "TREND", "TRENDING", "BULLISH", "BEARISH", "NEUTRAL", "TECHNICAL", "ANALYSIS", "SCREENER",
        "GROWTH", "INCOME", "DIVIDEND", "DIVIDENDS", "YIELD", "VALUE", "QUALITY", "RETURNS", "PROFIT", "LOSS",
        "SUPPORT", "RESIST", "VOLATILE", "VOLATILITY", "OVERVALUE", "UNDERVALUE",
        "RSI", "MACD", "SMA", "EMA", "ATR", "ADX", "VWAP", "OBV",
        "PE", "PB", "PEG", "EPS", "ROE", "ROCE", "CAGR", "BETA",
        "STOCH", "BBANDS", "BOLLINGER", "BANDS", "BAND", "BANDWIDTH",
        "UPPER", "LOWER", "MIDDLE", "INDICATOR", "INDICATORS",
        "OVERBOUGHT", "OVERSOLD", "STOP", "STOPS", "LEVEL", "LEVELS",
        "SENTIMENT", "NEWS", "MOOD", "INVESTOR", "HEADLINE", "HEADLINES", "FACTOR",
        "CAS", "TIMINGS", "HOURS", "SESSION", "AUCTION", "CLOSING", "PREOPEN",
        "SMALL", "LARGE", "MIDCAP", "LARGECAP", "SMALLCAP", "CAP", "MICRO", "NANO",
        "GOOD", "SAFE", "RISK", "RISKY", "INDIA", "INDIAN", "OPTION", "OPTIONS", "FUTURE", "FUTURES",
        "PORTFOLIO", "DIVERSIFY", "ANALYZE", "ANALYSE", "COMPARE", "PREDICT", "FORECAST", "ACCUMULATE", "PARK",
        "REBALANCE", "HOLD", "WATCH", "EXIT", "ENTER", "INVEST", "RECOMMEND", "NEAR", "STRONG", "WEAK", "QUICK",
        "FAIR", "FAIRLY", "BLUE", "CHIP", "CHIPS", "ENTRY", "SETUP", "WITH", "FROM", "ABOUT", "AFTER", "BEFORE",
        "OUTLOOK", "IMPACT", "EFFECT", "POINT", "POINTS", "MONEY", "GAINS", "PAYING", "QUARTER", "QUARTERLY",
        "ANNUAL", "MONTHLY", "PEERS", "YEAR", "MONTH", "WEEK", "TERM", "LONG", "SHORT", "NEXT", "LAST",
        "TODAY", "DAILY", "WEEKLY", "MONTHLY", "VS", "VERSUS",
    }
    tokens = re.findall(r'\b[A-Z][A-Z0-9\-]{1,9}\b', query.upper())
    for tok in tokens:
        if tok not in _SKIP and len(tok) >= 3:
            resolved = _resolve_listed_symbol(tok)
            if resolved not in symbols:
                symbols.append(resolved)

    return symbols


def extract_entities_from_query(query: str) -> dict:
    """
    Extract financial entities: price targets, time horizons, quantities.
    Returns dict with extracted parameters for contextualizing response.
    """
    entities = {}
    q_lower = query.lower()

    # Price levels (₹XXXX, reach 1500, above 2000, target 3500)
    price_patterns = [
        (r'₹\s*([\d,]+(?:\.\d+)?)', 'price_inr'),
        (r'\$\s*([\d,]+(?:\.\d+)?)', 'price_usd'),
        (r'reach\s+([\d,]+(?:\.\d+)?)', 'price_target'),
        (r'target\s+([\d,]+(?:\.\d+)?)', 'price_target'),
        (r'(above|below)\s+([\d,]+(?:\.\d+)?)', 'price_level'),
        (r'at\s+([\d,]+(?:\.\d+)?)', 'price_level'),
    ]
    for pattern, key in price_patterns:
        match = re.search(pattern, q_lower)
        if match:
            price_str = match.group(1 if 'price_target' not in pattern else 1).replace(',', '')
            try:
                entities[key] = float(price_str)
                break  # Use first found price
            except ValueError:
                pass

    # Time horizons (3 months, next quarter, 1 year, by year-end)
    time_patterns = [
        (r'(\d+)\s*(month|week|day|year)', 'duration'),
        (r'next\s+(quarter|month|week|year)', 'next_period'),
        (r'(q[1-4]|quarter\s*[1-4]|h[1-2])', 'fiscal_period'),
        (r'year.?end', 'by_year_end'),
    ]
    for pattern, key in time_patterns:
        match = re.search(pattern, q_lower)
        if match:
            entities[key] = match.group(0)
            break

    # Quantity/percentage (10% gain, doubled, 2x return)
    quantity_patterns = [
        (r'(\d+)\s*%\s*(gain|return|rise|fall|drop)', 'percentage_target'),
        (r'(\d+)x\s*(return|gain|multiple)', 'multiple_target'),
        (r'double[d]?', 'multiple_target'),
    ]
    for pattern, key in quantity_patterns:
        match = re.search(pattern, q_lower)
        if match:
            entities[key] = match.group(0)
            break

    return entities


def extract_time_window_from_query(query: str) -> Optional[dict]:
    """
    Extract temporal window from query: '6 months ago' → {'period': '6m', 'lookback_days': 180}
    Returns None if no time reference found.
    Supported periods: 1d/5d/1mo/3mo/6mo/1y/2y/5y/10y/max
    """
    q_lower = query.lower()

    # Map time patterns to yfinance periods and lookback days
    time_patterns = [
        (r'(\d+)\s+days?\s+ago', lambda m: {
            'period': f"{int(m.group(1))}d" if int(m.group(1)) <= 60 else '3mo',
            'lookback_days': int(m.group(1))
        }),
        (r'(\d+)\s+weeks?\s+ago', lambda m: {
            'period': '1mo' if int(m.group(1)) <= 4 else '3mo' if int(m.group(1)) <= 13 else '1y',
            'lookback_days': int(m.group(1)) * 7
        }),
        (r'(\d+)\s+months?\s+ago', lambda m: {
            'period': f"{int(m.group(1))}mo" if int(m.group(1)) <= 6 else 'max',
            'lookback_days': int(m.group(1)) * 30
        }),
        (r'(\d+)\s+years?\s+ago', lambda m: {
            'period': f"{int(m.group(1))}y" if int(m.group(1)) <= 10 else 'max',
            'lookback_days': int(m.group(1)) * 365
        }),
        (r'last\s+(week|month|quarter|year)', lambda m: {
            'week': {'period': '5d', 'lookback_days': 7},
            'month': {'period': '1mo', 'lookback_days': 30},
            'quarter': {'period': '3mo', 'lookback_days': 90},
            'year': {'period': '1y', 'lookback_days': 365},
        }.get(m.group(1), {'period': '1mo', 'lookback_days': 30})),
        (r'past\s+(week|month|quarter|year)', lambda m: {
            'week': {'period': '5d', 'lookback_days': 7},
            'month': {'period': '1mo', 'lookback_days': 30},
            'quarter': {'period': '3mo', 'lookback_days': 90},
            'year': {'period': '1y', 'lookback_days': 365},
        }.get(m.group(1), {'period': '1mo', 'lookback_days': 30})),
        (r'in\s+the\s+past\s+(\d+)\s+(days?|weeks?|months?|years?)', lambda m: {
            'day': {'period': f"{int(m.group(1))}d", 'lookback_days': int(m.group(1))},
            'days': {'period': f"{int(m.group(1))}d", 'lookback_days': int(m.group(1))},
            'week': {'period': f"{int(m.group(1)) * 7}d", 'lookback_days': int(m.group(1)) * 7},
            'weeks': {'period': f"{int(m.group(1)) * 7}d", 'lookback_days': int(m.group(1)) * 7},
            'month': {'period': f"{int(m.group(1))}mo", 'lookback_days': int(m.group(1)) * 30},
            'months': {'period': f"{int(m.group(1))}mo", 'lookback_days': int(m.group(1)) * 30},
            'year': {'period': f"{int(m.group(1))}y", 'lookback_days': int(m.group(1)) * 365},
            'years': {'period': f"{int(m.group(1))}y", 'lookback_days': int(m.group(1)) * 365},
        }.get(m.group(2), {'period': '1mo', 'lookback_days': 30})),
    ]

    for pattern, handler in time_patterns:
        match = re.search(pattern, q_lower, re.IGNORECASE)
        if match:
            try:
                return handler(match)
            except Exception as e:
                logger.debug(f"Error parsing time window: {e}")
                continue

    return None


def link_news_to_price_moves(symbol: str, headlines: Optional[List[Dict]] = None, pct_change: float = 0) -> Optional[Dict]:
    """
    Link recent news events to price movements.
    Returns: {
        "likely_catalyst": headline text,
        "event_type": "earnings" | "dividend" | "regulation" | "acquisition" | "news",
        "sentiment": "positive" | "negative" | "neutral",
        "confidence": "high" | "medium" | "low"
    }
    """
    if not headlines or abs(pct_change) < 1.5:  # Ignore small moves
        return None

    # Event type keywords
    event_keywords = {
        "earnings": ["earn", "result", "q1", "q2", "q3", "q4", "quarterly", "fy", "ebitda", "profit"],
        "dividend": ["dividend", "bonus", "split", "rights", "distribution", "payout"],
        "regulation": ["sebi", "rbi", "regulation", "penalty", "ban", "fine", "court", "legal"],
        "acquisition": ["acqui", "merger", "deal", "stake", "buyout", "joint venture"],
        "management": ["ceo", "md", "chairman", "board", "resign", "appoint", "leadership"],
    }

    best_match = None
    best_confidence = 0

    for headline_obj in headlines[:5]:  # Check first 5 headlines
        if not headline_obj:
            continue

        title = headline_obj.get("title", "").lower()
        source = headline_obj.get("source", "").lower()

        # Keyword matching for event type
        for event_type, keywords in event_keywords.items():
            keyword_matches = sum(1 for kw in keywords if kw in title)
            if keyword_matches > 0:
                # Confidence calculation
                base_confidence = min(100, 30 + keyword_matches * 20)

                # Boost confidence if move is large (>3%)
                if abs(pct_change) > 3:
                    base_confidence = min(100, base_confidence + 20)

                # Check sentiment from headline keywords
                positive_words = ["bullish", "gain", "beat", "upgrade", "partnership", "growth", "record", "profit"]
                negative_words = ["bearish", "loss", "miss", "downgrade", "warning", "decline", "fraud", "weak"]

                sentiment = "neutral"
                if any(w in title for w in positive_words):
                    sentiment = "positive"
                elif any(w in title for w in negative_words):
                    sentiment = "negative"

                # Return if this is the best match so far
                if base_confidence > best_confidence:
                    best_confidence = base_confidence
                    best_match = {
                        "likely_catalyst": headline_obj.get("title", ""),
                        "event_type": event_type,
                        "sentiment": sentiment,
                        "confidence": "high" if base_confidence >= 70 else "medium" if base_confidence >= 50 else "low",
                        "source": source,
                    }

    return best_match


def screen_stocks(criteria: Optional[Dict] = None) -> List[Dict]:
    """
    Screen stocks by sector and financial criteria.
    criteria = {
        "sector": "IT" | "Banking" | "Pharma" | etc.,
        "pe_max": 25,          # P/E ratio upper limit
        "dividend_min": 2,     # Minimum dividend yield %
        "market_cap": "large" | "mid" | "small"  # Market cap category
    }
    Returns: List of matching stocks with quote data (top 10, sorted by P/E)
    """
    if not criteria:
        criteria = {}

    # Sector keywords mapping to stock symbols (common Indian stocks)
    SECTOR_STOCKS = {
        "IT": ["TCS", "INFY", "WIPRO", "HCLTECH", "TECHM", "MPHASIS", "LTTS"],
        "BANKING": ["SBIN", "ICICIBANK", "HDFCBANK", "AXISBANK", "KOTAKBANK", "INDUSINDBK"],
        "PHARMA": ["SUNPHARMA", "CIPLA", "LUPIN", "DIVISLAB", "BIOCON", "DRREDDY"],
        "AUTO": ["MARUTI", "TMPV", "TMCV", "M&M", "BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT"],
        "ENERGY": ["RELIANCE", "NTPC", "POWERGRID", "ONGC", "IOC", "BPCL"],
        "INFRA": ["LT", "ADANIPORTS", "ADANIENT", "IRCON", "RVNL"],
        "FMCG": ["ITC", "HINDUNILVR", "NESTLEIND", "BRITANNIA", "DABUR", "MARICO"],
        "CEMENT": ["ULTRACEMCO", "SHREECEM", "AMBUJACEM", "ACC", "DALMIACEM"],
        "METAL": ["TATASTEEL", "HINDALCO", "JSWSTEEL", "VEDL", "SAIL"],
        "DEFENCE": ["HAL", "BEL", "BDL", "MAZDOCK", "COCHINSHIP", "GRSE", "DATAPATTNS"],
        "DEFENSE": ["HAL", "BEL", "BDL", "MAZDOCK", "COCHINSHIP", "GRSE", "DATAPATTNS"],
        "PSU": ["SBIN", "NTPC", "ONGC", "BPCL", "IOC", "COALINDIA", "BEL", "HAL"],
        "REALTY": ["DLF", "GODREJPROP", "OBEROIRLTY", "PRESTIGE", "LODHA"],
        "RAILWAY": ["IRCTC", "IRFC", "RVNL", "IRCON", "RAILTEL"],
    }

    sector = criteria.get("sector", "").upper()
    pe_max = criteria.get("pe_max")
    dividend_min = criteria.get("dividend_min")

    # Get stocks for sector
    sector_key = None
    for key in SECTOR_STOCKS:
        if sector in key or key in sector:
            sector_key = key
            break

    stocks_to_check = SECTOR_STOCKS.get(sector_key, []) if sector_key else []

    if not stocks_to_check:
        return []

    # Fetch quotes for sector stocks
    try:
        from ..market_data import fetch_quotes
        quotes = fetch_quotes(stocks_to_check)
    except Exception as e:
        logger.debug(f"Could not fetch quotes: {e}")
        return []

    # Apply filters
    filtered = []
    for quote in quotes:
        if not quote:
            continue

        # P/E filter
        if pe_max and quote.get("pe", 999) > pe_max:
            continue

        # Dividend filter
        if dividend_min and quote.get("dividendYield", 0) < dividend_min:
            continue

        filtered.append(quote)

    # Sort by P/E (ascending) and return top 10
    filtered.sort(key=lambda x: x.get("pe", 999))
    return filtered[:10]


def extract_symbol_from_query(query: str) -> Optional[str]:
    # Strip Android prompt wrapper first so tokens like CONTEXT / WALLET aren't tickers.
    raw = (query or "").strip()
    if raw.lower().startswith("user_query:"):
        raw = raw.split(":", 1)[1].strip()
    if " | context:" in raw:
        raw = raw.split(" | context:", 1)[0].strip()
    query = raw
    q_lower = query.lower()

    # Step 1: reject general market / list queries — no specific stock being asked
    _GENERAL_PATTERNS = [
        r'\btop\b.{0,30}\bstocks?\b',
        r'\bbest\b.{0,30}\bstocks?\b',
        r'\bwhich\b.{0,30}\bstocks?\b',
        r'\blist\b.{0,30}\bstocks?\b',
        r'\bstocks?\b.{0,20}\bto\s+(buy|sell|watch|invest|trade|avoid|accumulate)\b',
        r'\bstocks?\b.{0,25}\bfor\s+(swing|day|long|short|momentum|growth|value|intraday|quick|medium)\b',
        r'\bstocks?\b.{0,20}\s*(near|around|below|above|under|paying|close)\b',
        r'\bundervalued\b.{0,30}\bstocks?\b',
        r'\bovervalued\b.{0,30}\bstocks?\b',
        r'\bgood\b.{0,20}\bstocks?\b',
        r'\bsafest?\b.{0,20}\bstocks?\b',
        r'\bdividend\b.{0,20}\bstocks?\b',
        r'\bblue.?chip\b',
        r'\b(optimize|rebalance|diversify)\b.{0,30}\bportfolio\b',
        r'\bmy\s+(portfolio|holdings?)\b',
        r'\bwhich\b.{0,30}\bholdings?\b',
        r'\bbest\b.{0,20}\bentry\b',
        r'\bentry\s+(point|price|level)s?\b',
        r'\b(bank|pharma|auto|energy|fmcg|defence|defense|infra|it|psu|realty|railway)\s+stocks?\b',
        r'\b(defence|defense|psu|realty)\b',
        r'\baccumulate\b',
        r'\bbreakout\b.{0,20}\bstocks?\b',
        r'\bstocks?\b.{0,20}\bbreakout\b',
        r'\bstocks?\b.{0,20}\bsetup\b',
        r'\brecommend\b',
        r'\bsuggestion[s]?\b',
        r'\bwhat\s+(are|is)\s+.{0,20}\bstocks?\b',
        r'\bhow\s+to\s+(pick|choose|select|find)\b',
        r'\bmarket\s+outlook\b',
        r'\bnifty\s+outlook\b',
    ]
    for pattern in _GENERAL_PATTERNS:
        if re.search(pattern, q_lower):
            return None

    # Definitional indicator/term asks are not stock lookups ("What is RSI?").
    if re.search(
        r"\b(what is|what are|define|definition|meaning of|explain|formula|equation)\b",
        q_lower,
    ) and re.search(
        r"\b(rsi|macd|sma|ema|atr|pe|p/e|pb|peg|eps|roe|vwap|bollinger|stochastic|cagr|beta|"
        r"cas|closing auction|market timings|market hours|trading hours)\b",
        q_lower,
    ) and not re.search(r"\b(of|for|on)\s+[a-z]{2,15}\b", q_lower):
        return None

    # Step 2: name-based lookup — most reliable for natural language
    for name, sym in sorted(_NAME_TO_SYMBOL.items(), key=lambda x: -len(x[0])):
        if name in q_lower:
            return _resolve_listed_symbol(sym)

    # Step 2b: BSE scrip codes / explicit exchange prefixes
    bse_pref = re.search(r"\bBSE:([A-Za-z0-9]{2,15})\b", query, flags=re.IGNORECASE)
    if bse_pref:
        return bse_pref.group(1).upper()
    nse_pref = re.search(r"\bNSE:([A-Za-z0-9]{2,15})\b", query, flags=re.IGNORECASE)
    if nse_pref:
        return _resolve_listed_symbol(nse_pref.group(1).upper())
    code_match = re.search(r"\b(\d{6})\b", query)
    if code_match:
        return code_match.group(1)

    # Step 3: regex for explicit uppercase tickers (e.g. "Is LUPIN a buy?")
    _SKIP = {
        # articles / prepositions / conjunctions
        "A", "AN", "BY", "TO", "OF", "ON", "AS",
        "IS", "IN", "AT", "OR", "AND", "THE", "FOR",
        # question words
        "HOW", "WHY", "WHAT", "WHEN", "WHERE", "WHICH", "WHO",
        # pronouns
        "IT", "ITS", "THIS", "THAT", "THEY", "THEM",
        # common verbs
        "BUY", "SELL", "GET", "CAN", "WILL", "HAS", "HIT", "ARE", "WAS",
        "GIVE", "SHOW", "TELL", "LIST", "FIND", "SHOULD", "WANT",
        # directional / status
        "UP", "DOWN", "NOW", "NEW", "TOP", "BEST", "HIGH", "LOW", "BIG",
        "MID", "MOST", "LESS", "MORE", "ALL", "ANY",
        # market / finance terms that look like tickers
        "STOCK", "STOCKS", "SHARE", "SHARES", "MARKET", "SECTOR", "SECTORS",
        "BANK", "BANKS", "FUND", "FUNDS", "INDEX", "NIFTY", "NSE", "BSE",
        "SENSEX", "IPO", "FII", "DII", "NIFTY50", "NIFTY100",
        # trading / strategy terms
        "MOMENTUM", "SWING", "TRADING", "TRADE", "TRADER", "INTRADAY",
        "SCALP", "SCALPING", "BREAKOUT", "PULLBACK", "REBOUND",
        "RALLY", "RALLY", "TREND", "TRENDING",
        "BULLISH", "BEARISH", "NEUTRAL",
        "TECHNICAL", "ANALYSIS", "SCREENER",
        # financial metrics / concepts / indicators (never tickers)
        "GROWTH", "INCOME", "DIVIDEND", "DIVIDENDS", "YIELD",
        "VALUE", "QUALITY", "RETURNS", "PROFIT", "LOSS",
        "SUPPORT", "RESIST", "VOLATILE", "VOLATILITY",
        "OVERVALUE", "UNDERVALUE",
        "RSI", "MACD", "SMA", "EMA", "ATR", "ADX", "VWAP", "OBV",
        "PE", "PB", "PEG", "EPS", "ROE", "ROCE", "CAGR", "BETA",
        "STOCH", "BBANDS", "BOLLINGER", "BANDS", "BAND", "BANDWIDTH",
        "UPPER", "LOWER", "MIDDLE", "INDICATOR", "INDICATORS",
        "OVERBOUGHT", "OVERSOLD", "FORMULA", "EQUATION", "MEANING",
        "EXPLAIN", "DEFINE", "DEFINITION",
        "STOP", "STOPS", "LEVEL", "LEVELS", "PIVOT", "PIVOTS",
        "SENTIMENT", "NEWS", "MOOD", "INVESTOR", "HEADLINE", "HEADLINES", "FACTOR",
        # session / literacy tokens mistaken for tickers
        "CAS", "TIMINGS", "HOURS", "SESSION", "AUCTION", "CLOSING", "PREOPEN",
        # size / category
        "SMALL", "LARGE", "MIDCAP", "LARGECAP", "SMALLCAP", "CAP",
        "MICRO", "NANO",
        # descriptive / general
        "GOOD", "SAFE", "RISK", "RISKY", "INDIA", "INDIAN",
        "OPTION", "OPTIONS", "FUTURE", "FUTURES",
        "PORTFOLIO", "DIVERSIFY",
        # common verbs used in prompts
        "ANALYZE", "ANALYSE", "COMPARE", "PREDICT", "FORECAST",
        "ACCUMULATE", "PARK", "REBALANCE", "HOLD", "WATCH", "EXIT",
        "ENTER", "INVEST", "RECOMMEND",
        # adjectives / descriptors
        "NEAR", "STRONG", "WEAK", "QUICK", "FAIR", "FAIRLY",
        "BLUE", "CHIP", "CHIPS", "ENTRY", "SETUP", "WITH", "FROM",
        "ABOUT", "AFTER", "BEFORE",
        # non-stock nouns / prompt filler mistaken for tickers
        "OUTLOOK", "IMPACT", "EFFECT", "POINT", "POINTS",
        "MONEY", "GAINS", "PAYING", "QUARTER", "QUARTERLY",
        "ANNUAL", "MONTHLY", "PEERS", "SECTOR",
        "FULL", "MATH", "COMPLETE", "DETAILED", "QUANT", "QUANTITATIVE",
        "STACK", "PLAN", "VIEW", "ZONE", "BIAS", "SCORE", "CHECK",
        # Android prompt-wrapper tokens
        "CONTEXT", "WALLET", "HOLDINGS", "HISTORY", "SYMBOL", "USER",
        "QUERY", "PORTFOLIOSCORE", "PCTCHANGE", "MARKETCAP",
        # time
        "YEAR", "MONTH", "WEEK", "TERM", "LONG", "SHORT",
        "NEXT", "LAST", "TODAY", "DAILY", "WEEKLY", "MONTHLY",
        # sector themes / common nouns that look like tickers
        "DEFENCE", "DEFENSE", "PHARMA", "FMCG", "INFRA", "REALTY",
        "SETTLEMENT", "CIRCUIT", "LIMIT", "LIMITS", "RATIO", "RATIOS",
        "UNDER", "NIFTY", "INDEX",
        # Retail literacy tokens often mistaken for tickers
        "OPEN", "CLOSE", "MARGIN", "LOT", "LOTS", "DEMAT", "ACCOUNT", "ACCOUNTS",
        "PLEDGE", "BROKERAGE", "CHARGES", "GTT", "ASBA", "ALLOTMENT",
        "CNC", "MIS", "NRML", "BONUS", "SPLIT", "RIGHTS", "AUCTION",
        "STCG", "LTCG", "TAX", "TAXES", "SIP", "NAVS", "PROTECTION",
        "ORDER", "ORDERS", "TRIGGER", "BLOCKED", "KYC", "PAN",
    }
    q_upper = query.upper().strip()
    # Prefer "… of/for/on TICKER" so "full math for KAYNES" resolves KAYNES, not FULL.
    of_for = re.search(
        r"\b(?:OF|FOR|ON)\s+([A-Z][A-Z0-9\-]{1,14})\b",
        q_upper,
    )
    if of_for:
        cand = of_for.group(1)
        if cand not in _SKIP and len(cand) >= 2:
            return _resolve_listed_symbol(cand)
    tokens = re.findall(r'\b[A-Z][A-Z0-9\-]{1,9}\b', q_upper)
    for tok in tokens:
        if tok not in _SKIP and len(tok) >= 3:
            return _resolve_listed_symbol(tok)

    # Step 4: fuzzy matching fallback for typos (e.g., "RELIANGE" → "RELIANCE")
    from difflib import get_close_matches
    extracted_tokens = re.findall(r'\b[A-Za-z0-9\-]{3,10}\b', query.upper())
    if extracted_tokens:
        for token in extracted_tokens:
            if token not in _SKIP:
                candidates = get_close_matches(token, list(_NAME_TO_SYMBOL.values()), n=1, cutoff=0.75)
                if candidates:
                    return _resolve_listed_symbol(candidates[0])

    return None



def _fmt_inr_cr(val) -> Optional[str]:
    try:
        v = float(val)
        cr = v / 1e7
        if cr >= 1_00_000:
            return f"₹{cr/1_00_000:.1f} lakh crore"
        if cr >= 1_000:
            return f"₹{cr:,.0f} crore"
        return f"₹{cr:.1f} crore"
    except (TypeError, ValueError):
        return None


def _rolling_mean(arr, n):
    if len(arr) < n:
        return None
    return sum(arr[-n:]) / n


def _rolling_std(arr, n):
    if len(arr) < n:
        return None
    mean = _rolling_mean(arr, n)
    variance = sum((x - mean) ** 2 for x in arr[-n:]) / n
    return variance ** 0.5


def _calc_rsi(closes, period=14):
    if len(closes) < period + 1:
        return None
    gains, losses = [], []
    for i in range(1, len(closes)):
        diff = closes[i] - closes[i - 1]
        gains.append(max(diff, 0))
        losses.append(max(-diff, 0))
    avg_gain = sum(gains[-period:]) / period
    avg_loss = sum(losses[-period:]) / period
    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    return round(100 - (100 / (1 + rs)), 1)


def _news_sentiment(headlines: list[str]) -> dict:
    """Keyword sentiment over headlines (shared lexicon with ISM sentiment pack)."""
    try:
        from indian_stock_llm.analysis_math import score_news_headlines

        scored = score_news_headlines(list(headlines or []))
        if scored.get("ok"):
            return {
                "overall": scored.get("overall"),
                "breakdown": scored.get("breakdown"),
                "score": scored.get("score"),
                "tagged": scored.get("tagged"),
            }
    except Exception:
        pass
    pos, neg, neu = 0, 0, 0
    for h in headlines:
        words = set(re.findall(r"[a-z0-9]+", (h or "").lower()))
        p = len(words & _POS_WORDS)
        n = len(words & _NEG_WORDS)
        if p > n:
            pos += 1
        elif n > p:
            neg += 1
        else:
            neu += 1
    total = pos + neg + neu or 1
    if pos > neg:
        overall = "Positive"
    elif neg > pos:
        overall = "Negative"
    else:
        overall = "Neutral"
    return {
        "overall": overall,
        "breakdown": f"Positive {pos*100//total}% / Neutral {neu*100//total}% / Negative {neg*100//total}%",
        "score": round((pos - neg) / total, 3),
    }


# ---------------------------------------------------------------------------
# Source 1: NSE India API — P/E, sector, company name, 52-week, live price
# ---------------------------------------------------------------------------
def _fetch_nse_fundamentals(symbol: str) -> dict:
    """
    NSE India official API. Returns P/E, sector P/E avg, company name,
    industry/sector, 52-week high/low, and live price. No API key needed.
    """
    try:
        import requests
        session = requests.Session()
        session.headers.update(_NSE_HEADERS)
        # Step 1: visit home page to get cookies (NSE checks for session)
        session.get("https://www.nseindia.com/", timeout=8)
        resp = session.get(
            f"https://www.nseindia.com/api/quote-equity?symbol={symbol}",
            timeout=8,
        )
        if resp.status_code != 200:
            logger.warning("NSE API returned %s for %s", resp.status_code, symbol)
            return {}
        d = resp.json()
        meta = d.get("metadata", {})
        price_info = d.get("priceInfo", {})
        info = d.get("info", {})
        whl = price_info.get("weekHighLow", {})
        pe_val = meta.get("pdSymbolPe")
        pe_sector_val = meta.get("pdSectorPe")
        # Delivery % lives under trade_info (separate call; best-effort).
        delivery_pct = None
        try:
            trade_resp = session.get(
                f"https://www.nseindia.com/api/quote-equity?symbol={symbol}&section=trade_info",
                timeout=8,
            )
            if trade_resp.status_code == 200:
                td = trade_resp.json() or {}
                sw = td.get("securityWiseDP") or {}
                raw_del = sw.get("deliveryToTradedQuantity")
                if raw_del is not None:
                    delivery_pct = float(raw_del)
                    if 0 < delivery_pct <= 1.5:
                        delivery_pct *= 100.0
        except Exception:
            delivery_pct = None

        return {
            "company_name": info.get("companyName"),
            "sector": info.get("industry") or meta.get("industry"),
            "price": price_info.get("lastPrice") or price_info.get("close"),
            "pe": float(pe_val) if pe_val else None,
            "pe_sector": float(pe_sector_val) if pe_sector_val else None,
            "week52_high": float(whl["max"]) if whl.get("max") else None,
            "week52_low": float(whl["min"]) if whl.get("min") else None,
            "delivery_pct": delivery_pct,
        }
    except Exception as e:
        logger.warning("NSE fetch failed for %s: %s", symbol, e)
        return {}


# ---------------------------------------------------------------------------
# Source 1b: Yahoo Finance direct API — P/E, sector, dividend (cloud-reliable)
# ---------------------------------------------------------------------------
_YF_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json",
    "Accept-Language": "en-US,en;q=0.9",
}


def _resolve_listed_symbol(symbol: str) -> str:
    """Map retired/renamed NSE tickers to the current listed symbol."""
    try:
        from app.market_data import normalize_listed_symbol

        return normalize_listed_symbol(symbol)
    except Exception:
        aliases = {"TATAMOTORS": "TMPV"}
        sym = (symbol or "").strip().upper()
        return aliases.get(sym, sym)


def _fetch_yahoo_fundamentals(symbol: str) -> dict:
    """
    Direct Yahoo Finance quoteSummary API call using requests (not yfinance lib).
    More reliable on cloud/Docker than ticker.info because we control the session.
    Returns P/E, dividend yield, sector, company name.
    """
    try:
        import requests
        from app.market_data import _yf_ticker

        symbol = _resolve_listed_symbol(symbol)
        yf_sym = _yf_ticker(symbol)
        url = (
            "https://query1.finance.yahoo.com/v10/finance/quoteSummary/"
            f"{yf_sym}?modules=summaryDetail,summaryProfile,defaultKeyStatistics"
        )
        resp = requests.get(url, headers=_YF_HEADERS, timeout=10)
        if resp.status_code != 200:
            # try query2 mirror
            url2 = url.replace("query1", "query2")
            resp = requests.get(url2, headers=_YF_HEADERS, timeout=10)
        if resp.status_code != 200:
            logger.warning("Yahoo Finance direct API %s for %s", resp.status_code, symbol)
            return {}
        result = (resp.json()
                  .get("quoteSummary", {})
                  .get("result") or [{}])[0]

        def _raw(d: dict, key: str):
            v = d.get(key, {})
            return v.get("raw") if isinstance(v, dict) else v

        summary = result.get("summaryDetail", {})
        profile = result.get("summaryProfile", {})
        stats = result.get("defaultKeyStatistics", {})

        pe = _raw(summary, "trailingPE") or _raw(stats, "forwardPE")
        div = _raw(summary, "dividendYield") or _raw(summary, "trailingAnnualDividendYield")
        sector = profile.get("sector") or profile.get("industry")
        company_name = profile.get("longName") or profile.get("shortName")

        return {
            "pe": float(pe) if pe else None,
            "div_yield": float(div) if div else None,
            "sector": sector,
            "company_name": company_name,
        }
    except Exception as e:
        logger.warning("Yahoo Finance direct API failed for %s: %s", symbol, e)
        return {}


# ---------------------------------------------------------------------------
# Source 2: Google News RSS — aggregates ET, MoneyControl, Business Standard
# ---------------------------------------------------------------------------
def _fetch_google_news_headlines(symbol: str, company_name: str = "") -> list[str]:
    """
    Google News RSS feed for the stock. Aggregates headlines from
    Economic Times, MoneyControl, Business Standard etc. — no scraping needed.
    """
    try:
        import requests
        query = quote_plus(f"{company_name or symbol} (NSE OR BSE) stock India")
        url = (
            f"https://news.google.com/rss/search"
            f"?q={query}&hl=en-IN&gl=IN&ceid=IN:en"
        )
        resp = requests.get(url, timeout=8, headers={"User-Agent": "Mozilla/5.0"})
        if resp.status_code != 200:
            return []
        root = ET.fromstring(resp.content)
        headlines: list[str] = []
        for item in root.findall(".//item")[:10]:
            title = item.findtext("title", "").strip()
            if title:
                # Google News appends " - Source Name"; strip it
                title = title.rsplit(" - ", 1)[0].strip()
                headlines.append(title)
        return headlines
    except Exception as e:
        logger.warning("Google News RSS failed for %s: %s", symbol, e)
        return []


# ---------------------------------------------------------------------------
# Main enricher: merges all four sources
# ---------------------------------------------------------------------------
def _fetch_yfinance(symbol: str) -> dict:
    try:
        import yfinance as yf
        from app.market_data import _yf_ticker

        symbol = _resolve_listed_symbol(symbol)

        # Ensure NSE + BSE equity lists are loaded (current listings).
        _load_nse_equity_map()
        _load_bse_equity_map()

        bse_rec = lookup_bse_listing(symbol)
        # Dual-listed BSE codes → NSE twin for Yahoo OHLCV ( .BO history is flaky ).
        analysis_symbol = resolve_analysis_symbol(symbol)
        yf_sym = _yf_ticker(analysis_symbol)
        nse_api_symbol = analysis_symbol if analysis_symbol in _NSE_EQUITY_MAP else symbol
        if nse_api_symbol not in _NSE_EQUITY_MAP and bse_rec:
            sid = str(bse_rec.get("scrip_id") or "").upper()
            if sid in _NSE_EQUITY_MAP:
                nse_api_symbol = sid
                yf_sym = _yf_ticker(sid)
                analysis_symbol = sid
        use_nse_api = nse_api_symbol in _NSE_EQUITY_MAP
        ticker = yf.Ticker(yf_sym)
        # Keep BSE label when user asked a BSE code; data may still come from NSE twin.
        asked_bse = bool(
            (len(symbol) == 6 and symbol.isdigit())
            or str(_yf_ticker(symbol)).endswith(".BO")
        )
        exchange_label = "BSE" if asked_bse and not use_nse_api else (
            "BSE→NSE" if asked_bse and use_nse_api else ("BSE" if str(yf_sym).endswith(".BO") else "NSE")
        )

        # --- Source 1: NSE India API (when an NSE listing exists) ---
        nse: dict = {}
        if use_nse_api:
            nse = _fetch_nse_fundamentals(nse_api_symbol)
        company_name: str = nse.get("company_name") or ""
        sector: str = nse.get("sector") or ""
        price = nse.get("price")
        pe = nse.get("pe")
        pe_sector_avg = nse.get("pe_sector")
        week52_high = nse.get("week52_high")
        week52_low = nse.get("week52_low")

        # --- Source 1b: Yahoo Finance direct API (fills gaps when NSE is blocked) ---
        yf_fund = _fetch_yahoo_fundamentals(analysis_symbol or symbol)
        if not pe:
            pe = yf_fund.get("pe")
        div_yield = yf_fund.get("div_yield")
        if not sector:
            sector = yf_fund.get("sector") or ""
        if not company_name:
            company_name = yf_fund.get("company_name") or ""

        # --- NSE / BSE equity masters ---
        if not company_name:
            company_name = _NSE_EQUITY_MAP.get(symbol, "")
        if not company_name and bse_rec:
            company_name = str(bse_rec.get("name") or "")
        if not company_name:
            company_name = (_BSE_BY_ID.get(symbol) or {}).get("name") or (
                (_BSE_BY_CODE.get(symbol) or {}).get("name") or ""
            )

        # --- Hardcoded maps (top 200 stocks, 100% reliable) ---
        if not sector:
            sector = _SYMBOL_SECTOR.get(symbol, "N/A")
            if sector == "N/A" and bse_rec and bse_rec.get("scrip_id"):
                sector = _SYMBOL_SECTOR.get(str(bse_rec["scrip_id"]), "N/A")
        if not company_name:
            company_name = _SYMBOL_COMPANY.get(symbol, symbol)

        # --- Source 3: yfinance fast_info (market cap + fallback price) ---
        market_cap = None
        try:
            fi = ticker.fast_info
            market_cap = getattr(fi, "market_cap", None) or getattr(fi, "marketCap", None)
            if not price:
                price = getattr(fi, "last_price", None)
            if not week52_high:
                week52_high = getattr(fi, "fifty_two_week_high", None)
            if not week52_low:
                week52_low = getattr(fi, "fifty_two_week_low", None)
        except Exception as e:
            logger.warning("fast_info failed for %s: %s", symbol, e)

        # yfinance ticker.info: last-resort fill for anything still missing
        pb = None
        roe = None
        eps = None
        try:
            info = ticker.info or {}
            if not div_yield:
                div_yield = info.get("dividendYield")
            if not pe:
                pe = info.get("trailingPE") or info.get("forwardPE")
            pb = info.get("priceToBook")
            roe = info.get("returnOnEquity")
            if roe is not None:
                try:
                    roe_f = float(roe)
                    # yfinance often returns ROE as fraction (0.18 → 18%)
                    roe = roe_f * 100.0 if abs(roe_f) <= 1.5 else roe_f
                except (TypeError, ValueError):
                    roe = None
            eps = info.get("trailingEps") or info.get("epsTrailingTwelveMonths")
            if not sector or sector == "N/A":
                sector = info.get("sector") or info.get("industry") or _SYMBOL_SECTOR.get(symbol, "N/A")
            if not company_name or company_name == symbol:
                company_name = (info.get("longName") or info.get("shortName")
                                or _NSE_EQUITY_MAP.get(symbol)
                                or _SYMBOL_COMPANY.get(symbol, symbol))
            if not market_cap:
                market_cap = info.get("marketCap")
            if not week52_high:
                week52_high = info.get("fiftyTwoWeekHigh")
            if not week52_low:
                week52_low = info.get("fiftyTwoWeekLow")
            if not price:
                price = (info.get("currentPrice")
                         or info.get("regularMarketPrice")
                         or info.get("previousClose"))
        except Exception as e:
            logger.warning("ticker.info failed for %s: %s", symbol, e)

        # 52-week position string
        pos_52w = None
        if price and week52_high and week52_low and week52_high > week52_low:
            pct = (price - week52_low) / (week52_high - week52_low) * 100
            if pct >= 80:
                position = "near 52-week high"
            elif pct <= 20:
                position = "near 52-week low"
            else:
                position = "middle of range"
            pos_52w = f"₹{week52_low:,.2f} – ₹{week52_high:,.2f} | Current at {pct:.0f}% ({position})"

        # --- Source 4: yf.download() for OHLCV (technicals) ---
        closes, highs, lows = [], [], []
        try:
            hist = yf.download(yf_sym, period="1y", progress=False, auto_adjust=True)
            if not hist.empty:
                # Newer yfinance returns MultiIndex columns (field, ticker) — flatten
                if hasattr(hist.columns, "levels"):
                    hist.columns = hist.columns.get_level_values(0)
                if "Close" in hist.columns:
                    closes = [float(v) for v in hist["Close"].dropna()]
                if "High" in hist.columns:
                    highs = [float(v) for v in hist["High"].dropna()]
                if "Low" in hist.columns:
                    lows = [float(v) for v in hist["Low"].dropna()]
        except Exception as e:
            logger.warning("yf.download failed for %s: %s", symbol, e)

        current = closes[-1] if closes else price

        # Moving Averages
        sma5   = _rolling_mean(closes, 5)
        sma20  = _rolling_mean(closes, 20)
        sma50  = _rolling_mean(closes, 50)
        sma200 = _rolling_mean(closes, 200)

        ma_lines = []
        if sma5:   ma_lines.append(f"5-SMA ₹{sma5:,.2f}")
        if sma20:  ma_lines.append(f"20-SMA ₹{sma20:,.2f}")
        if sma50:  ma_lines.append(f"50-SMA ₹{sma50:,.2f}")
        if sma200: ma_lines.append(f"200-SMA ₹{sma200:,.2f}")

        if current and sma50 and sma200:
            if current > sma50 > sma200:
                ma_trend = "Bullish — price above 50 & 200 SMA. " + ", ".join(ma_lines)
            elif current < sma50 < sma200:
                ma_trend = "Bearish — price below 50 & 200 SMA. " + ", ".join(ma_lines)
            else:
                ma_trend = "Mixed. " + ", ".join(ma_lines)
        elif ma_lines:
            ma_trend = ", ".join(ma_lines)
        else:
            ma_trend = None

        # Bollinger Bands (20-day)
        bb_band = None
        if len(closes) >= 20:
            bb_mid = _rolling_mean(closes, 20)
            bb_std = _rolling_std(closes, 20)
            if bb_mid and bb_std:
                bb_upper = bb_mid + 2 * bb_std
                bb_lower = bb_mid - 2 * bb_std
                if current:
                    if current > bb_upper:
                        bb_pos = "above upper band (overbought signal)"
                    elif current < bb_lower:
                        bb_pos = "below lower band (oversold signal)"
                    else:
                        pct_in = (current - bb_lower) / (bb_upper - bb_lower) * 100
                        bb_pos = f"{pct_in:.0f}% within bands"
                    bb_band = (f"Upper ₹{bb_upper:,.2f} | Mid ₹{bb_mid:,.2f} | "
                               f"Lower ₹{bb_lower:,.2f} — price {bb_pos}")

        # RSI
        rsi_val = _calc_rsi(closes)
        rsi_interp = None
        rsi_str = None
        if rsi_val is not None:
            if rsi_val > 70:
                rsi_interp = "overbought"
            elif rsi_val < 30:
                rsi_interp = "oversold"
            else:
                rsi_interp = "neutral"
            rsi_str = f"{rsi_val} ({rsi_interp})"

        # Support & Resistance
        support1 = round(min(lows[-20:]), 2) if len(lows) >= 20 else None
        resistance1 = round(max(highs[-20:]), 2) if len(highs) >= 20 else None
        support2 = round(min(lows[-52:]), 2) if len(lows) >= 52 else None
        resistance2 = round(max(highs[-52:]), 2) if len(highs) >= 52 else None

        # Stop Loss & Take Profit
        stop_loss = round(support1 * 0.98, 2) if support1 else None
        take_profit = resistance1
        rr_ratio = None
        if current and stop_loss and take_profit and current > stop_loss:
            risk = current - stop_loss
            reward = take_profit - current
            if risk > 0:
                rr_ratio = f"1 : {reward/risk:.1f}"

        # Sector trend from 1-month price momentum
        sector_trend = "Neutral"
        if len(closes) >= 21:
            ret_1m = (closes[-1] - closes[-21]) / closes[-21] * 100
            if ret_1m > 3:
                sector_trend = f"Bullish (stock +{ret_1m:.1f}% in 1 month)"
            elif ret_1m < -3:
                sector_trend = f"Bearish (stock {ret_1m:.1f}% in 1 month)"
            else:
                sector_trend = f"Neutral (stock {ret_1m:+.1f}% in 1 month)"

        # --- Source 2: Google News RSS (primary for headlines) ---
        headlines = _fetch_google_news_headlines(symbol, company_name)

        # Fallback: yfinance news if Google News returned nothing
        if not headlines:
            try:
                raw_news = ticker.news or []
                for item in raw_news[:8]:
                    title = (item.get("title")
                             or (item.get("content") or {}).get("title", ""))
                    if title:
                        headlines.append(title)
            except Exception:
                pass

        sentiment = _news_sentiment(headlines) if headlines else {}

        fundamental_data = {
            "pe_ratio": f"{pe:.1f}" if pe else None,
            "pe": pe,
            "pb": pb,
            "p/b": pb,
            "roe": roe,
            "eps": eps,
            "pe_sector_avg": f"{pe_sector_avg:.1f}" if pe_sector_avg else None,
            "pe_sector": pe_sector_avg,
            "market_cap": _fmt_inr_cr(market_cap),
            "dividend_yield": (
                f"{(div_yield * 100.0 if isinstance(div_yield, (int, float)) and div_yield < 1 else div_yield):.2f}%"
                if isinstance(div_yield, (int, float))
                else ("Not declared" if not div_yield else str(div_yield))
            ),
            "week_52": pos_52w,
            "delivery_pct": nse.get("delivery_pct") if isinstance(nse, dict) else None,
        }
        trading_levels_data = {
            "support_1": f"{support1:,.2f}" if support1 else None,
            "support_2": f"{support2:,.2f}" if support2 else None,
            "resistance_1": f"{resistance1:,.2f}" if resistance1 else None,
            "resistance_2": f"{resistance2:,.2f}" if resistance2 else None,
            "stop_loss": f"{stop_loss:,.2f}" if stop_loss else None,
            "take_profit": f"{take_profit:,.2f}" if take_profit else None,
            "risk_reward": rr_ratio,
        }
        technical_data = {
            "rsi": rsi_str,
            "rsi_interpretation": rsi_interp,
            "bollinger_bands": bb_band,
            "moving_averages": ma_trend,
            "trend": ("strong_bullish" if current and sma50 and sma200 and current > sma50 > sma200
                      else "bearish" if current and sma50 and sma200 and current < sma50 < sma200
                      else "neutral"),
        }

        # Pre-compute signal conclusions for each indicator
        pre_signals = compute_pre_signals(technical_data, fundamental_data, trading_levels_data, current)

        return {
            "symbol": symbol,
            "company_name": company_name,
            "sector": sector,
            "exchange": exchange_label,
            "yahoo_ticker": yf_sym,
            "analysis_symbol": analysis_symbol,
            "bse_code": (bse_rec or {}).get("code") or (
                symbol if len(symbol) == 6 and symbol.isdigit() else None
            ),
            "isin": (bse_rec or {}).get("isin") or "",
            "current_price": f"{current:,.2f}" if current else None,
            "fundamental": fundamental_data,
            "technical": technical_data,
            "trading_levels": trading_levels_data,
            "sentiment": {
                "overall": sentiment.get("overall"),
                "breakdown": sentiment.get("breakdown"),
                "score": sentiment.get("score"),
                "tagged": sentiment.get("tagged"),
                "recent_events": headlines[:5],
                "sector_trend": sector_trend,
            },
            "news_headlines": headlines,
            "pre_signals": pre_signals,
        }

    except Exception as exc:
        logger.error("Stock enricher failed for %s: %s", symbol, exc, exc_info=True)
        raise


async def enrich(symbol: str) -> dict:
    now = time()
    cached = _cache.get(symbol)
    if cached and (now - cached[0]) < _CACHE_TTL:
        return cached[1]
    loop = asyncio.get_event_loop()
    data = await loop.run_in_executor(None, _fetch_yfinance, symbol)
    if data:
        _cache[symbol] = (now, data)
    return data


def format_news_for_prompt(headlines: list[str]) -> str:
    if not headlines:
        return ""
    lines = "\n".join(f"  • {h}" for h in headlines)
    return f"RECENT NEWS HEADLINES:\n{lines}"


def compute_pre_signals(
    tech: dict, fund: dict, tl: dict, current_price: Optional[float]
) -> dict:
    """
    Pre-compute human-readable signal conclusions from raw data so the LLM
    receives analysis results rather than raw numbers.
    """
    signals: dict = {}

    # RSI signal
    try:
        rsi_val = float(str(tech.get("rsi", "")).split()[0].replace(",", ""))
        if rsi_val >= 75:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Strongly overbought, high reversal risk, avoid chasing"
        elif rsi_val >= 70:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Overbought zone, potential short-term pullback"
        elif rsi_val <= 25:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Strongly oversold, watch for reversal/bounce"
        elif rsi_val <= 30:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Oversold, potential accumulation zone"
        elif 48 <= rsi_val <= 55:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Neutral momentum, no directional signal"
        elif rsi_val > 55:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Bullish momentum building, trend intact"
        else:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Bearish momentum, wait for stabilization"
    except (ValueError, TypeError, IndexError):
        pass

    # MA trend signal
    ma_str = str(tech.get("moving_averages", "") or tech.get("trend", "")).lower()
    if "strong_bullish" in ma_str or "strongly bullish" in ma_str:
        signals["ma_signal"] = "All major MAs aligned bullish — strong uptrend, dips are buying opportunities"
    elif "bullish" in ma_str and "above" in ma_str:
        signals["ma_signal"] = "Price above key MAs — uptrend intact, pullbacks to 50-SMA are entry zones"
    elif "strong_bearish" in ma_str or "strongly bearish" in ma_str:
        signals["ma_signal"] = "Price below all major MAs — strong downtrend, avoid buying until MA reclaim"
    elif "bearish" in ma_str and "below" in ma_str:
        signals["ma_signal"] = "Price below key MAs — downtrend, wait for 20-SMA reclaim before buying"
    elif ma_str:
        signals["ma_signal"] = "Mixed MA signals — sideways consolidation, wait for directional breakout"

    # 52-week position
    week52_str = str(fund.get("week_52", "") or "")
    if current_price and week52_str:
        try:
            nums = re.findall(r"[\d]+\.?\d*", week52_str.replace(",", ""))
            if len(nums) >= 2:
                low52, high52 = float(nums[0]), float(nums[-1])
                rng = high52 - low52
                if rng > 0:
                    pos_pct = (current_price - low52) / rng * 100
                    if pos_pct >= 90:
                        signals["week52_signal"] = (
                            f"Near 52-week HIGH ({pos_pct:.0f}% of range) — "
                            f"resistance at ₹{high52:,.0f}, profit booking risk"
                        )
                    elif pos_pct >= 70:
                        signals["week52_signal"] = (
                            f"Upper half of 52-week range ({pos_pct:.0f}%) — "
                            f"momentum favors bulls but watch ₹{high52:,.0f} resistance"
                        )
                    elif pos_pct <= 10:
                        signals["week52_signal"] = (
                            f"Near 52-week LOW ({pos_pct:.0f}% of range) — "
                            f"potential value zone, but confirm before buying"
                        )
                    elif pos_pct <= 30:
                        signals["week52_signal"] = (
                            f"Lower 52-week range ({pos_pct:.0f}%) — "
                            f"contrarian opportunity, but sentiment still negative"
                        )
                    else:
                        signals["week52_signal"] = (
                            f"Mid 52-week range ({pos_pct:.0f}%) — "
                            f"neutral zone, ₹{high52:,.0f} target | ₹{low52:,.0f} floor"
                        )
        except (ValueError, IndexError):
            pass

    # Support/Resistance proximity
    if current_price and tl.get("support_1") and tl.get("resistance_1"):
        try:
            s1 = float(str(tl["support_1"]).replace(",", "").replace("₹", ""))
            r1 = float(str(tl["resistance_1"]).replace(",", "").replace("₹", ""))
            dist_sup = (current_price - s1) / current_price * 100
            dist_res = (r1 - current_price) / current_price * 100
            if dist_sup < 2:
                signals["level_signal"] = (
                    f"Price ≈ support ₹{s1:,.0f} ({dist_sup:.1f}% away) — "
                    f"low-risk entry zone, stop below ₹{s1:,.0f}"
                )
            elif dist_res < 2:
                signals["level_signal"] = (
                    f"Price ≈ resistance ₹{r1:,.0f} ({dist_res:.1f}% away) — "
                    f"breakout or rejection imminent, wait for confirmation"
                )
            elif dist_res < dist_sup:
                signals["level_signal"] = (
                    f"Limited upside to resistance ₹{r1:,.0f} ({dist_res:.1f}%) "
                    f"vs downside to support ₹{s1:,.0f} ({dist_sup:.1f}%) — "
                    f"unfavorable risk/reward until breakout"
                )
            else:
                signals["level_signal"] = (
                    f"Good risk/reward — ₹{dist_res:.1f}% upside to ₹{r1:,.0f} "
                    f"vs ₹{dist_sup:.1f}% risk to support ₹{s1:,.0f}"
                )
        except (ValueError, TypeError):
            pass

    # P/E valuation signal
    try:
        pe = float(str(fund.get("pe_ratio", "") or "").replace(",", "").split()[0])
        if pe < 10:
            signals["pe_signal"] = f"P/E {pe:.1f} — Very cheap, potential value trap or turnaround; check earnings trend"
        elif pe < 18:
            signals["pe_signal"] = f"P/E {pe:.1f} — Reasonably valued, room for re-rating if earnings grow"
        elif pe < 30:
            signals["pe_signal"] = f"P/E {pe:.1f} — Fairly to slightly premium, earnings growth must sustain"
        elif pe < 50:
            signals["pe_signal"] = f"P/E {pe:.1f} — Premium valuation, growth expectations already priced in"
        else:
            signals["pe_signal"] = f"P/E {pe:.1f} — Expensive, high risk if earnings disappoint"
    except (ValueError, TypeError, IndexError):
        pass

    # Aliases so answer_composer / older prompts find the same cues.
    if signals.get("ma_signal") and not signals.get("trend_signal"):
        signals["trend_signal"] = signals["ma_signal"]
    if signals.get("pe_signal") and not signals.get("valuation_signal"):
        signals["valuation_signal"] = signals["pe_signal"]
    if signals.get("level_signal") and not signals.get("levels_signal"):
        signals["levels_signal"] = signals["level_signal"]

    return signals
