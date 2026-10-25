import sys
from pathlib import Path

import pandas as pd

sys.path.insert(0, str(Path(__file__).parent.parent))

from app import market_data


class _FakeTicker:
    def __init__(self, has_data: bool = True):
        self._has_data = has_data

    def history(self, period: str = "1d"):
        if self._has_data:
            return pd.DataFrame({"Close": [123.45]})
        return pd.DataFrame()

    @property
    def info(self):
        return {"shortName": "Apis India Ltd"}


def test_search_stocks_handles_stock_suffix_for_known_symbol():
    results = market_data.search_stocks("reliance stock", limit=5)

    assert results
    assert results[0]["symbol"] == "RELIANCE"


def test_search_stocks_tries_cleaned_candidate_on_yahoo_fallback(monkeypatch):
    # The catalog grows over time; keep the symbol unknown locally so the test
    # still exercises the Yahoo fallback rather than an exact hit.
    catalog = {
        sym: row
        for sym, row in market_data.get_stock_catalog().items()
        if sym != "APIS" and "apis" not in str(row[1]).lower()
    }
    monkeypatch.setattr(market_data, "get_stock_catalog", lambda: catalog)
    monkeypatch.setattr(market_data, "_yahoo_search_query", lambda phrase, limit=5: [])
    monkeypatch.setattr(market_data.yf, "Ticker", lambda symbol: _FakeTicker(has_data=symbol == "APIS.NS"))

    results = market_data.search_stocks("apis stock", limit=5)

    assert results
    assert results[0]["symbol"] == "APIS"
    assert results[0]["matchType"] == "yahoo"
