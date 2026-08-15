from app.portfolio_scorer import (
    _resolve_mark,
    calculate_portfolio_health,
)


def _quotes(prices):
    return [{"symbol": symbol, "last": price} for symbol, price in prices.items()]


def test_empty_book_is_not_a_forecast():
    result = calculate_portfolio_health([])
    assert result["overallScore"] == 0
    assert result["scoreType"] == "snapshot"
    assert "forecast" in result["snapshotNote"].lower()
    assert result["pnlReliable"] is False


def test_missing_quotes_do_not_look_flat_healthy(monkeypatch):
    monkeypatch.setattr(
        "app.market_data.fetch_quotes",
        lambda _symbols, **_kwargs: [],
    )
    result = calculate_portfolio_health([
        {"symbol": "MOSCHIP", "quantity": 10, "avgPrice": 200.0},
    ])
    assert result["scoreType"] == "snapshot"
    assert result["quotedCount"] == 0
    assert result["pnlReliable"] is False
    assert result["totalPnlPercent"] == 0
    assert "quotes missing" in result["summary"].lower()
    # Same 100% name must not get the "few losers" bonus just because P&L is unknown.
    risk = result["breakdown"]["risk"]
    assert "without a mark" in risk["details"]
    assert risk["score"] < 15


def test_stale_last_price_counts_as_marked_pnl(monkeypatch):
    monkeypatch.setattr(
        "app.market_data.fetch_quotes",
        lambda _symbols, **_kwargs: [],
    )
    result = calculate_portfolio_health([
        {"symbol": "TCS", "quantity": 2, "avgPrice": 4000.0, "lastPrice": 3000.0},
    ])
    assert result["pnlReliable"] is True
    assert result["totalPnl"] == -2000.0
    assert result["totalPnlPercent"] == -25.0
    assert result["quotedCount"] == 0
    assert any("down 25" in tip for tip in result["suggestions"])


def test_live_quotes_drive_weights_and_losers(monkeypatch):
    monkeypatch.setattr(
        "app.market_data.fetch_quotes",
        lambda _symbols, **_kwargs: _quotes({"TCS": 3000.0, "INFY": 1500.0}),
    )
    result = calculate_portfolio_health([
        {"symbol": "TCS", "quantity": 1, "avgPrice": 4000.0},
        {"symbol": "INFY", "quantity": 1, "avgPrice": 1500.0},
    ])
    assert result["quotedCount"] == 2
    assert result["quoteCoverage"] == 1.0
    assert result["totalPnl"] == -1000.0
    assert result["pnlReliable"] is True
    assert "forecast" in result["summary"].lower()


def test_diversified_blue_chips_score_higher_than_one_name(monkeypatch):
    names = [
        ("HDFCBANK", "Banking"),
        ("TCS", "IT"),
        ("SUNPHARMA", "Pharma"),
        ("HINDUNILVR", "FMCG"),
        ("RELIANCE", "Energy"),
        ("MARUTI", "Auto"),
        ("NTPC", "Power"),
        ("LT", "Infra"),
    ]
    prices = {symbol: 100.0 for symbol, _ in names}
    monkeypatch.setattr(
        "app.market_data.fetch_quotes",
        lambda _symbols, **_kwargs: _quotes(prices),
    )
    diversified = calculate_portfolio_health([
        {"symbol": symbol, "quantity": 1, "avgPrice": 100.0} for symbol, _ in names
    ])
    concentrated = calculate_portfolio_health([
        {"symbol": "MOSCHIP", "quantity": 10, "avgPrice": 100.0},
    ])
    assert diversified["stockCount"] == 8
    assert diversified["sectorCount"] >= 6
    assert diversified["overallScore"] > concentrated["overallScore"]
    assert diversified["breakdown"]["quality"]["score"] > concentrated["breakdown"]["quality"]["score"]


def test_britannia_typo_is_treated_as_blue_chip():
    mark, source = _resolve_mark({"avgPrice": 5000, "lastPrice": 0}, 5120.0)
    assert source == "live"
    assert mark == 5120.0
    from app.portfolio_scorer import _quality_score
    score, details = _quality_score([
        {"symbol": "BRITANNIA", "weight": 100.0},
    ])
    assert "100.0% in blue-chips" in details
    assert score >= 22
