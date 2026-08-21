import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.market_data import (
    _merge_fundamentals,
    _needs_fundamentals,
    _overlay_fundamentals,
    _parse_yahoo_v7_rows,
    _quote_from_yahoo_v7,
    clear_fundamentals_cache,
    fundamentals_from_fast_info,
    fundamentals_from_yahoo_quote,
)
from app.routes import _quote_from_raw


YAHOO_V7_RELIANCE = {
    "symbol": "RELIANCE.NS",
    "regularMarketPrice": 1375.5,
    "regularMarketChangePercent": 0.45,
    "regularMarketVolume": 5_200_000,
    "averageDailyVolume3Month": 8_400_000,
    "averageVolume": 8_100_000,
    "bid": 1375.0,
    "ask": 1375.6,
    "trailingPE": 24.52,
    "epsTrailingTwelveMonths": 56.12,
    "dividendYield": 0.36,
    "trailingAnnualDividendYield": 0.0036,
    "dividendRate": 5.0,
    "targetMeanPrice": 1550.0,
    "marketCap": 18_600_000_000_000,
    "fiftyTwoWeekHigh": 1608.8,
    "fiftyTwoWeekLow": 1115.55,
}


YAHOO_QUOTESUMMARY = {
    "quoteSummary": {
        "result": [
            {
                "summaryDetail": {
                    "trailingPE": {"raw": 28.4},
                    "dividendYield": {"raw": 0.012},
                    "bid": {"raw": 3120.1},
                    "ask": {"raw": 3121.4},
                },
                "defaultKeyStatistics": {
                    "trailingEps": {"raw": 137.7},
                },
                "financialData": {
                    "targetMeanPrice": {"raw": 3450.0},
                },
            }
        ]
    }
}


def test_yahoo_v7_payload_maps_pe_eps_yield_bid_ask():
    mapped = fundamentals_from_yahoo_quote(YAHOO_V7_RELIANCE, last_price=1375.5)
    assert mapped["trailingPE"] == 24.52
    assert mapped["pe"] == 24.52
    assert mapped["eps"] == 56.12
    assert mapped["dividendYield"] == 0.36
    assert mapped["bid"] == 1375.0
    assert mapped["ask"] == 1375.6
    assert mapped["avgVolume"] == 8_400_000
    assert mapped["targetMeanPrice"] == 1550.0
    assert mapped["marketCap"] == 18_600_000_000_000


def test_yahoo_quotesummary_payload_maps_nested_raw_fields():
    mapped = fundamentals_from_yahoo_quote(YAHOO_QUOTESUMMARY, last_price=3120.0)
    assert mapped["trailingPE"] == 28.4
    assert mapped["eps"] == 137.7
    assert mapped["bid"] == 3120.1
    assert mapped["ask"] == 3121.4
    assert mapped["targetMeanPrice"] == 3450.0
    assert mapped["dividendYield"] == 1.2


def test_yahoo_payload_maps_through_to_api_quote():
    payload = {
        **YAHOO_V7_RELIANCE,
        "symbol": "RELIANCE",
        "pctChange": 0.45,
    }
    quote = _quote_from_raw(payload)
    assert quote.symbol == "RELIANCE"
    assert quote.last == 1375.5
    assert quote.trailingPE == 24.52
    assert quote.pe == 24.52
    assert quote.eps == 56.12
    assert quote.dividendYield == 0.36
    assert quote.bid == 1375.0
    assert quote.ask == 1375.6
    assert quote.avgVolume == 8_400_000
    assert quote.targetMeanPrice == 1550.0
    assert quote.volume == 5_200_000


def test_missing_bid_ask_stay_empty_when_yahoo_has_no_book():
    mapped = fundamentals_from_yahoo_quote(
        {
            "regularMarketPrice": 100.0,
            "trailingPE": 18.0,
            "epsTrailingTwelveMonths": 5.5,
            "bid": 0,
            "ask": 0,
        },
        last_price=100.0,
    )
    assert "bid" not in mapped
    assert "ask" not in mapped
    quote = _quote_from_raw({"symbol": "INFY", "last": 100.0, "pctChange": 0.0, **mapped})
    assert quote.bid is None
    assert quote.ask is None
    assert quote.trailingPE == 18.0
    assert quote.eps == 5.5


