"""ISM sentence analysis + user sentiment."""
from __future__ import annotations

from indian_stock_llm.query_contract import resolve_query_contract
from indian_stock_llm.sentence_analysis import (
    analyze_user_query,
    detect_user_sentiment,
    split_user_sentences,
)


def test_splits_compound_price_and_buy():
    parts = split_user_sentences("What is the price of RELIANCE and should I buy it?")
    assert len(parts) >= 2
    assert "price" in parts[0].lower()
    assert "buy" in parts[1].lower()


def test_compound_contract_keeps_first_ask_as_quote():
    contract = resolve_query_contract("What is the price of RELIANCE and should I buy it?")
    assert contract.profile == "quote"
    assert contract.compound is True
    assert contract.secondary_profile == "trade_plan"
    assert contract.sentence_count >= 2


def test_sentiment_then_plan_is_compound():
    contract = resolve_query_contract("RELIANCE market sentiment. Also should I buy RELIANCE?")
    assert contract.profile == "sentiment"
    assert contract.secondary_profile == "trade_plan"


def test_worried_sell_query_is_negative_tone():
    tone = detect_user_sentiment("I'm worried RELIANCE is crashing, should I sell now?")
    assert tone["polarity"] == "negative"
    assert tone["emotion"] in {"frustrated", "anxious"}
    assert tone["urgency"] in {"medium", "high"}


def test_analyze_user_query_tags_each_sentence():
    nlu = analyze_user_query("Show RELIANCE chart. Why is RELIANCE falling?")
    assert nlu.compound is True
    assert nlu.primary and nlu.primary.profile == "technical"
    assert nlu.secondary and nlu.secondary.profile == "news"
    assert len(nlu.sentences) >= 2


def test_composer_adds_second_ask_on_compound_query():
    from indian_stock_llm.answer_composer import compose_structured_answer

    ctx = {
        "symbol": "RELIANCE",
        "current_price": 1400.0,
        "technical": {"rsi": 55.0, "trend": "bullish"},
        "trading_levels": {"support": 1350.0, "resistance": 1450.0, "stop_loss": 1320.0},
        "trade_plan": {"action": "HOLD", "stop": 1299.0, "target_1": 1388.0},
        "query_nlu": {
            "compound": True,
            "primary_profile": "quote",
            "secondary_profile": "trade_plan",
        },
    }
    answer = compose_structured_answer(
        query="What is the price of RELIANCE and should I buy it?",
        intent="price_action",
        market_context=ctx,
        context_lines=[],
        profile="quote",
    ) or ""
    low = answer.lower()
    assert "live quote" in low
    assert "also" in low
    assert "hold" in low


def test_single_buy_ask_is_not_compound():
    contract = resolve_query_contract("Should I buy RELIANCE?")
    assert contract.profile == "trade_plan"
    assert contract.compound is False
    assert contract.secondary_profile is None
