from app.models.schemas import ScannerResponse
from app.market_scanner import (
    SCANNER_MODES,
    band_cagr,
    band_de,
    band_interest_coverage,
    band_roce,
    band_roe,
    band_rsi,
    build_scanner_payload,
    clear_scanner_cache,
    color_band,
    detect_anomalies,
    evaluate_quality_screen,
    get_market_scanner,
    renormalized_score,
    score_quality,
    score_row,
    score_label_token,
    top_contributing_metrics,
)


def test_missing_quality_metrics_are_skipped_not_defaulted():
    quality, notes, parts = score_quality(
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
    assert parts["roce"] is None
    assert parts["roe"] is None


def test_quality_renormalizes_when_only_roe_exists():
    only_roe, _, _ = score_quality(roe=22.0, roce=None, debt_to_equity=None)
    both, _, _ = score_quality(roe=22.0, roce=16.0, debt_to_equity=None)
    assert only_roe == 70
    assert both is not None
    assert both < only_roe
    assert both == 51


def test_roce_roe_debt_band_edges():
    assert band_roce(None) is None
    assert band_roce(8) == 0
    assert band_roce(30) == 100
    assert band_roce(22) == 64
    assert band_roe(8) == 0
    assert band_roe(28) == 100
    assert band_roe(18) == 50
    assert band_de(None) is None
    assert band_de(0.3) == 100
    assert band_de(0.6) == 84
    assert band_de(1.8) == 20
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
    assert band_rsi(50) == 80
    assert band_rsi(65) == 90
    assert band_rsi(20, trend_score=70) == 80
    assert score_label_token(80) == "strong"
    assert score_label_token(65) == "good"
    assert score_label_token(50) == "mixed"
    assert score_label_token(35) == "weak"
    assert score_label_token(20) == "poor"
    assert "buy" not in score_label_token(90)
    assert "sell" not in score_label_token(90)


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
    assert "BYSEL Score" in scores["ai_summary"]
    assert "not investment advice" in scores["ai_summary"].lower()
    assert scores["score_label"] in {"strong", "good", "mixed", "weak", "poor", "insufficient"}
    assert "buy" not in scores["score_label"]
    assert scores["bysel_score"] == scores["byselScore"]
    assert scores["colorBand"] in {"teal", "blue", "grey", "amber", "red", "none"}
    assert scores["formulaChangedDate"] == "2026-09-14"
    q_top = scores["pillars"]["quality"]["topMetrics"]
    assert len(q_top) <= 3
    assert all(item["id"] != "roce" for item in q_top)
    assert any(item["id"] == "roe" for item in q_top)


def test_top_metrics_skip_missing_and_cap_three():
    metrics = {
        "roce": {"value": None, "score": None, "used": False},
        "roe": {"value": 22.0, "score": 70, "used": True},
        "debtToEquity": {"value": 0.4, "score": 95, "used": True},
        "cfo": {"value": None, "score": None, "used": False},
        "margin": {"value": None, "score": None, "used": False},
        "gov": {"value": 8.0, "score": 70, "used": True},
    }
    weights = {
        "roce": 0.25,
        "roe": 0.20,
        "debtToEquity": 0.20,
        "cfo": 0.15,
        "margin": 0.10,
        "gov": 0.10,
    }
    top = top_contributing_metrics(metrics, weights, limit=3)
    assert len(top) == 3
    assert all(item["id"] in {"roe", "debtToEquity", "gov"} for item in top)
    assert "roce" not in {item["id"] for item in top}
    assert top[0]["contribution"] >= top[-1]["contribution"]


def test_color_band_thresholds():
    assert color_band(None) == "none"
    assert color_band(80) == "teal"
    assert color_band(79) == "blue"
    assert color_band(65) == "blue"
    assert color_band(64) == "grey"
    assert color_band(50) == "grey"
    assert color_band(49) == "amber"
    assert color_band(34) == "red"


def test_swing_setup_has_paper_levels_and_no_invented_winrate():
    row = {
        "symbol": "INFY",
        "name": "Infosys",
        "last": 1500.0,
        "pctChange": 0.5,
        "pe": 24.0,
        "roe": 20.0,
        "fiftyDayAverage": 1480.0,
        "twoHundredDayAverage": 1400.0,
        "rsi": 52.0,
        "volumeRatio": 1.7,
        "sector": "IT",
    }
    scores = score_row(row, "swing", sector_pe=26.0)
    setup = scores["setup"]
    assert setup is not None
    assert setup["setupType"] in {"pullback", "breakout"}
    assert setup["t1"] is not None and setup["t2"] is not None and setup["stop"] is not None
    assert setup["riskReward"] is not None
    assert "paper" in setup["note"].lower()
    assert "advice" in setup["note"].lower()
    assert setup.get("winRate") is None
    assert "n/a" in (setup.get("winRateNote") or "").lower()
    assert setup.get("momentumScore") == scores["momentum"]


def test_swing_payload_caps_cards_with_setups():
    quotes = [
        {
            "symbol": f"S{i}",
            "last": 1000.0 + i,
            "pctChange": 0.2,
            "trailingPE": 20.0,
            "roe": 18.0,
            "fiftyDayAverage": 990.0,
            "twoHundredDayAverage": 900.0,
            "rsi": 55.0,
            "volume": 2_000_000,
            "avgVolume": 1_000_000,
        }
        for i in range(20)
    ]
    payload = build_scanner_payload(quotes, mode="swing", limit=30)
    assert 1 <= len(payload["rows"]) <= 15
    assert all(row.get("setup") for row in payload["rows"])
    assert all(row["setup"]["setupType"] in {"pullback", "breakout"} for row in payload["rows"])


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
    assert infy["score_label"] in {"strong", "good", "mixed", "weak", "poor", "insufficient"}
    assert "buy" not in infy["score_label"]
    assert "Never Strong Buy" in payload["education"]["scoreGuide"]


def test_anomalies_flag_unusual_volume_and_existing_pledge_only():
    empty = detect_anomalies({"volumeRatio": 1.4, "pledge": None, "marginPct": None})
    assert empty == []
    volume = detect_anomalies({"volumeRatio": 2.4})
    assert any(item["id"] == "unusual_volume" for item in volume)
    assert all(item["id"] != "pledging" for item in volume)
    pledge = detect_anomalies({"pledge": 12.0})
    assert any(item["id"] == "pledging" and "12" in item["detail"] for item in pledge)
    zero_pledge = detect_anomalies({"pledge": 0.0})
    assert all(item["id"] != "pledging" for item in zero_pledge)
    invented_ids = {item["id"] for item in detect_anomalies({"volumeRatio": 3.0, "pledge": 8.0})}
    assert "promoter_selling" not in invented_ids
    assert "related_party" not in invented_ids


def test_custom_payload_sorts_by_score_and_keeps_anomalies():
    quotes = [
        {
            "symbol": "LOW",
            "last": 100.0,
            "pctChange": -0.4,
            "trailingPE": 40.0,
            "roe": 8.0,
            "volume": 900_000,
            "avgVolume": 1_000_000,
        },
        {
            "symbol": "HIGH",
            "last": 200.0,
            "pctChange": 1.2,
            "trailingPE": 18.0,
            "roe": 22.0,
            "fiftyDayAverage": 190.0,
            "twoHundredDayAverage": 170.0,
            "volume": 3_000_000,
            "avgVolume": 1_000_000,
            "pledge": 9.0,
        },
    ]
    payload = build_scanner_payload(quotes, mode="custom", limit=20)
    assert payload["mode"] == "custom"
    symbols = [row["symbol"] for row in payload["rows"]]
    assert symbols[0] == "HIGH"
    high = payload["rows"][0]
    anomaly_ids = {item["id"] for item in high.get("anomalies") or []}
    assert "unusual_volume" in anomaly_ids
    assert "pledging" in anomaly_ids
    assert all("buy" not in (row.get("scoreLabel") or "").lower() for row in payload["rows"])
    assert high.get("setup") is None


def test_quality_screen_skips_missing_and_does_not_invent_promoter():
    thin = evaluate_quality_screen(
        {"marketCap": 8_000_000_000, "pe": 18.0},
        sector_pe=22.0,
    )
    by_id = {item["id"]: item for item in thin["checks"]}
    assert by_id["mcap"]["status"] == "pass"
    assert by_id["pe_vs_sector"]["status"] == "pass"
    assert by_id["roce"]["status"] == "skip"
    assert by_id["promoter"]["status"] == "skip"
    assert by_id["pledge"]["status"] == "skip"
    assert thin["matches"] is False
    assert thin["passed"] == 2

    rich = evaluate_quality_screen(
        {
            "marketCap": 12_000_000_000,
            "peg": 0.8,
            "pe": 16.0,
            "roe": 24.0,
            "revenueGrowth": 18.0,
            "earningsGrowth": 21.0,
            "marginPct": 19.0,
            "priceToSales": 4.2,
            "evEbitda": 12.0,
        },
        sector_pe=20.0,
    )
    assert rich["failed"] == 0
    assert rich["passed"] >= 4
    assert rich["matches"] is True
    assert all(item["status"] != "pass" for item in rich["checks"] if item["id"] in {"roce", "promoter", "pledge"})


def test_quality_screen_payload_drops_fails_and_keeps_honest_copy():
    quotes = [
        {
            "symbol": "PASSER",
            "name": "Passer Ltd",
            "last": 410.0,
            "pctChange": 0.4,
            "marketCap": 9_000_000_000,
            "trailingPE": 14.0,
            "peg": 0.7,
            "roe": 26.0,
            "revenueGrowth": 0.22,
            "earningsGrowth": 0.18,
            "operatingMargins": 0.21,
            "priceToSales": 3.5,
            "enterpriseToEbitda": 11.0,
        },
        {
            "symbol": "FAILER",
            "name": "Failer Ltd",
            "last": 90.0,
            "pctChange": -0.2,
            "marketCap": 1_000_000_000,
            "trailingPE": 40.0,
            "peg": 2.4,
            "roe": 8.0,
        },
    ]
    payload = build_scanner_payload(quotes, mode="quality_screen", limit=10)
    assert payload["mode"] == "quality_screen"
    assert payload["education"]["title"].startswith("Quality screen")
    assert "not a forecast" in payload["education"]["summary"]
    symbols = [row["symbol"] for row in payload["rows"]]
    assert "PASSER" in symbols
    assert "FAILER" not in symbols
    passer = payload["rows"][0]
    assert passer["qualityScreen"]["matches"] is True
    assert "multibagger prediction" in payload["education"]["riskNote"]


def test_quality_screen_shows_closest_fits_when_nobody_fully_matches():
    quotes = [
        {
            "symbol": "NEAR",
            "name": "Near Fit Ltd",
            "last": 220.0,
            "pctChange": 0.3,
            "marketCap": 9_000_000_000,
            "trailingPE": 16.0,
            "peg": 0.8,
            "roe": 12.0,
            "revenueGrowth": 0.18,
        },
        {
            "symbol": "THIN",
            "name": "Thin Ltd",
            "last": 80.0,
            "pctChange": -0.1,
            "marketCap": 2_000_000_000,
        },
    ]
    payload = build_scanner_payload(quotes, mode="quality_screen", limit=10)
    symbols = [row["symbol"] for row in payload["rows"]]
    assert "NEAR" in symbols
    assert all(not (row.get("qualityScreen") or {}).get("matches") for row in payload["rows"])


def test_score_row_does_not_invent_promoter_or_related_party_anomalies():
    scores = score_row(
        {
            "symbol": "TCS",
            "last": 3500.0,
            "pctChange": 0.2,
            "pe": 22.0,
            "roe": 28.0,
            "volumeRatio": 1.1,
        },
        "custom",
        sector_pe=24.0,
    )
    ids = {item["id"] for item in scores.get("anomalies") or []}
    assert "promoter_selling" not in ids
    assert "related_party" not in ids
    assert "unusual_volume" not in ids


def test_get_market_scanner_warms_sibling_modes(monkeypatch):
    clear_scanner_cache()
    quotes = [
        {
            "symbol": "TCS",
            "last": 3500.0,
            "pctChange": 0.4,
            "trailingPE": 22.0,
            "roe": 28.0,
            "fiftyDayAverage": 3400.0,
            "twoHundredDayAverage": 3100.0,
            "rsi": 58.0,
            "volume": 2_000_000,
            "avgVolume": 1_000_000,
        },
        {
            "symbol": "INFY",
            "last": 1500.0,
            "pctChange": -0.2,
            "trailingPE": 26.0,
            "roe": 24.0,
            "fiftyDayAverage": 1480.0,
            "twoHundredDayAverage": 1400.0,
            "rsi": 47.0,
            "volume": 1_500_000,
            "avgVolume": 1_200_000,
        },
    ]
    calls = {"n": 0}

    def fake_fetch(*_args, **_kwargs):
        calls["n"] += 1
        return quotes

    monkeypatch.setattr("app.market_data.fetch_quotes", fake_fetch)
    first = get_market_scanner("long_term", limit=10, force_refresh=True)
    assert first["mode"] == "long_term"
    assert set(first["byMode"]) == set(SCANNER_MODES)
    assert calls["n"] == 1

    monkeypatch.setattr(
        "app.market_data.fetch_quotes",
        lambda *_args, **_kwargs: (_ for _ in ()).throw(AssertionError("cache miss")),
    )
    swing = get_market_scanner("swing", limit=10, force_refresh=False)
    assert swing["cached"] is True
    assert swing["mode"] == "swing"
    assert "swing" in swing["byMode"]
    ScannerResponse.model_validate(first)
    ScannerResponse.model_validate(swing)


def test_scanner_response_accepts_empty_qm_badge():
    """Missing / mid-band Q+M used to send qmBadge=None and FastAPI 500'd every tab."""
    payload = build_scanner_payload(
        [
            {"symbol": "MIXED", "last": 100.0, "roe": 16.0},
            {"symbol": "MID", "last": 110.0, "rsi": 55.0},
        ],
        mode="long_term",
        limit=10,
    )
    assert all(isinstance(row.get("qmBadge"), str) for row in payload["rows"])
    payload["byMode"] = {mode: list(payload["rows"]) for mode in SCANNER_MODES}
    parsed = ScannerResponse.model_validate(payload)
    assert parsed.rows
    assert all(isinstance(row.qmBadge, str) for row in parsed.rows)


def test_daily_snapshot_roundtrip_without_migration():
    from app.database.db import ByselScoreSnapshotModel, SessionLocal
    from app.market_scanner import get_score_history, persist_daily_score_snapshots

    persist_daily_score_snapshots(
        [
            {
                "symbol": "BYSELTEST",
                "byselScore": 72,
                "quality": 80,
                "valuation": 60,
                "trend": 70,
                "momentum": 65,
            }
        ]
    )
    history = get_score_history("BYSELTEST", 30)
    assert history["symbol"] == "BYSELTEST"
    assert any(point.get("byselScore") == 72 for point in history["points"])
    db = SessionLocal()
    try:
        db.query(ByselScoreSnapshotModel).filter(ByselScoreSnapshotModel.symbol == "BYSELTEST").delete()
        db.commit()
    finally:
        db.close()


def test_worked_example_quality_and_balanced_total():
    from app.market_scanner import (
        FORMULA_CHANGED_DATE,
        _weighted_bysel_score,
        band_cfo_ratio,
        band_de,
        band_roce,
        band_roe,
        score_quality,
    )

    assert band_roce(22) == 64
    assert band_roe(18) == 50
    assert band_de(0.6) == 84
    assert band_cfo_ratio(1.1) == 89
    quality, notes, parts = score_quality(
        roce=22,
        roe=18,
        debt_to_equity=0.6,
        cfo=110,
        pat=100,
        margin=12,
        margin_avg_3y=12,
        pledge=8,
    )
    assert quality == 69
    assert parts["gov"] == 70
    assert "Pledge 8%" in " ".join(notes)
    total = _weighted_bysel_score(69, 58, 72, 61, mode="custom", incomplete=False)
    assert total == 65
    assert FORMULA_CHANGED_DATE == "2026-09-14"


def test_incomplete_pillar_caps_total_at_70():
    from app.market_scanner import _weighted_bysel_score

    uncapped = _weighted_bysel_score(90, 90, 90, 90, mode="custom", incomplete=False)
    capped = _weighted_bysel_score(90, 90, 90, 90, mode="custom", incomplete=True)
    assert uncapped == 90
    assert capped == 70


def test_style_tilt_changes_weights_not_metric_math():
    from app.market_scanner import _style_weights, _weighted_bysel_score

    q = v = t = m = 80
    balanced = _weighted_bysel_score(q, v, t, m, mode="custom")
    long_term = _weighted_bysel_score(q, v, t, m, mode="long_term")
    swing = _weighted_bysel_score(q, v, t, m, mode="swing")
    assert balanced == long_term == swing == 80
    assert _style_weights("long_term")["quality"] == 0.45
    assert _style_weights("swing")["trend"] == 0.30
    assert _style_weights("fno")["momentum"] == 0.40
    assert _style_weights("custom") == _style_weights("balanced")


def test_long_term_momentum_is_qm_not_rsi():
    scores = score_row(
        {
            "symbol": "INFY",
            "last": 1500.0,
            "pe": 24.0,
            "roe": 20.0,
            "rsi": 62.0,
            "macd": 4.0,
            "r122Pct": 88.0,
            "smooth": 0.72,
            "h52": 0.94,
            "fiftyTwoWeekHigh": 1600.0,
            "sector": "IT",
        },
        "long_term",
        sector_pe=26.0,
    )
    assert scores["pillars"]["momentum"]["metrics"]["r122"]["used"] is True
    assert "rsi" not in scores["pillars"]["momentum"]["metrics"]
    assert "buy" not in scores["convictionLabel"].lower()
    swing = score_row(
        {
            "symbol": "INFY",
            "last": 1500.0,
            "rsi": 62.0,
            "fiftyDayAverage": 1480.0,
            "twoHundredDayAverage": 1400.0,
        },
        "swing",
        sector_pe=26.0,
    )
    assert swing["pillars"]["momentum"]["metrics"]["rsi"]["used"] is True
    assert "r122" not in swing["pillars"]["momentum"]["metrics"]


def test_qm_leaders_payload_copy_is_analysis():
    payload = build_scanner_payload(
        [
            {
                "symbol": "WIN",
                "last": 200.0,
                "r122": 0.45,
                "smooth": 0.8,
                "fiftyTwoWeekHigh": 210.0,
                "roe": 22.0,
            },
            {
                "symbol": "LAG",
                "last": 80.0,
                "r122": -0.1,
                "smooth": 0.4,
                "fiftyTwoWeekHigh": 120.0,
            },
            {
                "symbol": "JUICE",
                "last": 40.0,
                "r122": 0.90,
                "smooth": 0.3,
                "fiftyTwoWeekHigh": 42.0,
                "roe": 5.0,
                "debtToEquity": 2.8,
                "pledge": 28.0,
            },
        ],
        mode="momentum",
        limit=10,
    )
    assert payload["education"]["title"] == "QM Leaders"
    assert "buy list" in payload["education"]["summary"].lower()
    assert "sequential" in payload["education"]["summary"].lower()
    symbols = [row["symbol"] for row in payload["rows"]]
    assert "WIN" in symbols
    assert "JUICE" not in symbols
    win = next(row for row in payload["rows"] if row["symbol"] == "WIN")
    assert win["qualityGate"]["passed"] is True


def test_missing_governance_and_supertrend_are_not_invented():
    scores = score_row(
        {
            "symbol": "INFY",
            "last": 1500.0,
            "pe": 24.0,
            "roe": 20.0,
            "sector": "IT",
        },
        "custom",
        sector_pe=26.0,
    )
    assert scores["pillars"]["quality"]["metrics"]["gov"]["used"] is False
    assert scores["pillars"]["trend"]["metrics"]["st"]["used"] is False
    assert "supertrend" in scores["missing"]
    assert "buy" not in (scores.get("score_label") or "").lower()
    assert "sell" not in (scores.get("convictionLabel") or "").lower()
    assert scores["convictionLabel"] in {"Strong", "Good", "Mixed", "Weak", "Poor", "Insufficient data"}
