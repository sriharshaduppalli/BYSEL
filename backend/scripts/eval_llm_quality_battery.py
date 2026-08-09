#!/usr/bin/env python3
"""Quality battery: 40+ LLM queries + chained suggestion follow-ups.

Checks:
- non-empty readable answers (no raw grounding dumps)
- compare purity (only named tickers)
- holdings/context wrapper must not leak extra stocks
- intent-shaped content (levels / sentiment / literacy / plan)
- chained suggestions stay on-topic when clicked
"""
from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.ai_engine import _build_stock_suggestions, _extract_user_query  # noqa: E402
from app.llm_integration import ask_llm, llm_available  # noqa: E402
from app.stock_enricher import extract_all_symbols_from_query  # noqa: E402

BAD_DUMP = [
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
]

# Known liquid NSE names used as pollution in wrapped prompts.
POLLUTE = ["HCLTECH", "ICICIBANK", "WIPRO"]

# (query, kind, extra)
# kind: plan|sentiment|levels|compare|literacy|sector|indicator|general
CASES: list[tuple[str, str, dict]] = [
    ("Should I buy RELIANCE?", "plan", {"must": ["RELIANCE"]}),
    ("Should I buy TMPV for swing?", "plan", {"must": ["TMPV"]}),
    ("Analyze MARUTI", "plan", {"must": ["MARUTI"]}),
    ("INFY analysis", "plan", {"must": ["INFY"]}),
    ("HDFCBANK trade plan", "plan", {"must": ["HDFCBANK"]}),
    ("Should I buy or sell SBIN?", "plan", {"must": ["SBIN"]}),
    ("TCS buy or sell?", "plan", {"must": ["TCS"]}),
    ("About BEL", "plan", {"must": ["BEL"]}),
    ("KAYNES", "plan", {"must": ["KAYNES"]}),
    ("LTIM outlook", "plan", {"must": ["LTIM"]}),
    ("Sentiment of TCS", "sentiment", {"must": ["TCS"]}),
    ("Bajaj Finance sentiment", "sentiment", {"must": ["BAJFINANCE"]}),
    ("Market sentiment today", "sentiment", {}),
    ("RELIANCE news sentiment", "sentiment", {"must": ["RELIANCE"]}),
    ("Support and resistance of TCS", "levels", {"must": ["TCS", "support"]}),
    ("Key levels for MARUTI", "levels", {"must": ["MARUTI"]}),
    ("RSI of RELIANCE", "indicator", {"must": ["RELIANCE", "rsi"]}),
    ("MACD of HDFCBANK", "indicator", {"must": ["HDFCBANK"]}),
    ("PE ratio of WIPRO", "indicator", {"must": ["WIPRO"]}),
    ("Compare TCS and INFY", "compare", {"pair": ["TCS", "INFY"]}),
    ("Compare TMPV with MARUTI", "compare", {"pair": ["TMPV", "MARUTI"]}),
    ("Compare RELIANCE vs TATASTEEL", "compare", {"pair": ["RELIANCE", "TATASTEEL"]}),
    ("HDFCBANK vs ICICIBANK", "compare", {"pair": ["HDFCBANK", "ICICIBANK"]}),
    ("Compare HAL and BEL", "compare", {"pair": ["HAL", "BEL"]}),
    # Wrapped Android-style prompts (holdings pollution)
    (
        "user_query:compare TMPV with MARUTI | context:holdings=HCLTECH:10@1400;ICICIBANK:5@1100,wallet=50000,symbol=HCLTECH,price=1400",
        "compare_wrapped",
        {"pair": ["TMPV", "MARUTI"], "forbid": POLLUTE},
    ),
    (
        "user_query:Should I buy RELIANCE? | context:holdings=HCLTECH:2@100,ICICIBANK:3@200,symbol=WIPRO,price=250",
        "plan_wrapped",
        {"must": ["RELIANCE"], "forbid": ["HCLTECH", "ICICIBANK", "WIPRO"]},
    ),
    (
        "user_query:Sentiment of MARUTI | context:holdings=TCS:1@1,symbol=TCS,price=1",
        "sentiment_wrapped",
        {"must": ["MARUTI"], "forbid": ["TCS"]},
    ),
    ("Top defence stocks in India", "sector", {"must_any": ["HAL", "BEL", "defence", "defense"]}),
    ("Best IT stocks for swing", "sector", {"must_any": ["TCS", "INFY", "HCLTECH", "IT"]}),
    ("What is RSI?", "literacy", {"forbid_plan": True}),
    ("What is PE ratio?", "literacy", {"forbid_plan": True}),
    ("What are market timings?", "literacy", {"forbid_plan": True}),
    ("How does T+1 settlement work?", "literacy", {"forbid_plan": True}),
    ("Explain circuit limits on NSE", "literacy", {"forbid_plan": True}),
    ("What is CAS?", "literacy", {"forbid_plan": True}),
    ("Nifty outlook today", "general", {}),
    ("Is WIPRO overbought?", "plan", {"must": ["WIPRO"]}),
    ("TATASTEEL price action", "plan", {"must": ["TATASTEEL"]}),
    ("Stop loss for INFY swing trade", "plan", {"must": ["INFY"]}),
    ("Fair value for HDFCBANK", "plan", {"must": ["HDFCBANK"]}),
    ("Risks in TMPV right now", "plan", {"must": ["TMPV"]}),
    ("Should I wait for a dip in MARUTI?", "plan", {"must": ["MARUTI"]}),
    # --- Expanded coverage ---
    ("Should I buy TITAN?", "plan", {"must": ["TITAN"]}),
    ("Analyze ASIANPAINT", "plan", {"must": ["ASIANPAINT"]}),
    ("NTPC buy or sell?", "plan", {"must": ["NTPC"]}),
    ("POWERGRID outlook", "plan", {"must": ["POWERGRID"]}),
    ("About COALINDIA", "plan", {"must": ["COALINDIA"]}),
    ("Should I accumulate AXISBANK on dips?", "plan", {"must": ["AXISBANK"]}),
    ("KOTAKBANK trade plan", "plan", {"must": ["KOTAKBANK"]}),
    ("DRREDDY analysis", "plan", {"must": ["DRREDDY"]}),
    ("CIPLA risks right now", "plan", {"must": ["CIPLA"]}),
    ("ITC long term view", "plan", {"must": ["ITC"]}),
    ("Sentiment of HDFCBANK", "sentiment", {"must": ["HDFCBANK"]}),
    ("SBIN news sentiment", "sentiment", {"must": ["SBIN"]}),
    ("BEL market mood", "sentiment", {"must_any": ["BEL", "sentiment", "bullish", "bearish", "neutral"]}),
    ("Support and resistance for RELIANCE", "levels", {"must": ["RELIANCE"]}),
    ("Trading levels of INFY", "levels", {"must": ["INFY"]}),
    ("RSI of TCS", "indicator", {"must": ["TCS", "rsi"]}),
    ("MACD of SBIN", "indicator", {"must": ["SBIN"]}),
    ("PE ratio of TCS", "indicator", {"must": ["TCS"]}),
    ("Compare INFY vs WIPRO", "compare", {"pair": ["INFY", "WIPRO"]}),
    ("Compare SBIN with HDFCBANK", "compare", {"pair": ["SBIN", "HDFCBANK"]}),
    ("AXISBANK vs KOTAKBANK", "compare", {"pair": ["AXISBANK", "KOTAKBANK"]}),
    ("Compare SUNPHARMA and CIPLA", "compare", {"pair": ["SUNPHARMA", "CIPLA"]}),
    ("M&M vs TMPV", "compare", {"pair": ["M&M", "TMPV"]}),
    (
        "user_query:Compare INFY vs WIPRO | context:holdings=RELIANCE:1@1;TCS:2@2,symbol=RELIANCE,price=1",
        "compare_wrapped",
        {"pair": ["INFY", "WIPRO"], "forbid": ["RELIANCE", "TCS"]},
    ),
    (
        "user_query:Should I buy HAL? | context:holdings=SHOULD:1@1,symbol=SHOULD,price=1",
        "plan_wrapped",
        {"must": ["HAL"], "forbid": ["SHOULD"]},
    ),
    ("Top pharma stocks", "sector", {"must_any": ["SUNPHARMA", "CIPLA", "DRREDDY", "pharma"]}),
    ("Best banking stocks", "sector", {"must_any": ["HDFCBANK", "ICICIBANK", "SBIN", "bank"]}),
    ("What is MACD?", "literacy", {"forbid_plan": True}),
    ("What is GTT order?", "literacy", {"forbid_plan": True}),
    ("Explain F&O basics", "literacy", {"forbid_plan": True}),
    ("What is demat account?", "literacy", {"forbid_plan": True}),
    ("Entry and target for RELIANCE swing", "plan", {"must": ["RELIANCE"]}),
    ("Take profit for TCS", "plan", {"must": ["TCS"]}),
    ("Is TATAMOTORS same as TMPV?", "plan", {"must_any": ["TMPV", "TATAMOTORS"]}),
]


