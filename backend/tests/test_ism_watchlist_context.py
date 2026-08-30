"""Watchlist / holdings are ISM inputs as symbols only — no invented P&L."""
from __future__ import annotations

from indian_stock_llm.answer_composer import compose_structured_answer
from indian_stock_llm.query_contract import resolve_query_contract, should_inherit_symbol


def test_watchlist_ask_routes_to_portfolio_without_screen_stock():
    contract = resolve_query_contract(
        "What's on my watchlist?",
        screen_context={"symbol": "RELIANCE"},
    )
    assert contract.profile == "portfolio"
    assert contract.slots.symbol is None
    assert should_inherit_symbol(contract.profile, "What's on my watchlist?", False) is False


def test_ask_llm_keeps_client_watchlist_without_symbol():
    from app.llm_integration import ask_llm

    result = ask_llm(
        "What's on my watchlist?",
        {
            "portfolio_context": {
                "symbols": [],
                "concentrations": {},
                "watchlist": ["TCS", "HDFCBANK"],
            }
        },
    )
    answer = ((result or {}).get("answer") or "").lower()
    assert "on your list" in answer
    assert "tcs" in answer
    assert "hdfcbank" in answer
    assert "do not invent a return" in answer


def test_composer_lists_symbols_without_invented_pnl():
    answer = compose_structured_answer(
        query="What's on my watchlist?",
        intent="portfolio",
        market_context={
            "portfolio_context": {
                "symbols": ["INFY"],
                "concentrations": {"INFY": 4},
                "watchlist": ["TCS", "HDFCBANK"],
            }
        },
        context_lines=[],
        profile="portfolio",
    ) or ""
    low = answer.lower()
    assert "on your list" in low
    assert "infy" in low
    assert "tcs" in low
    assert "do not invent a return" in low
    assert "total_value" not in low
    assert "p&l +" not in low
