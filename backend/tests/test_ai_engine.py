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
    assert "TATAMOTORS" in symbols


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
