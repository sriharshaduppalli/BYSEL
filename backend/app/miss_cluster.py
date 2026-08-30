"""Nightly miss clustering from ISM feedback logs.

Human-review only. Does not write learned_knowledge.json and must not
run on the /ai/ask request path.
"""
from __future__ import annotations

import json
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

_WS = re.compile(r"\s+")

DEFAULT_OUT = Path(__file__).resolve().parents[1] / "llm_data" / "miss_clusters.json"


def _feedback_paths() -> list[Path]:
    here = Path(__file__).resolve()
    return [
        here.parents[1] / "llm_data" / "daily_feedback.log",
        here.parents[1] / "vendor" / "indian_stock_market" / "data" / "daily_feedback.log",
        Path("backend/llm_data/daily_feedback.log"),
        Path("data/daily_feedback.log"),
    ]


def _profile_for(query: str, intent: str = "") -> str:
    try:
        from indian_stock_llm.query_contract import resolve_query_contract

        contract = resolve_query_contract(query, intent_hint=intent)
        return str(contract.profile or "unknown")
    except Exception:
        return (intent or "unknown").strip() or "unknown"


def normalize_miss_phrase(query: str) -> str:
    text = _WS.sub(" ", (query or "").strip().lower()).strip()
    return text[:96]


def _parse_line(line: str) -> dict[str, Any] | None:
    text = (line or "").strip()
    if not text:
        return None
    if text.startswith("{"):
        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            return None
        if not isinstance(payload, dict):
            return None
        kind = str(payload.get("kind") or "")
        query = str(payload.get("query") or payload.get("query_key") or "").strip()
        if not query:
            return None
        helpful = payload.get("helpful")
        grounded = payload.get("grounded")
        try:
            confidence = float(payload.get("confidence")) if payload.get("confidence") is not None else None
        except Exception:
            confidence = None
        miss = False
        if kind == "thumbs_v1" and helpful is False:
            miss = True
        if kind == "interaction_v1" and (grounded is False or (confidence is not None and confidence < 0.45)):
            miss = True
        if helpful is False:
            miss = True
        return {
            "query": query,
            "intent": str(payload.get("intent") or ""),
            "miss": miss,
        }
    parts = text.split("\t")
    if len(parts) >= 3:
        return {"query": parts[2].strip(), "intent": parts[1].strip(), "miss": False}
    return None


def cluster_feedback_lines(lines: list[str]) -> dict[str, Any]:
    buckets: dict[tuple[str, str], dict[str, Any]] = {}
    total = 0
    miss_count = 0
    for line in lines:
        row = _parse_line(line)
        if not row:
            continue
        total += 1
        phrase = normalize_miss_phrase(row["query"])
        if not phrase:
            continue
        profile = _profile_for(row["query"], row.get("intent") or "")
        key = (profile, phrase)
        bucket = buckets.get(key)
        if bucket is None:
            bucket = {
                "profile": profile,
                "phrase": phrase,
                "count": 0,
                "misses": 0,
                "samples": [],
            }
            buckets[key] = bucket
        bucket["count"] += 1
        if row.get("miss"):
            bucket["misses"] += 1
            miss_count += 1
        samples: list[str] = bucket["samples"]
        if row["query"] not in samples and len(samples) < 5:
            samples.append(row["query"][:160])

    clusters = []
    for bucket in buckets.values():
        needs_review = bool(bucket["misses"] >= 2 or bucket["count"] >= 4)
        clusters.append({**bucket, "needs_review": needs_review})
    clusters.sort(key=lambda item: (int(item["misses"]), int(item["count"])), reverse=True)
    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "total_lines": total,
        "miss_count": miss_count,
        "cluster_count": len(clusters),
        "clusters": clusters[:80],
        "note": "Human review only. Do not auto-promote into learned_knowledge.json.",
    }


def cluster_and_write(
    log_path: Path | None = None,
    out_path: Path | None = None,
) -> dict[str, Any]:
    paths = [log_path] if log_path is not None else _feedback_paths()
    lines: list[str] = []
    used: list[str] = []
    for path in paths:
        if path is None or not path.exists():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        if not text.strip():
            continue
        used.append(str(path))
        lines.extend(text.splitlines())
    report = cluster_feedback_lines(lines)
    report["sources"] = used
    dest = out_path or DEFAULT_OUT
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    report["written_to"] = str(dest)
    return report
