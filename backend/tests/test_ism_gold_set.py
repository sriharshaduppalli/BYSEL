"""Gold set: ISM must route and answer the Indian-retail asks we already taught."""
from __future__ import annotations

from indian_stock_llm.answer_composer import compose_structured_answer
from indian_stock_llm.conversation import small_talk_reply
from indian_stock_llm.ism_agent import run_ism_agent
from indian_stock_llm.query_contract import resolve_query_contract, should_inherit_symbol
from indian_stock_llm.symbol_linker import is_index_or_market_ask, link_symbols

# query, profile, heading needle, forbidden needles
GOLD = (
    ("What is the price of RELIANCE?", "quote", "live quote", ("— paper trade plan", "wilder", "entry zone")),
    ("kitna hai TCS", "quote", "live quote", ("— paper trade plan", "entry zone")),
    ("Should I buy RELIANCE?", "trade_plan", "paper buy plan", ("wilder", "ev/ebitda")),
    ("How is RELIANCE doing?", "stock_analysis", "snapshot", ("paper trade plan", "entry zone", "direct answer")),
    ("INFY target next month", "prediction", "scenario range", ("paper trade plan", "entry zone")),
    ("Any update on RELIANCE?", "news", "news", ("paper trade plan",)),
    ("Mood on HDFCBANK", "sentiment", "sentiment", ("paper trade plan",)),
    ("Is TCS cheap?", "fundamentals", "fundamental", ("entry zone",)),
    ("Downside in RELIANCE?", "risks", "risk", ("entry zone",)),
    ("Show RELIANCE chart", "technical", "technical", ("direct answer",)),
    ("What is RSI?", "literacy", "rsi", ("reliance",)),
    ("good morning", "small_talk", "", ("reliance —",)),
    ("Nifty outlook", "prediction", "index outlook", ("paper trade plan",)),
    ("Is market bullish today?", "sentiment", "market mood", ("paper trade plan",)),
    ("TCS or INFY?", "compare", "tcs", ()),
    ("Explain delivery vs intraday", "literacy", "delivery", ("delhivery",)),
    ("kya main HDFCBANK kharidun?", "trade_plan", "paper buy plan", ()),
    ("50 EMA of RELIANCE", "technical", "technical", ()),
    ("FII buying in RELIANCE", "news", "news", ()),
    ("what is a straddle", "literacy", "straddle", ()),
    ("Is the market open?", "session", "session", ("reliance —",)),
    ("Dividend date of RELIANCE", "corporate_actions", "corporate", ("2024-01-01",)),
    ("Dividend date of INFY", "corporate_actions", "2026-05-15", ("dividend yield", "equation")),
    ("What's on my watchlist?", "portfolio", "on your list", ("paper trade plan", "entry zone")),
)


CTX = {
    "symbol": "RELIANCE",
    "current_price": 1380.0,
    "technical": {"rsi": 58.0, "trend": "up"},
    "fundamental": {"pe": 24.0, "pb": 2.1},
    "trading_levels": {"support": 1340.0, "resistance": 1420.0},
    "trade_plan": {"action": "HOLD", "stop": 1320.0, "target_1": 1460.0},
    "news_headlines": ["Reliance Jio capex update"],
    "sentiment": {"overall": "mixed"},
    "sentiment_pack": {"label": "mixed", "summary": "Mixed tape."},
}


def test_gold_set_routes_and_shapes():
    for query, profile, needle, forbidden in GOLD:
        contract = resolve_query_contract(query)
        assert contract.profile == profile, (query, contract.profile, profile)
        talk = small_talk_reply(query)
        if talk and profile == "small_talk":
            answer = talk
        else:
            ctx = dict(CTX)
            if contract.slots.symbol:
                ctx["symbol"] = contract.slots.symbol
            elif not should_inherit_symbol(profile, query, bool(contract.slots.symbol)):
                ctx.pop("symbol", None)
            answer = compose_structured_answer(
                query=query,
                intent=contract.ism_intent,
                market_context=ctx,
                context_lines=[],
                profile=contract.profile,
            ) or ""
        low = answer.lower()
        if needle:
            assert needle in low, (query, needle, answer[:180])
        for bad in forbidden:
            assert bad not in low, (query, bad, answer[:180])


def test_linker_resolves_spoken_indian_names():
    assert "RELIANCE" in link_symbols("Should I buy Reliance Industries?")
    assert "HDFCBANK" in link_symbols("compare hdfc bank and icici bank")
    assert "SBIN" in link_symbols("sbi outlook this week")
    assert "RELIANCE" in link_symbols("relaince price")


def test_index_ask_does_not_inherit_screen_stock():
    assert is_index_or_market_ask("Nifty outlook")
    contract = resolve_query_contract(
        "Nifty outlook",
        screen_context={"symbol": "RELIANCE"},
    )
    assert contract.profile == "prediction"
    assert contract.slots.symbol is None
    rsi = resolve_query_contract("What is RSI?", screen_context={"symbol": "TCS"})
    assert rsi.profile == "literacy"
    assert rsi.slots.symbol is None


def test_agent_plans_indian_market_tools():
    quote = run_ism_agent("What is the price of RELIANCE?", market_context=CTX)
    assert quote["profile"] == "quote"
    assert "live_quote" in quote["tools"]
    assert "live quote" in quote["answer"].lower()

    index = run_ism_agent(
        "Nifty outlook",
        market_context=CTX,
        screen_context={"symbol": "RELIANCE"},
    )
    assert index["profile"] == "prediction"
    assert index["symbol"] is None
    assert "index outlook" in index["answer"].lower()
    assert "reliance —" not in index["answer"].lower()