def _ascii(s: str) -> str:
    return (s or "").encode("ascii", "replace").decode("ascii")


def _has_dump(answer: str) -> list[str]:
    return [p for p in BAD_DUMP if re.search(p, answer or "")]


def _mentioned_tickers(answer: str) -> set[str]:
    # Crude: uppercase tokens that look like NSE symbols.
    toks = set(re.findall(r"\b[A-Z][A-Z0-9\-]{1,11}\b", answer or ""))
    skip = {
        "RSI", "MACD", "SMA", "EMA", "ATR", "ADX", "VWAP", "PE", "PB", "ROE", "EPS",
        "NSE", "BSE", "NIFTY", "INDIA", "OK", "BUY", "SELL", "HOLD", "WAIT", "TRIM",
        "ACTION", "ENTRY", "STOP", "TARGET", "BIAS", "DIRECT", "ANSWER", "SENTIMENT",
        "BULLISH", "BEARISH", "NEUTRAL", "COMPARISON", "SCORECARD", "FUNDAMENTAL",
        "TECHNICAL", "EDUCATIONAL", "BYSEL", "LLM", "PAPER", "NOTES", "TREND",
        "PRICE", "STOCK", "MCAP", "DIV", "ROE", "VS", "AND", "WITH", "FOR", "THE",
        "A", "B", "C", "N/A", "NA", "OK", "P0", "RS", "BETA",
    }
    return {t for t in toks if t not in skip and len(t) >= 3}


