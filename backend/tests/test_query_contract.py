"""Query contract: one brain for intent, follow-ups, and answer shape."""
from __future__ import annotations

from indian_stock_llm.answer_composer import resolve_stock_response_profile
from indian_stock_llm.prediction import PredictionEngine
from indian_stock_llm.query_contract import resolve_query_contract


def test_retail_asks_match_expected_profiles():
    cases = {
        "Can I buy RELIANCE now?": "trade_plan",
        "Is it a good time to buy TCS?": "trade_plan",
        "Should I wait for a dip in MARUTI?": "trade_plan",
        "Show RELIANCE chart": "technical",
        "View chart for INFY": "technical",
        "How is RELIANCE doing?": "stock_analysis",
        "Tell me about BEL": "stock_analysis",
        "Why is RELIANCE falling?": "news",
        "RELIANCE vs last week": "stock_analysis",
        "HDFCBANK vs ICICIBANK": "compare",
        "kitna hai TCS": "quote",
        "kya main HDFCBANK kharidun?": "trade_plan",
        "Hold or exit SBIN?": "trade_plan",
        "Add more RELIANCE?": "trade_plan",
        "Book profit in INFY?": "trade_plan",
        "Average down on TCS?": "trade_plan",
        "Is RELIANCE a good buy for swing?": "trade_plan",
        "Short HAL?": "trade_plan",
        "Any update on RELIANCE?": "news",
        "What is happening in INFY?": "news",
        "Is market bullish today?": "sentiment",
        "Mood on HDFCBANK": "sentiment",
        "Is TCS cheap?": "fundamentals",
        "Book value of SBIN": "fundamentals",
        "INFY target next month": "prediction",
        "Will TCS reach 4000?": "prediction",
        "Downside in RELIANCE?": "risks",
        "What can go wrong in INFY?": "risks",
        "TCS or INFY?": "compare",
        "Better HDFCBANK or ICICIBANK": "compare",
        "Which PSU stocks?": "sector_screen",
        "Auto names for swing": "sector_screen",
        "Explain delivery vs intraday": "literacy",
        "How does SIP work?": "literacy",
        "Nifty call option meaning": "literacy",
        "good morning": "small_talk",
        "RELIANCE ka price": "quote",
        "TCS ka rate kya hai": "quote",
        "SBIN cmp": "quote",
        "Should I hold TCS?": "trade_plan",
        "Exit INFY now?": "trade_plan",
        "Trim HDFCBANK?": "trade_plan",
        "Buy the dip in MARUTI?": "trade_plan",
        "Is it time to sell ITC?": "trade_plan",
        "accumulate HDFCBANK on dips": "trade_plan",
        "Why did HAL jump": "news",
        "RELIANCE Q2 results": "news",
        "Any announcement on SBIN": "news",
        "upper circuit on YESBANK": "news",
        "Are traders bullish on TCS": "sentiment",
        "200 DMA of INFY": "technical",
        "Support resistance for SBIN": "technical",
        "RELIANCE breakout?": "technical",
        "volume spike in SBIN": "technical",
        "FII DII data": "literacy",
        "bonus issue meaning": "literacy",
        "portfolio concentration": "portfolio",
        "promoter pledge in ADANIENT": "fundamentals",
        "SIP vs lumpsum": "compare_concepts",
        "gold vs stocks": "compare_concepts",
        "Nifty outlook": "prediction",
        "Nifty ela undi?": "prediction",
        "TCS ka kya haal": "stock_analysis",
        "INFY target next month": "prediction",
        "add RELIANCE on every dip": "trade_plan",
        "50 EMA of RELIANCE": "technical",
        "FII buying in RELIANCE": "news",
        "Market cap of RELIANCE": "fundamentals",
        "Covered call on RELIANCE": "derivatives",
        "what is a straddle": "literacy",
        "is paper trading useful": "literacy",
        "best banks to paper trade": "sector_screen",
        "How much brokerage on RELIANCE": "literacy",
        "good night": "small_talk",
        "kaise ho": "small_talk",
        "position size for RELIANCE": "portfolio",
    }
    for query, expected in cases.items():
        contract = resolve_query_contract(query)
        assert contract.profile == expected, (query, contract.profile)


