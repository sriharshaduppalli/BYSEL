"""Session clock, corporate-action dates, and paper tickets for the ISM agent."""
from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

from indian_stock_llm.agent_tools import (
    format_corporate_actions_card,
    paper_order_from_plan,
    session_snapshot,
)
from indian_stock_llm.answer_composer import compose_structured_answer
from indian_stock_llm.ism_agent import run_ism_agent
from indian_stock_llm.query_contract import resolve_query_contract

IST = ZoneInfo("Asia/Kolkata")


def test_session_routes_without_screen_stock():
    contract = resolve_query_contract(
        "Is the market open?",
        screen_context={"symbol": "RELIANCE"},
    )
    assert contract.profile == "session"
    assert contract.slots.symbol is None


def test_session_card_uses_ist_clock():
    saturday = datetime(2026, 8, 29, 11, 0, tzinfo=IST)
    snap = session_snapshot(saturday)
    assert snap["isOpen"] is False
    assert snap["phase"] in {"weekend", "closed"}
    agent = run_ism_agent("Market hours today", market_context={"symbol": "TCS"})
    assert agent["profile"] == "session"
    assert "session_clock" in agent["tools"]
    assert "ist" in agent["answer"].lower()
    assert "tcs —" not in agent["answer"].lower()


def test_corporate_actions_do_not_invent_dates():
    contract = resolve_query_contract("Dividend date of RELIANCE")
    assert contract.profile == "corporate_actions"
    assert contract.slots.symbol == "RELIANCE"
    card = format_corporate_actions_card("Dividend date of RELIANCE", "RELIANCE", rows=[])
    assert "do not invent" in card.lower()
    infy = resolve_query_contract("Dividend date of INFY")
    answer = compose_structured_answer(
        query="Dividend date of INFY",
        intent=infy.ism_intent,
        market_context={"symbol": "INFY"},
        context_lines=[],
        profile="corporate_actions",
    ) or ""
    low = answer.lower()
    assert "corporate" in low
    assert "2026-05-15" in answer or "no dated" in low


def test_paper_ticket_from_buy_plan():
    ticket = paper_order_from_plan(
        "RELIANCE",
        {"action": "BUY", "position_qty_for_risk": 3, "stop": 1320},
    )
    assert ticket
    assert ticket["side"] == "BUY"
    assert ticket["qty"] == 3
    assert "buying 3 shares of RELIANCE" in ticket["line"]

    hold = paper_order_from_plan("RELIANCE", {"action": "HOLD"}, stop=1320)
    assert hold
    assert hold["kind"] == "alert"
    assert "alert" in hold["line"].lower()


def test_trade_plan_answer_exposes_parseable_paper_buy():
    answer = compose_structured_answer(
        query="Should I buy RELIANCE?",
        intent="price_action",
        market_context={
            "symbol": "RELIANCE",
            "current_price": 1380,
            "technical": {"rsi": 55, "trend": "up"},
            "trading_levels": {"support": 1340, "resistance": 1420, "stop_loss": 1320},
            "trade_plan": {
                "action": "BUY",
                "stop": 1320,
                "target_1": 1460,
                "position_qty_for_risk": 2,
                "horizon": "swing",
            },
        },
        context_lines=[],
        profile="trade_plan",
    ) or ""
    assert "consider buying 2 shares of reliance" in answer.lower()
    assert "paper" in answer.lower()