def _check(case_kind: str, query: str, answer: str, extra: dict) -> list[str]:
    misses: list[str] = []
    low = (answer or "").lower()
    up = (answer or "").upper()
    user_q = _extract_user_query(query)

    dumps = _has_dump(answer)
    if dumps:
        misses.append("dump:" + dumps[0])

    if not (answer or "").strip() or len((answer or "").strip()) < 40:
        misses.append("too_short")

    for m in extra.get("must") or []:
        if m.lower() not in low and m.upper() not in up:
            misses.append(f"missing:{m}")

    if extra.get("must_any"):
        if not any(x.lower() in low or x.upper() in up for x in extra["must_any"]):
            misses.append("missing_any:" + ",".join(extra["must_any"][:3]))

    for f in extra.get("forbid") or []:
        # Allow forbid ticker only if it was in the user question.
        if f.upper() in extract_all_symbols_from_query(user_q):
            continue
        if re.search(rf"\b{re.escape(f)}\b", up):
            misses.append(f"leak:{f}")

    if case_kind.startswith("compare"):
        pair = [p.upper() for p in (extra.get("pair") or [])]
        for p in pair:
            if p not in up:
                misses.append(f"compare_missing:{p}")
        if "compar" not in low and "|" not in answer:
            misses.append("compare_shape")
        # Extra tickers beyond the pair (+ a few benign tokens) = leak
        mentioned = _mentioned_tickers(answer)
        allowed = set(pair) | set(extra.get("forbid") or [])  # forbid checked separately
        # Also allow sector peers only if not present — strict: only pair
        extras = mentioned - set(pair)
        # Filter common false positives already mostly skipped
        bad_extras = [e for e in extras if e in POLLUTE or e in {"TCS", "INFY", "WIPRO", "RELIANCE"} and e not in pair]
        # For TMPV/MARUTI specifically flag POLLUTE; for others only POLLUTE + obvious wrong
        if case_kind == "compare_wrapped" or pair == ["TMPV", "MARUTI"]:
            bad_extras = [e for e in extras if e not in pair]
            # Keep only clear liquid-name leaks
            liquid = {
                "HCLTECH", "ICICIBANK", "WIPRO", "TCS", "INFY", "RELIANCE", "HDFCBANK",
                "SBIN", "BAJFINANCE", "LTIM", "TECHM",
            }
            bad_extras = [e for e in bad_extras if e in liquid]
        else:
            bad_extras = [e for e in extras if e in POLLUTE]
        for e in bad_extras[:3]:
            misses.append(f"compare_extra:{e}")

    if case_kind.startswith("sentiment"):
        if "sentiment" not in low and "bullish" not in low and "bearish" not in low and "neutral" not in low:
            misses.append("sentiment_shape")

    if case_kind == "levels":
        if "support" not in low and "resistance" not in low and "level" not in low:
            misses.append("levels_shape")

    if case_kind == "literacy" and extra.get("forbid_plan"):
        if "paper-practice trade plan" in low or "action: **buy" in low:
            misses.append("literacy_looks_like_trade")

    if case_kind.startswith("plan"):
        planish = any(x in low for x in ("action", "entry", "stop", "target", "bias", "direct answer", "trade"))
        if not planish and "rsi" not in low:
            misses.append("plan_shape")

    # Catch English-word "symbols" becoming the answer header (e.g. **SHOULD**).
    header = re.match(r"\*\*([A-Z][A-Z0-9\-]{1,11})\*\*", (answer or "").strip())
    if header:
        fake = header.group(1)
        if fake in {
            "SHOULD", "COULD", "WOULD", "MIGHT", "SHALL", "MUST", "NEED",
            "WANT", "PLEASE", "ABOUT", "AFTER", "BEFORE", "CONTEXT", "HOLDINGS",
        }:
            misses.append(f"fake_symbol_header:{fake}")

    return misses


