import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import app.ai_engine as ai_engine


def _fake_analysis(symbol: str):
    return {
        "symbol": symbol,
        "name": symbol,
        "summary": f"{symbol} summary",
        "currentPrice": 1000.0,
        "score": 72,
        "signal": "BUY",
        "technical": {
            "rsi": 54.2,
            "macd": {"trend": "bullish"},
            "movingAverages": {"trend": "bullish"},
        },
        "fundamental": {
            "pe": 24.0,
            "roe": 16.5,
            "debtToEquity": 0.3,
        },
        "predictions": [
            {
                "days": 30,
                "predictedPrice": 1060.0,
                "changePercent": 6.0,
                "horizon": "1 Month",
                "direction": "up",
                "confidenceLow": 980.0,
                "confidenceHigh": 1120.0,
            }
        ],
    }


def test_extract_symbols_handles_wrapped_prompt_and_lowercase_name():
    symbols = ai_engine._extract_symbols(
        "user_query:should i buy tata motors now? | context:symbol=RELIANCE,wallet=1000"
    )
    # Post-demerger: Tata Motors PV lists as TMPV (TATAMOTORS is not listed).
    assert "TMPV" in symbols


def test_extract_symbols_recognizes_common_bank_aliases():
    symbols = ai_engine._extract_symbols("compare sbi and hdfc bank")
    assert "SBIN" in symbols
    assert "HDFCBANK" in symbols


def test_extract_symbols_supports_ticker_with_stock_suffix():
    symbols = ai_engine._extract_symbols("apis stock")
    assert symbols
    assert symbols[0] == "APIS"


def test_extract_symbols_ignores_generic_screening_phrases():
    symbols = ai_engine._extract_symbols("best stocks to buy today")
    assert symbols == []


def test_extract_symbols_ignores_wait_action_label():
    assert "WAIT" not in ai_engine._extract_symbols("Action: WAIT")
    assert "WAIT" not in ai_engine._extract_symbols("should I wait now?")
    symbols = ai_engine._extract_symbols("should I wait on reliance?")
    assert "WAIT" not in symbols
    assert "RELIANCE" in symbols


def test_ai_assistant_prefers_user_query_over_context_symbol(monkeypatch):
    monkeypatch.setattr(ai_engine, "analyze_stock", _fake_analysis)

    response = ai_engine.ai_assistant(
        "user_query:analyze reliance | context:symbol=INFY,holdings=INFY:5@1500"
    )

    assert response["type"] == "analysis"
    assert response["symbol"] == "RELIANCE"
    assert "RELIANCE" in response["answer"]


def test_ai_assistant_uses_context_symbol_when_followup_omits_symbol(monkeypatch):
    monkeypatch.setattr(ai_engine, "analyze_stock", _fake_analysis)

    response = ai_engine.ai_assistant(
        "user_query:is it overvalued now? | context:symbol=INFY,wallet=10000"
    )

    assert response["type"] == "analysis"
    assert response["symbol"] == "INFY"
    assert "INFY" in response["answer"]


def test_ai_assistant_compare_with_single_symbol_uses_sector_peer(monkeypatch):
    monkeypatch.setattr(ai_engine, "analyze_stock", _fake_analysis)

    response = ai_engine.ai_assistant("compare reliance with peers")

    assert response["type"] == "comparison"
    assert "RELIANCE" in response["answer"]
    assert "ONGC" in response["answer"]


def test_ai_assistant_routes_technical_sector_query_to_screening(monkeypatch):
    def fake_quote(symbol: str):
        return {"last": 100.0, "pctChange": 1.25}

    monkeypatch.setattr(ai_engine, "fetch_quote", fake_quote)

    response = ai_engine.ai_assistant("technical setup for nifty it stocks")

    assert response["type"] == "screening"
    assert response["stocks"]


def test_analyze_stock_uses_in_memory_cache():
    payload = {"symbol": "INFY", "score": 70, "signal": "HOLD"}
    ai_engine._ANALYSIS_CACHE.clear()
    ai_engine._cache_analysis("INFY", payload)
    assert ai_engine.analyze_stock("INFY") == payload


def test_recommendations_reuse_analysis_cache(monkeypatch):
    ai_engine._ANALYSIS_CACHE.clear()
    ai_engine._RECOMMENDATIONS_CACHE["data"] = None
    ai_engine._RECOMMENDATIONS_CACHE["timestamp"] = 0

    fake = _fake_analysis("RELIANCE")
    monkeypatch.setattr(ai_engine, "analyze_stock", lambda symbol: {**fake, "symbol": symbol, "name": symbol})

    first = ai_engine.get_best_stocks_to_buy(limit=3)
    assert "recommendations" in first
    assert first.get("error") is None
    cached = ai_engine.get_best_stocks_to_buy(limit=3)
    assert cached is first


def test_stock_chip_queries_resolve_distinct_response_profiles():
    from indian_stock_llm.answer_composer import resolve_stock_response_profile

    cases = {
        "Should I buy RELIANCE?": "trade_plan",
        "Predict RELIANCE price": "prediction",
        "Technical analysis of RELIANCE": "technical",
        "Support and resistance for RELIANCE": "technical",
        "Practice levels for RELIANCE": "technical",
        "Latest news on RELIANCE": "news",
        "RELIANCE market sentiment": "sentiment",
        "What is the price of RELIANCE?": "quote",
        "Is RELIANCE overvalued?": "fundamentals",
        "Analyze RELIANCE": "stock_analysis",
        "Best entry price for RELIANCE with stop-loss": "trade_plan",
        "Profit potential for RELIANCE this quarter": "trade_plan",
        "What are risks in RELIANCE now?": "risks",
    }
    for query, expected in cases.items():
        assert resolve_stock_response_profile(query, "general_query") == expected, query


def test_custom_llm_composer_uses_distinct_shapes_for_stock_chips():
    from indian_stock_llm.answer_composer import compose_structured_answer

    ctx = {
        "symbol": "RELIANCE",
        "current_price": 1400.0,
        "pct_change": 1.2,
        "technical": {"rsi": 55.0, "trend": "bullish"},
        "fundamental": {"pe": 24.0},
        "trading_levels": {"support": 1350.0, "resistance": 1450.0, "stop_loss": 1320.0},
        "news_headlines": ["Jio tariff hike announced"],
        "sentiment": {"overall": "bullish", "composite_score": 0.4, "ok": True},
        "sentiment_pack": {"ok": True, "label": "bullish", "composite_score": 0.4},
    }

    def _compose(query: str, intent: str) -> str:
        return compose_structured_answer(
            query=query,
            intent=intent,
            market_context=ctx,
            context_lines=[],
        ) or ""

    news = _compose("Latest news on RELIANCE", "events_news")
    quote = _compose("What is the price of RELIANCE?", "price_action")
    ta = _compose("Technical analysis of RELIANCE", "stock_analysis")
    plan = _compose("Should I buy RELIANCE?", "price_action")
    risks = _compose("What are risks in RELIANCE now?", "events_news")

    assert "news" in news.lower()
    assert "not a buy/sell call" in news.lower()
    assert "live quote" in quote.lower()
    assert "technical analysis" in ta.lower()
    assert "paper trade plan" in plan.lower() or "direct answer" in plan.lower()
    assert "key risks" in risks.lower()
    assert "PRIMARY SIGNAL" not in news
    assert news.lower().startswith("**reliance** — news")
    assert quote.lower().startswith("**reliance** — live quote")
    assert "direct answer" in plan.lower() or "paper trade plan" in plan.lower()
