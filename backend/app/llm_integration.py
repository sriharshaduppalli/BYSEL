"""
Indian Stock Market LLM — production-ready local knowledge assistant.

Grounded RAG over BYSEL's Indian-market knowledge pack (equations, terms,
sectors, symbols, analysis frameworks) with deterministic education answers.
No paid API required. Optional remote model via ISM_MODEL_ENDPOINT.
"""
from __future__ import annotations

import json
import logging
import os
import re
import sys
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

_LLM_DATA = Path(__file__).parent.parent / "llm_data"
_LLM_PKG = Path(__file__).parent.parent / "indian_stock_llm"
_ENTERPRISE = _LLM_DATA / "enterprise"

_assistant = None

_INDICATOR_TOKENS = {
    "RSI", "MACD", "SMA", "EMA", "ATR", "ADX", "VWAP", "OBV", "PE", "PB", "PEG",
    "EPS", "ROE", "ROCE", "CAGR", "BETA", "STOCH", "BBANDS", "BOLLINGER",
    "BANDS", "BAND", "BANDWIDTH", "UPPER", "LOWER", "MIDDLE", "STOP", "STOPS",
    "SENTIMENT", "NEWS", "MOOD", "HEADLINE", "HEADLINES", "ANALYSIS",
    "FULL", "MATH", "QUANT", "QUANTITATIVE", "STACK", "PLAN", "CHECK",
    # Composer action/stance labels — never treat as tickers.
    "BUY", "SELL", "HOLD", "WAIT", "TRIM", "ACCUMULATE",
    "ACTION", "DIRECT", "ANSWER", "LEGEND", "PAPER", "PRACTICE",
}


def _sync_instrument_master() -> Path:
    """Expand instrument_master.json from the live Indian stock catalog."""
    target = _ENTERPRISE / "instrument_master.json"
    _ENTERPRISE.mkdir(parents=True, exist_ok=True)
    try:
        from .market_data import INDIAN_STOCKS, get_stock_catalog

        catalog = get_stock_catalog()
        try:
            from .stock_enricher import lookup_bse_listing
        except Exception:
            lookup_bse_listing = None  # type: ignore
        rows = []
        for symbol, (yahoo, name) in catalog.items():
            if symbol in {"NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT"}:
                continue
            isin = ""
            if lookup_bse_listing is not None:
                try:
                    rec = lookup_bse_listing(symbol) or {}
                    isin = str(rec.get("isin") or "")
                except Exception:
                    isin = ""
            rows.append(
                {
                    "symbol": symbol,
                    "company_name": name,
                    "yahoo_ticker": yahoo,
                    "isin": isin,
                    "exchange": "BSE" if str(yahoo).endswith(".BO") else "NSE",
                }
            )
        # Prefer curated names when present
        for symbol, (yahoo, name) in INDIAN_STOCKS.items():
            if symbol in {"NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT"}:
                continue
        target.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
        logger.info("Indian Stock LLM instrument master synced: %d symbols", len(rows))
    except Exception as exc:
        logger.warning("Instrument master sync skipped: %s", exc)
        if not target.exists():
            target.write_text("[]", encoding="utf-8")
    return target


def _load_assistant():
    global _assistant
    if _assistant is not None:
        return _assistant

    if not _LLM_PKG.exists():
        logger.error("indian_stock_llm package not found at %s", _LLM_PKG)
        return None

    pkg_parent = str(_LLM_PKG.parent)
    if pkg_parent not in sys.path:
        sys.path.insert(0, pkg_parent)

    try:
        from indian_stock_llm import StockMarketAssistant
        from indian_stock_llm.config import default_config

        instrument_path = _sync_instrument_master()
        base = default_config()

        emb = os.getenv("ISM_EMBEDDING_LOCAL_MODEL")
        if emb is None:
            try:
                import sentence_transformers  # noqa: F401

                emb = "sentence-transformers/all-MiniLM-L6-v2"
            except Exception:
                emb = None

        cfg = base.__class__(
            **{
                **base.__dict__,
                "knowledge_base_path": _LLM_DATA / "sample_knowledge.json",
                "instrument_master_path": instrument_path,
                "corporate_actions_path": _ENTERPRISE / "corporate_actions.json",
                "filings_path": _ENTERPRISE / "filings.json",
                "regulatory_updates_path": _ENTERPRISE / "regulatory_updates.json",
                "market_events_path": _ENTERPRISE / "market_events.json",
                # Production-local defaults: answer from KB even when enterprise JSON is thin.
                "require_ready_data_for_factual": False,
                "top_k_context": 6,
                "min_retrieval_score": 0.12,
                "min_confidence_threshold": 0.30,
                "model_timeout_seconds": 4.0,
                # Closed-loop RAG learning paths (retrieval KB growth — NOT LoRA auto-train).
                "feedback_log_path": _LLM_DATA / "daily_feedback.log",
                "learned_knowledge_path": _LLM_DATA / "learned_knowledge.json",
                "embedding_cache_path": _LLM_DATA / "embedding_cache.json",
                "feedback_learning_enabled": True,
                "nightly_refresh_enabled": True,
                "open_source_market_data_enabled": True,
                "embedding_local_model": emb,
            }
        )
        _assistant = StockMarketAssistant(config=cfg)
        emb_mode = (
            f"sentence-transformers:{emb}"
            if emb
            else ("http:" + str(cfg.embedding_endpoint) if cfg.embedding_endpoint else "local-hash-fallback")
        )
        logger.info(
            "Indian Stock LLM loaded OK (kb=%d items, embedding=%s, feedback_learning=%s)",
            len(getattr(_assistant.knowledge_base, "items", []) or []),
            emb_mode,
            cfg.feedback_learning_enabled,
        )
        return _assistant
    except Exception as exc:
        logger.error("Failed to load Indian Stock LLM: %s", exc, exc_info=True)
        return None


def llm_available() -> bool:
    return _load_assistant() is not None


def _is_definitional_query(query: str) -> bool:
    q = (query or "").lower()
    return bool(
        re.search(
            r"\b(what is|what are|define|definition|meaning of|explain|formula|equation|"
            r"how to open|how to start|difference between|tell me about)\b",
            q,
        )
    )


def _sanitize_symbol(symbol: str | None, query: str) -> str | None:
    if not symbol:
        return None
    sym = str(symbol).upper().strip()
    if sym in _INDICATOR_TOKENS:
        return None
    try:
        from .market_data import normalize_listed_symbol

        sym = normalize_listed_symbol(sym)
    except Exception:
        if sym == "TATAMOTORS":
            sym = "TMPV"
    if _is_definitional_query(query) and re.search(
        r"\b(rsi|macd|sma|ema|atr|pe|p/e|pb|peg|eps|roe|vwap|bollinger|cagr|beta)\b",
        query.lower(),
    ) and not re.search(
        r"\b(of|for|on)\s+(?:tatamotors|" + re.escape(sym.lower()) + r")\b",
        query.lower(),
    ):
        return None
    return sym


def _enrich_symbol_sync(symbol: str) -> dict[str, Any]:
    """Best-effort sync enrich for peer legs / missing context."""
    try:
        from .stock_enricher import _fetch_yfinance

        data = _fetch_yfinance(symbol.upper())
        return data if isinstance(data, dict) else {}
    except Exception as exc:
        logger.debug("sync enrich failed for %s: %s", symbol, exc)
        return {}


def _merge_fundamentals(base: dict[str, Any] | None, extra: dict[str, Any] | None) -> dict[str, Any]:
    """Fill missing fundamental fields from a secondary source (e.g. quant pack)."""
    out = dict(base or {})
    extra = extra or {}
    for key in ("pe", "pb", "p/b", "roe", "eps", "market_cap", "dividend_yield", "pe_sector_avg"):
        if out.get(key) in (None, "", "n/a", "N/A", "Not declared") and extra.get(key) not in (
            None,
            "",
        ):
            out[key] = extra[key]
    if out.get("pb") is None and extra.get("price_to_book") is not None:
        out["pb"] = extra.get("price_to_book")
    return out


