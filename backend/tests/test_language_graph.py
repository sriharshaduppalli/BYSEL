"""ISM language graph: deeper query understanding for habit / teach asks."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.ism_bootstrap import ensure_ism_on_path

ensure_ism_on_path()

from indian_stock_llm.language_graph import build_language_graph
from indian_stock_llm.query_contract import resolve_query_contract


def test_opening_range_is_literacy_not_trade_or_nse_dump():
    graph = build_language_graph(
        "Teach the NSE opening range and first-hour volatility as a paper habit."
    )
    assert "opening_range" in graph.concepts
    assert graph.profile_hint == "literacy"
    assert graph.intent_hint == "market_literacy"
    assert "session clock" in graph.retrieval_query

    contract = resolve_query_contract(
        "Teach the NSE opening range and first-hour volatility as a paper habit."
    )
    assert contract.profile == "literacy"
    assert contract.ism_intent == "market_literacy"
    assert "opening_range" in contract.graph_concepts
    assert "session clock" in contract.retrieval_query


def test_stop_loss_habit_is_not_a_live_trade_plan():
    contract = resolve_query_contract(
        "Teach stop-loss, size, and how to avoid FOMO on NSE paper trades."
    )
    assert contract.profile == "literacy"
    assert contract.ism_intent == "market_literacy"


def test_futures_vs_options_habit_is_not_a_name_compare():
    contract = resolve_query_contract(
        "What are futures vs options, lot size, margin and expiry for NSE beginners? "
        "Educational paper habits only. Not a stock pick."
    )
    assert contract.profile == "literacy"
    assert contract.groq_intent == "EDUCATIONAL"


def test_named_stock_trade_plan_still_wins():
    contract = resolve_query_contract("Should I buy RELIANCE?")
    assert contract.profile == "trade_plan"
    assert contract.slots.symbol == "RELIANCE"


def test_opening_range_of_named_stock_is_technical():
    graph = build_language_graph("What is the opening range of RELIANCE?")
    assert "opening_range" in graph.concepts
    assert graph.profile_hint == "technical"
    assert "RELIANCE" in graph.symbols


def test_beginners_is_not_a_stock_slot():
    from indian_stock_llm.query_contract import _symbol_candidates

    assert "BEGINNERS" not in _symbol_candidates(
        "How should beginners do long-term investing in Indian stocks?"
    )
    contract = resolve_query_contract(
        "How should beginners do long-term investing in Indian stocks?"
    )
    assert contract.slots.symbol is None
    assert contract.profile == "literacy"


def test_language_graph_can_be_disabled(monkeypatch):
    monkeypatch.setenv("ISM_LANGUAGE_GRAPH_ENABLED", "false")
    graph = build_language_graph("Teach the NSE opening range as a paper habit.")
    assert graph.concepts == ()
    assert graph.profile_hint is None