def test_batch_price_shell_overlays_cached_yahoo_fundamentals():
    clear_fundamentals_cache()
    price_only = {
        "symbol": "TCS",
        "last": 3500.0,
        "pctChange": 0.4,
        "volume": 1_200_000,
        "pe": None,
        "trailingPE": None,
        "eps": None,
        "dividendYield": None,
        "bid": None,
        "ask": None,
        "avgVolume": None,
        "targetMeanPrice": None,
    }
    fund = fundamentals_from_yahoo_quote(
        {
            "trailingPE": 22.1,
            "epsTrailingTwelveMonths": 158.4,
            "dividendYield": 1.8,
            "bid": 3499.5,
            "ask": 3500.5,
            "averageDailyVolume3Month": 2_400_000,
            "targetMeanPrice": 3900.0,
        },
        last_price=3500.0,
    )
    merged = _overlay_fundamentals(price_only, fund)
    quote = _quote_from_raw(merged)
    assert quote.trailingPE == 22.1
    assert quote.eps == 158.4
    assert quote.dividendYield == 1.8
    assert quote.bid == 3499.5
    assert quote.ask == 3500.5
    assert quote.avgVolume == 2_400_000
    assert quote.targetMeanPrice == 3900.0
    assert quote.last == 3500.0


def test_saturday_v7_payload_keeps_pe_and_drops_zero_book():
    mapped = fundamentals_from_yahoo_quote(
        {
            "regularMarketPrice": 1310.0,
            "regularMarketVolume": 10_492_367,
            "averageDailyVolume3Month": 14_119_577,
            "trailingPE": 23.69754,
            "epsTrailingTwelveMonths": 55.28,
            "dividendYield": 0.46,
            "dividendRate": 6.0,
            "bid": 0.0,
            "ask": 0.0,
            "targetMeanPrice": None,
            "fiftyTwoWeekHigh": 1611.8,
        },
        last_price=1310.0,
    )
    quote = _quote_from_raw({"symbol": "RELIANCE", "last": 1310.0, "pctChange": -0.53, **mapped})
    assert quote.trailingPE == 23.7
    assert quote.eps == 55.28
    assert quote.dividendYield == 0.46
    assert quote.avgVolume == 14_119_577
    assert quote.volume == 10_492_367
    assert quote.bid is None
    assert quote.ask is None
    assert quote.targetMeanPrice is None


def test_quote_summary_target_merges_onto_v7_shell():
    v7 = fundamentals_from_yahoo_quote(
        {
            "trailingPE": 23.7,
            "epsTrailingTwelveMonths": 55.28,
            "dividendYield": 0.46,
            "bid": 0,
            "ask": 0,
        },
        last_price=1310.0,
    )
    summary = fundamentals_from_yahoo_quote(
        {
            "quoteSummary": {
                "result": [{"financialData": {"targetMeanPrice": {"raw": 1681.6875}}}]
            }
        },
        last_price=1310.0,
    )
    merged = _merge_fundamentals(v7, summary)
    quote = _quote_from_raw({"symbol": "RELIANCE", "last": 1310.0, "pctChange": 0.0, **merged})
    assert quote.trailingPE == 23.7
    assert quote.targetMeanPrice == 1681.69
    assert quote.bid is None


def test_avg_volume_alone_still_needs_valuation_fundamentals():
    assert _needs_fundamentals({"avgVolume": 1_400_000, "marketCap": 1e12}) is True
    assert _needs_fundamentals({"trailingPE": 18.0, "avgVolume": None}) is False


def test_fast_info_maps_volume_and_range_not_pe():
    mapped = fundamentals_from_fast_info(
        {
            "last_price": 1310.0,
            "market_cap": 17_727_539_151_850,
            "three_month_average_volume": 13_780_448,
            "last_volume": 10_497_358,
            "year_high": 1611.8,
            "year_low": 1249.8,
        },
        last_price=1310.0,
    )
    assert mapped["avgVolume"] == 13_780_448
    assert mapped["fiftyTwoWeekHigh"] == 1611.8
    assert "trailingPE" not in mapped
    assert "eps" not in mapped


