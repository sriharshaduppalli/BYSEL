from app.groq_llm import redact_vendor_names_for_display
from app.routes.ai_v2 import _build_risk_payload_from_returns, _illustrative_risk_payload


def test_illustrative_payload_is_flagged_as_sample():
    payload = _illustrative_risk_payload(
        ["RELIANCE", "TCS"],
        30,
        reason="Live return history was unavailable; showing sample Risk Lab numbers.",
    )
    assert payload["illustrative"] is True
    assert payload["demoBasket"] is True
    note = payload["disclaimer"].lower()
    assert "illustrative" in note or "sample" in note
    assert payload["var95"] == -1.8


def test_computed_payload_is_not_illustrative_unless_demo_basket():
    import numpy as np

    returns = {
        "RELIANCE": np.array([0.01, -0.005, 0.002, 0.0, -0.01, 0.004, 0.001], dtype=float),
        "TCS": np.array([0.008, -0.002, 0.001, 0.003, -0.006, 0.002, 0.0], dtype=float),
    }
    live = _build_risk_payload_from_returns(returns, ["RELIANCE", "TCS"], [0.5, 0.5], 10, False)
    assert live["illustrative"] is False
    assert live["demoBasket"] is False
    assert "yahoo" not in live["disclaimer"].lower()
    assert "computed" in live["disclaimer"].lower() or "market history" in live["disclaimer"].lower()

    demo = _build_risk_payload_from_returns(returns, ["RELIANCE", "TCS"], [0.5, 0.5], 10, True)
    assert demo["illustrative"] is True
    assert demo["demoBasket"] is True


def test_vendor_names_are_redacted_for_display():
    assert "Yahoo" not in redact_vendor_names_for_display("from available Yahoo fields")
    assert "yahoo" not in redact_vendor_names_for_display("live NSE/Yahoo history for TCS").lower()
    assert "Yahoo" not in redact_vendor_names_for_display("Yahoo Finance quotes")
