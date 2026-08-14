import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import app.market_data as market_data
from app.stock_enricher import extract_symbol_from_query, lookup_bse_listing


def test_yf_ticker_resolves_bse_codes_and_prefix():
    assert market_data._yf_ticker("500325") == "500325.BO"
    assert market_data._yf_ticker("BSE:500325") == "500325.BO"
    assert market_data._yf_ticker("INFY") == "INFY.NS"


def test_extract_symbol_recognizes_bse_code_and_prefix():
    assert extract_symbol_from_query("analyze 500325") == "500325"
    assert extract_symbol_from_query("BSE:ABB outlook").upper() == "ABB"


def test_extract_symbol_ignores_wait_action_label():
    assert extract_symbol_from_query("WAIT") is None
    assert extract_symbol_from_query("Action: WAIT") is None
    assert extract_symbol_from_query("HOLD / wait — no clear edge yet") is None
    assert extract_symbol_from_query("Should I wait on RELIANCE?") == "RELIANCE"
    assert extract_symbol_from_query(
        "**Action:** WAIT\nSELL = exit/avoid · WAIT = skip until setup improves"
    ) is None


def test_catalog_includes_bse_code_and_nse_symbol():
    market_data.invalidate_stock_catalog()
    catalog = market_data.get_stock_catalog()
    assert "INFY" in catalog
    assert str(catalog["INFY"][0]).endswith(".NS")
    # Reliance BSE code should be searchable.
    assert "500325" in catalog
    assert str(catalog["500325"][0]).endswith(".BO")


def test_lookup_bse_listing_reliance_code():
    rec = lookup_bse_listing("500325")
    assert rec is not None
    assert rec.get("code") == "500325"
    assert str(rec.get("scrip_id") or "").upper() == "RELIANCE"


def test_resolve_analysis_symbol_maps_dual_listed_bse_code():
    from app.stock_enricher import format_symbol_display, resolve_analysis_symbol

    assert resolve_analysis_symbol("500325") == "RELIANCE"
    assert resolve_analysis_symbol("BSE:500325") == "RELIANCE"
    assert "BSE:500325" in format_symbol_display("500325")


def test_tech_mahindra_resolves_to_techm_not_absl_mf():
    from app.stock_enricher import extract_all_symbols_from_query, order_symbols_in_query

    assert extract_symbol_from_query("tech mahindra") == "TECHM"
    assert extract_symbol_from_query("Tech Mahindra") == "TECHM"
    assert extract_symbol_from_query("should I buy tech mahindra") == "TECHM"

    symbols = extract_all_symbols_from_query("tech mahindra")
    assert symbols[0] == "TECHM"
    assert "TECH" not in symbols
    assert "M&M" not in symbols

    # Even if the BSE ETF ticker sneaks in, ranking must keep TECHM first.
    ordered = order_symbols_in_query(["TECH", "TECHM"], "tech mahindra")
    assert ordered[0] == "TECHM"
    assert "TECH" not in ordered[:1]


def test_compare_left_to_right_order_still_works():
    from app.stock_enricher import extract_all_symbols_from_query, order_symbols_in_query

    named = extract_all_symbols_from_query("compare TCS and INFY")
    ordered = order_symbols_in_query(named, "compare TCS and INFY")
    assert ordered[0] == "TCS"
    assert "INFY" in ordered
