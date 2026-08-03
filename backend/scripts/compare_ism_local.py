#!/usr/bin/env python3
"""Local Indian Stock LLM smoke test + gap notes vs expected answer shapes."""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.llm_integration import ask_llm, llm_available  # noqa: E402

QUERIES = [
    ("What is RSI?", None),
    (
        "Should I buy RELIANCE for swing trade?",
        {
            "symbol": "RELIANCE",
            "current_price": 1319,
            "technical": {"rsi": 54.2, "trend": "sideways", "macd_hist": -0.8},
            "fundamental": {"pe": 26.4, "pb": 2.1, "roe": 9.5},
            "trading_levels": {"support": 1280, "resistance": 1360, "stop_loss": 1250},
        },
    ),
    (
        "Compare TCS and INFY",
        {
            "symbol": "TCS",
            "current_price": 2473,
            "technical": {"rsi": 61.0, "trend": "bullish"},
            "fundamental": {"pe": 28.5, "pb": 12.0, "roe": 45.0},
            "all_symbols": ["TCS", "INFY"],
        },
    ),
    ("Top defence stocks in India", None),
    ("Explain circuit limits on NSE", None),
    (
        "What is PE ratio of WIPRO?",
        {
            "symbol": "WIPRO",
            "current_price": 188.66,
            "fundamental": {"pe": 22.4, "pb": 3.1, "roe": 15.2, "eps": 8.4},
        },
    ),
    ("MACD of HDFCBANK", {"symbol": "HDFCBANK"}),
    ("How does T+1 settlement work?", None),
]


def main() -> None:
    print("llm_available:", llm_available())
    out = {}
    for q, ctx in QUERIES:
        t0 = time.time()
        r = ask_llm(q, context=ctx)
        ms = int((time.time() - t0) * 1000)
        if not r:
            print(f"FAIL {q}")
            out[q] = {"ok": False}
            continue
        ans = (r.get("answer") or "").strip()
        preview = ans.replace("\n", " ")[:180].encode("ascii", "replace").decode("ascii")
        print(
            f"{ms:5}ms conf={r.get('confidence')} src={r.get('source')} "
            f"intent={r.get('intent')} | {q}"
        )
        print(" ", preview)
        out[q] = {
            "ok": True,
            "ms": ms,
            "confidence": r.get("confidence"),
            "source": r.get("source"),
            "intent": r.get("intent"),
            "citations": r.get("citations"),
            "answer": ans,
            "answer_preview": ans[:400],
        }
    path = Path(__file__).resolve().parent / "ism_local_comparison.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Wrote", path)


if __name__ == "__main__":
    main()