def test_chip_queries_keep_distinct_profiles():
    cases = {
        "Should I buy RELIANCE?": ("trade_plan", "BUY_SELL"),
        "Predict RELIANCE price": ("prediction", "PREDICT"),
        "Technical analysis of RELIANCE": ("technical", "TECHNICAL"),
        "Latest news on RELIANCE": ("news", "NEWS"),
        "RELIANCE market sentiment": ("sentiment", "SENTIMENT"),
        "What is the price of RELIANCE?": ("quote", "QUOTE"),
        "Is RELIANCE overvalued?": ("fundamentals", "FUNDAMENTAL"),
        "What is RSI?": ("literacy", "EDUCATIONAL"),
        "calculate CAGR for 100000 to 180000 in 3 years": ("calculations", "CALCULATION"),
    }
    for query, (profile, groq_intent) in cases.items():
        contract = resolve_query_contract(query)
        assert contract.profile == profile, (query, contract.profile)
        assert contract.groq_intent == groq_intent, (query, contract.groq_intent)
        assert resolve_stock_response_profile(query, "general_query") == profile


def test_forecast_target_is_not_a_trade_plan_card():
    from indian_stock_llm.answer_composer import compose_structured_answer

    answer = compose_structured_answer(
        query="INFY target next month",
        intent="prediction",
        market_context={
            "symbol": "INFY",
            "current_price": 1400.0,
            "technical": {"rsi": 55.0, "trend": "up"},
            "trading_levels": {"support": 1350.0, "resistance": 1450.0},
        },
        context_lines=[],
        profile="prediction",
    ) or ""
    low = answer.lower()
    assert "scenario range" in low or "not a price guarantee" in low
    assert "paper trade plan" not in low
    assert "entry zone" not in low


def test_how_is_named_stock_is_not_the_market_primer():
    from indian_stock_llm.answer_composer import compose_structured_answer

    for query in ("RELIANCE how is it?", "How is RELIANCE?", "How is this one?"):
        answer = compose_structured_answer(
            query=query if query != "How is this one?" else "How is RELIANCE?",
            intent="market_literacy",
            market_context={
                "symbol": "RELIANCE",
                "current_price": 1226.0,
                "technical": {"rsi": 25.7, "trend": "bearish"},
                "trading_levels": {"support": 1210.5, "resistance": 1330.0},
            },
            context_lines=[],
            profile="stock_analysis",
        ) or ""
        assert "How the Indian stock market works" not in answer, query
        assert "1226" in answer or "1,226" in answer, query
        assert "RELIANCE" in answer.upper()


def test_quote_hides_blank_ohlc():
    from indian_stock_llm.answer_composer import compose_structured_answer

    answer = compose_structured_answer(
        query="What is the price of RELIANCE?",
        intent="price_action",
        market_context={"symbol": "RELIANCE", "current_price": 1226.0},
        context_lines=[],
        profile="quote",
    ) or ""
    assert "1226" in answer or "1,226" in answer
    assert "Open / High / Low" not in answer
    assert "n/a" not in answer.lower()


def test_news_drops_unrelated_headlines():
    from indian_stock_llm.answer_composer import compose_structured_answer

    answer = compose_structured_answer(
        query="Any news on HDFCBANK?",
        intent="events_news",
        market_context={
            "symbol": "HDFCBANK",
            "company_name": "HDFC Bank",
            "current_price": 1700.0,
            "news_headlines": [
                "Microsoft opens a new campus in Hyderabad",
                "HDFC Bank raises deposit rates",
                "Chainlink price jumps on crypto flows",
            ],
        },
        context_lines=[],
        profile="news",
    ) or ""
    assert "HDFC Bank raises deposit rates" in answer
    assert "Microsoft" not in answer
    assert "Chainlink" not in answer


