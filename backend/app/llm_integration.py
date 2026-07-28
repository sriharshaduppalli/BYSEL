"""
Indian Stock Market LLM — production-ready local knowledge assistant.

Grounded RAG over BYSEL's Indian-market knowledge pack (equations, terms,
sectors, symbols, analysis frameworks) with deterministic education answers.
No paid API required. Optional remote model via ISM_MODEL_ENDPOINT.
"""
from __future__ import annotations

import json
import logging
import sys
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

_LLM_DATA = Path(__file__).parent.parent / "llm_data"
_LLM_PKG = Path(__file__).parent.parent / "indian_stock_llm"
_ENTERPRISE = _LLM_DATA / "enterprise"

_assistant = None


def _sync_instrument_master() -> Path:
    """Expand instrument_master.json from the live Indian stock catalog."""
    target = _ENTERPRISE / "instrument_master.json"
    _ENTERPRISE.mkdir(parents=True, exist_ok=True)
    try:
        from .market_data import INDIAN_STOCKS, get_stock_catalog

        catalog = get_stock_catalog()
        rows = []
        for symbol, (yahoo, name) in catalog.items():
            if symbol in {"NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT"}:
                continue
            rows.append(
                {
                    "symbol": symbol,
                    "company_name": name,
                    "yahoo_ticker": yahoo,
                    "isin": "",
                    "exchange": "BSE" if str(yahoo).endswith(".BO") else "NSE",
                }
            )
        # Prefer curated names when present
        for symbol, (yahoo, name) in INDIAN_STOCKS.items():
            if symbol in {"NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT"}:
                continue
        target.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
        logger.info("Indian Stock LLM instrument master synced: %d symbols", len(rows))
    except Exception as exc:
        logger.warning("Instrument master sync skipped: %s", exc)
        if not target.exists():
            target.write_text("[]", encoding="utf-8")
    return target


def _load_assistant():
    global _assistant
    if _assistant is not None:
        return _assistant

    if not _LLM_PKG.exists():
        logger.error("indian_stock_llm package not found at %s", _LLM_PKG)
        return None

    pkg_parent = str(_LLM_PKG.parent)
    if pkg_parent not in sys.path:
        sys.path.insert(0, pkg_parent)

    try:
        from indian_stock_llm import StockMarketAssistant
        from indian_stock_llm.config import default_config

        instrument_path = _sync_instrument_master()
        base = default_config()
        cfg = base.__class__(
            **{
                **base.__dict__,
                "knowledge_base_path": _LLM_DATA / "sample_knowledge.json",
                "instrument_master_path": instrument_path,
                "corporate_actions_path": _ENTERPRISE / "corporate_actions.json",
                "filings_path": _ENTERPRISE / "filings.json",
                "regulatory_updates_path": _ENTERPRISE / "regulatory_updates.json",
                "market_events_path": _ENTERPRISE / "market_events.json",
                # Production-local defaults: answer from KB even when enterprise JSON is thin.
                "require_ready_data_for_factual": False,
                "top_k_context": 6,
                "min_retrieval_score": 0.12,
                "min_confidence_threshold": 0.30,
                "model_timeout_seconds": 4.0,
            }
        )
        _assistant = StockMarketAssistant(config=cfg)
        logger.info(
            "Indian Stock LLM loaded OK (kb=%d items)",
            len(getattr(_assistant.knowledge_base, "items", []) or []),
        )
        return _assistant
    except Exception as exc:
        logger.error("Failed to load Indian Stock LLM: %s", exc, exc_info=True)
        return None


def llm_available() -> bool:
    return _load_assistant() is not None


def ask_llm(query: str, context: dict[str, Any] | None = None) -> dict | None:
    """Answer using education pack first, then grounded Indian-market RAG."""
    cleaned = (query or "").strip()
    if not cleaned:
        return None

    # 1) Deterministic equations / glossary (highest precision).
    try:
        from .market_education import get_education_answer

        education = get_education_answer(cleaned)
        if education:
            return {
                "answer": education,
                "intent": "market_calculations",
                "confidence": 0.94,
                "citations": ["bysel_market_education"],
                "category": "calculations",
                "source": "indian-stock-llm-education",
            }
    except Exception as exc:
        logger.debug("Education pack miss: %s", exc)

    assistant = _load_assistant()
    if assistant is None:
        return None

    try:
        prompt = cleaned
        if context:
            symbol = context.get("symbol")
            price = context.get("current_price")
            bits = []
            if symbol:
                bits.append(f"Focus symbol: {symbol}")
            if price is not None:
                bits.append(f"Live price context: {price}")
            if bits:
                prompt = f"{cleaned}\n\n" + " | ".join(bits)

        result = assistant.query(prompt)
        answer = (result.get("answer") or "").strip()
        disclaimer = (result.get("disclaimer") or "").strip()
        if disclaimer and disclaimer not in answer:
            answer = f"{answer}\n\n{disclaimer}" if answer else disclaimer
        return {
            "answer": answer,
            "intent": result.get("intent", "general_query"),
            "confidence": float(result.get("confidence", 0.0) or 0.0),
            "citations": result.get("citations", []),
            "category": result.get("category", "stocks"),
            "source": "indian-stock-llm",
            "diagnostics": result.get("diagnostics"),
        }
    except Exception as exc:
        logger.error("Indian Stock LLM query failed: %s", exc, exc_info=True)
        return None
