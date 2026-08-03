#!/usr/bin/env python3
"""Re-compare improved local Indian Stock LLM vs production Groq on sample NSE queries."""
from __future__ import annotations

import asyncio
import json
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

BASE = "https://bysel-backend.onrender.com"

QUERIES = [
    # ── Swing / stance ──────────────────────────────────────────
    "Should I buy RELIANCE for swing trade?",
    "Buy or sell ITC this week?",
    "Is SBIN overbought now?",
    "Should I buy HAL for long term?",
    "Is MARUTI good for swing trade?",
    "Buy or sell HDFCBANK now?",
    "Should I sell AXISBANK this week?",
    "Analyze TMPV for swing",
    "Analyze INFY",
    "Is TITAN a buy for swing?",
    "Should I accumulate SUNPHARMA?",
    "Buy or sell BAJFINANCE now?",
    # ── Sentiment ───────────────────────────────────────────────
    "Sentiment of TCS",
    "Sentiment of RELIANCE",
    "Market sentiment today",
    "What is sentiment analysis?",
    "Is news sentiment bullish for INFY?",
    # ── BSE / dual exchange ─────────────────────────────────────
    "Analyze 500325",
    "Support and resistance of BSE:500325",
    "Analyze BSE:500180",
    "Quote for 532540",
    # ── Fundamentals / levels / indicators ──────────────────────
    "What is the PE ratio of WIPRO?",
    "PE and PB of SBIN",
    "Dividend yield of ITC",
    "What is beta of INFY?",
    "ROE of TCS",
    "Support and resistance of TCS",
    "ATR stop for RELIANCE",
    "MACD of HDFCBANK",
    "RSI of TMPV",
    "Bollinger bands of ASIANPAINT",
    "Supertrend of INFY",
    "VWAP of RELIANCE",
    # ── Compare / screen ────────────────────────────────────────
    "Compare TCS and INFY for long term",
    "Compare RELIANCE and ONGC",
    "Compare WIPRO and HCLTECH",
    "Compare HDFCBANK vs ICICIBANK",
    "Top defence stocks in India",
    "Best IT stocks under Nifty",
    "Best banking stocks in India",
    "Pharma stocks to research",
    # ── Market / F&O / outlook ──────────────────────────────────
    "Nifty outlook",
    "BankNifty outlook",
    "Nifty futures outlook",
    "What is futures and options?",
    "Explain iron condor",
    "What is put call ratio?",
    # ── Literacy / personal finance ─────────────────────────────
    "What is technical analysis?",
    "Explain risk management in trading",
    "What is personal finance SIP?",
    "What is expense ratio?",
    "How does the stock market work?",
    "What is RSI?",
    "Explain candlestick patterns",
    "What is pair trading?",
]


def _ask_groq_remote(query: str, timeout: int = 90) -> dict:
    payload = json.dumps(
        {"query": query, "tier": "groq", "conversation_history": []}
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
        }
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "ms": int((time.time() - t0) * 1000), "error": str(exc)[:240]}


async def _enrich_for_query(query: str) -> dict:
    """Build the same enrich context /ai/ask would pass into ISM."""
    from app.stock_enricher import enrich, extract_symbol_from_query, extract_all_symbols_from_query

    symbol = extract_symbol_from_query(query)
    all_syms = extract_all_symbols_from_query(query) or ([] if not symbol else [symbol])
    ctx: dict = {"all_symbols": all_syms}
    if symbol:
        ctx["symbol"] = symbol
        try:
            live = await enrich(symbol)
            if live:
                ctx["current_price"] = live.get("current_price")
                ctx["company_name"] = live.get("company_name")
                ctx["sector"] = live.get("sector")
                ctx["technical"] = live.get("technical") or {}
                ctx["fundamental"] = live.get("fundamental") or {}
                ctx["trading_levels"] = live.get("trading_levels") or {}
                ctx["sentiment"] = live.get("sentiment") or {}
                if live.get("pre_signals"):
                    ctx["pre_signals"] = live["pre_signals"]
        except Exception as exc:  # noqa: BLE001
            ctx["enrich_error"] = str(exc)[:120]
    return ctx


def _ask_ism_local(query: str, ctx: dict) -> dict:
    from app.llm_integration import ask_llm

    t0 = time.time()
    try:
        r = ask_llm(query, context=ctx or None)
        if not r:
            return {"ok": False, "ms": int((time.time() - t0) * 1000), "error": "empty"}
        return {
            "ok": True,
            "ms": int((time.time() - t0) * 1000),
            "source": r.get("source"),
            "intent": r.get("intent"),
            "confidence": r.get("confidence"),
            "answer": (r.get("answer") or "").strip(),
            "citations": r.get("citations"),
        }
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "ms": int((time.time() - t0) * 1000), "error": str(exc)[:240]}


