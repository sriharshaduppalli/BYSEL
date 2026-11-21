"""Cross-layer TA math parity and ISM query-contract reliability tests."""
from __future__ import annotations

from app.stock_enricher import _calc_atr, _calc_rsi
from indian_stock_llm.analysis_math import (
    company_performance_scorecard,
    graham_intrinsic_value,
    structural_targets,
    trend_stage_analysis,
    wilder_rsi,
)
from indian_stock_llm.answer_composer import compose_structured_answer
from indian_stock_llm.calculations import _atr_last, _rsi_last
from indian_stock_llm.prediction import PredictionEngine
from indian_stock_llm.query_contract import resolve_query_contract
from indian_stock_llm.symbol_linker import link_symbols


def test_wilder_rsi_parity_across_all_backend_and_ism_layers():
    # 30 bars of realistic NSE price closes with mixed up and down swings
    closes = [
        2800.0, 2815.5, 2808.0, 2822.0, 2835.0, 2829.5, 2841.0, 2855.0, 2848.0, 2862.5,
        2870.0, 2858.0, 2845.0, 2852.0, 2868.0, 2880.0, 2872.0, 2890.0, 2905.0, 2898.0,
        2912.0, 2925.0, 2910.0, 2895.0, 2908.0, 2922.0, 2935.0, 2928.0, 2945.0, 2960.0,
    ]
    r_enricher = _calc_rsi(closes, 14)
    r_math = round(wilder_rsi(closes, 14), 2)
    r_calc = round(_rsi_last(closes, 14), 2)

    assert r_enricher is not None
    assert r_math is not None
    assert r_enricher == r_math == r_calc


def test_wilder_atr_parity_between_enricher_and_calculations():
    closes = [100.0 + (i * 1.2 if i % 3 != 0 else -0.8) for i in range(25)]
    highs = [c + 2.5 for c in closes]
    lows = [c - 2.0 for c in closes]

    atr_enricher = _calc_atr(highs, lows, closes, 14)
    atr_calc = round(_atr_last(highs, lows, closes, 14), 2)

    assert atr_enricher is not None
    assert atr_enricher == atr_calc


def test_no_synthetic_percentage_bands_when_atr_and_structure_missing():
    targets = structural_targets(
        price=1500.0,
        stop=None,
        atr_val=None,
        pivots=None,
        fib=None,
        resistance=None,
        bb_upper=None,
    )
    assert targets["target_1"] is None
    assert targets["target_2"] is None

    bands = PredictionEngine._bands(
        price=1500.0,
        atr_val=None,
        support=None,
        resistance=None,
        event_blackout=None,
    )
    assert bands == {}


def test_expanded_indian_market_definitions_route_to_literacy():
    queries = [
        "What is ROCE?",
        "What is India VIX?",
        "What is book value?",
        "What is market cap?",
        "Explain PCR in options",
        "What is ASM and GSM in NSE?",
        "What is Supertrend indicator?",
        "What is XIRR?",
    ]
    for q in queries:
        contract = resolve_query_contract(q)
        assert contract.profile == "literacy", (q, contract.profile, contract.slots.symbol)
        assert not contract.needs_symbol, (q, contract.needs_symbol)


def test_graham_intrinsic_value_and_company_performance_scorecard():
    # EPS=40, Book Value=200 -> Graham = sqrt(22.5 * 40 * 200) = sqrt(180000) = 424.26
    g = graham_intrinsic_value(eps=40.0, book_value=200.0, price=350.0)
    assert g["graham_number"] == 424.26
    assert g["margin_of_safety_pct"] is not None and g["margin_of_safety_pct"] > 15.0

    perf = company_performance_scorecard(
        {
            "eps": 40.0,
            "roe": 22.5,
            "operating_margin_pct": 18.0,
            "profit_margin_pct": 14.0,
            "revenue_growth": 15.0,
            "earnings_growth": 18.0,
            "debt_to_equity": 0.25,
            "current_ratio": 1.8,
            "free_cash_flow": 50_000_000_000.0,
            "market_cap": 1_000_000_000_000.0,
            "book_value": 200.0,
        },
        price=350.0,
        pe=18.0,
    )
    assert perf["grade"] == "strong"
    assert perf["quality_score"] == 9
    assert perf["fcf_yield_pct"] == 5.0
    assert perf["graham_number"] == 424.26


def test_trend_stage_and_cpr_breakout_analysis():
    # 210 bars steadily rising -> Price > SMA50 > SMA200 (Stage 2 Advancing)
    closes = [100.0 + i * 0.5 for i in range(210)]
    cpr = {"P": 200.0, "TC": 200.2, "BC": 199.8, "width_pct": 0.18}
    stage = trend_stage_analysis(closes, cpr=cpr)
    assert stage["dma_regime"] == "golden_cross_bullish"
    assert stage["weinstein_stage"] == "stage_2_advancing"
    assert stage["cpr_position"] == "above_tc_bullish"
    assert stage["narrow_cpr_breakout_alert"] is True


def test_ambiguous_english_word_tickers_require_stock_context():
    # "crude oil" must not link OIL (Oil India), but Asian Paints must link ASIANPAINT
    assert link_symbols("How does crude oil affect Asian Paints?") == ["ASIANPAINT"]
    # Lowercase "clean" in a technical sentence must not link CLEAN (Clean Science)
    assert link_symbols("Is this a clean breakout on the chart?") == []
    # ALL-CAPS ticker or explicit "share" context must link
    assert "IDEA" in link_symbols("Analyze IDEA")
    assert "IDEA" in link_symbols("What is idea share price?")


def test_indian_macro_transmission_cards_render_deterministically():
    crude_q = "How does crude oil price rise impact Indian market sectors?"
    contract = resolve_query_contract(crude_q)
    assert contract.profile == "literacy"
    ans = compose_structured_answer(
        query=crude_q,
        intent="market_literacy",
        market_context={},
        context_lines=[],
        profile="literacy",
    )
    assert ans is not None
    assert "Brent Crude Oil" in ans
    assert "ASIANPAINT" in ans
    assert "ONGC" in ans
