from indian_stock_llm.query_contract import resolve_query_contract, rewrite_followup

from app.trade_cta import (
    allow_response_symbol,
    is_buy_sell_suggestion,
    sanitize_ask_payload,
    user_named_symbol,
)


_TCS_HISTORY = [
    {"role": "user", "content": "Technical analysis of TCS"},
    {"role": "assistant", "content": "**TCS** — RSI 55"},
]


def test_user_named_symbol_ignores_literacy_terms():
    assert user_named_symbol("What is RSI?") is None
    assert user_named_symbol("How does SIP work?") is None
    assert user_named_symbol("Should I buy RELIANCE?") == "RELIANCE"
    assert user_named_symbol("TCS news") == "TCS"


def test_literacy_does_not_inherit_last_ticker():
    resolved, follow, prior, _ = rewrite_followup("What is RSI?", _TCS_HISTORY)
    assert follow is False
    assert "TCS" not in resolved.upper()
    assert prior == "TCS"

    contract = resolve_query_contract("What is RSI?", conversation_history=_TCS_HISTORY)
    assert contract.profile == "literacy"
    assert contract.slots.follow_up is False
    assert contract.slots.symbol in {None, ""}
    assert "TCS" not in (contract.resolved_query or "").upper()


def test_how_to_sip_is_not_a_stock_followup():
    contract = resolve_query_contract("how to start SIP", conversation_history=_TCS_HISTORY)
    assert contract.profile in {"literacy", "session", "compare_concepts"}
    assert not allow_response_symbol(
        "how to start SIP",
        profile=contract.profile,
        follow_up=bool(contract.slots.follow_up),
    )


def test_what_about_sentiment_still_reuses_prior_stock():
    contract = resolve_query_contract("what about sentiment?", conversation_history=_TCS_HISTORY)
    assert contract.slots.follow_up is True
    assert "TCS" in (contract.resolved_query or "").upper()


def test_sanitize_strips_buy_chips_from_general_asks():
    out = sanitize_ask_payload(
        {
            "answer": "RSI measures momentum.",
            "symbol": "TCS",
            "signal": "HOLD",
            "current_price": 3500,
            "suggestions": ["Should I buy TCS?", "What is MACD?"],
        },
        "What is RSI?",
        source="indian-stock-llm",
        profile="literacy",
        intent="EDUCATIONAL",
    )
    assert "symbol" not in out
    assert "signal" not in out
    assert "current_price" not in out
    assert "Should I buy TCS?" not in out.get("suggestions", [])
    assert "What is MACD?" in out.get("suggestions", [])


def test_sanitize_keeps_named_stock_trade_ask():
    out = sanitize_ask_payload(
        {
            "answer": "**Direct answer:** HOLD",
            "symbol": "RELIANCE",
            "suggestions": ["Technical analysis of RELIANCE"],
        },
        "Should I buy RELIANCE?",
        profile="trade_plan",
        intent="BUY_SELL",
    )
    assert out["symbol"] == "RELIANCE"
    assert is_buy_sell_suggestion("Should I buy RELIANCE?")
