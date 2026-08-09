#!/usr/bin/env python3
"""Batch smoke: confirm AI answers are readable (not raw grounding dumps)."""
from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.llm_integration import ask_llm, llm_available  # noqa: E402

# Bad patterns from the old fallback dump
BAD = [
    r"Live quote snapshot for",
    r"Full math for \w+:",
    r"Matched instrument:",
    r"Live enrich \w+:",
    r"\| Wilder RSI\(14\)=",
    r"data_degraded=",
    r"TRADE PLAN action=",
    r"RISK VaR1d95",
    r"• \*\*Live quote ",
    r"• \*\*Quantitative \+ trade plan",
    r"• \*\*Live enrich ",
]

QUERIES: list[tuple[str, dict | None, set[str]]] = [
    # (query, context, expected_tags)
    # tags: stock_plan | sentiment | literacy | levels | compare | no_dump
    ("KAYNES", None, {"stock_plan", "no_dump"}),
    ("Should I buy KAYNES?", None, {"stock_plan", "sentiment", "no_dump"}),
    ("Sentiment of TCS", None, {"sentiment", "no_dump"}),
    ("Should I buy RELIANCE for swing trade?", None, {"stock_plan", "sentiment", "no_dump"}),
    ("INFY analysis", None, {"stock_plan", "no_dump"}),
    ("HDFCBANK", None, {"stock_plan", "no_dump"}),
    ("Is WIPRO overbought?", None, {"stock_plan", "no_dump"}),
    ("Support and resistance of TCS", None, {"levels", "no_dump"}),
    ("PE ratio of WIPRO", None, {"no_dump"}),
    ("MACD of HDFCBANK", None, {"no_dump"}),
    ("RSI of RELIANCE", None, {"no_dump"}),
    ("Compare TCS and INFY", None, {"compare", "no_dump"}),
    ("Compare RELIANCE vs TATASTEEL", None, {"compare", "no_dump"}),
    ("Top defence stocks in India", None, {"no_dump"}),
    ("Best IT stocks for swing", None, {"no_dump"}),
    ("Nifty outlook today", None, {"no_dump"}),
    ("Market sentiment today", None, {"sentiment", "no_dump"}),
    ("What is RSI?", None, {"literacy", "no_dump"}),
    ("What is PE ratio?", None, {"literacy", "no_dump"}),
    ("What are market timings?", None, {"literacy", "no_dump"}),
    ("How does T+1 settlement work?", None, {"literacy", "no_dump"}),
    ("Explain circuit limits on NSE", None, {"literacy", "no_dump"}),
    ("What is CAS?", None, {"literacy", "no_dump"}),
    ("Should I buy SBIN?", None, {"stock_plan", "sentiment", "no_dump"}),
    ("ICICIBANK trade plan", None, {"stock_plan", "no_dump"}),
    ("Bajaj Finance sentiment", None, {"sentiment", "no_dump"}),
    ("LTIM buy or sell?", None, {"stock_plan", "no_dump"}),
    ("full math for KAYNES", None, {"no_dump"}),  # full math OK, but not Live-quote dump
    ("About BEL", None, {"stock_plan", "no_dump"}),
    ("TATASTEEL price action", None, {"stock_plan", "no_dump"}),
]


def _has_dump(answer: str) -> list[str]:
    hits = []
    for pat in BAD:
        if re.search(pat, answer):
            hits.append(pat)
    # Classic dump header with bullet grounding stack
    if "**Indian market knowledge**" in answer and "Live quote" in answer:
        hits.append("Indian market knowledge + Live quote dump")
    return hits


def _check_tags(answer: str, tags: set[str], query: str) -> list[str]:
    misses: list[str] = []
    low = answer.lower()
    if "stock_plan" in tags:
        # Readable plan signals
        planish = any(
            x in low
            for x in (
                "direct answer",
                "action:",
                "entry",
                "stop",
                "target",
                "paper-practice",
                "trade plan",
                "bias",
            )
        )
        if not planish:
            misses.append("stock_plan")
    if "sentiment" in tags:
        if "sentiment" not in low and "bullish" not in low and "bearish" not in low:
            misses.append("sentiment")
    if "literacy" in tags:
        # Should not look like a stock trade dump
        if "live quote snapshot" in low:
            misses.append("literacy_clean")
    if "levels" in tags:
        if "support" not in low and "resistance" not in low:
            misses.append("levels")
    if "compare" in tags:
        if "compar" not in low and " vs " not in low and "versus" not in low:
            # still ok if both tickers discussed
            tickers = re.findall(r"\b[A-Z]{2,15}\b", query.upper())
            if not any(t in answer.upper() for t in tickers[:2]):
                misses.append("compare")
    if "no_dump" in tags:
        dumps = _has_dump(answer)
        if dumps:
            misses.append("no_dump:" + ",".join(dumps[:3]))
    return misses


def main() -> int:
    print("llm_available:", llm_available())
    rows = []
    fail = 0
    dump_fail = 0
    tag_fail = 0
    empty = 0

    for q, ctx, tags in QUERIES:
        t0 = time.time()
        try:
            r = ask_llm(q, context=ctx)
        except Exception as exc:
            ms = int((time.time() - t0) * 1000)
            print(f"ERR  {ms:5}ms | {q} | {exc}")
            fail += 1
            rows.append({"query": q, "ok": False, "error": str(exc), "ms": ms})
            continue
        ms = int((time.time() - t0) * 1000)
        if not r or not (r.get("answer") or "").strip():
            print(f"EMPTY {ms:5}ms | {q}")
            empty += 1
            fail += 1
            rows.append({"query": q, "ok": False, "empty": True, "ms": ms})
            continue

        ans = (r.get("answer") or "").strip()
        dumps = _has_dump(ans)
        misses = _check_tags(ans, tags, q)
        ok = not dumps and not misses
        if dumps:
            dump_fail += 1
            fail += 1
        elif misses:
            tag_fail += 1
            fail += 1

        preview = ans.replace("\n", " ")[:220]
        # Keep console ASCII-safe on Windows
        preview_safe = preview.encode("ascii", "replace").decode("ascii")
        status = "PASS" if ok else "FAIL"
        print(
            f"{status} {ms:5}ms conf={r.get('confidence')} src={r.get('source')} "
            f"intent={r.get('intent')} | {q}"
        )
        if dumps:
            print("  DUMP:", dumps[:4])
        if misses:
            print("  MISS:", misses)
        print(" ", preview_safe)
        print()

        rows.append(
            {
                "query": q,
                "ok": ok,
                "ms": ms,
                "confidence": r.get("confidence"),
                "source": r.get("source"),
                "intent": r.get("intent"),
                "dump_hits": dumps,
                "tag_misses": misses,
                "answer_preview": ans[:500],
                "answer": ans,
            }
        )

    summary = {
        "total": len(QUERIES),
        "pass": len(QUERIES) - fail,
        "fail": fail,
        "empty": empty,
        "dump_fail": dump_fail,
        "tag_fail": tag_fail,
        "llm_available": llm_available(),
    }
    out = {"summary": summary, "rows": rows}
    path = Path(__file__).resolve().parent / "readable_answers_smoke.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print("=" * 60)
    print(
        f"SUMMARY total={summary['total']} pass={summary['pass']} fail={summary['fail']} "
        f"empty={empty} dump_fail={dump_fail} tag_fail={tag_fail}"
    )
    print("Wrote", path)
    return 0 if fail == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
