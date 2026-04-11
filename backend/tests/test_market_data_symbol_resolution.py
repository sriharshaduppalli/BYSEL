import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import app.market_data as market_data


def test_yf_ticker_supports_explicit_exchange_suffixes():
    assert market_data._yf_ticker("INFY.NS") == "INFY.NS"
    assert market_data._yf_ticker("500325.BO") == "500325.BO"


def test_yf_ticker_supports_exchange_prefixed_inputs():
    assert market_data._yf_ticker("NSE:INFY") == "INFY.NS"
    assert market_data._yf_ticker("BSE:500325") == "500325.BO"


def test_yf_ticker_defaults_numeric_codes_to_bse():
    assert market_data._yf_ticker("500325") == "500325.BO"


def test_get_stock_name_handles_exchange_suffix_lookup():
    assert "Infosys" in market_data.get_stock_name("INFY.NS")