def _quality(answer: str, query: str) -> dict:
    a = (answer or "").strip()
    al = a.lower()
    ql = query.lower()
    checks = {
        "non_empty": len(a) > 40,
        "has_direct_or_structure": any(
            x in al for x in ("direct answer", "**", "•", "1.", "|", "snapshot", "compare", "sector")
        ),
        "not_withheld": "could not find enough" not in al and "low-confidence" not in al,
        "not_pure_glossary_when_stock_ask": True,
        "mentions_relevant_tickers": True,
        "has_numbers_or_levels": True,
        "actionable_or_educational": True,
    }

    stock_ask = any(
        k in ql
        for k in (
            "buy", "sell", "compare", "pe", "macd", "rsi", "overbought", "analyze",
            "defence", "defense", "it stocks", "reliance", "wipro", "tcs", "sbin", "itc", "hdfc",
            "sentiment", "500325", "bse:",
        )
    )
    if stock_ask and any(k in ql for k in ("buy", "sell", "swing", "overbought", "this week", "analyze")):
        checks["actionable_or_educational"] = any(
            k in al
            for k in (
                "buy", "hold", "sell", "avoid", "wait", "support", "resistance",
                "rsi", "stop", "sentiment", "bullish", "bearish", "neutral",
            )
        )
        checks["has_numbers_or_levels"] = any(ch.isdigit() for ch in a)

    if "pe" in ql and "wipro" in ql:
        checks["not_pure_glossary_when_stock_ask"] = "p/e =" not in al or "wipro" in al
        checks["has_numbers_or_levels"] = "wipro" in al and any(ch.isdigit() for ch in a)

    if "compare" in ql:
        checks["mentions_relevant_tickers"] = ("tcs" in al) and ("infy" in al or "infosys" in al or "peer" in al)

    if "defence" in ql or "defense" in ql:
        checks["mentions_relevant_tickers"] = sum(
            1 for t in ("hal", "bel", "bdl", "mazdock", "cochinship", "grse") if t in al
        ) >= 2

    if "macd" in ql:
        checks["has_numbers_or_levels"] = any(ch.isdigit() for ch in a) and "macd" in al

    if "sentiment" in ql:
        checks["actionable_or_educational"] = any(
            k in al for k in ("bullish", "bearish", "neutral", "sentiment", "news", "factor", "mood")
        )
        if "what is" in ql:
            checks["has_numbers_or_levels"] = True  # literacy may be text-only
            checks["not_pure_glossary_when_stock_ask"] = "sentiment" in al and len(a) > 120
        else:
            checks["has_numbers_or_levels"] = any(ch.isdigit() for ch in a) or "score" in al

    if "500325" in ql or "bse:" in ql:
        checks["mentions_relevant_tickers"] = any(
            t in al for t in ("500325", "reliance", "bse", "support", "resistance", "rsi")
        )
        checks["has_numbers_or_levels"] = any(ch.isdigit() for ch in a)

    if any(
        k in ql
        for k in (
            "what is", "explain", "technical analysis", "risk management", "personal finance", "futures and options",
        )
    ):
        checks["actionable_or_educational"] = len(a) > 120
        checks["has_numbers_or_levels"] = True

    score = sum(1 for v in checks.values() if v) / max(1, len(checks))
    return {"score": round(score, 3), "checks": checks, "chars": len(a)}


def _gap_notes(query: str, ism: dict, groq: dict) -> list[str]:
    gaps = []
    ia = (ism.get("answer") or "").lower()
    ga = (groq.get("answer") or "").lower()
    ql = query.lower()

    if not ism.get("ok"):
        gaps.append("ISM failed: " + str(ism.get("error")))
        return gaps
    if not groq.get("ok"):
        gaps.append("Groq failed: " + str(groq.get("error")))

    ism_q = (ism.get("quality") or {}).get("score", 0)
    groq_q = (groq.get("quality") or {}).get("score", 0) if groq.get("ok") else 0

    if "compare" in ql and ("infy" not in ia and "infosys" not in ia):
        gaps.append("ISM compare lacks second-name fundamentals (often only primary symbol enrich).")
    if ("buy" in ql or "sell" in ql) and "direct answer" in ia and "scenario" not in ia and len(ia) < 400:
        if groq.get("ok") and len(ga) > len(ia) * 1.4:
            gaps.append("ISM stance is shorter / less narrative than Groq (fewer catalysts & scenarios).")
    if "defence" in ql and "order" not in ia and groq.get("ok") and "order" in ga:
        gaps.append("ISM defence list is static universe; Groq may add order-book/news narrative.")
    if "it stocks" in ql or "best it" in ql:
        if "tcs" not in ia and "infy" not in ia:
            gaps.append("ISM IT screen weak / missed liquid IT names.")
    if ism_q + 0.05 < groq_q:
        gaps.append(f"Heuristic quality still behind Groq ({ism_q} vs {groq_q}).")
    if not gaps and ism_q >= groq_q:
        gaps.append("No major quality gap on heuristic checks for this query.")
    return gaps


