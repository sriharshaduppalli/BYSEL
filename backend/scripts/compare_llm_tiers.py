#!/usr/bin/env python3
"""Side-by-side comparison: Groq vs Gemini vs Indian Stock LLM."""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from pathlib import Path

BASE = "https://bysel-backend.onrender.com"
QUERIES = [
    "What is RSI?",
    "Should I buy RELIANCE for swing trade?",
    "Compare TCS and INFY",
    "Top defence stocks in India",
    "Explain circuit limits on NSE",
    "What is PE ratio of WIPRO?",
    "MACD of HDFCBANK",
    "How does T+1 settlement work?",
]
TIERS = ["groq", "gemini", "indian-stock-llm"]


def ask(query: str, tier: str, timeout: int = 90) -> dict:
    payload = json.dumps(
        {"query": query, "tier": tier, "conversation_history": []}
    ).encode()
    req = urllib.request.Request(
        f"{BASE}/ai/ask",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = json.loads(resp.read().decode())
        return {
            "ok": True,
            "ms": int((time.time() - t0) * 1000),
            "source": data.get("source"),
            "answer": (data.get("answer") or "").strip(),
            "symbol": data.get("symbol"),
            "confidence": data.get("confidence"),
        }
    except Exception as exc:  # noqa: BLE001
        return {
            "ok": False,
            "ms": int((time.time() - t0) * 1000),
            "error": str(exc)[:240],
        }


def score_answer(answer: str, query: str) -> dict:
    """Heuristic quality signals (not LLM-as-judge)."""
    a = (answer or "").strip()
    al = a.lower()
    ql = query.lower()
    checks = {
        "non_empty": bool(a),
        "has_structure": any(x in a for x in ("\n", "•", "-", "1.", "**")),
        "mentions_disclaimer": any(
            x in al for x in ("disclaimer", "educational", "not investment advice", "paper")
        ),
        "not_low_conf_withhold": "low-confidence response" not in al
        and "could not find enough domain context" not in al,
        "actionable_levels": any(
            x in al for x in ("support", "resistance", "stop", "target", "entry", "rsi", "pe", "macd")
        )
        if any(k in ql for k in ("buy", "sell", "swing", "compare", "macd", "pe", "reliance", "wipro", "hdfc"))
        else True,
        "named_tickers": True,
    }
    if "compare" in ql or "defence" in ql or "defense" in ql:
        checks["named_tickers"] = sum(
            1 for t in ("tcs", "infy", "reliance", "hdfc", "bel", "hal", "bharat", "mazdock", "wipro")
            if t in al
        ) >= 2
    score = sum(1 for v in checks.values() if v) / max(1, len(checks))
    return {"score": round(score, 3), "checks": checks, "chars": len(a)}


def main() -> None:
    try:
        urllib.request.urlopen(f"{BASE}/health", timeout=60).read()
        print("Backend awake")
    except Exception as exc:  # noqa: BLE001
        print("Wake warning:", exc)

    results: dict = {}
    for q in QUERIES:
        results[q] = {}
        print("\nQ:", q)
        for tier in TIERS:
            r = ask(q, tier)
            if r.get("ok"):
                r["quality"] = score_answer(r.get("answer") or "", q)
                preview = (r["answer"] or "").replace("\n", " ")[:160]
                print(
                    f"  {tier}: source={r.get('source')} {r['ms']}ms "
                    f"q={r['quality']['score']} len={len(r['answer'])} | {preview}"
                )
            else:
                print(f"  {tier}: FAIL {r.get('error')} ({r['ms']}ms)")
            results[q][tier] = r

    # Aggregate
    summary = {}
    for tier in TIERS:
        rows = [results[q][tier] for q in QUERIES if results[q].get(tier, {}).get("ok")]
        if not rows:
            summary[tier] = {"n": 0}
            continue
        summary[tier] = {
            "n": len(rows),
            "avg_ms": int(sum(r["ms"] for r in rows) / len(rows)),
            "avg_quality": round(sum(r["quality"]["score"] for r in rows) / len(rows), 3),
            "avg_chars": int(sum(len(r.get("answer") or "") for r in rows) / len(rows)),
            "sources": sorted({r.get("source") for r in rows}),
        }
    results["_summary"] = summary
    print("\nSUMMARY")
    print(json.dumps(summary, indent=2))

    out = Path(__file__).resolve().parent / "llm_tier_comparison.json"
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Wrote", out)


if __name__ == "__main__":
    main()
