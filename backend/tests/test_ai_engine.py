import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import app.ai_engine as ai_engine


def test_extract_symbols_handles_wrapped_prompt_and_lowercase_name():
    symbols = ai_engine._extract_symbols(
        "user_query:should i buy tata motors now? | context:symbol=RELIANCE,wallet=1000"
    )
    assert "TATAMOTORS" in symbols


def test_extract_symbols_recognizes_common_bank_aliases():
    symbols = ai_engine._extract_symbols("compare sbi and hdfc bank")
    assert "SBIN" in symbols
    assert "HDFCBANK" in symbols


def test_ai_assistant_prefers_user_query_over_context_symbol(monkeypatch):
    def fake_analyze(symbol: str):
        return {
            "summary": f"{symbol} summary",
            "score": 72,
            "signal": "BUY",
        }

    monkeypatch.setattr(ai_engine, "analyze_stock", fake_analyze)

    response = ai_engine.ai_assistant(
        "user_query:analyze reliance | context:symbol=INFY,holdings=INFY:5@1500"
    )

    assert response["type"] == "analysis"
    assert response["symbol"] == "RELIANCE"
    assert "RELIANCE" in response["answer"]
