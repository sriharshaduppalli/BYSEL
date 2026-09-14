from app.quantitative_momentum import (
    QM_ADV_FLOOR_INR,
    attach_qm_cross_section,
    band_h52,
    generic_momentum_12_2,
    h52_ratio,
    one_month_return,
    percentile_rank,
    qm_flags,
    score_momentum_qm,
    smoothness_up_week_fraction,
    uses_qm_momentum,
)


def _grind(start: float = 100.0, weekly_up: bool = True) -> list:
    closes = [start]
    for week in range(52):
        step = 1.01 if weekly_up else 0.99
        for day in range(5):
            bump = step if day == 4 else 1.001
            closes.append(closes[-1] * bump)
    return closes


def test_12_2_skips_last_month_and_needs_252_bars():
    closes = _grind()
    assert len(closes) >= 252
    assert generic_momentum_12_2(closes[:200]) is None
    value = generic_momentum_12_2(closes)
    assert value is not None
    # Last 21 sessions excluded: a late crash must not define 12-2.
    crashed = list(closes)
    for index in range(1, 21):
        crashed[-index] = crashed[-index] * 0.5
    skipped = generic_momentum_12_2(crashed)
    assert skipped is not None
    assert abs(skipped - value) < 1e-9
    assert one_month_return(crashed) is not None
    assert one_month_return(crashed) < -0.4


def test_smoothness_prefers_grind_over_one_gap():
    grind = _grind()
    gap = [100.0] * 230 + [100.0 + i * 0.01 for i in range(22)]
    gap[20] = 180.0
    while len(gap) < 260:
        gap.append(gap[-1])
    grind_s = smoothness_up_week_fraction(grind)
    gap_s = smoothness_up_week_fraction(gap)
    assert grind_s is not None
    assert grind_s > 0.7
    assert gap_s is None or gap_s < grind_s


def test_h52_and_qm_blend_without_earnings():
    assert h52_ratio(90, 100) == 0.9
    assert band_h52(0.70) == 0
    assert band_h52(1.0) == 100
    assert abs((band_h52(0.85) or 0) - 50) < 1e-6
    score, notes, parts = score_momentum_qm(
        r122_percentile=80,
        smoothness=0.70,
        h52=0.92,
        earn_percentile=None,
    )
    assert score is not None
    assert parts["earn"] is None
    assert "EPS revision —" in notes
    assert "buy" not in " ".join(notes).lower()
    expected = (0.40 * 80 + 0.25 * 70 + 0.20 * (band_h52(0.92) or 0)) / 0.85
    assert score == round(expected)


def test_cross_section_rank_and_squeeze_flag():
    rows = [
        {"symbol": "A", "r122": 0.40, "r1m": 0.02},
        {"symbol": "B", "r122": 0.10, "r1m": 0.40},
        {"symbol": "C", "r122": 0.25, "r1m": 0.03},
    ]
    attach_qm_cross_section(rows)
    assert rows[0]["qmRank"] == 1
    assert rows[1]["qmRank"] == 3
    assert rows[0]["r122Pct"] == 100
    assert rows[1]["r122Pct"] == 0
    squeeze = qm_flags(r1m_percentile=96, adv=QM_ADV_FLOOR_INR * 2)
    assert any(item["id"] == "qm_squeeze" for item in squeeze)
    assert "buy" not in squeeze[0]["label"].lower()
    liquid = qm_flags(adv=QM_ADV_FLOOR_INR * 2)
    assert liquid == []
    thin = qm_flags(adv=1_000_000)
    assert any(item["id"] == "qm_low_adv" for item in thin)


def test_quality_gates_are_filters_not_a_fifth_pillar():
    from app.quantitative_momentum import evaluate_quality_gates, qm_style_badge

    passed = evaluate_quality_gates(roe=18, debt_to_equity=0.6, interest_coverage=4, pledge=2)
    assert passed["passed"] is True
    junk = evaluate_quality_gates(roe=6, debt_to_equity=2.4, interest_coverage=1.1, pledge=25)
    assert junk["passed"] is False
    assert "roe" in junk["failures"]
    assert "de" in junk["failures"]
    assert "pledge" in junk["failures"]
    missing_roe = evaluate_quality_gates(debt_to_equity=0.4)
    assert missing_roe["passed"] is False
    bank = evaluate_quality_gates(roe=16, debt_to_equity=8.0, sector="Banking")
    assert bank["passed"] is True
    assert any(item["id"] == "de" and item["status"] == "skip" for item in bank["checks"])
    assert qm_style_badge(80, 80) == "Quality Momentum"
    assert qm_style_badge(80, 55) == "Quality, trend mixed"
    assert qm_style_badge(40, 80) == "Speculative momentum"
    assert qm_style_badge(30, 20) == "Weak"
    assert qm_style_badge(60, 80) is None
    assert qm_style_badge(None, 80) is None


def test_qm_mode_gate_keeps_swing_on_short_horizon():
    assert uses_qm_momentum("long_term") is True
    assert uses_qm_momentum("momentum") is True
    assert uses_qm_momentum("custom") is True
    assert uses_qm_momentum("swing") is False
    assert uses_qm_momentum("fno") is False
