"""Home / F&O Learn chips must hit a concrete habit lesson, not a glossary dump."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.habit_lessons import get_habit_lesson, resolve_habit_lesson_id
from app.market_education import get_education_answer

# Keep in lockstep with android/.../HabitLiteracy.kt
_CATALOG = {
    "open": "Teach the NSE opening range and first-hour volatility as a paper habit. Cover: mark the first 15-minute band after 9:15, why the first hour is noisy, one wait-for-break rule, and the FOMO chase mistake. Not a stock pick.",
    "risk": "Teach stop-loss, size, and how to avoid FOMO on NSE paper trades. Cover: write invalidation first, size so a full stop is small, and skip late chases. Not a stock pick.",
    "chop": "Teach midday chop and revenge trading on NSE as a paper habit. Cover: when to stand aside after lunch, and why a second trade to 'get even' is a journal fail. Not a stock pick.",
    "close": "Teach the NSE closing auction CAS and why to square off intraday paper trades. Cover: flatten before the close window, and that the official close can differ from the last tick. Not a stock pick.",
    "weekend": "Teach how beginners should review paper trades on weekends and holidays. Cover: three-line journal, next week's calendar, and watchlist hygiene. Not a stock pick.",
    "long_term": "Teach how beginners should do long-term investing in Indian stocks. Cover: match money to a horizon, a simple mix, costs, and why monthly stock-swapping is not investing. Not a stock pick.",
    "mutual_funds": "What are mutual funds, NAV, SIP and TER for Indian beginners? Teach one factsheet habit. Educational only — not a fund recommendation.",
    "ipo": "Teach how beginners should read an IPO DRHP, valuation vs peers, and allotment risk. Cover: use of proceeds, risk factors, and why oversubscription is not a thesis. Not an IPO recommendation.",
    "fno": "What are futures vs options, lot size, margin and expiry for NSE beginners? Educational paper habits only. Not a stock pick.",
    "sgb": "What are Sovereign Gold Bonds vs gold ETF for Indian beginners? Teach one issue-circular habit. Educational only — not a bond recommendation.",
    "fno_vs": "What is the difference between NSE futures and options for beginners? Educational paper practice only. Not a stock pick.",
    "fno_lot": "What are lot size and margin in NSE F&O for beginners? Educational paper practice only. Not a stock pick.",
    "fno_expiry": "Why do NSE options lose value as expiry nears? Educational paper practice only. Not a stock pick.",
}

_LEGACY = {
    "open": "What is the NSE opening range and first-hour volatility? Educational paper-practice habits only — no buy or sell.",
    "risk": "How should beginners set stop-loss, size, and avoid FOMO on NSE paper trades? Educational only — no buy or sell.",
    "chop": "What is midday chop and revenge trading on NSE? Educational session habits only — no buy or sell.",
    "close": "What is the NSE closing auction CAS and why square off intraday paper trades? Educational only — no buy or sell.",
    "weekend": "How should beginners review paper trades on weekends and holidays? Educational session habits only.",
    "long_term": "How should beginners do long-term investing in Indian stocks? Educational investor habits only — no buy or sell.",
}


def test_catalog_queries_resolve_to_matching_lesson():
    for lesson_id, query in _CATALOG.items():
        assert resolve_habit_lesson_id(query) == lesson_id, lesson_id
        answer = get_education_answer(query)
        assert answer, lesson_id
        assert "paper habit" in answer.lower(), lesson_id
        assert "nseindia.com" not in answer.lower(), lesson_id
        assert "equation" not in answer.lower(), lesson_id


def test_legacy_home_queries_still_hit_habits():
    for lesson_id, query in _LEGACY.items():
        assert resolve_habit_lesson_id(query) == lesson_id, lesson_id


def test_greeks_literacy_card_is_not_stolen_by_habit_pack():
    query = "What are futures vs options, lot size, margin, PCR and Greeks for NSE beginners?"
    assert resolve_habit_lesson_id(query) is None


def test_product_screen_short_queries():
    assert resolve_habit_lesson_id("What are mutual funds?") == "mutual_funds"
    assert resolve_habit_lesson_id("What are Sovereign Gold Bonds?") == "sgb"


def test_tip_fallback_is_concrete():
    query = (
        "Teach this paper habit: Define the horizon first. "
        "Context: Money needed within 3 years usually shouldn't sit in concentrated equity bets. "
        "Educational investor habits only. Not a stock pick."
    )
    answer = get_habit_lesson(query)
    assert answer
    assert "Define the horizon first" in answer
    assert "3 years" in answer
    assert "not a buy or sell call" in answer.lower()


def test_opening_range_is_not_nseindia_dump():
    answer = get_education_answer(_CATALOG["open"])
    assert "opening range" in answer.lower()
    assert "9:15" in answer
    assert "do **not** crawl" not in answer.lower()


def test_ask_llm_habit_wins_over_selected_symbol():
    from app.llm_integration import ask_llm

    result = ask_llm(_CATALOG["risk"], {"symbol": "INFY"})
    assert result
    assert result.get("source") == "indian-stock-llm-education"
    assert "bysel_habit_lessons" in (result.get("citations") or [])
    assert "fomo" in result["answer"].lower()
    assert "infy" not in result["answer"].lower()


def test_dividend_date_of_symbol_skips_yield_glossary():
    assert get_education_answer("Dividend date of INFY") is None
    assert get_education_answer("Corporate actions of TCS") is None
    yield_card = get_education_answer("what is dividend")
    assert yield_card
    assert "dividend yield" in yield_card.lower()
    primer = get_education_answer("what is a corporate action")
    assert primer
    assert "ex-date" in primer.lower()


def test_ask_llm_dividend_date_uses_dated_pack():
    from app.llm_integration import ask_llm

    result = ask_llm("Dividend date of INFY")
    assert result
    answer = (result.get("answer") or "")
    assert "2026-05-15" in answer
    assert "equation" not in answer.lower()
    assert "dividend yield" not in answer.lower()
    assert "indian-stock-llm-education" not in str(result.get("source") or "")
