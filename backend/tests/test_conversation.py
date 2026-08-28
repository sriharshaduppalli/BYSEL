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
