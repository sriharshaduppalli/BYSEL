"""ISM query transformer: analyze the ask without loading torch."""
from __future__ import annotations

from indian_stock_llm.query_contract import resolve_query_contract
from indian_stock_llm.query_transformer import (
    apply_transformer_route,
    analyze_query_transformer,
    query_transformer_enabled,
)


def test_transformer_ranks_price_vs_buy():
    price = analyze_query_transformer("What is the price of RELIANCE?")
    buy = analyze_query_transformer("Should I buy RELIANCE?")
    assert price.profile == "quote"
    assert buy.profile == "trade_plan"
    assert price.profile != buy.profile


def test_transformer_does_not_override_high_confidence_literacy():
    analysis = analyze_query_transformer("Should I buy RELIANCE?")
    profile, confidence, used = apply_transformer_route("literacy", 90, analysis)
    assert profile == "literacy"
    assert confidence == 90
    assert used is False


def test_transformer_rescues_weak_fallback():
    analysis = analyze_query_transformer("Is the market open?")
    profile, confidence, used = apply_transformer_route("stock_analysis", 45, analysis)
    assert used is True
    assert profile == "session"
    assert confidence >= 76


def test_contract_still_routes_retail_asks(monkeypatch):
    assert query_transformer_enabled() is True
    quote = resolve_query_contract("What is the price of RELIANCE?")
    assert quote.profile == "quote"
    assert quote.transformer_profile
    buy = resolve_query_contract("Should I buy RELIANCE?")
    assert buy.profile == "trade_plan"
    ipo = resolve_query_contract("How to apply for IPO")
    assert ipo.profile == "literacy"


def test_disable_transformer(monkeypatch):
    monkeypatch.setenv("ISM_QUERY_TRANSFORMER", "false")
    analysis = analyze_query_transformer("Is the market open?")
    profile, confidence, used = apply_transformer_route("stock_analysis", 45, analysis)
    assert used is False
    assert profile == "stock_analysis"
    assert confidence == 45