async def main() -> None:
    try:
        urllib.request.urlopen(f"{BASE}/health", timeout=60).read()
        print("Backend awake", flush=True)
    except Exception as exc:  # noqa: BLE001
        print("Wake warning:", exc, flush=True)

    results = {"queries": {}, "summary": {}, "round": "round2_expanded"}
    ism_scores, groq_scores = [], []
    ism_ms, groq_ms = [], []
    print(f"Running {len(QUERIES)} queries…", flush=True)

    for i, q in enumerate(QUERIES, 1):
        print("\n" + "=" * 72, flush=True)
        print(f"Q [{i}/{len(QUERIES)}]:", q, flush=True)
        ctx = await _enrich_for_query(q)
        ism = _ask_ism_local(q, ctx)
        groq = _ask_groq_remote(q)

        if ism.get("ok"):
            ism["quality"] = _quality(ism["answer"], q)
            ism_scores.append(ism["quality"]["score"])
            ism_ms.append(ism["ms"])
            preview = ism["answer"].replace("\n", " ")[:200].encode("ascii", "replace").decode()
            print(
                f"ISM  {ism['ms']:5}ms conf={ism.get('confidence')} "
                f"intent={ism.get('intent')} q={ism['quality']['score']} | {preview}"
            )
        else:
            print("ISM FAIL", ism.get("error"))

        if groq.get("ok"):
            groq["quality"] = _quality(groq["answer"], q)
            groq_scores.append(groq["quality"]["score"])
            groq_ms.append(groq["ms"])
            preview = groq["answer"].replace("\n", " ")[:200].encode("ascii", "replace").decode()
            print(
                f"GROQ {groq['ms']:5}ms source={groq.get('source')} "
                f"q={groq['quality']['score']} | {preview}"
            )
        else:
            print("GROQ FAIL", groq.get("error"))

        gaps = _gap_notes(q, ism, groq)
        print("GAPS:", "; ".join(gaps))
        results["queries"][q] = {
            "enrich_symbol": ctx.get("symbol"),
            "ism": {
                **{k: v for k, v in ism.items() if k != "answer"},
                "answer_preview": (ism.get("answer") or "")[:600],
                "answer": ism.get("answer"),
            },
            "groq": {
                **{k: v for k, v in groq.items() if k != "answer"},
                "answer_preview": (groq.get("answer") or "")[:600],
                "answer": groq.get("answer"),
            },
            "gaps": gaps,
        }

    results["summary"] = {
        "n": len(QUERIES),
        "ism_avg_quality": round(sum(ism_scores) / len(ism_scores), 3) if ism_scores else 0,
        "groq_avg_quality": round(sum(groq_scores) / len(groq_scores), 3) if groq_scores else 0,
        "ism_avg_ms": int(sum(ism_ms) / len(ism_ms)) if ism_ms else 0,
        "groq_avg_ms": int(sum(groq_ms) / len(groq_ms)) if groq_ms else 0,
        "ism_wins_or_ties": sum(
            1
            for q in QUERIES
            if results["queries"][q]["ism"].get("ok")
            and results["queries"][q]["groq"].get("ok")
            and results["queries"][q]["ism"]["quality"]["score"]
            >= results["queries"][q]["groq"]["quality"]["score"]
        ),
        "groq_wins": sum(
            1
            for q in QUERIES
            if results["queries"][q]["ism"].get("ok")
            and results["queries"][q]["groq"].get("ok")
            and results["queries"][q]["ism"]["quality"]["score"]
            < results["queries"][q]["groq"]["quality"]["score"]
        ),
    }
    print("\n" + "=" * 72)
    print("SUMMARY")
    print(json.dumps(results["summary"], indent=2))

    out = Path(__file__).resolve().parent / "ism_vs_groq_round2.json"
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    # Also refresh the latest alias used by prior tooling.
    alias = Path(__file__).resolve().parent / "ism_vs_groq_recheck.json"
    alias.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Wrote", out)
    print("Wrote", alias)


if __name__ == "__main__":
    asyncio.run(main())
