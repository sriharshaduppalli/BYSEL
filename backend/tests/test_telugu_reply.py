from app.telugu_reply import polish_telugu_answer


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


def test_polish_leaves_english_asks_alone():
    text = "**Direct answer:** HOLD"
    assert polish_telugu_answer("Should I buy RELIANCE?", text) == text
