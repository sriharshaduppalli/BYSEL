#!/usr/bin/env python3
"""Direct ask_llm smoke for latest custom LLM features."""
from __future__ import annotations

import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.llm_integration import ask_llm, llm_available  # noqa: E402

QUERIES = [
    "What are market timings?",
    "What is CAS?",
    "Sentiment of TCS",
    "Support and resistance of BSE:500325",
    "Should I buy RELIANCE for swing trade?",
    "Market sentiment today",
]


def main() -> int:
    print("llm_available:", llm_available())
    fails = 0
    for q in QUERIES:
        t0 = time.time()
        r = ask_llm(q)
        ms = int((time.time() - t0) * 1000)
        if not r or not (r.get("answer") or "").strip():
            print(f"FAIL {ms}ms | {q}")
            fails += 1
            continue
        ans = (r.get("answer") or "").replace("\n", " ")
        print(
            f"OK {ms:5}ms conf={r.get('confidence')} src={r.get('source')} "
            f"intent={r.get('intent')} | {q}"
        )
        print(" ", ans[:200])
    print(f"done fails={fails}/{len(QUERIES)}")
    return 1 if fails else 0


if __name__ == "__main__":
    raise SystemExit(main())