def test_short_followup_does_not_reprint_the_plan():
    from indian_stock_llm.answer_composer import compose_structured_answer

    answer = compose_structured_answer(
        query="Why this paper stance on TCS?",
        intent="price_action",
        market_context={
            "symbol": "TCS",
            "current_price": 2082.0,
            "technical": {"rsi": 48.0, "trend": "neutral"},
            "trading_levels": {"support": 1995.0, "resistance": 2212.0},
        },
        context_lines=[],
        profile="trade_plan",
    ) or ""
    assert "in short" in answer.lower()
    assert "**Why:**" in answer
    assert "Paper ticket" not in answer
    assert "Entry zone" not in answer


def test_cheaper_followup_names_the_lower_price():
    from indian_stock_llm.answer_composer import compose_structured_answer

    answer = compose_structured_answer(
        query="Which is cheaper, TCS or INFY?",
        intent="compare",
        market_context={
            "symbol": "TCS",
            "current_price": 2082.0,
            "peers": [{"symbol": "INFY", "current_price": 1000.2}],
        },
        context_lines=[],
        profile="compare",
    ) or ""
    low = answer.lower()
    assert "tcs" in low and "infy" in low
    assert "infy is lower" in low


def test_or_compare_names_the_pair_even_without_second_tape():
    from indian_stock_llm.answer_composer import compose_structured_answer

    answer = compose_structured_answer(
        query="TCS or INFY?",
        intent="compare",
        market_context={
            "symbol": "INFY",
            "current_price": 1400.0,
            "technical": {"rsi": 55.0, "trend": "up"},
            "fundamental": {"pe": 24.0},
        },
        context_lines=[],
        profile="compare",
    ) or ""
    low = answer.lower()
    assert "tcs" in low and "infy" in low
    assert "| tcs |" in low and "| infy |" in low
    assert "wilder" not in low
    assert "pass a second ticker" not in low


def test_literacy_followup_does_not_reuse_last_symbol():
    history = [
        {"role": "user", "content": "Technical analysis of TCS"},
        {"role": "assistant", "content": "**TCS** — RSI 55"},
    ]
    contract = resolve_query_contract("What is RSI?", conversation_history=history)
    assert contract.profile == "literacy"
    assert contract.slots.follow_up is False
    assert "TCS" not in (contract.resolved_query or "").upper()


def test_short_compare_chip_is_not_an_ambiguous_name():
    for query in (
        "Compare HDFCBANK with ICICIBANK",
        "Compare HDFCBANK with ICICBANK",
        "Compare ICICI Bank and HDFC Bank",
    ):
        contract = resolve_query_contract(query)
        assert contract.profile == "compare", query
        assert contract.clarifier is None, query
        names = {contract.slots.symbol, *contract.slots.peer_symbols}
        assert "HDFCBANK" in names, query
        assert "ICICIBANK" in names, query