def _enrich_compare_leg(symbol: str) -> dict[str, Any]:
    """Full enrich for one compare leg (live + quant fundamentals fallback)."""
    data = _enrich_symbol_sync(symbol)
    fund = dict((data or {}).get("fundamental") or {})
    # Quant pack fills PE/PB/EPS gaps when Yahoo/NSE enrich is thin.
    try:
        from indian_stock_llm.analysis_math import build_p0_analysis_pack

        pack = build_p0_analysis_pack(symbol) or {}
        val = pack.get("valuation_math") or pack.get("valuation") or {}
        yf_fund = pack.get("fundamentals") or {}
        fund = _merge_fundamentals(
            fund,
            {
                "pe": val.get("pe") or yf_fund.get("pe") or pack.get("pe"),
                "pb": val.get("pb") or yf_fund.get("pb") or yf_fund.get("price_to_book"),
                "roe": val.get("roe_pct") or yf_fund.get("roe"),
                "eps": val.get("eps") or yf_fund.get("eps"),
                "market_cap": yf_fund.get("market_cap") or pack.get("market_cap"),
                "dividend_yield": yf_fund.get("dividend_yield") or val.get("dividend_yield"),
            },
        )
        tech = dict((data or {}).get("technical") or {})
        if tech.get("rsi") is None and (pack or {}).get("wilder_rsi_14") is not None:
            tech["rsi"] = (pack or {}).get("wilder_rsi_14")
        price = (data or {}).get("current_price") or (pack or {}).get("price")
        return {
            "symbol": symbol,
            "current_price": price,
            "technical": tech,
            "fundamental": fund,
            "trading_levels": (data or {}).get("trading_levels") or {},
            "company_name": (data or {}).get("company_name"),
            "sector": (data or {}).get("sector"),
        }
    except Exception:
        return {
            "symbol": symbol,
            "current_price": (data or {}).get("current_price"),
            "technical": (data or {}).get("technical") or {},
            "fundamental": fund,
            "trading_levels": (data or {}).get("trading_levels") or {},
            "company_name": (data or {}).get("company_name"),
            "sector": (data or {}).get("sector"),
        }


def _build_peers(query: str, primary: str | None, ctx: dict[str, Any]) -> list[dict]:
    """Enrich secondary tickers for compare asks (deep fundamentals).

    Only tickers named in the user question are allowed — never portfolio /
    selected-quote symbols that may ride along in `all_symbols` from the app wrapper.
    """
    peers: list[dict] = []
    try:
        from .stock_enricher import extract_all_symbols_from_query, order_symbols_in_query

        from_query = list(extract_all_symbols_from_query(query) or [])
    except Exception:
        from_query = []

    # Prefer query order (left → right). Fall back to ctx only if query had none.
    if from_query:
        from_query = order_symbols_in_query(from_query, query)
        symbols = from_query
    else:
        symbols = list(ctx.get("all_symbols") or [])

    cleaned: list[str] = []
    for sym in symbols:
        s = _sanitize_symbol(str(sym), query)
        if s and s not in cleaned:
            cleaned.append(s)

    # Primary must be one of the named compare legs when the user named tickers.
    if from_query and primary and primary not in cleaned:
        primary = cleaned[0] if cleaned else primary
    elif primary and primary not in cleaned:
        cleaned.insert(0, primary)

    for sym in cleaned:
        if primary and sym == primary:
            continue
        peers.append(_enrich_compare_leg(sym))
        # Pairwise compares should stay at 1 peer; allow up to 2 for "A vs B vs C".
        if len(peers) >= (2 if len(cleaned) >= 3 else 1):
            break
    return peers


def _prior_symbol_from_history(history: list | None) -> str | None:
    """Pick the most recent stock symbol mentioned in chat history."""
    if not history:
        return None
    try:
        from .stock_enricher import extract_symbol_from_query
    except Exception:
        extract_symbol_from_query = None  # type: ignore
    for turn in reversed(list(history)[-8:]):
        content = str((turn or {}).get("content") or "").strip()
        if not content:
            continue
        if extract_symbol_from_query:
            try:
                sym = extract_symbol_from_query(content)
                if sym:
                    return str(sym).upper()
            except Exception:
                pass
        # Fallback: bold header like **TCS** from prior answers.
        m = re.search(r"\*\*([A-Z][A-Z0-9.&-]{1,11})\*\*", content)
        if m:
            cand = m.group(1)
            if cand not in {
                "RSI", "MACD", "BUY", "SELL", "HOLD", "WAIT", "TRIM", "ACTION",
                "DIRECT", "ANSWER", "SENTIMENT", "BYSEL", "NSE", "BSE",
                "ACCUMULATE", "LEGEND", "PAPER", "PRACTICE",
            }:
                return cand
    return None


