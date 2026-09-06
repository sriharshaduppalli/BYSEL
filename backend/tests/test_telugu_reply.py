from app.telugu_reply import localize_suggestions, polish_telugu_answer, polish_telugu_result


def test_polish_fixes_buy_lead_on_hold_plan():
    raw = (
        "**RELIANCE** — paper buy plan\n"
        "**Your ask:** RELIANCE should I buy ?\n\n"
        "**Direct answer:** HOLD / wait — no clear edge yet\n"
        "**Action:** HOLD (score 0)\n"
        "• Meaning: No clear edge — stay flat or keep what you have (paper)\n"
    )
    out = polish_telugu_answer("రిలయన్స్ కొనాలా?", raw)
    assert "తెలుగు సారాంశం" in out
    assert out.startswith("**తెలుగు సారాంశం:**")
    assert "కొనండి (BUY)" not in out.splitlines()[0]
    assert "Your ask" not in out
    assert "should I buy ?" not in out
    assert "HOLD" in out or "హోల్డ్" in out


def test_sell_ask_lead_says_do_not_sell_on_accumulate():
    raw = (
        "**INFY** — paper sell plan\n"
        "**Direct answer:** ACCUMULATE on dips (paper) — staged entries\n"
        "**Action:** ACCUMULATE (score 2)\n"
    )
    out = polish_telugu_answer("INFY అమ్మాలా?", raw)
    assert out.startswith("**తెలుగు సారాంశం:**")
    assert "అమ్మకం వద్దు" in out.splitlines()[0]
    assert "ACCUMULATE" in out.splitlines()[0]


def test_leftover_english_labels_are_telugu():
    from indian_stock_llm.telugu_response import apply_telugu_leftovers

    raw = (
        "**WIPRO — sentiment analysis**\n"
        "**Overall:** Neutral\n"
        "ta RSI(14) is 45.92 from live market history for BAJFINANCE.\n"
        "Consider buying 121 shares of INFY (paper practice only).\n"
        "how much now\n"
        "This response is informational and should be validated against live market data.\n"
        "**ITC** — live quote · how much now\n"
        "_Live quote snapshot — educational only._\n"
        "Ask for technicals, news, valuation, or a paper trade plan if you need more depth.\n"
        "soft momentum\n"
        "MA bias down\n"
    )
    out = apply_telugu_leftovers(raw)
    assert "sentiment analysis" not in out
    assert "Overall:" not in out
    assert "ta RSI(14) is" not in out
    assert "Consider buying" not in out
    assert "how much now" not in out
    assert "Live quote snapshot" not in out
    assert "Ask for technicals" not in out
    assert "soft momentum" not in out
    assert "45.92" in out
    assert "INFY" in out
    outlook = apply_telugu_leftovers(
        "**NIFTY50 outlook (educational snapshot)**\n"
        "Read as a short-term bias from enrich signals — not a futures tip / not SPAN.\n"
    )
    assert "educational snapshot" not in outlook
    assert "futures tip" not in outlook
    disc = apply_telugu_leftovers(
        "_Educational — confirm with live NSE quotes before trading._\n"
        "• Last≈23897 | Trend≈bearish | RSI≈33.6\n"
    )
    assert "confirm with live NSE" not in disc
    assert "23897" in disc


def test_telugu_nifty_definition_stays_literacy():
    from indian_stock_llm.telugu_response import telugu_literacy_answer

    out = telugu_literacy_answer("NIFTY అంటే ఏమిటి?")
    assert out
    assert "NIFTY 50" in out
    assert "అంటే ఏమిటి" in out


def test_index_chips_are_not_buy_asks():
    tips = localize_suggestions(
        "Nifty ela undi?",
        [
            "Should I buy NIFTY50?",
            "What is the price of NIFTY50?",
            "Is NIFTY50 overvalued?",
            "Latest news on NIFTY50",
        ],
    )
    assert "కొనాలా?" not in " ".join(tips)
    assert any("ఎలా ఉంది" in t or "ధర ఎంత" in t for t in tips)
    assert tips[-2] == "NIFTY50 వార్తలు" or any("వార్తలు" in t for t in tips)


def test_polish_leaves_english_asks_alone():
    text = "**Direct answer:** HOLD"
    assert polish_telugu_answer("Should I buy RELIANCE?", text) == text


def test_localize_suggestions_for_telugu_ask():
    tips = localize_suggestions(
        "రిలయన్స్ కొనాలా?",
        [
            "Technical analysis of RELIANCE",
            "Latest news on RELIANCE",
            "Should I buy RELIANCE?",
        ],
    )
    assert tips[0] == "RELIANCE టెక్నికల్ అనాలిసిస్"
    assert tips[1] == "RELIANCE వార్తలు"
    assert tips[2] == "RELIANCE కొనాలా?"


def test_localize_suggestions_leaves_english_asks_alone():
    tips = ["Technical analysis of RELIANCE"]
    assert localize_suggestions("Should I buy RELIANCE?", tips) == tips


def test_telugu_greeting_uses_small_talk():
    from app.groq_llm import get_small_talk_response

    out = get_small_talk_response("నమస్తే")
    assert out
    assert "BYSEL" in out
    assert "నమస్తే" in out or "నేను" in out


def test_polish_result_updates_suggestions():
    out = polish_telugu_result(
        "రిలయన్స్ కొనాలా?",
        {
            "answer": "**Direct answer:** HOLD / wait — no clear edge yet",
            "suggestions": ["Technical analysis of RELIANCE"],
        },
    )
    assert "తెలుగు సారాంశం" in out["answer"]
    assert out["suggestions"] == ["RELIANCE టెక్నికల్ అనాలిసిస్"]