def test_followup_replies_do_not_repeat_or_drop_the_pair():
    quote_hist = [
        {"role": "user", "content": "What is the price of RELIANCE?"},
        {"role": "assistant", "content": "**RELIANCE** last 1226"},
    ]
    plan_hist = [
        {"role": "user", "content": "Should I buy TCS?"},
        {"role": "assistant", "content": "**TCS** paper plan HOLD"},
    ]
    compare_hist = [
        {"role": "user", "content": "Compare TCS and INFY"},
        {"role": "assistant", "content": "Comparison TCS INFY"},
    ]
    bank_hist = [
        {"role": "user", "content": "Compare HDFCBANK and ICICIBANK"},
        {"role": "assistant", "content": "HDFCBANK ICICIBANK"},
    ]
    ela_hist = [
        {"role": "user", "content": "RELIANCE ela undi?"},
        {"role": "assistant", "content": "**RELIANCE** snapshot"},
    ]

    why_quote = resolve_query_contract("why?", conversation_history=quote_hist)
    assert "explain the reliance snapshot" in why_quote.resolved_query.lower()
    assert why_quote.profile != "quote"

    why_plan = resolve_query_contract("why?", conversation_history=plan_hist)
    assert "why this paper stance" in why_plan.resolved_query.lower()
    assert why_plan.profile != "trade_plan"

    more = resolve_query_contract("tell me more", conversation_history=quote_hist)
    assert "how is reliance doing" in more.resolved_query.lower()
    assert more.profile == "stock_analysis"

    simple = resolve_query_contract("simplify", conversation_history=plan_hist)
    assert "simple words" in simple.resolved_query.lower()
    assert simple.profile != "trade_plan"

    cheaper = resolve_query_contract("which is cheaper?", conversation_history=compare_hist)
    assert cheaper.profile == "compare"
    assert "TCS" in cheaper.resolved_query.upper()
    assert "INFY" in cheaper.resolved_query.upper()

    dono = resolve_query_contract("dono", conversation_history=bank_hist)
    assert dono.profile == "compare"
    assert "HDFCBANK" in dono.resolved_query.upper()
    assert "ICICIBANK" in dono.resolved_query.upper()

    aur = resolve_query_contract("aur INFY?", conversation_history=plan_hist)
    assert aur.profile == "trade_plan"
    assert "INFY" in aur.resolved_query.upper()
    assert aur.slots.symbol == "INFY"

    entha = resolve_query_contract("entha?", conversation_history=ela_hist)
    assert entha.profile == "quote"
    assert entha.slots.symbol == "RELIANCE"

    levels = resolve_query_contract("levels?", screen_context={"symbol": "SBIN"})
    assert "practice levels" in levels.resolved_query.lower()
    assert levels.slots.symbol == "SBIN"
    assert levels.profile == "technical"


def test_telugu_both_followup_compares_the_named_pair():
    history = [
        {"role": "user", "content": "Compare HDFCBANK with ICICIBANK"},
        {"role": "assistant", "content": "Which symbol do you want?"},
    ]
    for ask in ("rendu", "donu", "రెండు"):
        contract = resolve_query_contract(ask, conversation_history=history)
        assert contract.profile == "compare", ask
        if ask == "rendu":
            assert contract.language == "te-en"
        assert contract.clarifier is None, ask
        assert "HDFCBANK" in contract.resolved_query.upper(), ask
        assert "ICICIBANK" in contract.resolved_query.upper(), ask


def test_hinglish_missing_symbol_clarifier():
    contract = resolve_query_contract("kya main kharidun?")
    assert contract.language == "hi-en"
    assert contract.clarifier
    assert "symbol" in contract.clarifier.lower() or "NSE" in contract.clarifier


def test_both_followup_compares_the_named_pair():
    history = [
        {"role": "user", "content": "Compare HDFCBANK with ICICIBANK"},
        {
            "role": "assistant",
            "content": "That name matches more than one listed company (HDFCBANK, ICICIBANK). Which symbol do you want?",
        },
    ]
    contract = resolve_query_contract("both", conversation_history=history)
    assert contract.profile == "compare"
    assert contract.slots.follow_up is True
    assert contract.clarifier is None
    assert "HDFCBANK" in contract.resolved_query.upper()
    assert "ICICIBANK" in contract.resolved_query.upper()


def test_this_one_uses_screen_symbol_without_chat_history():
    contract = resolve_query_contract(
        "how is this one?",
        screen_context={"symbol": "RELIANCE", "source": "stock_detail"},
    )
    assert contract.slots.follow_up is True
    assert contract.slots.symbol == "RELIANCE"
    assert "RELIANCE" in contract.resolved_query.upper()
    assert contract.profile in {"stock_analysis", "quote"}

    literacy = resolve_query_contract(
        "What is RSI?",
        screen_context={"symbol": "RELIANCE", "source": "stock_detail"},
    )
    assert literacy.profile == "literacy"
    assert literacy.slots.symbol is None


def test_followup_reuses_last_symbol_and_changes_shape():
    history = [
        {"role": "user", "content": "Technical analysis of RELIANCE"},
        {"role": "assistant", "content": "**RELIANCE** — RSI 55"},
    ]
    contract = resolve_query_contract("what about sentiment?", conversation_history=history)
    assert contract.slots.follow_up is True
    assert "RELIANCE" in contract.resolved_query.upper()
    assert contract.profile == "sentiment"
    assert contract.clarifier is None


