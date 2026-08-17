from app.market_scanner import (
    band_cagr,
    band_de,
    band_interest_coverage,
    band_roce,
    band_roe,
    band_rsi,
    build_scanner_payload,
    renormalized_score,
    score_quality,
    score_row,
    score_label_token,
)


def test_missing_quality_metrics_are_skipped_not_defaulted():
    quality, notes = score_quality(
        symbol="RELIANCE",
        roe=None,
        roce=None,
        debt_to_equity=None,
        interest_coverage=None,
        sales_cagr=None,
        profit_cagr=None,
        pledge=None,
        sector="Energy",
    )
    assert quality is None
    assert "ROCE —" in notes
    assert "ROE —" in notes
    assert "D/E —" in notes


def test_quality_renormalizes_when_only_roe_exists():
    only_roe, _ = score_quality(roe=22.0, roce=None, debt_to_equity=None)
    both, _ = score_quality(roe=22.0, roce=16.0, debt_to_equity=None)
    assert only_roe == 100
    assert both is not None
    assert both < only_roe
    assert both == 81


def test_roce_roe_debt_band_edges():
    assert band_roce(None) is None
    assert band_roce(25) == 100
    assert band_roce(20) == 85
    assert band_roce(15) == 65
    assert band_roce(10) == 40
    assert band_roce(9.9) == 15
    assert band_roe(20) == 100
    assert band_roe(15) == 85
    assert band_roe(7) == 15
    assert band_de(None) is None
    assert band_de(0.5) == 100
    assert band_de(1.0) == 85
    assert band_de(2.1) == 15
    assert band_interest_coverage(None) is None
    assert band_interest_coverage(8) == 100
    assert band_interest_coverage(4) == 85
    assert band_interest_coverage(0.5) == 15
    assert band_cagr(None) is None
    assert band_cagr(15) == 100
    assert band_cagr(10) == 85
    assert band_cagr(-1) == 15


def test_renormalize_skips_none_and_does_not_use_zero_defaults():
    blended = renormalized_score(
        {"roce": None, "roe": 100, "de": None},
        {"roce": 0.25, "roe": 0.20, "de": 0.15},
    )
    assert blended == 100
    empty = renormalized_score({"roce": None, "roe": None}, {"roce": 0.25, "roe": 0.20})
    assert empty is None


def test_rsi_band_and_score_label_tokens():
    assert band_rsi(None) is None
    assert band_rsi(50) == 90
    assert band_rsi(65) == 100
    assert score_label_token(80) == "high_conviction"
    assert score_label_token(65) == "attractive"
    assert score_label_token(50) == "neutral"
    assert score_label_token(35) == "caution"
    assert score_label_token(20) == "weak"
    assert "buy" not in score_label_token(90)


def test_score_row_json_has_pillars_and_skips_unknown_roce():
    row = {
        "symbol": "RELIANCE",
        "name": "Reliance",
        "last": 1400.0,
        "pctChange": 0.4,
        "pe": 24.0,
        "roe": 18.0,
        "roce": None,
        "debtToEquity": None,
        "fiftyDayAverage": 1380.0,
        "twoHundredDayAverage": 1300.0,
        "sector": "Energy",
    }
    scores = score_row(row, "long_term", sector_pe=28.0)
    assert scores["pillars"]["quality"]["metrics"]["roce"]["used"] is False
    assert scores["pillars"]["quality"]["metrics"]["roe"]["used"] is True
    assert scores["ai_summary"].startswith("scores ")
    assert scores["score_label"] in {"high_conviction", "attractive", "neutral", "caution", "weak", "insufficient"}
    assert "buy" not in scores["score_label"]
    assert scores["bysel_score"] == scores["byselScore"]


def test_build_payload_keeps_missing_honest():
    payload = build_scanner_payload(
        [
            {"symbol": "TCS", "last": 3500.0, "pctChange": 0.2, "trailingPE": 22.0, "roe": 28.0},
            {"symbol": "INFY", "last": 1500.0, "pctChange": -0.1, "trailingPE": 28.0},
        ],
        mode="long_term",
        limit=10,
    )
    infy = next(row for row in payload["rows"] if row["symbol"] == "INFY")
    assert infy["pillars"]["quality"]["score"] is None
    assert infy["pillars"]["quality"]["metrics"]["roce"]["used"] is False
    assert "pledge" in infy["missing"]
    assert infy["score_label"] in {"high_conviction", "attractive", "neutral", "caution", "weak", "insufficient"}
    assert "buy" not in infy["score_label"]
    assert "Never Strong Buy" in payload["education"]["scoreGuide"]
