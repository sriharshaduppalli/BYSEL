"""
Full-universe sector map for the Smart Sentiment Heatmap.

Active listed equities come from get_stock_catalog() (curated + NSE EQUITY_L).
Known sector maps are applied first; remaining names are classified with
lightweight company-name heuristics into the heatmap buckets (+ Others).
"""

from __future__ import annotations

import logging
import re
import threading
from typing import Dict, List, Tuple

logger = logging.getLogger(__name__)

# Canonical heatmap buckets (display order). "Others" catches unmapped actives.
HEATMAP_SECTOR_ORDER: List[str] = [
    "Banking",
    "IT",
    "Pharma",
    "Auto",
    "FMCG",
    "Energy",
    "Metals",
    "Infra",
    "Finance",
    "Realty",
    "Defence",
    "Consumer",
    "Telecom",
    "Chemicals",
    "Others",
]

# Map portfolio_scorer / enricher labels → heatmap buckets.
_SECTOR_ALIASES: Dict[str, str] = {
    "banking": "Banking",
    "bank": "Banking",
    "nbfc": "Finance",
    "finance": "Finance",
    "financial": "Finance",
    "insurance": "Finance",
    "it": "IT",
    "technology": "IT",
    "pharma": "Pharma",
    "pharmaceuticals": "Pharma",
    "healthcare": "Pharma",
    "health care": "Pharma",
    "auto": "Auto",
    "automobile": "Auto",
    "fmcg": "FMCG",
    "energy": "Energy",
    "oil & gas": "Energy",
    "power": "Energy",
    "metals": "Metals",
    "metal": "Metals",
    "infra": "Infra",
    "infrastructure": "Infra",
    "cement": "Infra",
    "realty": "Realty",
    "real estate": "Realty",
    "defence": "Defence",
    "defense": "Defence",
    "consumer": "Consumer",
    "telecom": "Telecom",
    "telecommunications": "Telecom",
    "chemicals": "Chemicals",
    "chemical": "Chemicals",
    "paints": "Chemicals",
}

_NAME_RULES: List[Tuple[re.Pattern, str]] = [
    (re.compile(r"\b(bank|banking)\b", re.I), "Banking"),
    (re.compile(r"\b(nbfc|finance|finserv|financial|insurance|life insurance|general insurance)\b", re.I), "Finance"),
    (re.compile(r"\b(software|infotech|information technology|technologies|tech |systems|consult)\b", re.I), "IT"),
    (re.compile(r"\b(pharma|pharmaceutical|laborator|lab\b|hospital|healthcare|health care)\b", re.I), "Pharma"),
    (re.compile(r"\b(auto|motor|motors|tyre|tire|vehicle)\b", re.I), "Auto"),
    (re.compile(r"\b(fmcg|foods|beverage|soap|detergent|dairy|biscuit)\b", re.I), "FMCG"),
    (re.compile(r"\b(oil|gas|petroleum|power|energy|coal|renewable|petro)\b", re.I), "Energy"),
    (re.compile(r"\b(steel|metal|aluminium|aluminum|copper|zinc|mining|minerals)\b", re.I), "Metals"),
    (re.compile(r"\b(cement|infra|infrastructure|construction|engineering|port |ports)\b", re.I), "Infra"),
    (re.compile(r"\b(realty|real estate|property|housing|developer|builders)\b", re.I), "Realty"),
    (re.compile(r"\b(defence|defense|shipyard|aerospace|ordnance)\b", re.I), "Defence"),
    (re.compile(r"\b(telecom|telecommunication|airtel)\b", re.I), "Telecom"),
    (re.compile(r"\b(chemical|paint|fertiliz|pigment)\b", re.I), "Chemicals"),
    (re.compile(r"\b(retail|apparel|consumer|jewellery|jewelry|electronics|durable)\b", re.I), "Consumer"),
]

_INDEX_LIKE = {
    "NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT", "NIFTYBANK", "NIFTY",
}

_universe_lock = threading.Lock()
_sector_symbols_cache: Dict[str, List[str]] | None = None
_symbol_sector_cache: Dict[str, str] | None = None


