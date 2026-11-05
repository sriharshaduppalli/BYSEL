"""Desk cards for Indian Stock Model chat. No network."""
from app.ism_desk import (
    atm_straddle,
    build_chat_desk,
    chain_max_pain,
    detect_desk_focus,
    render_desk_answer,
    render_desk_footnote,
)
from app.market_education import get_education_answer
from indian_stock_llm.answer_composer import compose_structured_answer
from indian_stock_llm.query_language import detect_query_language, polish_hinglish_answer


def test_max_pain_picks_the_heavy_strike():
    contracts = [
        {"strike": 100, "callOi": 10, "putOi": 0, "callLtp": 5, "putLtp": 1},
        {"strike": 110, "callOi": 0, "putOi": 50, "callLtp": 2, "putLtp": 4},
    ]
    assert chain_max_pain(contracts) == 110
    assert atm_straddle(contracts, 108)["strike"] == 110


def test_chain_card_refuses_a_missing_board():
    text = render_desk_answer(
        "NIFTY max pain",
        {"focus": "chain", "chain": {"symbol": "NIFTY", "expiry": "2026-11-03", "source": "missing", "loaded": False}},
    )
    assert "did not load" in text
    assert "PCR" not in text


def test_teaching_chain_is_labeled():
    text = render_desk_answer(
        "show the straddle",
        {
            "focus": "chain",
            "chain": {
                "symbol": "NIFTY",
                "expiry": "2026-11-03",
                "source": "synthetic",
                "loaded": True,
                "spot": 24000,
                "pcr": 1.1,
                "max_pain": 24000,
                "straddle": 210,
                "atm": 24000,
            },
        },
    )
    assert "Teaching chain" in text
    assert "not a traded straddle" in text


def test_focus_leaves_definitions_alone():
    assert detect_desk_focus("What is SIP?") == ""
    assert detect_desk_focus("What is an IPO?") == ""
    assert detect_desk_focus("what is max pain") == ""
    assert detect_desk_focus("what is a study note") == ""
    assert detect_desk_focus("NIFTY max pain") == "chain"
    assert detect_desk_focus("this chain") == "chain"
    assert detect_desk_focus("show my study note") == "journal"
    assert detect_desk_focus("my notes") == "journal"
    assert detect_desk_focus("open ipo list") == "ipo"
    assert detect_desk_focus("show ipos") == "ipo"
    assert detect_desk_focus("list of ipos") == "ipo"
    assert detect_desk_focus("How to apply for IPO") == ""
    assert detect_desk_focus("nav of parag parikh") == "funds"
    assert detect_desk_focus("what is expense ratio") == ""


def test_ipo_card_omits_grey_market_premium():
    text = render_desk_answer(
        "open ipo list",
        {"focus": "ipo", "ipos": [{"name": "Example Ltd", "status": "OPEN", "open": "2026-11-01", "close": "2026-11-04", "band": "100–110", "lot": "10"}]},
    )
    assert "Grey-market premium is not in this list." in text
    assert "Example Ltd" in text


def test_screen_chain_answers_with_the_open_board():
    desk = build_chat_desk(
        "NIFTY max pain",
        screen={
            "option_symbol": "NIFTY",
            "option_expiry": "2026-11-03",
            "option_source": "nse",
            "option_pcr": "1.12",
            "option_max_pain": "24500",
            "option_straddle": "210.50",
            "option_atm": "24500",
            "option_spot": "24480",
        },
    )
    text = render_desk_answer("NIFTY max pain", desk)
    assert "Chain on this screen" in text
    assert "1.12" in text
    assert "24500" in text
    assert "did not load" not in text


def test_synthetic_screen_chain_is_not_used(monkeypatch):
    monkeypatch.setattr(
        "app.derivatives_data.fetch_nse_option_chain",
        lambda *_args, **_kwargs: None,
    )
    desk = build_chat_desk(
        "NIFTY max pain",
        screen={
            "option_symbol": "NIFTY",
            "option_expiry": "2026-11-03",
            "option_source": "synthetic",
            "option_pcr": "9.99",
            "option_max_pain": "1",
            "option_straddle": "5",
            "option_spot": "100",
        },
    )
    text = render_desk_answer("NIFTY max pain", desk)
    assert "did not load" in text
    assert "9.99" not in text


def test_named_symbol_metric_skips_the_glossary():
    assert get_education_answer("stochastic of RELIANCE") is None
    assert get_education_answer("show ipos") is None
    assert get_education_answer("what is max pain")
    assert get_education_answer("How to apply for IPO")
    assert get_education_answer("what is nifty")


def test_earnings_footnote_on_a_stock_card():
    note = render_desk_footnote(
        {"focus": "", "earnings": {"date": "2026-11-12", "illustrative": True}}
    )
    assert "illustrative" in note
    assert render_desk_footnote({"focus": "chain", "earnings": {"date": "2026-11-12"}}) == ""


def test_rsi_ask_does_not_grow_a_trade_plan():
    text = compose_structured_answer(
        query="RSI of RELIANCE",
        intent="stock_analysis",
        market_context={
            "symbol": "RELIANCE",
            "current_price": 1400,
            "technical": {"rsi": 61.2, "rsi_interpretation": "bullish momentum"},
            "trade_plan": {"action": "BUY", "stop": 1300, "target_1": 1500},
        },
        context_lines=[],
        profile="technical",
    )
    assert "RSI" in text
    assert "Stop idea" not in text
    assert "BUY" not in text


def test_hindi_labels_keep_the_figure():
    assert detect_query_language("आर एस आई क्या है") == "hi"
    text = polish_hinglish_answer(
        "RELIANCE ka RSI kya hai",
        "**Technical readout:**\n• RSI: 61.20\n_Educational only — not investment advice._",
    )
    assert "61.20" in text
    assert "**Technical:**" in text
    assert "research-analyst call nahi" in text
    indicator = polish_hinglish_answer(
        "RELIANCE ka RSI kya hai",
        "**RELIANCE — indicator snapshot**\n\n• ta RSI(14) is 37.97 from live market history for RELIANCE.\n\n"
        "Use with price structure / volume — one indicator is not a trade plan.\n"
        "_Educational calculation from recent market history — not investment advice._",
    )
    assert "37.97" in indicator
    assert indicator.startswith("**Seedha jawab:**")
    assert "ek indicator trade plan nahi hai" in indicator


def test_missed_asks_stay_on_the_right_card(monkeypatch):
    from indian_stock_llm.calculations import PandasTaIndicatorCalculator
    from indian_stock_llm.query_contract import resolve_query_contract

    monkeypatch.setattr("app.ism_desk._fetch_exchange_chain", lambda *_a, **_k: None)
    assert PandasTaIndicatorCalculator._period_from_query("50 ema of reliance") == 50
    assert PandasTaIndicatorCalculator._period_from_query("rsi of tcs") == 14
    expense = get_education_answer("what is expense ratio") or ""
    assert "expense" in expense.lower()
    gmp = get_education_answer("what is grey market premium") or ""
    assert "not an NSE or BSE price" in gmp
    assert "₹" not in gmp
    condor = get_education_answer("iron condor on nifty") or ""
    assert "Iron Condor" in condor
    assert "CONCOR" not in condor
    junk = resolve_query_contract("asdf qwerty market")
    assert junk.slots.symbol is None
    chain = build_chat_desk(
        "this chain",
        symbol="CHOLAFIN",
        screen={"source": "options", "symbol": "NIFTY", "chart_symbol": "NIFTY"},
    )
    rendered = render_desk_answer("this chain", chain) or ""
    assert "NIFTY" in rendered
    assert "CHOLAFIN" not in rendered