def test_same_for_other_symbol_keeps_prior_shape():
    history = [
        {"role": "user", "content": "Should I buy TCS?"},
        {"role": "assistant", "content": "HOLD TCS"},
    ]
    contract = resolve_query_contract("same for INFY", conversation_history=history)
    assert "INFY" in contract.resolved_query.upper()
    assert contract.profile == "trade_plan"


def test_trade_plan_format_gives_buy_sell_advice():
    text = resolve_query_contract("Should I buy RELIANCE?").format_instructions
    assert "BUY / SELL / HOLD" in text
    assert "NEVER say BUY" not in text


def test_quote_format_does_not_force_a_trade_call():
    text = resolve_query_contract("What is the price of RELIANCE?").format_instructions
    assert "quote snapshot" in text.lower()
    assert "BUY / SELL / HOLD" not in text


def test_prediction_engine_emits_advice_and_bands():
    engine = PredictionEngine()
    signals = engine.predict(
        context_items=[],
        p0_math={
            "price": 1000.0,
            "atr_14": 20.0,
            "wilder_rsi_14": 55,
            "supertrend": {"direction": "bullish"},
            "macd": {"histogram": 1.2},
            "vs_nifty": {"rs_20d": 1.04},
            "trade_plan": {"action": "BUY"},
            "levels": {"support": 960.0, "resistance": 1040.0},
        },
    )
    assert signals.advice in {"BUY", "SELL", "HOLD"}
    assert signals.scenarios.get("swing_band")
    assert signals.intraday.direction in {"bullish", "bearish", "neutral"}


def test_event_window_blocks_buy_advice():
    engine = PredictionEngine()
    signals = engine.predict(
        context_items=[],
        p0_math={
            "price": 1000.0,
            "atr_14": 20.0,
            "event_note": "earnings date tomorrow",
            "trade_plan": {"action": "BUY"},
            "supertrend": {"direction": "bullish"},
        },
    )
    assert signals.event_blackout
    assert signals.advice == "HOLD"


def test_telugu_script_routes_like_english():
    buy = resolve_query_contract("రిలయన్స్ కొనాలా?")
    assert buy.language == "te"
    assert buy.profile == "trade_plan"
    assert buy.slots.symbol == "RELIANCE"

    quote = resolve_query_contract("ITC ధర ఎంత?")
    assert quote.profile == "quote"
    assert quote.slots.symbol == "ITC"

    literacy = resolve_query_contract("RSI అంటే ఏమిటి?")
    assert literacy.profile == "literacy"


def test_tenglish_routes_like_english():
    quote = resolve_query_contract("ITC dhara entha undi?")
    assert quote.language == "te-en"
    assert quote.profile == "quote"
    assert quote.slots.symbol == "ITC"

    buy = resolve_query_contract("Reliance konala?")
    assert buy.profile == "trade_plan"
    assert buy.slots.symbol == "RELIANCE"

    about = resolve_query_contract("\u0c28\u0c3e\u0c15\u0c41 \u0c10\u0c1f\u0c40\u0c38\u0c40 \u0c17\u0c41\u0c30\u0c3f\u0c02\u0c1a\u0c3f \u0c1a\u0c46\u0c2a\u0c4d\u0c2a\u0c02\u0c21\u0c3f")
    assert about.slots.symbol == "ITC"
    assert "\u0c28\u0c3e\u0c15\u0c41" not in about.resolved_query

    predict = resolve_query_contract("HDFCBANK ela untundi?")
    assert predict.profile == "prediction"