def _chain_followups(seed_query: str, seed_answer: str, symbol: str | None) -> list[str]:
    """Build chained clicks: server-style suggestions + one compare suggestion."""
    tips = []
    if symbol:
        tips.extend(_build_stock_suggestions(symbol, exclude="")[:4])
    # Mimic common chained suggestion from a TMPV/MARUTI analysis
    if symbol == "TMPV":
        tips.append("Compare TMPV with MARUTI")
    if symbol == "TCS":
        tips.append("Compare TCS and INFY")
    if symbol == "RELIANCE":
        tips.append("Compare RELIANCE vs TATASTEEL")
    if symbol == "SBIN":
        tips.append("Compare SBIN with HDFCBANK")
    # Dedup
    out = []
    seen = set()
    for t in tips:
        k = t.lower().strip()
        if k in seen:
            continue
        seen.add(k)
        out.append(t)
    return out[:5]


def _primary_named(user_q: str, fallback: str | None = None) -> list[str]:
    named = extract_all_symbols_from_query(user_q)
    fake = {
        "SHOULD", "COULD", "WOULD", "MIGHT", "SHALL", "MUST", "NEED", "WANT",
        "PLEASE", "ABOUT", "CONTEXT", "HOLDINGS", "WALLET", "SYMBOL", "PRICE",
    }
    named = [n for n in named if n and n.upper() not in fake]
    if named:
        return named
    return [fallback] if fallback else []


def _eval_one(query: str, kind: str, extra: dict, rows: list, *, seed: str | None = None) -> bool:
    user_q = _extract_user_query(query)
    named = _primary_named(user_q)
    t0 = time.time()
    try:
        ctx = {"symbol": named[0], "all_symbols": named} if named else None
        r = ask_llm(user_q, context=ctx)
    except Exception as exc:
        ms = int((time.time() - t0) * 1000)
        print(f"ERR  {ms:5}ms | {kind} | {user_q} | {exc}")
        rows.append({"query": user_q, "kind": kind, "ok": False, "error": str(exc), "ms": ms, "seed": seed})
        return False
    ms = int((time.time() - t0) * 1000)
    ans = ((r or {}).get("answer") or "").strip()
    check_kind = "compare_wrapped" if kind in {"chain_compare", "hop_compare"} else kind
    misses = _check(check_kind, query, ans, extra)
    ok = not misses
    status = "PASS" if ok else "FAIL"
    label = f"chain from [{seed}] -> " if seed else ""
    print(f"{status} {ms:5}ms conf={(r or {}).get('confidence')} | {kind} | {label}{user_q}")
    if misses:
        print("  MISS:", misses)
    print(" ", _ascii(ans.replace("\n", " ")[:200]))
    print()
    rows.append(
        {
            "query": user_q,
            "raw_query": query if query != user_q else None,
            "kind": kind,
            "seed": seed,
            "ok": ok,
            "misses": misses,
            "ms": ms,
            "confidence": (r or {}).get("confidence"),
            "intent": (r or {}).get("intent"),
            "answer_preview": ans[:400],
        }
    )
    return ok


