"""ISM must change card format when the user's ask changes."""
from __future__ import annotations

from indian_stock_llm.answer_composer import compose_structured_answer
from indian_stock_llm.answer_shape import detect_answer_shape
from indian_stock_llm.conversation import small_talk_reply
from indian_stock_llm.query_contract import resolve_query_contract, should_inherit_symbol

CTX = {
    "symbol": "RELIANCE",
    "current_price": 1380.0,
    "technical": {"rsi": 58.0, "trend": "up", "macd_hist": 1.2},
    "fundamental": {"pe": 24.0, "pb": 2.1, "roe": 8.5, "eps": 58, "market_cap": "19L cr"},
    "trading_levels": {"support": 1340.0, "resistance": 1420.0, "stop_loss": 1320.0},
    "trade_plan": {
        "action": "HOLD",
        "stop": 1320.0,
        "target_1": 1460.0,
        "horizon": "swing",
        "entry_zone": [1350, 1370],
        "risk_reward": 1.8,
        "position_qty_for_risk": 3,
    },
    "news_headlines": ["Reliance Jio capex update"],
    "sentiment": {"overall": "mixed"},
    "sentiment_pack": {"label": "mixed", "summary": "Mixed tape."},
    "portfolio_context": {
        "symbols": ["INFY"],
        "concentrations": {"INFY": 4},
        "watchlist": ["TCS"],
    },
}

# Different users / different asks — each pair must not share a heading.
SHAPE_PAIRS = (
    ("Should I buy RELIANCE?", "Hold or exit SBIN?"),
    ("Book profit in INFY?", "accumulate HDFCBANK on dips"),
    ("Any update on RELIANCE?", "Why is RELIANCE falling?"),
    ("RELIANCE Q2 results", "FII buying in RELIANCE"),
    ("Show RELIANCE chart", "50 EMA of RELIANCE"),
    ("volume spike in SBIN", "Support resistance for SBIN"),
    ("Is TCS cheap?", "Market cap of RELIANCE"),
    ("PE of INFY", "promoter pledge in ADANIENT"),
    ("INFY target next month", "Will TCS reach 4000?"),
    ("Covered call on RELIANCE", "lot size of RELIANCE"),
    ("What's on my watchlist?", "position size for RELIANCE"),
    ("What is the price of RELIANCE?", "SBIN cmp"),
)


def _heading(text: str) -> str:
    for line in (text or "").splitlines():
        if line.startswith("**") and "your ask" not in line.lower():
            return re_sub_ws(line)
    return re_sub_ws((text or "").splitlines()[0] if text else "")


def re_sub_ws(text: str) -> str:
    return " ".join((text or "").lower().split())


def _answer(query: str) -> tuple[str, str]:
    contract = resolve_query_contract(query)
    talk = small_talk_reply(query)
    if talk and contract.profile == "small_talk":
        return contract.profile, talk
    ctx = dict(CTX)
    if contract.slots.symbol:
        ctx["symbol"] = contract.slots.symbol
    elif not should_inherit_symbol(contract.profile, query, bool(contract.slots.symbol)):
        ctx.pop("symbol", None)
    answer = (
        compose_structured_answer(
            query=query,
            intent=contract.ism_intent,
            market_context=ctx,
            context_lines=[],
            profile=contract.profile,
        )
        or ""
    )
    return contract.profile, answer


def test_same_profile_asks_get_distinct_headings():
    for left, right in SHAPE_PAIRS:
        _pl, a = _answer(left)
        _pr, b = _answer(right)
        assert _heading(a) != _heading(b), (left, right, _heading(a), _heading(b))


def test_retail_misroutes_are_query_faithful():
    ipo = resolve_query_contract("How to apply for IPO")
    assert ipo.profile == "literacy"
    assert "ipo" in _answer("How to apply for IPO")[1].lower()

    expiry = resolve_query_contract("Bank Nifty expiry this week")
    assert expiry.profile == "derivatives"
    assert expiry.slots.symbol is None

    nifty_pe = resolve_query_contract(
        "Nifty 50 PE ratio",
        screen_context={"symbol": "RELIANCE"},
    )
    assert nifty_pe.profile == "fundamentals"
    assert nifty_pe.slots.symbol is None
    assert nifty_pe.clarifier is None
    answer = _answer("Nifty 50 PE ratio")[1].lower()
    assert "index valuation" in answer
    assert "reliance —" not in answer

    cagr = _answer("calculate CAGR for 100000 to 180000 in 3 years")[1].lower()
    assert "cagr working" in cagr
    assert "21.64%" in cagr or "21.6" in cagr


def test_shape_facets_match_wording():
    assert detect_answer_shape("50 EMA of RELIANCE", "technical")[0] == "ema"
    assert detect_answer_shape("Why is RELIANCE falling?", "news")[0] == "why_move"
    assert detect_answer_shape("Book profit in INFY?", "trade_plan")[0] == "book"
    assert detect_answer_shape("PE of INFY", "fundamentals")[0] == "pe"
