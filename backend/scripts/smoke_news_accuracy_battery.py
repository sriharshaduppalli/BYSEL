#!/usr/bin/env python3
"""Broader multi-stock accuracy + news/sentiment battery for Indian Stock LLM."""
from __future__ import annotations

import json
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.llm_integration import ask_llm, llm_available  # noqa: E402
from app.market_data import fetch_quote  # noqa: E402

STOCKS = [
    "RELIANCE",
    "TCS",
    "INFY",
    "HDFCBANK",
    "SBIN",
    "ICICIBANK",
    "WIPRO",
    "TATASTEEL",
    "BEL",
    "KAYNES",
    "BAJFINANCE",
    "ITC",
]

# Multiple query shapes per stock (subset applied across the list).
QUERY_SHAPES = [
    "{sym}",
    "Should I buy {sym}?",
    "Sentiment of {sym}",
    "{sym} news and trend",
    "Analyze {sym} for swing trade",
]

BAD_DUMP = [
    r"Live quote snapshot for",
    r"Full math for \w+:",
    r"\| Wilder RSI\(14\)=",
    r"TRADE PLAN action=",
    r"• \*\*Live quote ",
    r"• \*\*Quantitative \+ trade plan",
]


def _ascii(s: str) -> str:
    return (s or "").encode("ascii", "replace").decode("ascii")


def _price_from_answer(ans: str) -> float | None:
    m = re.search(r"Price:\s*([0-9]+(?:\.[0-9]+)?)", ans)
    if m:
        return float(m.group(1))
    m = re.search(r"Last[^\d]*([0-9]{2,3}(?:,[0-9]{3})*(?:\.[0-9]+)?|[0-9]+(?:\.[0-9]+)?)", ans)
    if m:
        return float(m.group(1).replace(",", ""))
    return None


def _rsi_from_answer(ans: str) -> float | None:
    m = re.search(r"RSI[:\s]+([0-9]+(?:\.[0-9]+)?)", ans, re.I)
    if m:
        return float(m.group(1))
    m = re.search(r"Wilder RSI\s*([0-9]+(?:\.[0-9]+)?)", ans, re.I)
    if m:
        return float(m.group(1))
    return None


def main() -> int:
    print("llm_available:", llm_available())
    rows = []
    fail = 0
    news_ok = 0
    sent_ok = 0
    price_ok = 0
    price_checked = 0

    # Build ~36 queries: 3 shapes × 12 stocks
    queries: list[tuple[str, str]] = []
    shapes = QUERY_SHAPES[:3]
    for sym in STOCKS:
        for shape in shapes:
            queries.append((sym, shape.format(sym=sym)))
    # Extra multi-angle asks
    extras = [
        ("RELIANCE", "RELIANCE news and trend"),
        ("TCS", "Analyze TCS for swing trade"),
        ("INFY", "Is INFY bullish or bearish?"),
        ("HDFCBANK", "HDFCBANK prediction this week"),
        ("BEL", "BEL defence outlook"),
        ("ITC", "Should I hold ITC?"),
    ]
    queries.extend(extras)

    live_cache: dict[str, dict] = {}
    for sym, q in queries:
        if sym not in live_cache:
            try:
                live_cache[sym] = fetch_quote(sym) or {}
            except Exception:
                live_cache[sym] = {}

        t0 = time.time()
        try:
            r = ask_llm(q)
        except Exception as exc:
            ms = int((time.time() - t0) * 1000)
            print(f"ERR  {ms:5}ms | {q} | {exc}")
            fail += 1
            rows.append({"query": q, "symbol": sym, "ok": False, "error": str(exc), "ms": ms})
            continue
        ms = int((time.time() - t0) * 1000)
        ans = ((r or {}).get("answer") or "").strip()
        if not ans:
            print(f"EMPTY {ms:5}ms | {q}")
            fail += 1
            rows.append({"query": q, "symbol": sym, "ok": False, "empty": True, "ms": ms})
            continue

        dump_hits = [p for p in BAD_DUMP if re.search(p, ans)]
        has_sent = bool(
            re.search(r"Sentiment analysis|sentiment snapshot|Overall:\s*\*\*", ans, re.I)
            or re.search(r"\b(bullish|bearish|neutral)\b", ans, re.I)
        )
        has_news = bool(
            re.search(r"News & trends|News mix|News headlines|recent_events", ans, re.I)
            or ("– " in ans and "Sentiment" in ans)
        )
        # For explicit news/sentiment asks, require stronger signals
        wants_news = "news" in q.lower() or "sentiment" in q.lower() or "bullish or bearish" in q.lower()
        wants_plan = q.strip().upper() == sym or any(
            x in q.lower() for x in ("buy", "hold", "analyze", "swing", "prediction")
        )

        live = live_cache.get(sym) or {}
        live_price = None
        try:
            live_price = float(live.get("last") or live.get("price") or 0) or None
        except Exception:
            live_price = None
        ans_price = _price_from_answer(ans)
        price_close = None
        if live_price and ans_price:
            price_checked += 1
            # near-accurate within 3% (quotes can lag slightly across sources)
            price_close = abs(ans_price - live_price) / live_price <= 0.03
            if price_close:
                price_ok += 1

        ok = not dump_hits and bool(ans)
        if wants_news and not (has_sent or has_news):
            ok = False
        if wants_plan and not re.search(r"Direct answer|Action:|paper-practice|outlook|sentiment", ans, re.I):
            # soft: literacy-like short answers for "prediction" may still pass via outlook
            if "prediction" not in q.lower():
                ok = False

        if has_sent:
            sent_ok += 1
        if has_news:
            news_ok += 1
        if not ok:
            fail += 1

        status = "PASS" if ok else "FAIL"
        preview = _ascii(ans.replace("\n", " ")[:200])
        print(
            f"{status} {ms:5}ms conf={r.get('confidence')} intent={r.get('intent')} "
            f"sent={has_sent} news={has_news} price_ok={price_close} | {q}"
        )
        if dump_hits:
            print("  DUMP", dump_hits[:2])
        print(" ", preview)
        print()
        rows.append(
            {
                "query": q,
                "symbol": sym,
                "ok": ok,
                "ms": ms,
                "confidence": r.get("confidence"),
                "intent": r.get("intent"),
                "source": r.get("source"),
                "has_sentiment": has_sent,
                "has_news": has_news,
                "live_price": live_price,
                "answer_price": ans_price,
                "price_near": price_close,
                "dump_hits": dump_hits,
                "answer_preview": ans[:600],
            }
        )

    summary = {
        "total": len(queries),
        "pass": len(queries) - fail,
        "fail": fail,
        "with_sentiment": sent_ok,
        "with_news_section": news_ok,
        "price_checked": price_checked,
        "price_near_accurate": price_ok,
        "llm_available": llm_available(),
    }
    out = {"summary": summary, "rows": rows}
    path = Path(__file__).resolve().parent / "news_accuracy_battery.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print("=" * 60)
    print(json.dumps(summary, indent=2))
    print("Wrote", path)
    return 0 if fail == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