def main() -> int:
    print("llm_available:", llm_available())
    rows: list[dict] = []
    fail = 0
    t_all = time.time()

    # --- Primary battery ---
    print(f"\n=== PRIMARY CASES ({len(CASES)}) ===\n")
    for query, kind, extra in CASES:
        if not _eval_one(query, kind, extra, rows):
            fail += 1

    # --- One-hop chained suggestion follow-ups ---
    chain_seeds = [
        ("Analyze TMPV", "TMPV"),
        ("Should I buy TCS?", "TCS"),
        ("Sentiment of MARUTI", "MARUTI"),
        ("Analyze HAL", "HAL"),
        ("Should I buy RELIANCE?", "RELIANCE"),
        ("Analyze SBIN", "SBIN"),
        ("Sentiment of INFY", "INFY"),
        ("About BEL", "BEL"),
    ]
    print("\n=== CHAINED SUGGESTIONS (1-hop) ===\n")
    for seed_q, sym in chain_seeds:
        r0 = ask_llm(seed_q, context={"symbol": sym, "all_symbols": [sym]})
        ans0 = ((r0 or {}).get("answer") or "").strip()
        followups = _chain_followups(seed_q, ans0, sym)
        wrapped_follow = (
            f"user_query:{followups[0]} | context:holdings=HCLTECH:1@1;ICICIBANK:1@1,symbol=HCLTECH"
            if followups
            else None
        )
        chain_list = list(followups[:3])
        if wrapped_follow:
            chain_list.append(wrapped_follow)

        for fq in chain_list:
            user_q = _extract_user_query(fq)
            named = _primary_named(user_q, sym)
            if re.search(r"\bcompare\b|\bvs\b", user_q, re.I):
                pair = named[:2] if len(named) >= 2 else named
                extra = {"pair": pair, "forbid": POLLUTE}
                kind = "chain_compare"
            else:
                extra = {"must": named[:1] if named else [sym], "forbid": POLLUTE}
                kind = "chain"
            if not _eval_one(fq, kind, extra, rows, seed=seed_q):
                fail += 1

    # --- Multi-hop series: seed -> tip1 -> tip2 (suggestion of tip1) ---
    hop_seeds = [
        ("Analyze TMPV", "TMPV", "Compare TMPV with MARUTI"),
        ("Should I buy TCS?", "TCS", "Support and resistance for TCS"),
        ("Analyze RELIANCE", "RELIANCE", "Stop loss for RELIANCE swing trade"),
        ("Sentiment of SBIN", "SBIN", "Compare SBIN with HDFCBANK"),
        ("Analyze HAL", "HAL", "Should I buy HAL?"),
    ]
    print("\n=== MULTI-HOP SERIES (seed -> A -> B) ===\n")
    for seed_q, sym, forced_second in hop_seeds:
        # Hop 0 already implied; hop 1 = first stock suggestion; hop 2 = forced/compare/levels
        hop1_list = _chain_followups(seed_q, "", sym)
        hop1 = next((t for t in hop1_list if "compare" not in t.lower()), hop1_list[0] if hop1_list else f"Analyze {sym}")
        series = [seed_q, hop1, forced_second]
        path = seed_q
        for i, q in enumerate(series):
            user_q = _extract_user_query(q)
            named = _primary_named(user_q, sym)
            if re.search(r"\bcompare\b|\bvs\b", user_q, re.I):
                extra = {"pair": named[:2] if len(named) >= 2 else named, "forbid": POLLUTE}
                kind = "hop_compare"
            elif i == 0:
                extra = {"must": [sym], "forbid": POLLUTE}
                kind = "hop0"
            else:
                extra = {"must": named[:1] if named else [sym], "forbid": POLLUTE}
                kind = f"hop{i}"
            # Also test hop2 with holdings wrapper once
            raw = q
            if i == 2 and "compare" in q.lower():
                raw = (
                    f"user_query:{q} | context:holdings=HCLTECH:1@1;ICICIBANK:1@1,symbol=HCLTECH,price=1"
                )
                kind = "hop_compare"
                extra = {
                    "pair": named[:2] if len(named) >= 2 else named,
                    "forbid": POLLUTE,
                }
            if not _eval_one(raw, kind, extra, rows, seed=path):
                fail += 1
            path = f"{path} -> {user_q}"

    total = len(rows)
    passed = sum(1 for r in rows if r.get("ok"))
    out = {
        "total": total,
        "passed": passed,
        "failed": total - passed,
        "pass_rate": round(100.0 * passed / total, 1) if total else 0,
        "elapsed_s": round(time.time() - t_all, 1),
        "rows": rows,
    }
    out_path = ROOT / "scripts" / "eval_llm_quality_results.json"
    out_path.write_text(json.dumps(out, indent=2), encoding="utf-8")
    print(
        f"\nSUMMARY  {passed}/{total} passed ({out['pass_rate']}%)  "
        f"failed={out['failed']}  elapsed={out['elapsed_s']}s"
    )
    print(f"Wrote {out_path}")
    fails = [r for r in rows if not r.get("ok")]
    if fails:
        print("\nFAILURES:")
        for r in fails:
            print(" -", r.get("kind"), "|", r.get("query"), "|", r.get("misses") or r.get("error"))
    return 0 if out["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