def ask_llm(query: str, context: dict[str, Any] | None = None) -> dict | None:
    """Answer using education pack first, then grounded Indian-market RAG."""
    cleaned = (query or "").strip()
    if not cleaned:
        return None

    ctx = dict(context or {})
    history = ctx.get("conversation_history")
    if isinstance(history, list) and history:
        # Compact multi-turn memory for the composer (ISM path).
        bits: list[str] = []
        for turn in history[-4:]:
            role = str((turn or {}).get("role") or "user")
            content = str((turn or {}).get("content") or "").replace("\n", " ").strip()
            if content:
                bits.append(f"{role}: {content[:160]}")
        if bits:
            ctx["conversation_summary"] = " | ".join(bits)

    symbol_hint = _sanitize_symbol(str(ctx.get("symbol") or "").upper().strip() or None, cleaned)
    if not symbol_hint:
        try:
            from .stock_enricher import extract_symbol_from_query

            symbol_hint = _sanitize_symbol(extract_symbol_from_query(cleaned), cleaned)
        except Exception:
            symbol_hint = None
    # Follow-ups like "what about sentiment?" keep the prior ticker.
    if not symbol_hint and isinstance(history, list):
        prior = _prior_symbol_from_history(history)
        if prior:
            symbol_hint = _sanitize_symbol(prior, cleaned)
    if symbol_hint:
        ctx["symbol"] = symbol_hint
    else:
        ctx.pop("symbol", None)

    # 1) Deterministic equations / glossary (highest precision).
    try:
        from indian_stock_llm.calculations import PandasTaIndicatorCalculator
        from .market_education import get_education_answer

        definitional = _is_definitional_query(cleaned)
        has_of_symbol = bool(
            re.search(r"\b(of|for|on)\s+[a-z0-9][a-z0-9.&-]{1,15}\b", cleaned.lower())
        )
        live_symbol = PandasTaIndicatorCalculator._symbol_from_query(cleaned)
        live_symbol = _sanitize_symbol(live_symbol, cleaned)
        resolved_symbol = symbol_hint or live_symbol

        wants_live_indicator = (
            PandasTaIndicatorCalculator.indicator_requested(cleaned)
            and bool(resolved_symbol)
            and not (definitional and not has_of_symbol)
        )
        # "What is the PE ratio of WIPRO?" / "beta of INFY" / "S/R of TCS"
        # are stock-specific, not glossary asks.
        stock_specific_metric = bool(resolved_symbol) and bool(
            re.search(
                r"\b(p/?e|pe ratio|pb|p/b|roe|eps|valuation|rsi|macd|sma|ema|"
                r"atr|bollinger|bbands|overbought|oversold|"
                r"support|resistance|s/?r|trading levels?|pivots?|\bbeta\b)\b",
                cleaned.lower(),
            )
        ) and (has_of_symbol or not definitional)
        stock_specific_levels = bool(resolved_symbol) and bool(
            re.search(r"\b(support|resistance|s/?r|trading levels?|pivots?)\b", q_low := cleaned.lower())
        ) and (has_of_symbol or bool(re.search(rf"\b{re.escape(resolved_symbol.lower())}\b", q_low)))
        # "Stop loss for INFY swing" → live plan/levels, not Stop Loss glossary.
        stock_specific_stop = bool(resolved_symbol) and bool(
            re.search(
                r"\b(stop[\s-]?loss|take[\s-]?profit|entry price|target price|trade plan)\b",
                cleaned.lower(),
            )
        ) and (
            has_of_symbol
            or bool(re.search(rf"\b{re.escape(resolved_symbol.lower())}\b", cleaned.lower()))
        ) and not re.search(r"\b(what is|define|definition|meaning of)\b", cleaned.lower())
        # "Technical analysis of KAYNES" → live stock TA, not the NCFM glossary primer.
        stock_specific_ta = bool(resolved_symbol) and bool(
            re.search(
                r"\b(technical analysis|chart analysis|price action|"
                r"technically (analyse|analyze)|ta of|ta for)\b",
                cleaned.lower(),
            )
        ) and (
            has_of_symbol
            or bool(re.search(rf"\b{re.escape(resolved_symbol.lower())}\b", cleaned.lower()))
        )
        stock_specific_beta = bool(resolved_symbol) and bool(
            re.search(r"\bbeta\b", cleaned.lower())
        ) and (has_of_symbol or bool(re.search(rf"\b{re.escape(resolved_symbol.lower())}\b", cleaned.lower())))
        nifty_outlook_ask = bool(
            re.search(r"\b(nifty|banknifty)\b", cleaned.lower())
            and re.search(r"\b(outlook|view|bias|forecast)\b", cleaned.lower())
        )
        sentiment_ask = bool(
            re.search(
                r"\b(sentiment analysis|market sentiment|news sentiment|stock sentiment|"
                r"investor sentiment|bullish or bearish|mood of (the )?market|"
                r"\bsentiment\b)\b",
                cleaned.lower(),
            )
        )
        # Live score when a symbol/market is implied; pure "what is sentiment" stays literacy.
        sentiment_live = bool(
            sentiment_ask
            and (
                bool(resolved_symbol)
                or has_of_symbol
                or bool(
                    re.search(
                        r"\b(nifty|banknifty|sensex|market sentiment|today'?s? sentiment|"
                        r"current sentiment|sentiment today|sentiment now)\b",
                        cleaned.lower(),
                    )
                )
            )
            and not (
                definitional
                and not has_of_symbol
                and not resolved_symbol
                and re.search(r"\bwhat is\b", cleaned.lower())
            )
        )
        # "TCS futures margin / lot / basis" → quant F&O pack, not glossary steal.
        q_low = cleaned.lower()
        stock_specific_fo = bool(resolved_symbol) and bool(
            re.search(
                r"\b(futures?|options?|f&o|fno|lot size|\blot\b|margin|basis|hedge|"
                r"open interest|\boi\b|leverage|mtm|mark to market|"
                r"straddle|strangle|iron condor|bull call|bull put|bear put|bear call|"
                r"ratio back|spread|greeks|delta|theta|vega|gamma|iv|moneyness)\b",
                q_low,
            )
        ) and (
            has_of_symbol
            or bool(re.search(rf"\b{re.escape(resolved_symbol.lower())}\b", q_low))
        )

        # Live-ish USDINR / gold / crude snapshot (skip glossary steal).
        live_ccg = bool(
            re.search(
                r"\b((current|live|today'?s?|price of|mcx)\s+)?(usd/?inr|dollar rupee|gold|"
                r"crude|silver|natural gas)\b.*(price|rate|quote|level|now|today|mcx)?|"
                r"\b(usd/?inr|gold|crude)\s+(price|rate|quote)\b|"
                r"\bt-?bill yield\b|\birp\b.*usdinr",
                q_low,
            )
        ) and not definitional

        if live_ccg and not wants_live_indicator:
            try:
                from indian_stock_llm.analysis_math import fetch_ccg_market_snapshot

                snap = fetch_ccg_market_snapshot()
                if snap.get("ok"):
                    gold_edu = snap.get("gold_mcx_big_edu") or {}
                    lines = [
                        "**Currency / Commodity snapshot (educational)**",
                        "",
                        f"• USDINR≈{snap.get('usdinr')} | 30d IRP fair≈{snap.get('usdinr_30d_irp_fair')} "
                        f"(INR 6.5% / USD 4.5% assumption)",
                        f"• Gold (COMEX $/oz)≈{snap.get('gold_usd_oz')} → India ≈₹{snap.get('india_gold_rs_per_10g_approx')}/10g "
                        f"(duty factor=1; MCX is all-in — confirm live)",
                        f"• Silver $/oz≈{snap.get('silver_usd_oz')} | Crude $/bbl≈{snap.get('crude_usd_bbl')} | "
                        f"NatGas≈{snap.get('natural_gas_usd')}",
                    ]
                    if gold_edu.get("contract_value") is not None:
                        lines.append(
                            f"• Gold big-contract edu: value≈₹{gold_edu.get('contract_value')} | "
                            f"P&L/tick≈₹{gold_edu.get('pnl_per_tick_rs')} "
                            f"({gold_edu.get('note')})"
                        )
                    tb = snap.get("tbill_example_91d") or {}
                    if tb.get("yield_pct") is not None:
                        lines.append(
                            f"• T-bill yield illustration (par {tb.get('par')} @ {tb.get('price')} "
                            f"for {tb.get('days')}d)≈{tb.get('yield_pct')}%"
                        )
                    lines.extend(
                        [
                            "",
                            "_yfinance proxies — not MCX/NSE official quotes; not investment advice._",
                        ]
                    )
                    return {
                        "answer": "\n".join(lines),
                        "intent": "market_calculations",
                        "confidence": 0.88,
                        "citations": ["ccg_market_snapshot_v1"],
                        "category": "calculations",
                        "source": "indian-stock-llm-ccg",
                    }
            except Exception:
                pass

        # Personal finance calculator pack (Varsity PF / Mutual Funds module).
        pf_ask = bool(
            re.search(
                r"\b(personal finance|retirement corpus|sip calculator|sip required|"
                r"rule of 72|time value of money|\btvm\b|expense ratio drag|"
                r"emergency fund calculator|financial planning calculator)\b",
                q_low,
            )
        )
        if pf_ask or (
            re.search(r"\b(sip|retirement|corpus|emergency fund)\b", q_low)
            and re.search(r"\b(calculate|calculator|how much|fv|future value|required)\b", q_low)
        ):
            try:
                from indian_stock_llm.analysis_math import build_personal_finance_pack

                monthly = 5000.0
                rate = 12.0
                years = 20.0
                inflation = 6.0
                expense = 50_000.0
                m = re.search(r"(?:sip|invest)\s*(?:of\s*)?₹?\s*([\d,]+)", q_low)
                if m:
                    monthly = float(m.group(1).replace(",", ""))
                y = re.search(r"([\d.]+)\s*(?:years?|yrs?)", q_low)
                if y:
                    years = float(y.group(1))
                r = re.search(r"([\d.]+)\s*%", q_low)
                if r:
                    rate = float(r.group(1))
                e = re.search(
                    r"(?:expense|spend(?:ing)?)\s*(?:of\s*)?₹?\s*([\d,]+)",
                    q_low,
                )
                if e:
                    expense = float(e.group(1).replace(",", ""))
                pf = build_personal_finance_pack(
                    monthly_sip=monthly,
                    annual_rate_pct=rate,
                    years=years,
                    inflation_pct=inflation,
                    monthly_expense=expense,
                )
                if pf.get("ok"):
                    drag = pf.get("expense_ratio_drag_example") or {}
                    ef = pf.get("emergency_fund_3_to_12_months") or {}
                    lines = [
                        "**Personal finance calculator (educational)**",
                        "",
                        f"• Assumptions: SIP ₹{monthly:,.0f}/mo @ {rate}% for {years}y | "
                        f"inflation {inflation}% | monthly expense ₹{expense:,.0f}",
                        f"• SIP future value≈₹{pf.get('sip_fv')}",
                        f"• ₹1L lump-sum FV≈₹{pf.get('lump_1L_fv')}",
                        f"• Rule of 72 (years to double)≈{pf.get('years_to_double_rule72')}",
                        f"• Real return≈{pf.get('real_return_pct')}%",
                        f"• Retirement corpus (4% rule on annual expense)≈₹{pf.get('retirement_corpus_4pct_rule')}",
                        f"• SIP to fund that corpus in {years}y≈₹{pf.get('sip_to_fund_that_corpus')}/mo",
                        f"• Emergency fund band≈₹{ef.get('low')}–₹{ef.get('high')}",
                    ]
                    if drag.get("ok"):
                        lines.append(
                            f"• TER drag example (1% ER on ₹1L @{rate}%/{years}y): "
                            f"gross FV₹{drag.get('gross_fv')} vs net₹{drag.get('net_fv_after_ter')} "
                            f"(drag≈₹{drag.get('drag_rupees')})"
                        )
                    lines.extend(
                        [
                            "",
                            f"Note: {pf.get('note')}",
                            "_Not personalized advice / not a SEBI RA recommendation._",
                        ]
                    )
                    return {
                        "answer": "\n".join(lines),
                        "intent": "market_calculations",
                        "confidence": 0.88,
                        "citations": ["personal_finance_pack_v1"],
                        "category": "calculations",
                        "source": "indian-stock-llm-pf",
                    }
            except Exception:
                pass

        # Pair-trade two-symbol snapshot (Trading Systems module).
        pair_ask = bool(re.search(r"\b(pair trade|pairs? trading|stat(?:istical)? arb)\b", q_low))
        pair_vs = re.search(
            r"\b([A-Za-z][A-Za-z0-9.&-]{1,14})\s+(?:vs\.?|versus|/|and)\s+([A-Za-z][A-Za-z0-9.&-]{1,14})\b",
            cleaned,
            flags=re.I,
        )
        if pair_ask or (pair_vs and re.search(r"\b(pair|ratio|spread|corr)\b", q_low)):
            try:
                from indian_stock_llm.analysis_math import build_pair_trade_pack

                a = b = None
                if pair_vs:
                    a, b = pair_vs.group(1).upper(), pair_vs.group(2).upper()
                else:
                    stop = {
                        "PAIR", "PAIRS", "TRADE", "TRADING", "WHAT", "IS", "THE", "AND",
                        "WITH", "FOR", "STAT", "ARB", "RATIO", "SPREAD", "CORRELATION",
                    }
                    toks = [
                        t
                        for t in re.findall(r"\b([A-Z]{2,15})\b", cleaned.upper())
                        if t not in stop
                    ]
                    if len(toks) >= 2:
                        a, b = toks[0], toks[1]
                if a and b and a != b:
                    pp = build_pair_trade_pack(a, b)
                    if pp.get("ok"):
                        def _fp(v: Any) -> str:
                            try:
                                return f"{float(v):.2f}"
                            except (TypeError, ValueError):
                                return "n/a"

                        ratio = pp.get("ratio") or {}
                        reg = pp.get("regression_a_on_b") or {}
                        adf = pp.get("adf_proxy") or {}
                        last = pp.get("last_prices") or {}
                        lines = [
                            f"**Pair trade snapshot — {a} vs {b}**",
                            "",
                            f"• Last: {a}={_fp(last.get('a'))} | {b}={_fp(last.get('b'))}",
                            f"• Return correlation≈{pp.get('corr_returns_approx')} ({pp.get('pair_quality')})",
                            f"• Ratio z≈{ratio.get('z')} (mean≈{ratio.get('mean')}, σ≈{ratio.get('stdev')})",
                            f"• Signal: {pp.get('ratio_z_signal')}",
                            f"• Regression {a} on {b}: slope(hedge)≈{reg.get('slope')} | "
                            f"R²≈{reg.get('r_squared')} | residual z≈{(pp.get('residual_z') or {}).get('z')}",
                            f"• ADF proxy: {adf.get('interpretation') or 'n/a'}",
                            "",
                            f"Note: {pp.get('note')}",
                            "_Educational pair math — not a live arb desk / not advice._",
                        ]
                        return {
                            "answer": "\n".join(lines),
                            "intent": "market_calculations",
                            "confidence": 0.87,
                            "citations": ["pair_trade_pack_v1"],
                            "category": "calculations",
                            "source": "indian-stock-llm-pairs",
                        }
            except Exception:
                pass

        # Live support/resistance for a named symbol (not glossary).
        if stock_specific_levels and resolved_symbol and not wants_live_indicator:
            try:
                from .stock_enricher import format_symbol_display, resolve_analysis_symbol

                analysis_sym = resolve_analysis_symbol(resolved_symbol)
                display = format_symbol_display(resolved_symbol, analysis_sym)
                filled = ctx if (ctx.get("trading_levels") or {}).get("support") or (
                    ctx.get("trading_levels") or {}
                ).get("support_1") else {}
                if not filled:
                    filled = _enrich_symbol_sync(analysis_sym)
                # Dual-listed BSE codes often enrich empty on .BO — retry NSE twin explicitly.
                if analysis_sym != resolved_symbol and not (
                    (filled or {}).get("trading_levels") or {}
                ).get("support_1") and not (
                    (filled or {}).get("trading_levels") or {}
                ).get("support"):
                    filled = _enrich_symbol_sync(analysis_sym) or filled
                levels = (filled or {}).get("trading_levels") or ctx.get("trading_levels") or {}
                tech = (filled or {}).get("technical") or ctx.get("technical") or {}
                price = (filled or {}).get("current_price") or ctx.get("current_price")

                # Quant fallback: pivots / trade-plan levels when enrich is thin.
                if not (levels.get("support") or levels.get("resistance") or levels.get("support_1")):
                    try:
                        from indian_stock_llm.analysis_math import build_p0_analysis_pack

                        pack = build_p0_analysis_pack(analysis_sym) or {}
                        if pack.get("ok"):
                            piv = pack.get("pivots_classic") or {}
                            plan = pack.get("trade_plan") or {}
                            atr_s = pack.get("atr_stop") or {}
                            price = price or pack.get("price")
                            levels = {
                                "support_1": piv.get("S1") or plan.get("stop") or atr_s.get("stop"),
                                "support_2": piv.get("S2"),
                                "resistance_1": piv.get("R1") or plan.get("target_1") or atr_s.get("structural_t1"),
                                "resistance_2": piv.get("R2") or plan.get("target_2"),
                                "pivot": piv.get("P"),
                            }
                            if pack.get("wilder_rsi_14") is not None:
                                tech = dict(tech)
                                tech["rsi"] = pack.get("wilder_rsi_14")
                    except Exception:
                        pass

                if levels.get("support") or levels.get("resistance") or levels.get("support_1"):
                    s1 = levels.get("support") or levels.get("support_1")
                    s2 = levels.get("support_2")
                    r1 = levels.get("resistance") or levels.get("resistance_1")
                    r2 = levels.get("resistance_2")
                    pivot = levels.get("pivot")
                    lines = [
                        f"**{display} — support & resistance (live enrich)**",
                        "",
                        f"• Last≈{price}",
                        f"• Support 1≈{s1}" + (f" | Support 2≈{s2}" if s2 else ""),
                        f"• Resistance 1≈{r1}" + (f" | Resistance 2≈{r2}" if r2 else ""),
                    ]
                    if pivot is not None:
                        lines.append(f"• Classic pivot≈{pivot}")
                    lines.extend(
                        [
                            f"• RSI≈{tech.get('rsi')} | Trend≈{tech.get('trend')}",
                            "",
                            "Levels from BYSEL enrich / pivots (20d/52w style cues) — zones, not hard stops.",
                        ]
                    )
                    if analysis_sym != resolved_symbol:
                        lines.append(
                            f"Dual-listed: analyzed via NSE `{analysis_sym}` "
                            f"(asked `{resolved_symbol}`)."
                        )
                    lines.append("_Educational — not investment advice._")
                    return {
                        "answer": "\n".join(lines),
                        "intent": "market_calculations",
                        "confidence": 0.9,
                        "citations": ["live_levels_v1", "bse_nse_twin_v1"],
                        "category": "calculations",
                        "source": "indian-stock-llm-levels",
                    }
            except Exception:
                pass

        # Live beta vs Nifty for a named symbol (not glossary) — even "what is beta of X".
        if stock_specific_beta and resolved_symbol and (not definitional or has_of_symbol):
            try:
                from indian_stock_llm.analysis_math import build_p0_analysis_pack

                pack = build_p0_analysis_pack(resolved_symbol)
                vs = (pack or {}).get("vs_nifty") or {}
                b60 = vs.get("beta_60d") or vs.get("beta_fallback")
                b120 = vs.get("beta_120d")
                # Fallback: enrich technical.beta if pack β missing.
                if b60 is None and b120 is None:
                    filled = _enrich_symbol_sync(resolved_symbol)
                    tech = (filled or {}).get("technical") or {}
                    b60 = tech.get("beta") or tech.get("beta_60d")
                if b60 is not None or b120 is not None:
                    lines = [
                        f"**{resolved_symbol} — beta vs Nifty (computed)**",
                        "",
                        f"• β60≈{b60} | β120≈{b120}",
                        f"• RS20≈{vs.get('rs_20d')} | RS60≈{vs.get('rs_60d')}",
                        f"• Price≈{pack.get('price') if pack else None} | "
                        f"HV20≈{((pack or {}).get('volatility') or {}).get('hv20_pct')}%",
                        "",
                        "β > 1 → amplifies Nifty moves; β < 1 → usually more defensive. "
                        "Sample-dependent — educational only.",
                        "_Grounded by BYSEL quantitative engine._",
                    ]
                    return {
                        "answer": "\n".join(lines),
                        "intent": "market_calculations",
                        "confidence": 0.9,
                        "citations": ["live_beta_v1", "bysel_quant_math_v2"],
                        "category": "calculations",
                        "source": "indian-stock-llm-beta",
                    }
            except Exception:
                pass

        # Dedicated sentiment analysis (stock or market) for custom LLM.
        if sentiment_live and not wants_live_indicator:
            try:
                from indian_stock_llm.analysis_math import (
                    build_p0_analysis_pack,
                    build_sentiment_analysis_pack,
                    format_sentiment_card,
                )

                sent_sym = resolved_symbol
                if not sent_sym or sent_sym in _INDICATOR_TOKENS:
                    sent_sym = (
                        "NIFTY50"
                        if re.search(
                            r"\b(nifty|banknifty|sensex|market)\b", cleaned.lower()
                        )
                        else None
                    )
                if not sent_sym:
                    raise ValueError("no symbol for live sentiment")
                filled = {}
                if sent_sym not in {"NIFTY50", "NIFTY", "MARKET", "SENSEX"}:
                    # Always sync-enrich so news headlines stay fresh even when a
                    # prior sentiment label already exists on the request context.
                    needs_headlines = not (
                        ctx.get("news_headlines")
                        or (ctx.get("sentiment") or {}).get("recent_events")
                    )
                    if needs_headlines or not (ctx.get("technical") or {}).get("rsi"):
                        filled = _enrich_symbol_sync(sent_sym)
                    else:
                        filled = ctx
                    if filled:
                        for key in (
                            "current_price",
                            "technical",
                            "fundamental",
                            "trading_levels",
                            "sentiment",
                            "company_name",
                            "sector",
                            "news_headlines",
                        ):
                            if filled.get(key) not in (None, {}, [], ""):
                                ctx[key] = filled.get(key)
                        if filled.get("news_headlines"):
                            sent = dict(ctx.get("sentiment") or {})
                            if not sent.get("recent_events"):
                                sent["recent_events"] = list(filled.get("news_headlines") or [])[:5]
                            ctx["sentiment"] = sent
                p0_for_sent = None
                try:
                    p0_for_sent = build_p0_analysis_pack(sent_sym or "NIFTY50")
                except Exception:
                    p0_for_sent = None
                sent_pack = build_sentiment_analysis_pack(
                    sent_sym or "NIFTY50",
                    enrich=ctx,
                    p0=p0_for_sent,
                    headlines=ctx.get("news_headlines")
                    or (ctx.get("sentiment") or {}).get("recent_events"),
                )
                if sent_pack.get("ok"):
                    return {
                        "answer": format_sentiment_card(sent_pack),
                        "intent": "stock_analysis",
                        "confidence": float(sent_pack.get("confidence") or 0.85),
                        "citations": ["sentiment_analysis_v1", "bysel_quant_math_v2"],
                        "category": "calculations",
                        "source": "indian-stock-llm-sentiment",
                    }
            except Exception as exc:
                logger.debug("Sentiment pack miss: %s", exc)

        # Nifty / BankNifty outlook → live bias, not futures literacy primer.
        if nifty_outlook_ask and not definitional:
            try:
                idx = "BANKNIFTY" if "banknifty" in q_low else "NIFTY50"
                filled = _enrich_symbol_sync(idx)
                tech = (filled or {}).get("technical") or {}
                price = (filled or {}).get("current_price")
                levels = (filled or {}).get("trading_levels") or {}
                trend = tech.get("trend") or "neutral"
                rsi = tech.get("rsi")
                lines = [
                    f"**{idx} outlook (educational snapshot)**",
                    "",
                    f"• Last≈{price} | Trend≈{trend} | RSI≈{rsi}",
                    f"• MACD bias≈{tech.get('macd_bias') or tech.get('macd')}",
                    f"• Support≈{levels.get('support') or levels.get('support_1')} | "
                    f"Resistance≈{levels.get('resistance') or levels.get('resistance_1')}",
                    "",
                    "Read as a short-term bias from enrich signals — not a futures tip / not SPAN.",
                    "_Educational — confirm with live NSE quotes before trading._",
                ]
                if price is not None or rsi is not None:
                    return {
                        "answer": "\n".join(lines),
                        "intent": "market_calculations",
                        "confidence": 0.86,
                        "citations": ["nifty_outlook_v1"],
                        "category": "calculations",
                        "source": "indian-stock-llm-outlook",
                    }
            except Exception:
                pass

        retail_literacy_ask = bool(
            re.search(
                r"\b(demat|how to open|gtt|brokerage|trading charges|asba|allotment|"
                r"\bcnc\b|\bmis\b|\bnrml\b|delivery vs|intraday vs|pledge|"
                r"short delivery|auction market|bonus issue|stock split|rights issue|"
                r"corporate actions?|\bstcg\b|\bltcg\b|capital gains|tax on|"
                r"\bipo\b|investor protection|\bsebi\b|fii|dii|"
                r"3-5-7|3–5–7|357 rule|three five seven|risk management|position sizing|"
                r"15-15-15|15–15–15|151515|1 crore sip|crorepati sip|"
                r"3-6-9|3–6–9|369 rule|three six nine|rule of money|emergency fund)\b",
                cleaned.lower(),
            )
        )
        education = None
        if (
            (not wants_live_indicator or retail_literacy_ask)
            and not stock_specific_metric
            and not stock_specific_fo
            and not stock_specific_levels
            and not stock_specific_stop
            and not stock_specific_ta
            and not stock_specific_beta
            and not nifty_outlook_ask
            and not live_ccg
            and not sentiment_live
        ):
            education = get_education_answer(cleaned)
        # Pure definitions only (no "of SYMBOL").
        if (
            (definitional or retail_literacy_ask)
            and not has_of_symbol
            and (not wants_live_indicator or retail_literacy_ask)
            and not stock_specific_fo
            and not stock_specific_levels
            and not stock_specific_stop
            and not stock_specific_ta
            and not stock_specific_beta
            and not sentiment_live
        ):
            education = education or get_education_answer(cleaned)

        if education:
            lit = bool(
                re.search(
                    r"\b(stock market|share market|how does|how are share|start investing|"
                    r"common mistakes|participants?|nsdl|cdsl|depository|price discovery|"
                    r"primary market|secondary market|share prices|what moves|day trader|"
                    r"scalper|swing trader|holding period|absolute return|trader vs|"
                    r"where do you fit|calculate returns|technical analysis|candlestick|"
                    r"marubozu|doji|hammer|engulfing|harami|shooting star|dark cloud|"
                    r"fibonacci|dow theory|central pivot|\bcpr\b|"
                    r"chart patterns?|trading breakouts?|trade breakouts?|false breakouts?|"
                    r"double tops?|head and shoulders|cup and handle|flag and pennant|"
                    r"triangle pattern|rising wedge|price gaps?|gap theory|narrow range|"
                    r"pipe bottom|inside bar|harami|throwback|protective stops?|"
                    r"\bncfm\b|elliott wave|elliot wave|dow theory|stochastic|williams|"
                    r"money flow|\bmfi\b|day trading|momentum trading|trading psychology|"
                    r"leading indicators?|rounded top|"
                    r"sentiment analysis|market sentiment|news sentiment|investor sentiment|"
                    r"market timings|market hours|trading hours|closing auction|\bcas\b|"
                    r"futures and options|\bf&o\b|\bfno\b|futures vs options|"
                    r"hedgers|speculators|arbitrageurs|cash settlement|"
                    r"futures trading|futures pricing|cost of carry|mark to market|\bm2m\b|"
                    r"open interest|contango|backwardation|hedging with futures|"
                    r"nifty futures|impact cost|physical settlement|forwards?|"
                    r"option strateg|bull call|bull put|bear put|bear call|straddle|"
                    r"strangle|iron condor|max pain|put call ratio|\bpcr\b|"
                    r"ratio back|synthetic long|option theory|moneyness|intrinsic|"
                    r"option greeks|\bgreeks\b|historical volatility|call option|put option|"
                    r"currency|usdinr|forex|commodity|\bmcx\b|gold|crude|g-?sec|"
                    r"treasury bill|t-bills?|bond yield|"
                    r"risk management|position sizing|value at risk|kelly|trading bias|"
                    r"3-5-7|3–5–7|357 rule|three five seven|"
                    r"15-15-15|15–15–15|151515|1 crore sip|crorepati sip|"
                    r"3-6-9|3–6–9|369 rule|three six nine|rule of money|emergency fund|"
                    r"equity curve|portfolio variance|recovery trauma|"
                    r"trading system|pair trading|momentum portfolio|adf test|cointegration|"
                    r"personal finance|time value of money|retirement|mutual fund|\bnav\b|"
                    r"expense ratio|\bter\b|asset allocation|smart beta|emergency fund|"
                    r"rolling returns|index fund|\betf\b|financial planning|"
                    r"demat|gtt|brokerage|asba|cnc|mis|nrml|pledge|short delivery|"
                    r"bonus|stock split|rights issue|corporate action|stcg|ltcg|ipo|"
                    r"sebi|fii|dii|auction)\b",
                    cleaned.lower(),
                )
            ) or retail_literacy_ask
            return {
                "answer": education,
                "intent": "market_literacy" if lit else "market_calculations",
                "confidence": 0.94,
                "citations": ["bysel_market_education"],
                "category": "nse_bse_sebi" if lit else "calculations",
                "source": "indian-stock-llm-education",
            }
        if wants_live_indicator:
            note = PandasTaIndicatorCalculator.indicator_note(
                cleaned,
                symbol_hint=symbol_hint or live_symbol,
            )
            if note:
                sym = symbol_hint or live_symbol or "SYMBOL"
                # Upgrade terse one-liners into a short structured card.
                if not note.startswith("**"):
                    note = (
                        f"**{sym} — indicator snapshot**\n\n"
                        f"• {note}\n\n"
                        "Use with price structure / volume — one indicator is not a trade plan.\n"
                        "_Educational calculation from recent market history — not investment advice._"
                    )
                elif "not investment advice" not in note.lower():
                    note = (
                        f"{note}\n\n"
                        "Educational calculation from recent market history — "
                        "not investment advice."
                    )
                return {
                    "answer": note,
                    "intent": "market_calculations",
                    "confidence": 0.9,
                    "citations": ["live_indicator_v1"],
                    "category": "calculations",
                    "source": "indian-stock-llm-indicator",
                }
    except Exception as exc:
        logger.debug("Education/indicator pack miss: %s", exc)

    assistant = _load_assistant()
    if assistant is None:
        return None

    try:
        # Hydrate live enrich when technicals OR news/sentiment headlines are missing.
        needs_tech = symbol_hint and not (ctx.get("technical") or {}).get("rsi")
        has_news = bool(ctx.get("news_headlines")) or bool(
            (ctx.get("sentiment") or {}).get("recent_events")
        )
        needs_news = bool(symbol_hint) and not has_news
        if symbol_hint and (needs_tech or needs_news):
            filled = _enrich_symbol_sync(symbol_hint)
            if filled:
                for key in (
                    "current_price",
                    "technical",
                    "fundamental",
                    "trading_levels",
                    "sentiment",
                    "company_name",
                    "sector",
                    "pre_signals",
                    "news_headlines",
                ):
                    if filled.get(key) not in (None, {}, [], ""):
                        # Prefer fresh enrich for technical/levels; keep caller price if set.
                        if key == "current_price" and ctx.get("current_price") not in (None, "", "n/a"):
                            continue
                        if key == "sentiment":
                            # Merge so existing rule sentiment isn't wiped without news.
                            merged_sent = dict(ctx.get("sentiment") or {})
                            merged_sent.update(
                                {k: v for k, v in (filled.get("sentiment") or {}).items() if v}
                            )
                            if filled.get("news_headlines") and not merged_sent.get("recent_events"):
                                merged_sent["recent_events"] = list(filled.get("news_headlines") or [])[:5]
                            ctx["sentiment"] = merged_sent
                            continue
                        ctx[key] = filled.get(key)
                if filled.get("news_headlines") and not ctx.get("news_headlines"):
                    ctx["news_headlines"] = filled.get("news_headlines")
                if ctx.get("news_headlines") and not ctx.get("news_summary"):
                    try:
                        from .stock_enricher import format_news_for_prompt

                        ctx["news_summary"] = format_news_for_prompt(ctx["news_headlines"])
                    except Exception:
                        pass

        peers = []
        if re.search(r"\bcompare\b|\bvs\b|\bversus\b|\bagainst\b", cleaned.lower()):
            # Force primary from the user question so selected-quote/holdings
            # cannot become the left side of the scorecard.
            try:
                from .stock_enricher import extract_all_symbols_from_query, order_symbols_in_query

                named = list(extract_all_symbols_from_query(cleaned) or [])
                if named:
                    named = order_symbols_in_query(named, cleaned)
                    named = [_sanitize_symbol(str(s), cleaned) for s in named]
                    named = [s for s in named if s]
                    if named:
                        symbol_hint = named[0]
                        ctx["symbol"] = symbol_hint
                        ctx["all_symbols"] = named
            except Exception:
                pass

            peers = _build_peers(cleaned, symbol_hint, ctx)
            # If primary missing but peers exist, promote first peer.
            if not symbol_hint and peers:
                first = peers.pop(0)
                symbol_hint = first.get("symbol")
                ctx["symbol"] = symbol_hint
                for key in (
                    "current_price",
                    "technical",
                    "fundamental",
                    "trading_levels",
                    "company_name",
                    "sector",
                ):
                    if first.get(key) not in (None, {}, [], ""):
                        ctx[key] = first.get(key)
            # Deep-enrich primary too so compare legs are equally complete.
            if symbol_hint:
                primary_leg = _enrich_compare_leg(symbol_hint)
                for key in (
                    "current_price",
                    "technical",
                    "fundamental",
                    "trading_levels",
                    "company_name",
                    "sector",
                ):
                    val = primary_leg.get(key)
                    if val in (None, {}, [], ""):
                        continue
                    if key == "fundamental":
                        ctx[key] = _merge_fundamentals(ctx.get("fundamental"), val)
                    elif key == "technical":
                        tech = dict(ctx.get("technical") or {})
                        tech.update({k: v for k, v in (val or {}).items() if v is not None})
                        ctx[key] = tech
                    else:
                        ctx[key] = val

        # Decide answer shape early so we don't always force trade-plan + sentiment.
        response_profile = "stock_analysis"
        if symbol_hint:
            try:
                from indian_stock_llm.answer_composer import resolve_stock_response_profile

                hint_intent = str(ctx.get("intent") or ctx.get("groq_intent") or "general_query")
                groq_to_ism = {
                    "NEWS": "events_news",
                    "SENTIMENT": "events_news",
                    "QUOTE": "price_action",
                    "TECHNICAL": "stock_analysis",
                    "BUY_SELL": "price_action",
                    "PREDICT": "prediction",
                    "FUNDAMENTAL": "fundamentals",
                    "COMPARE": "compare",
                    "CALCULATION": "market_calculations",
                }
                mapped = groq_to_ism.get(str(hint_intent).upper())
                if mapped:
                    hint_intent = mapped
                    ctx["intent"] = mapped
                response_profile = resolve_stock_response_profile(cleaned, hint_intent)
            except Exception:
                response_profile = "stock_analysis"

        # Full quantitative pack (P0+B/C) + buy/sell trade plan.
        # Skip heavy pack for pure quote asks — composer only needs last/levels.
        p0_pack = None
        need_p0 = symbol_hint and response_profile not in {"quote"}
        if need_p0:
            try:
                from indian_stock_llm.analysis_math import build_p0_analysis_pack

                qlow = cleaned.lower()
                if re.search(r"\b(intraday|today|scalp)\b", qlow):
                    horizon = "intraday"
                elif re.search(r"\b(this week|weekly)\b", qlow):
                    horizon = "week"
                elif re.search(r"\b(long term|long-term|invest|years?)\b", qlow):
                    horizon = "long"
                else:
                    horizon = "swing"

                p0_pack = build_p0_analysis_pack(
                    symbol_hint,
                    fund_hints=ctx.get("fundamental") or {},
                    horizon=horizon,
                )
                # Prefer Wilder RSI in technicals when available.
                if p0_pack.get("ok") and p0_pack.get("wilder_rsi_14") is not None:
                    tech = dict(ctx.get("technical") or {})
                    wrsi = float(p0_pack["wilder_rsi_14"])
                    tech["rsi"] = wrsi
                    tech["rsi_wilder"] = wrsi
                    if wrsi >= 70:
                        tech["rsi_interpretation"] = "overbought"
                    elif wrsi <= 30:
                        tech["rsi_interpretation"] = "oversold"
                    else:
                        tech["rsi_interpretation"] = "neutral"
                    tech["rsi_divergence"] = p0_pack.get("rsi_divergence")
                    if p0_pack.get("atr_14") is not None:
                        tech["atr"] = p0_pack["atr_14"]
                    bb = p0_pack.get("bollinger") or {}
                    if bb.get("pct_b") is not None:
                        tech["pct_b"] = bb["pct_b"]
                        tech["bb_bandwidth"] = bb.get("bandwidth")
                        tech["bb_regime"] = bb.get("bandwidth_regime")
                    st = p0_pack.get("supertrend") or {}
                    if st.get("direction"):
                        tech["supertrend"] = st.get("direction")
                        tech["supertrend_line"] = st.get("line")
                    macd_p = p0_pack.get("macd") or {}
                    if macd_p.get("histogram") is not None:
                        tech["macd_hist"] = macd_p.get("histogram")
                    vol = p0_pack.get("volume") or {}
                    if vol.get("delivery_pct") is not None:
                        tech["delivery_pct"] = vol.get("delivery_pct")
                    ctx["technical"] = tech
                    levels = dict(ctx.get("trading_levels") or {})
                    atr_stop = p0_pack.get("atr_stop") or {}
                    if atr_stop.get("stop") is not None:
                        levels["atr_stop"] = atr_stop["stop"]
                        levels["atr_target_1r"] = atr_stop.get("target_1r")
                    plan = p0_pack.get("trade_plan") or {}
                    # Prefer structural plan R:R over ATR 1R echo.
                    if plan.get("risk_reward") is not None:
                        levels["risk_reward"] = plan.get("risk_reward")
                    elif atr_stop.get("risk_reward") is not None:
                        levels["risk_reward"] = atr_stop.get("risk_reward")
                    piv = p0_pack.get("pivots_classic") or {}
                    if piv.get("S1") is not None:
                        levels["pivot"] = piv.get("P")
                        levels["pivot_s1"] = piv.get("S1")
                        levels["pivot_r1"] = piv.get("R1")
                    if plan.get("stop") is not None:
                        levels["stop_loss"] = plan.get("stop")
                    if plan.get("target_1") is not None:
                        levels["take_profit"] = plan.get("target_1")
                    ctx["trading_levels"] = levels
                    # Always refresh RSI pre_signal to Wilder (enrich SMA RSI conflicts with plan).
                    pre = dict(ctx.get("pre_signals") or {})
                    if wrsi >= 75:
                        pre["rsi_signal"] = (
                            f"Wilder RSI {wrsi:.0f} — Strongly overbought, high reversal risk, avoid chasing"
                        )
                    elif wrsi >= 70:
                        pre["rsi_signal"] = (
                            f"Wilder RSI {wrsi:.0f} — Overbought zone, potential short-term pullback"
                        )
                    elif wrsi <= 30:
                        pre["rsi_signal"] = (
                            f"Wilder RSI {wrsi:.0f} — Oversold, potential accumulation zone"
                        )
                    elif wrsi > 55:
                        pre["rsi_signal"] = (
                            f"Wilder RSI {wrsi:.0f} — Bullish momentum, trend intact (not yet ≥70 overbought)"
                        )
                    elif wrsi < 45:
                        pre["rsi_signal"] = (
                            f"Wilder RSI {wrsi:.0f} — Soft momentum, wait for stabilization"
                        )
                    else:
                        pre["rsi_signal"] = (
                            f"Wilder RSI {wrsi:.0f} — Neutral momentum, no directional RSI signal"
                        )
                    ctx["pre_signals"] = pre
            except Exception as exc:
                logger.debug("P0 analysis pack failed for %s: %s", symbol_hint, exc)
                p0_pack = None

        # Sentiment / news packs only when the response profile needs them.
        sentiment_pack = None
        need_sentiment = response_profile in {
            "news",
            "sentiment",
            "risks",
            "stock_analysis",
            "trade_plan",
            "calculations",
        }
        if need_sentiment and (symbol_hint or (ctx.get("sentiment") or {}).get("overall")):
            try:
                from indian_stock_llm.analysis_math import build_sentiment_analysis_pack

                sentiment_pack = build_sentiment_analysis_pack(
                    symbol_hint or "NIFTY50",
                    enrich=ctx,
                    p0=p0_pack,
                    headlines=ctx.get("news_headlines")
                    or (ctx.get("sentiment") or {}).get("recent_events"),
                )
                if sentiment_pack.get("ok"):
                    # Keep enrich news fields; overlay composite label for composer.
                    sent_ctx = dict(ctx.get("sentiment") or {})
                    sent_ctx["overall"] = sentiment_pack.get("label") or sent_ctx.get("overall")
                    sent_ctx["breakdown"] = (
                        (sentiment_pack.get("news") or {}).get("breakdown")
                        or sent_ctx.get("breakdown")
                    )
                    sent_ctx["composite_score"] = sentiment_pack.get("composite_score")
                    sent_ctx["confidence"] = sentiment_pack.get("confidence")
                    sent_ctx["summary"] = sentiment_pack.get("summary")
                    sent_ctx["factors"] = sentiment_pack.get("factors")
                    # Prefer tagged/live headlines from the pack when enrich events are thin.
                    pack_heads = (sentiment_pack.get("news") or {}).get("headlines") or []
                    if pack_heads and not sent_ctx.get("recent_events"):
                        sent_ctx["recent_events"] = [
                            (h.get("title") if isinstance(h, dict) else str(h))
                            for h in pack_heads[:5]
                            if h
                        ]
                    ctx["sentiment"] = sent_ctx
                    pre = dict(ctx.get("pre_signals") or {})
                    pre["sentiment_signal"] = (
                        f"Sentiment {sentiment_pack.get('label')} "
                        f"(score {sentiment_pack.get('composite_score'):+.2f})"
                    )
                    ctx["pre_signals"] = pre
                    # Soft-bias paper trade plan only for trade/analysis profiles.
                    if response_profile in {"trade_plan", "stock_analysis", "calculations"}:
                        plan = (p0_pack or {}).get("trade_plan") if p0_pack else None
                        if isinstance(plan, dict) and plan.get("action"):
                            n_score = float((sentiment_pack.get("news") or {}).get("score") or 0.0)
                            n_ok = bool((sentiment_pack.get("news") or {}).get("ok"))
                            if n_ok:
                                fors = list(plan.get("reasons_for") or [])
                                against = list(plan.get("reasons_against") or [])
                                score = int(plan.get("score") or 0)
                                if n_score >= 0.25:
                                    score += 1
                                    fors.append(
                                        f"News tone constructive ({(sentiment_pack.get('news') or {}).get('breakdown') or 'positive bias'})"
                                    )
                                elif n_score <= -0.25:
                                    score -= 1
                                    against.append(
                                        f"News tone cautious ({(sentiment_pack.get('news') or {}).get('breakdown') or 'negative bias'})"
                                    )
                                plan["score"] = score
                                plan["reasons_for"] = fors[:8]
                                plan["reasons_against"] = against[:8]
                                if p0_pack is not None:
                                    p0_pack["trade_plan"] = plan
            except Exception as exc:
                logger.debug("Sentiment pack attach failed: %s", exc)
                sentiment_pack = None

        market_context = {
            "symbol": symbol_hint,
            "current_price": ctx.get("current_price"),
            "technical": ctx.get("technical") or {},
            "fundamental": ctx.get("fundamental") or {},
            "trading_levels": ctx.get("trading_levels") or {},
            "sentiment": ctx.get("sentiment") or {},
            "sentiment_pack": sentiment_pack if sentiment_pack and sentiment_pack.get("ok") else None,
            "company_name": ctx.get("company_name"),
            "sector": ctx.get("sector"),
            "all_symbols": ctx.get("all_symbols") or [],
            "news_summary": ctx.get("news_summary"),
            "news_headlines": ctx.get("news_headlines") or [],
            "pre_signals": ctx.get("pre_signals"),
            "peers": peers,
            "p0_math": p0_pack if p0_pack and p0_pack.get("ok") else None,
            "trade_plan": (p0_pack or {}).get("trade_plan") if p0_pack and p0_pack.get("ok") else None,
            "conversation_summary": ctx.get("conversation_summary"),
            "user_sentiment": ctx.get("user_sentiment") if isinstance(ctx.get("user_sentiment"), dict) else None,
            "portfolio_context": ctx.get("portfolio_context"),
        }
        if not any(
            market_context.get(k)
            for k in (
                "symbol",
                "current_price",
                "technical",
                "fundamental",
                "trading_levels",
                "all_symbols",
                "peers",
            )
        ):
            market_context = None

        result = assistant.query(cleaned, market_context=market_context)
        answer = (result.get("answer") or "").strip()
        disclaimer = (result.get("disclaimer") or "").strip()
        if disclaimer and disclaimer not in answer:
            answer = f"{answer}\n\n{disclaimer}" if answer else disclaimer
        return {
            "answer": answer,
            "intent": result.get("intent", "general_query"),
            "confidence": float(result.get("confidence", 0.0) or 0.0),
            "citations": result.get("citations", []),
            "category": result.get("category", "stocks"),
            "source": "indian-stock-llm",
            "diagnostics": result.get("diagnostics"),
            "prediction_signals": result.get("prediction_signals"),
            "trade_plan": (p0_pack or {}).get("trade_plan") if p0_pack and p0_pack.get("ok") else None,
            "data_quality": (p0_pack or {}).get("data_quality") if p0_pack else None,
        }
    except Exception as exc:
        logger.error("Indian Stock LLM query failed: %s", exc, exc_info=True)
        return None


