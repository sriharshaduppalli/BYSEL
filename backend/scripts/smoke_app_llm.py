#!/usr/bin/env python3
"""App-like smoke test for BYSEL custom LLM (tier=fast, same as Android chat)."""
from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Queries mirror common AI Assistant asks + latest features.
QUERIES = [
    "What is RSI?",
    "What are market timings?",
    "What is CAS?",
    "Sentiment of TCS",
    "Should I buy RELIANCE for swing trade?",
    "Support and resistance of BSE:500325",
    "Compare TCS and INFY",
    "Top defence stocks in India",
]


def post_ask(base: str, query: str, tier: str = "fast", timeout: float = 90.0) -> dict:
    url = base.rstrip("/") + "/ai/ask"
    payload = json.dumps({"query": query, "tier": tier}).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            ms = int((time.time() - t0) * 1000)
            return {"ok": True, "ms": ms, "http": resp.status, "body": body}
    except urllib.error.HTTPError as e:
        ms = int((time.time() - t0) * 1000)
        text = e.read().decode("utf-8", errors="replace")[:500]
        return {"ok": False, "ms": ms, "http": e.code, "error": text}
    except Exception as e:
        ms = int((time.time() - t0) * 1000)
        return {"ok": False, "ms": ms, "http": None, "error": str(e)}


def summarize(result: dict) -> dict:
    body = result.get("body") or {}
    ans = (body.get("answer") or "").strip()
    return {
        "ok": result.get("ok"),
        "ms": result.get("ms"),
        "http": result.get("http"),
        "source": body.get("source"),
        "tier_requested": body.get("tier_requested") or body.get("tierRequested"),
        "confidence": body.get("confidence"),
        "symbol": body.get("symbol"),
        "answer_preview": ans.replace("\n", " ")[:220],
        "error": result.get("error"),
    }


def run_target(name: str, base: str) -> dict:
    print(f"\n=== {name} ({base}) ===")
    # Health first
    try:
        with urllib.request.urlopen(base.rstrip("/") + "/health", timeout=30) as resp:
            health = json.loads(resp.read().decode("utf-8"))
            print("health:", health.get("status") or health)
    except Exception as e:
        print("health FAIL:", e)
        return {"target": name, "base": base, "health_ok": False, "error": str(e), "results": {}}

    out = {}
    pass_n = 0
    for q in QUERIES:
        r = post_ask(base, q, tier="fast")
        s = summarize(r)
        out[q] = s
        src = s.get("source") or "?"
        # App expects indian-stock-llm* (or education/rule paths) for tier=fast when working.
        good_src = src.startswith("indian-stock") or src in {
            "education",
            "indian-stock-llm",
            "indian-stock-llm-education",
            "indian-stock-llm-indicator",
            "clarifier",
        }
        ok = bool(s.get("ok") and ans_ok(s) and (good_src or src in {"groq", "gemini", "rule-engine"}))
        if ok and good_src:
            pass_n += 1
        mark = "PASS" if ok else "FAIL"
        preview = (s.get("answer_preview") or s.get("error") or "")[:160]
        print(f"{mark} {s.get('ms'):5}ms src={src:28} conf={s.get('confidence')} | {q}")
        print(" ", preview)
    print(f"custom-llm-primary: {pass_n}/{len(QUERIES)}")
    return {
        "target": name,
        "base": base,
        "health_ok": True,
        "custom_llm_primary": pass_n,
        "total": len(QUERIES),
        "results": out,
    }


def ans_ok(s: dict) -> bool:
    prev = (s.get("answer_preview") or "").strip()
    if len(prev) < 40:
        return False
    low = prev.lower()
    if "error" in low and len(prev) < 80:
        return False
    return True


def main() -> int:
    targets = []
    if len(sys.argv) > 1:
        for i, arg in enumerate(sys.argv[1:]):
            targets.append((f"target-{i+1}", arg))
    else:
        targets = [
            ("local", "http://127.0.0.1:8000"),
            ("production", "https://bysel-backend.onrender.com"),
        ]

    report = []
    for name, base in targets:
        report.append(run_target(name, base))

    out_path = Path(__file__).resolve().parent / "app_llm_smoke.json"
    out_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"\nWrote {out_path}")
    # Exit non-zero only if local (first) fully fails health
    if report and not report[0].get("health_ok"):
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
