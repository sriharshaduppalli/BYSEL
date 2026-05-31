"""
Indian Stock Market LLM — wraps StockMarketAssistant.
Pure Python, no external API or paid services needed.
"""
from __future__ import annotations

import logging
import sys
from pathlib import Path

logger = logging.getLogger(__name__)

_LLM_DATA = Path(__file__).parent.parent / "llm_data"
_LLM_PKG  = Path(__file__).parent.parent / "indian_stock_llm"

_assistant = None


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

        cfg = default_config()
        cfg = cfg.__class__(
            **{
                **cfg.__dict__,
                "knowledge_base_path":    _LLM_DATA / "sample_knowledge.json",
                "instrument_master_path": _LLM_DATA / "enterprise" / "instrument_master.json",
                "corporate_actions_path": _LLM_DATA / "enterprise" / "corporate_actions.json",
                "filings_path":           _LLM_DATA / "enterprise" / "filings.json",
                "regulatory_updates_path":_LLM_DATA / "enterprise" / "regulatory_updates.json",
                "market_events_path":     _LLM_DATA / "enterprise" / "market_events.json",
            }
        )
        _assistant = StockMarketAssistant(config=cfg)
        logger.info("Indian Stock LLM loaded OK")
        return _assistant
    except Exception as exc:
        logger.error("Failed to load Indian Stock LLM: %s", exc)
        return None


def llm_available() -> bool:
    return _load_assistant() is not None


def ask_llm(query: str) -> dict | None:
    assistant = _load_assistant()
    if assistant is None:
        return None
    try:
        result = assistant.query(query)
        answer = result.get("answer", "")
        disclaimer = result.get("disclaimer", "")
        if disclaimer:
            answer = f"{answer}\n\n{disclaimer}"
        return {
            "answer": answer,
            "intent": result.get("intent", "general_query"),
            "confidence": result.get("confidence", 0.0),
            "citations": result.get("citations", []),
            "category": result.get("category", "stocks"),
        }
    except Exception as exc:
        logger.error("Indian Stock LLM query failed: %s", exc)
        return None
