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
    monkeypatch.setattr(market_data.yf, "Ticker", lambda symbol: _FakeTicker(has_data=symbol == "APIS.NS"))

    results = market_data.search_stocks("apis stock", limit=5)

    assert results
    assert results[0]["symbol"] == "APIS"
    assert results[0]["matchType"] == "yahoo"