def _normalize_bucket(label: str | None) -> str | None:
    if not label:
        return None
    key = label.strip().lower()
    if key in _SECTOR_ALIASES:
        return _SECTOR_ALIASES[key]
    titled = label.strip().title()
    if titled in HEATMAP_SECTOR_ORDER:
        return titled
    return None


def _classify_from_name(company_name: str) -> str:
    text = company_name or ""
    for pattern, sector in _NAME_RULES:
        if pattern.search(text):
            return sector
    return "Others"


def _seed_from_curated() -> Dict[str, str]:
    """symbol → sector from heatmap SECTOR_STOCKS + portfolio SECTOR_MAP."""
    mapping: Dict[str, str] = {}
    try:
        from .market_heatmap import SECTOR_STOCKS

        for sector, symbols in SECTOR_STOCKS.items():
            bucket = _normalize_bucket(sector) or sector
            for sym in symbols:
                mapping[str(sym).upper()] = bucket
    except Exception as exc:
        logger.warning("heatmap_universe.seed_sectors_failed reason=%s", exc)

    try:
        from .portfolio_scorer import SECTOR_MAP

        for sym, label in SECTOR_MAP.items():
            bucket = _normalize_bucket(label)
            if bucket:
                mapping.setdefault(str(sym).upper(), bucket)
    except Exception as exc:
        logger.warning("heatmap_universe.seed_portfolio_map_failed reason=%s", exc)

    try:
        from . import stock_enricher

        symbol_sector = getattr(stock_enricher, "_SYMBOL_SECTOR", None)
        if isinstance(symbol_sector, dict):
            for sym, label in symbol_sector.items():
                bucket = _normalize_bucket(str(label))
                if bucket:
                    mapping.setdefault(str(sym).upper(), bucket)
    except Exception:
        pass

    return mapping


def build_heatmap_universe(*, force_refresh: bool = False) -> Tuple[Dict[str, List[str]], Dict[str, str]]:
    """
    Returns (sector → symbols[], symbol → sector) covering all active catalog equities.
    """
    global _sector_symbols_cache, _symbol_sector_cache
    with _universe_lock:
        if not force_refresh and _sector_symbols_cache is not None and _symbol_sector_cache is not None:
            return _sector_symbols_cache, _symbol_sector_cache

        from .market_data import get_stock_catalog

        catalog = get_stock_catalog()
        seeded = _seed_from_curated()
        symbol_to_sector: Dict[str, str] = {}

        for sym, meta in catalog.items():
            key = str(sym or "").strip().upper()
            if not key or key in _INDEX_LIKE or key.startswith("^"):
                continue
            # Skip odd non-equity keys if any
            if not re.match(r"^[A-Z0-9][A-Z0-9.&-]{0,24}$", key):
                continue

            company = ""
            if isinstance(meta, (tuple, list)) and len(meta) >= 2:
                company = str(meta[1] or "")
            elif isinstance(meta, dict):
                company = str(meta.get("name") or meta.get("company_name") or "")

            sector = seeded.get(key) or _classify_from_name(company)
            if sector not in HEATMAP_SECTOR_ORDER:
                sector = "Others"
            symbol_to_sector[key] = sector

        sector_symbols: Dict[str, List[str]] = {name: [] for name in HEATMAP_SECTOR_ORDER}
        for sym, sector in symbol_to_sector.items():
            sector_symbols.setdefault(sector, []).append(sym)

        for sector, symbols in sector_symbols.items():
            symbols.sort()

        _sector_symbols_cache = sector_symbols
        _symbol_sector_cache = symbol_to_sector
        mapped = sum(1 for s in symbol_to_sector.values() if s != "Others")
        logger.info(
            "heatmap_universe.ready total=%s mapped=%s others=%s sectors=%s",
            len(symbol_to_sector),
            mapped,
            sum(1 for s in symbol_to_sector.values() if s == "Others"),
            {k: len(v) for k, v in sector_symbols.items() if v},
        )
        return _sector_symbols_cache, _symbol_sector_cache


def get_heatmap_sector_symbols() -> Dict[str, List[str]]:
    sectors, _ = build_heatmap_universe()
    return {k: list(v) for k, v in sectors.items() if v}


def universe_size() -> int:
    _, mapping = build_heatmap_universe()
    return len(mapping)
