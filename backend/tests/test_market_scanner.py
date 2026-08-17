from app.market_scanner import (
    band_cagr,
    band_de,
    band_interest_coverage,
    band_roce,
    band_roe,
    band_rsi,
    build_scanner_payload,
    color_band,
    detect_anomalies,
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
    assert "BYSEL Score" in scores["ai_summary"]
    assert "not investment advice" in scores["ai_summary"].lower()
    assert scores["score_label"] in {"high_conviction", "attractive", "neutral", "caution", "weak", "insufficient"}
    assert "buy" not in scores["score_label"]
    assert scores["bysel_score"] == scores["byselScore"]
    assert scores["colorBand"] in {"green", "light_green", "yellow", "orange_red", "none"}
    q_top = scores["pillars"]["quality"]["topMetrics"]
    assert len(q_top) <= 3
    assert all(item["id"] != "roce" for item in q_top)
    assert any(item["id"] == "roe" for item in q_top)


def test_top_metrics_skip_missing_and_cap_three():
    metrics = {
        "roce": {"value": None, "score": None, "used": False},
        "roe": {"value": 22.0, "score": 100, "used": True},
        "debtToEquity": {"value": 0.4, "score": 100, "used": True},
        "interestCoverage": {"value": None, "score": None, "used": False},
        "salesCagr": {"value": 12.0, "score": 85, "used": True},
        "profitCagr": {"value": 8.0, "score": 65, "used": True},
        "promoterPledge": {"value": None, "score": None, "used": False},
    }
    weights = {
        "roce": 0.25,
        "roe": 0.20,
        "debtToEquity": 0.15,
        "interestCoverage": 0.10,
        "salesCagr": 0.15,
        "profitCagr": 0.10,
        "promoterPledge": 0.05,
    }
    top = top_contributing_metrics(metrics, weights, limit=3)
    assert len(top) == 3
    assert all(item["id"] in {"roe", "debtToEquity", "salesCagr", "profitCagr"} for item in top)
    assert "roce" not in {item["id"] for item in top}
    assert top[0]["contribution"] >= top[-1]["contribution"]


def test_color_band_thresholds():
    assert color_band(None) == "none"
    assert color_band(80) == "green"
    assert color_band(79) == "light_green"
    assert color_band(65) == "light_green"
    assert color_band(64) == "yellow"
    assert color_band(50) == "yellow"
    assert color_band(49) == "orange_red"


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
    assert infy["score_label"] in {"high_conviction", "attractive", "neutral", "caution", "weak", "insufficient"}
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
