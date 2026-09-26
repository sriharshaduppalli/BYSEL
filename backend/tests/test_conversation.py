"""ISM conversational follow-ups and small talk."""
from __future__ import annotations

from indian_stock_llm.conversation import (
    conversational_lead,
    expand_conversational_followup,
    small_talk_reply,
)
from indian_stock_llm.query_contract import resolve_query_contract


def _history() -> list[dict]:
    return [
        {"role": "user", "content": "Should I buy RELIANCE?"},
        {"role": "assistant", "content": "**RELIANCE** — paper trade plan\n**Direct answer:** HOLD"},
    ]


def test_small_talk_stays_off_stocks():
    reply = small_talk_reply("Hi")
    assert reply
    assert "NSE" in reply or "stock" in reply.lower()
    assert "RELIANCE" not in reply


def test_good_morning_is_small_talk():
    reply = small_talk_reply("good morning")
    assert reply
    assert "RELIANCE" not in reply
    contract = resolve_query_contract("good morning")
    assert contract.profile == "small_talk"


def test_telugu_namaste_is_not_blank():
    reply = small_talk_reply("నమస్తే")
    assert reply
    assert "BYSEL" in reply
    assert "నమస్తే" in reply or "నేను" in reply
    contract = resolve_query_contract("నమస్తే")
    assert contract.profile == "small_talk"


def test_why_followup_stays_on_last_stock():
    contract = resolve_query_contract("why?", conversation_history=_history())
    assert contract.slots.follow_up is True
    assert "RELIANCE" in contract.resolved_query.upper()
    assert contract.profile in {"trade_plan", "stock_analysis", "risks"}


def test_and_other_symbol_keeps_buy_shape():
    contract = resolve_query_contract("and TCS?", conversation_history=_history())
    assert "TCS" in contract.resolved_query.upper()
    assert contract.profile == "trade_plan"
    assert contract.slots.follow_up is True


def test_what_next_offers_risks_after_plan():
    expanded = expand_conversational_followup(
        "what next?", prior_symbol="RELIANCE", prior_profile="trade_plan"
    )
    assert expanded
    assert "risk" in expanded.lower()
    assert "RELIANCE" in expanded


def test_what_about_sentiment_is_not_a_fake_ticker():
    contract = resolve_query_contract("what about sentiment?", conversation_history=_history())
    assert contract.profile == "sentiment"
    assert "RELIANCE" in contract.resolved_query.upper()
    assert "SENTIMENT" not in (contract.slots.symbol or "")


def test_conversational_lead_only_on_followup():
    assert conversational_lead(follow_up=False, symbol="RELIANCE", profile="quote") is None
    lead = conversational_lead(follow_up=True, symbol="RELIANCE", profile="quote")
    assert lead and "RELIANCE" in lead
    assert conversational_lead(
        follow_up=True,
        symbol="RELIANCE",
        profile="quote",
        query="రిలయన్స్ ఎలా ఉంది?",
    ) is None


def test_hinglish_detector_and_lead():
    from indian_stock_llm.query_language import detect_query_language, polish_hinglish_answer

    assert detect_query_language("kya main HDFCBANK kharidun?") == "hi-en"
    assert detect_query_language("Reliance bech dun?") == "hi-en"
    assert detect_query_language("market open hai kya") == "hi-en"
    assert detect_query_language("What is the price of RELIANCE?") == "en"
    plan = polish_hinglish_answer(
        "kya main HDFCBANK kharidun?",
        "**HDFCBANK** — paper buy plan\n**Direct answer:** HOLD / wait — no clear edge yet",
    )
    assert plan.startswith("**Paper plan:** HOLD")
    quote = polish_hinglish_answer(
        "kitna hai TCS",
        "**TCS** — live quote\n• Last: ₹3920",
    )
    assert quote.startswith("**Seedha jawab:**")
    assert "3920" in quote
    assert polish_hinglish_answer("Should I buy RELIANCE?", "**Direct answer:** HOLD") == (
        "**Direct answer:** HOLD"
    )
    session = polish_hinglish_answer(
        "market open hai kya",
        "**NSE / BSE session (IST)**\n**Your ask:** market open hai kya\nCLOSED — weekend",
    )
    assert session.startswith("**Session:**")
    assert "₹" not in session.splitlines()[0]


def test_namaste_replies_in_telugu():
    from app.groq_llm import get_small_talk_response

    reply = get_small_talk_response("namaste")
    assert reply
    assert "నమస్తే" in reply
    assert "Hi! I am BYSEL" not in reply


def test_hinglish_echo_does_not_repeat_glosses():
    from indian_stock_llm.answer_composer import compose_structured_answer
    from indian_stock_llm.query_language import normalize_user_query

    once = normalize_user_query("RELIANCE bech dun kya?")
    twice = normalize_user_query(once)
    assert twice.count("sell") == once.count("sell") == 1
    assert "sell sell" not in twice
    quote = compose_structured_answer(
        query=normalize_user_query("ITC ka price kitna hai?"),
        intent="price_action",
        market_context={"symbol": "ITC", "current_price": 269.0},
        context_lines=[],
        profile="quote",
    ) or ""
    ask = next(line for line in quote.splitlines() if line.startswith("**Your ask:**"))
    assert "kitna hai" in ask
    assert "how much" not in ask
    assert "is is" not in ask