def record_chat_feedback(
    query: str,
    answer: str,
    helpful: bool = True,
    intent: str | None = None,
) -> bool:
    """Persist chat thumbs into the Indian Stock LLM learning loop (TSV + JSONL)."""
    assistant = _load_assistant()
    if assistant is None:
        return False
    try:
        from datetime import datetime, timezone

        from indian_stock_llm.learning_loop import FeedbackLearningPipeline

        resolved_intent = (intent or "general_query").strip() or "general_query"
        lm = getattr(assistant, "learning_manager", None)
        if lm is None:
            return False

        if hasattr(lm, "record_feedback"):
            lm.record_feedback(query=query, intent=resolved_intent)
        if hasattr(lm, "record_anonymized_feedback"):
            lm.record_anonymized_feedback(query=query, intent=resolved_intent)

        feedback_path = getattr(lm, "feedback_log_path", None)
        if feedback_path is not None and hasattr(lm, "_write_line"):
            payload = {
                "ts": datetime.now(timezone.utc).isoformat(),
                "kind": "thumbs_v1",
                "intent": resolved_intent,
                "helpful": bool(helpful),
                "query_hash": lm.anonymize_query(query) if hasattr(lm, "anonymize_query") else "",
                "answer_chars": len((answer or "").strip()),
            }
            lm._write_line(json.dumps(payload, ensure_ascii=False) + "\n")

        if helpful:
            cfg = getattr(assistant, "config", None)
            FeedbackLearningPipeline.promote_from_feedback_log(
                getattr(cfg, "feedback_log_path", None),
                getattr(cfg, "learned_knowledge_path", None),
                min_count=3,
            )
        return True
    except Exception as exc:
        logger.debug("record_chat_feedback failed: %s", exc)
        return False
