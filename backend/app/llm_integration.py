"""
Indian Stock Market knowledge base retrieval for Gemini RAG.
Loads domain knowledge from llm_data/ JSON files and retrieves
relevant context to enrich Gemini prompts.
"""
from __future__ import annotations

import json
import logging
import re
from pathlib import Path

logger = logging.getLogger(__name__)

_LLM_DATA = Path(__file__).parent.parent / "llm_data"

_knowledge_base: list[dict] | None = None
_instrument_master: list[dict] | None = None


def _load_knowledge_base() -> list[dict]:
    global _knowledge_base
    if _knowledge_base is not None:
        return _knowledge_base
    try:
        path = _LLM_DATA / "sample_knowledge.json"
        _knowledge_base = json.loads(path.read_text(encoding="utf-8"))
        logger.info("Knowledge base loaded: %d entries", len(_knowledge_base))
    except Exception as e:
        logger.error("Failed to load knowledge base: %s", e)
        _knowledge_base = []
    return _knowledge_base


def _load_instrument_master() -> list[dict]:
    global _instrument_master
    if _instrument_master is not None:
        return _instrument_master
    try:
        path = _LLM_DATA / "enterprise" / "instrument_master.json"
        _instrument_master = json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        logger.error("Failed to load instrument master: %s", e)
        _instrument_master = []
    return _instrument_master


def _score_entry(entry: dict, query_tokens: set[str]) -> int:
    text = (entry.get("title", "") + " " + entry.get("content", "") + " " + " ".join(entry.get("tags", []))).lower()
    return sum(1 for t in query_tokens if t in text)


def retrieve_context(query: str, top_k: int = 3) -> str:
    """Return relevant knowledge base entries as a formatted context string."""
    kb = _load_knowledge_base()
    if not kb:
        return ""

    tokens = set(re.findall(r"[a-z0-9]+", query.lower()))
    scored = sorted(kb, key=lambda e: _score_entry(e, tokens), reverse=True)
    top = [e for e in scored[:top_k] if _score_entry(e, tokens) > 0]

    if not top:
        top = scored[:2]

    return "\n".join(f"- {e['title']}: {e['content']}" for e in top)


def resolve_symbol(query: str) -> dict | None:
    """Try to find a known Indian stock symbol mentioned in the query."""
    instruments = _load_instrument_master()
    q = query.upper()
    for inst in instruments:
        sym = inst.get("symbol", "")
        name = inst.get("company_name", "").upper()
        if sym and (sym in q or (len(name) > 4 and name in q)):
            return inst
    return None