def test_telugu_answer_gets_summary():
    from indian_stock_llm.query_language import localize_assistant_answer

    localized = localize_assistant_answer(
        "రిలయన్స్ కొనాలా?",
        "**Direct answer:** HOLD / wait — no clear edge yet\n**Why:** RSI 52 is mid-range",
    )
    assert "తెలుగు సారాంశం" in localized
    assert "HOLD" in localized
    assert "నేరుగా సమాధానం" in localized
    assert "కారణం" in localized
    assert "వేచి" in localized
    assert "మధ్యస్థం" in localized
    assert "కొనండి (BUY)" not in localized
    assert localize_assistant_answer("Should I buy RELIANCE?", "**Direct answer:** HOLD") == (
        "**Direct answer:** HOLD"
    )


def test_telugu_buy_plan_does_not_flip_hold_to_buy():
    from indian_stock_llm.query_language import localize_assistant_answer

    localized = localize_assistant_answer(
        "\u0c30\u0c3f\u0c32\u0c2f\u0c28\u0c4d\u0c38\u0c4d \u0c15\u0c4a\u0c28\u0c3e\u0c32\u0c3e?",
        "**RELIANCE** — paper buy plan\n"
        "**Your ask:** RELIANCE should I buy ?\n\n"
        "**Direct answer:** HOLD / wait — no clear edge yet\n"
        "**Action:** HOLD (score 0)\n"
        "• Meaning: No clear edge — stay flat or keep what you have (paper)\n",
    )
    assert "\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41 \u0c38\u0c3e\u0c30\u0c3e\u0c02\u0c36\u0c02" in localized
    assert "HOLD" in localized
    assert not localized.startswith(
        "**\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41 \u0c38\u0c3e\u0c30\u0c3e\u0c02\u0c36\u0c02:** \u0c15\u0c4a\u0c28\u0c02\u0c21\u0c3f"
    )
    assert "Your ask" not in localized
    assert "should I buy ?" not in localized


def test_telugu_literacy_uses_telugu_primer():
    from indian_stock_llm.query_language import localize_assistant_answer

    localized = localize_assistant_answer(
        "RSI అంటే ఏమిటి?",
        "**RSI (Relative Strength Index)**\n\nRSI is a momentum oscillator (0–100).",
    )
    assert "అంటే ఏమిటి" in localized
    assert "overbought" in localized
    assert "పెట్టుబడి సలహా కాదు" in localized


def test_tenglish_answer_is_also_telugu():
    from indian_stock_llm.query_language import localize_assistant_answer

    localized = localize_assistant_answer(
        "ITC dhara entha undi?",
        "**ITC** — live quote\n\n• Last: \u20b9412\n• Support / Resistance: 400 / 430",
    )
    assert "\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41 \u0c38\u0c3e\u0c30\u0c3e\u0c02\u0c36\u0c02" in localized
    assert "\u20b9412" in localized
    assert "\u0c1a\u0c3f\u0c35\u0c30\u0c3f" in localized


def test_indic_trans_shields_rupee_and_tickers():
    from indian_stock_llm.indic_trans import shield_protected_tokens, unshield_protected_tokens

    shielded, tokens = shield_protected_tokens(
        "RELIANCE last is ₹412 and RSI 52 is mid-range"
    )
    assert "RELIANCE" in tokens
    assert "₹412" in tokens
    assert "RSI" in tokens
    assert "[[T0]]" in shielded
    assert unshield_protected_tokens(shielded, tokens).startswith("RELIANCE")


def test_indic_trans_leftover_english_uses_worker(monkeypatch):
    from indian_stock_llm import indic_trans
    from indian_stock_llm.query_language import localize_assistant_answer

    monkeypatch.setenv("INDIC_TRANS_URL", "http://127.0.0.1:8101")
    monkeypatch.setenv("INDIC_TRANS_ENABLED", "true")

    def fake_batch(texts):
        return ["ఈ వాక్యం తెలు�ఈ వాక్యం తెలుగులో ఉంది" for _ in texts]

    monkeypatch.setattr(indic_trans, "translate_english_batch", fake_batch)
    localized = localize_assistant_answer(
        "రిలయన్స్ ఎలా ఉంది?",
        "**Direct answer:** HOLD / wait — no clear edge yet\n"
        "The tape looks mixed today and you should wait for confirmation at support.",
    )
    assert "తెలుగు సారాంశం" in localized
    assert "ఈ వాక్యం తెలుగులో ఉంది" in localized
    assert "నేరుగా సమాధానం" in localized


