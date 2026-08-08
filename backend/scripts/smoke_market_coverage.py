#!/usr/bin/env python3
"""Smoke: broad Indian market query coverage for education + ISM asks."""
from __future__ import annotations

import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.llm_integration import ask_llm, llm_available  # noqa: E402
from app.market_education import get_education_answer  # noqa: E402

QUERIES = [
    ("How to open a demat account in India?", ["demat", "kyc"]),
    ("What is the difference between delivery and intraday CNC vs MIS?", ["cnc", "mis", "delivery"]),
    ("What are brokerage and other charges on NSE equity trades?", ["brokerage", "stt"]),
    ("What is a GTT order?", ["gtt", "trigger"]),
    ("What is pledge for margin?", ["pledge", "margin"]),
    ("What is short delivery and auction market?", ["auction", "delivery"]),
    ("Explain IPO allotment and ASBA for retail", ["ipo", "asba"]),
    ("STCG vs LTCG on listed equity", ["stcg", "ltcg", "12 month"]),
    ("What happens in a bonus issue?", ["bonus"]),
    ("What is a stock split?", ["split"]),
    ("What is a rights issue?", ["rights"]),
    ("FII vs DII — how do flows affect Nifty?", ["fii", "dii"]),
    ("What is lot size and margin for Nifty futures?", ["lot", "margin"]),
    ("Circuit limit — can I exit on upper circuit?", ["circuit"]),
    ("How do SIPs and mutual fund NAV work?", ["sip", "nav", "mutual"]),
    ("What is SEBI investor protection?", ["sebi"]),
    ("What are market timings?", ["9:15", "cas"]),
    ("What is RSI?", ["rsi"]),
    ("Corporate actions in Indian stocks", ["dividend", "bonus", "corporate"]),
    ("Identifying chart patterns with technical analysis", ["pattern", "breakout"]),
    ("What is a head and shoulders pattern?", ["neckline", "shoulder"]),
    ("How to trade breakouts and false breakouts?", ["false breakout", "protective"]),
    ("What is a double top?", ["double top", "breakout"]),
    ("Explain cup and handle pattern", ["cup", "handle"]),
    ("What is a harami candlestick?", ["harami", "spinning"]),
]


def main() -> int:
    print("llm_available:", llm_available())
    fail = 0
    for q, needles in QUERIES:
        t0 = time.time()
        edu = get_education_answer(q)
        r = ask_llm(q) if not edu else {
            "answer": edu,
            "confidence": 0.94,
            "source": "indian-stock-llm-education",
            "intent": "market_literacy",
        }
        # Prefer full ask_llm path so we see real routing (education short-circuit inside).
        r = ask_llm(q)
        ms = int((time.time() - t0) * 1000)
        ans = ((r or {}).get("answer") or "").strip()
        low = ans.lower()
        hit = any(n.lower() in low for n in needles)
        dump = "live quote snapshot" in low or "full math for" in low
        ok = bool(ans) and hit and not dump and len(ans) > 80
        status = "PASS" if ok else "FAIL"
        if not ok:
            fail += 1
        preview = ans.replace("\n", " ")[:180].encode("ascii", "replace").decode()
        print(
            f"{status} {ms:5}ms src={r.get('source') if r else None} "
            f"conf={r.get('confidence') if r else None} | {q}"
        )
        print(" ", preview)
        print()
    print(f"SUMMARY pass={len(QUERIES) - fail}/{len(QUERIES)} fail={fail}")
    return 1 if fail else 0


if __name__ == "__main__":
    raise SystemExit(main())
