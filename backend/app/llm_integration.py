"""Wraps StockMarketAssistant for use inside the BYSEL backend."""
from __future__ import annotations

import logging
import sys
from pathlib import Path

logger = logging.getLogger(__name__)

_LLM_SRC = Path(__file__).parents[2] / "llm" / "src"
_LLM_DATA = Path(__file__).parents[2] / "llm" / "data"

_assistant = None


def _load_assistant():
    global _assistant
    if _assistant is not None:
        return _assistant
    if not _LLM_SRC.exists():
        logger.warning("LLM source not found at %s — skipping", _LLM_SRC)
        return None
    if str(_LLM_SRC) not in sys.path:
        sys.path.insert(0, str(_LLM_SRC))
    try:
        from indian_stock_llm import StockMarketAssistant
        from indian_stock_llm.config import default_config

        cfg = default_config()
        cfg = cfg.__class__(
            **{
                **cfg.__dict__,
                "knowledge_base_path": _LLM_DATA / "sample_knowledge.json",
                "instrument_master_path": _LLM_DATA / "enterprise" / "instrument_master.json",
                "corporate_actions_path": _LLM_DATA / "enterprise" / "corporate_actions.json",
                "filings_path": _LLM_DATA / "enterprise" / "filings.json",
                "regulatory_updates_path": _LLM_DATA / "enterprise" / "regulatory_updates.json",
                "market_events_path": _LLM_DATA / "enterprise" / "market_events.json",
            }
        )
        _assistant = StockMarketAssistant(config=cfg)
        logger.info("Indian Stock LLM loaded successfully")
        return _assistant
    except Exception as exc:
        logger.error("Failed to load Indian Stock LLM: %s", exc)
        return None


def ask_llm(query: str) -> dict | None:
    """Query the Indian Stock LLM. Returns None if unavailable."""
    assistant = _load_assistant()
    if assistant is None:
        return None
    try:
        result = assistant.query(query)
        return {
            "answer": result.get("answer", ""),
            "intent": result.get("intent", "general_query"),
            "confidence": result.get("confidence", 0.0),
            "citations": result.get("citations", []),
            "category": result.get("category", "stocks"),
            "disclaimer": result.get("disclaimer", ""),
        }
    except Exception as exc:
        logger.error("Indian Stock LLM query failed: %s", exc)
        return None


def llm_available() -> bool:
    return _load_assistant() is not None