def test_parse_yahoo_v7_rows_ignores_empty_envelope():
    assert _parse_yahoo_v7_rows({"quoteResponse": {"result": [], "error": "Unauthorized"}}) == {}
    parsed = _parse_yahoo_v7_rows(
        {"quoteResponse": {"result": [{"symbol": "TCS.NS", "trailingPE": 17.1}]}}
    )
    assert parsed["TCS.NS"]["trailingPE"] == 17.1


def test_quote_from_yahoo_v7_maps_last_and_pct():
    quote = _quote_from_yahoo_v7("RELIANCE", YAHOO_V7_RELIANCE)
    assert quote["symbol"] == "RELIANCE"
    assert quote["last"] == 1375.5
    assert quote["pctChange"] == 0.45
    assert quote["volume"] == 5_200_000
    assert quote["trailingPE"] == 24.52


def test_fetch_batch_quotes_uses_v7_not_download(monkeypatch):
    from app import market_data

    market_data._quote_cache.clear()
    download_calls = {"n": 0}

    def _fake_v7(yf_symbols, timeout=4.0):
        return {
            "TCS.NS": {
                "symbol": "TCS.NS",
                "regularMarketPrice": 3501.0,
                "regularMarketPreviousClose": 3480.0,
                "regularMarketChangePercent": 0.6,
                "regularMarketVolume": 1_000_000,
            }
        }

    monkeypatch.setattr(market_data, "_fetch_yahoo_v7_quotes", _fake_v7)
    monkeypatch.setattr(
        market_data,
        "_fetch_batch_quotes_download",
        lambda *_a, **_k: download_calls.__setitem__("n", download_calls["n"] + 1) or {},
    )

    quotes = market_data.fetch_quotes(["TCS"], max_age_seconds=5)
    assert len(quotes) == 1
    assert quotes[0]["last"] == 3501.0
    assert download_calls["n"] == 0


def test_fetch_batch_quotes_skips_download_when_v7_empty(monkeypatch):
    from app import market_data

    market_data._quote_cache.clear()
    download_calls = {"n": 0}

    monkeypatch.setattr(market_data, "_quotes_from_v7_batch", lambda *_a, **_k: {})
    monkeypatch.setattr(
        market_data,
        "_fetch_batch_quotes_download",
        lambda *_a, **_k: download_calls.__setitem__("n", download_calls["n"] + 1) or {},
    )
    monkeypatch.setattr(market_data, "fetch_quote", lambda _symbol: {"symbol": _symbol, "last": 0.0})

    quotes = market_data.fetch_quotes(
        ["RELIANCE", "TCS", "INFY"],
        max_age_seconds=5,
        individual_fallback=False,
    )
    assert quotes == []
    assert download_calls["n"] == 0


def test_fetch_quotes_caps_individual_history_fallback(monkeypatch):
    from app import market_data

    market_data._quote_cache.clear()
    fetch_calls = []

    monkeypatch.setattr(market_data, "_quotes_from_v7_batch", lambda *_a, **_k: {})
    monkeypatch.setattr(market_data, "_fetch_batch_quotes_download", lambda *_a, **_k: {})
    monkeypatch.setattr(market_data, "QUOTE_INDIVIDUAL_FALLBACK_MAX", 2)

    def _fake_fetch_quote(symbol):
        fetch_calls.append(symbol)
        return {"symbol": symbol, "last": 10.0, "pctChange": 0.1}

    monkeypatch.setattr(market_data, "fetch_quote", _fake_fetch_quote)
    quotes = market_data.fetch_quotes(["AAA", "BBB", "CCC", "DDD"], max_age_seconds=5)
    assert fetch_calls == ["AAA", "BBB"]
    assert [row["symbol"] for row in quotes] == ["AAA", "BBB"]

