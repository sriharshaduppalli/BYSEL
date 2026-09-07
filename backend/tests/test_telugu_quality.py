from app.telugu_reply import polish_telugu_answer


def test_sell_lead_does_not_override_accumulate_stance():
    raw = (
        "**\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41 \u0c38\u0c3e\u0c30\u0c3e\u0c02\u0c36\u0c02:** "
        "\u0c05\u0c2e\u0c4d\u0c2e\u0c02\u0c21\u0c3f (SELL) \u2014 details.\n\n"
        "**INFY** \u2014 paper sell plan\n"
        "**Your ask:** INFY should I sell ?\n\n"
        "**Direct answer:** ACCUMULATE on dips (paper) \u2014 staged entries\n"
        "**Action:** ACCUMULATE (score 2)\n"
    )
    out = polish_telugu_answer("INFY \u0c05\u0c2e\u0c4d\u0c2e\u0c3e\u0c32\u0c3e?", raw)
    assert "\u0c05\u0c2e\u0c4d\u0c2e\u0c15\u0c02 \u0c35\u0c26\u0c4d\u0c26\u0c41" in out.splitlines()[0]
    assert "Your ask" not in out


def test_nifty_ela_undi_outlook_is_telugu():
    raw = (
        "**NIFTY50 outlook (educational snapshot)**\n\n"
        "\u2022 Last\u224823,897.70 | Trend\u2248bearish | RSI\u224833.6 (neutral)\n"
        "\u2022 MACD bias\u2248None\n"
        "\u2022 Support\u224823,786.80 | Resistance\u224824,620.95\n\n"
        "Read as a short-term bias from enrich signals \u2014 not a futures tip / not SPAN.\n"
        "_Educational \u2014 confirm with live NSE quotes before trading._"
    )
    out = polish_telugu_answer("Nifty ela undi?", raw)
    assert out.startswith("**\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41 \u0c38\u0c3e\u0c30\u0c3e\u0c02\u0c36\u0c02:**")
    assert "outlook (educational snapshot)" not in out
    assert "Trend\u2248bearish" not in out
    assert "23,897.70" in out
    assert "Your ask" not in out