def test_indic_trans_needs_nmt_skips_formulas():
    from indian_stock_llm.indic_trans import needs_nmt

    assert needs_nmt("The tape looks mixed today and you should wait.") is True
    assert needs_nmt("`RSI = 100 − (100 / (1 + RS))`") is False
    assert needs_nmt("RSI 52") is False
    assert needs_nmt("Disclaimer: Educational / informational only") is False
    assert needs_nmt("• Entry zone: 1288.7 — 1320.5") is False
    assert needs_nmt("**కారణం:** RSI 52 is mid-range") is False
    assert needs_nmt("**కారణం:** RSI 52 మధ్యస్థంలో ఉంది") is False
    assert needs_nmt("Momentum (RSI): RSI 49 neutral") is False
    assert needs_nmt("**WIPRO** — Wipro Limited — news & catalysts") is False
    assert needs_nmt("| RELIANCE | 1322.0 | 55.14 | Neutral |") is False


def test_indic_trans_does_not_nmt_mixed_telugu_lines(monkeypatch):
    from indian_stock_llm import indic_trans

    called: list[list[str]] = []

    monkeypatch.setattr(indic_trans, "indic_trans_enabled", lambda: True)
    monkeypatch.setattr(
        indic_trans,
        "translate_english_batch",
        lambda texts: called.append(list(texts)) or texts,
    )
    mixed = (
        "**కారణం:** RSI 52 is mid-range\n"
        "The tape looks mixed today and you should wait."
    )
    out = indic_trans.translate_leftover_english(mixed)
    assert called == [["The tape looks mixed today and you should wait."]]
    assert "**కారణం:** RSI 52 is mid-range" in out


def test_indic_trans_rejects_nllb_garbage():
    from indian_stock_llm.indic_trans import usable_telugu_line

    assert usable_telugu_line("Why: RSI 52 is mid-range", ":::::") is False
    assert usable_telugu_line("Why: RSI 52 is mid-range", "# # # # # # #") is False
    assert usable_telugu_line("Why: RSI 52 is mid-range", "RSI 52 మధ్యస్థంలో ఉంది") is True
    assert usable_telugu_line("**WIPRO** — news", "* * విప్రో * *-వార్తలు") is False
    assert usable_telugu_line("| RELIANCE | 1322.0 |", "| విశ్వసనీయత | 1322.0 |") is False


def test_indic_trans_off_keeps_phrase_table(monkeypatch):
    from indian_stock_llm.query_language import localize_assistant_answer

    monkeypatch.delenv("INDIC_TRANS_URL", raising=False)
    monkeypatch.delenv("BYSEL_INDIC_TRANS_URL", raising=False)
    monkeypatch.delenv("INDIC_TRANS_ENABLED", raising=False)
    localized = localize_assistant_answer(
        "రిలయన్స్ కొనాలా?",
        "**Direct answer:** HOLD / wait — no clear edge yet\n**Why:** RSI 52 is mid-range",
    )
    assert "నేరుగా సమాధానం" in localized
    assert "మధ్యస్థం" in localized


def test_nifty_how_is_it_uses_index_not_clarifier():
    contract = resolve_query_contract("Nifty ela undi?")
    assert contract.clarifier is None
    assert contract.profile == "prediction"
    assert contract.slots.symbol in {None, "NIFTY50", "NIFTY"}
    inherited = resolve_query_contract(
        "Nifty ela undi?",
        screen_context={"symbol": "RELIANCE"},
    )
    assert inherited.profile == "prediction"
    assert inherited.slots.symbol != "RELIANCE"
    pe = resolve_query_contract(
        "Nifty 50 PE ratio",
        screen_context={"symbol": "RELIANCE"},
    )
    assert pe.profile == "fundamentals"
    assert pe.slots.symbol is None
    assert pe.clarifier is None
