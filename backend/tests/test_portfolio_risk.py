from app.portfolio_risk import (
    build_portfolio_risk,
    concentration_from_weights,
    day_pnl_rupees,
    empty_portfolio_risk,
    herfindahl_hhi,
    nifty_what_if,
    position_weights,
    sector_mix,
    sector_spread_from_mix,
    value_weighted_score,
)


SECTORS = {
    "RELIANCE": "Energy",
    "TCS": "IT",
    "INFY": "IT",
    "HDFCBANK": "Banking",
    "SUNPHARMA": "Pharma",
    "MOSCHIP": "Semiconductor",
}


def test_empty_book_has_no_fake_drawdown_or_vol():
    payload = empty_portfolio_risk()
    assert payload["empty"] is True
    assert payload["volatility"]["available"] is False
    assert payload["maxDrawdown"]["available"] is False
    assert "history" in payload["volatility"]["note"].lower()
    assert payload["whatIf"]["niftyDown5"] == 0.0
    assert "not a forecast" in payload["whatIf"]["label"].lower()


def test_single_name_is_fully_concentrated():
    weights = position_weights([10_000.0])
    conc = concentration_from_weights(weights, ["MOSCHIP"])
    assert conc["top1Pct"] == 100.0
    assert conc["top5Pct"] == 100.0
    assert conc["top1Symbol"] == "MOSCHIP"
    assert conc["gauge"] == 100


def test_five_equal_names_top1_is_20_top5_is_100():
    values = [100.0, 100.0, 100.0, 100.0, 100.0]
    weights = position_weights(values)
    conc = concentration_from_weights(weights, ["A", "B", "C", "D", "E"])
    assert conc["top1Pct"] == 20.0
    assert conc["top5Pct"] == 100.0
    assert conc["gauge"] == 20


def test_top5_caps_at_book_when_fewer_than_five():
    weights = position_weights([70.0, 30.0])
    conc = concentration_from_weights(weights, ["RELIANCE", "TCS"])
    assert conc["top1Pct"] == 70.0
    assert conc["top5Pct"] == 100.0


def test_what_if_nifty_shocks_use_beta_one():
    assert nifty_what_if(100_000.0, -5.0, beta=1.0) == -5_000.0
    assert nifty_what_if(100_000.0, -10.0, beta=1.0) == -10_000.0
    assert nifty_what_if(0.0, -5.0) == 0.0


def test_value_weighted_score_skips_missing():
    # Equal 50/50: 80 and missing → 80, not 40.
    result = value_weighted_score([1000.0, 1000.0], [80, None])
    assert result["valueWeighted"] == 80
    assert result["scoredCount"] == 1
    assert result["missingCount"] == 1
    assert result["coveredValuePct"] == 50.0

    blended = value_weighted_score([2000.0, 1000.0], [80, 50])
    assert blended["valueWeighted"] == 70  # (160000 + 50000) / 3000


def test_sector_spread_one_bucket_is_zero_equal_two_is_fifty():
    one = sector_mix([100.0], ["Energy"])
    spread_one = sector_spread_from_mix(one)
    assert spread_one["gauge"] == 0
    assert spread_one["sectorCount"] == 1
    assert herfindahl_hhi([100.0]) == 1.0

    two = sector_mix([50.0, 50.0], ["Energy", "IT"])
    spread_two = sector_spread_from_mix(two)
    assert spread_two["gauge"] == 50
    assert spread_two["sectorCount"] == 2


def test_day_pnl_from_prev_close_and_pct_change():
    rupees, ok = day_pnl_rupees(qty=10, last=110.0, prev_close=100.0)
    assert ok is True
    assert rupees == 100.0

    implied, implied_ok = day_pnl_rupees(qty=10, last=110.0, pct_change=10.0)
    assert implied_ok is True
    assert implied == 100.0

    missing, missing_ok = day_pnl_rupees(qty=10, last=110.0)
    assert missing_ok is False
    assert missing == 0.0


def test_build_snapshot_concentration_and_what_if():
    payload = build_portfolio_risk(
        holdings=[
            {"symbol": "RELIANCE", "qty": 10, "avgPrice": 1000.0, "last": 1400.0},
            {"symbol": "TCS", "qty": 5, "avgPrice": 3000.0, "last": 3600.0},
            {"symbol": "MOSCHIP", "qty": 20, "avgPrice": 100.0, "last": 100.0},
        ],
        quotes=[
            {"symbol": "RELIANCE", "last": 1400.0, "pctChange": -2.0, "prevClose": 1428.57},
            {"symbol": "TCS", "last": 3600.0, "pctChange": 1.0, "previousClose": 3564.36},
        ],
        scores={"RELIANCE": 70, "TCS": 80},
        sector_map=SECTORS,
    )
    assert payload["empty"] is False
    # Values: REL 14000, TCS 18000, MOSCHIP 2000 → total 34000
    assert payload["totalValue"] == 34000.0
    assert payload["concentration"]["top1Symbol"] == "TCS"
    assert payload["concentration"]["top1Pct"] == 52.94  # 18000/34000
    assert payload["whatIf"]["niftyDown5"] == -1700.0
    assert payload["whatIf"]["niftyDown10"] == -3400.0
    assert payload["byselScore"]["valueWeighted"] == 76  # (14000*70 + 18000*80) / 32000
    assert payload["byselScore"]["missingCount"] == 1
    assert payload["maxDrawdown"]["available"] is False
    names = [s["name"] for s in payload["sectors"]]
    assert "Energy" in names and "IT" in names and "Semiconductor" in names
    assert "illustration" in payload["whatIf"]["label"].lower()


def test_empty_holdings_snapshot():
    payload = build_portfolio_risk([], quotes=[], scores={}, sector_map=SECTORS)
    assert payload["empty"] is True
    assert "practice buy" in payload["message"].lower()
