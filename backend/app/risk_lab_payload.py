"""Risk Lab number builders — no FastAPI / DB imports."""

from __future__ import annotations

import logging
from typing import Dict, List

logger = logging.getLogger(__name__)


def illustrative_risk_payload(
    sym_list: List[str],
    horizon_days: int,
    *,
    reason: str,
) -> Dict:
    """Always-open educational payload when live history is unavailable."""
    logger.debug("risk.illustrative_payload symbols=%s reason=%s", ",".join(sym_list), reason)
    n = max(len(sym_list), 1)
    equal = [round(1.0 / n, 4)] * n
    corr = [[1.0 if i == j else 0.55 for j in range(n)] for i in range(n)]
    return {
        "symbols": sym_list,
        "weights": equal,
        "metrics": {
            "var95": -1.8,
            "var99": -2.9,
            "maxDrawdown": -12.5,
            "sharpeRatio": 0.85,
            "annualizedReturn": 14.2,
            "annualizedVolatility": 18.0,
        },
        "var95": -1.8,
        "var99": -2.9,
        "maxDrawdown": -12.5,
        "sharpeRatio": 0.85,
        "annualizedReturn": 14.2,
        "annualizedVolatility": 18.0,
        "correlationMatrix": corr,
        "monteCarlo": {
            "horizonDays": horizon_days,
            "simulations": 500,
            "p5": -8.5,
            "p50": 2.1,
            "p95": 12.4,
        },
        "monteCarloP5": -8.5,
        "monteCarloMedian": 2.1,
        "monteCarloP95": 12.4,
        "riskLevel": "Medium",
        "demoBasket": True,
        "illustrative": True,
        "disclaimer": "Sample educational numbers — not live risk on your paper book.",
    }


def build_risk_payload_from_returns(
    returns_dict: Dict[str, "object"],
    sym_list: List[str],
    weight_list: List[float],
    horizon_days: int,
    used_demo: bool,
) -> Dict:
    import numpy as np

    min_len = min(len(r) for r in returns_dict.values())
    if min_len < 5:
        raise ValueError("insufficient aligned return history")

    matrix = np.column_stack([returns_dict[s][-min_len:] for s in returns_dict])
    w = np.array([weight_list[i] for i, s in enumerate(sym_list) if s in returns_dict], dtype=float)
    if w.size == 0 or float(w.sum()) <= 0:
        raise ValueError("invalid portfolio weights")
    w /= w.sum()

    portfolio_returns = matrix @ w

    var_95 = float(np.percentile(portfolio_returns, 5))
    var_99 = float(np.percentile(portfolio_returns, 1))

    cum = np.cumprod(1 + portfolio_returns)
    peak = np.maximum.accumulate(cum)
    drawdown = (cum - peak) / peak
    max_drawdown = float(drawdown.min())

    rf_daily = 0.065 / 252
    excess = portfolio_returns - rf_daily
    sharpe = float(excess.mean() / excess.std() * np.sqrt(252)) if excess.std() > 0 else 0.0

    symbols_in = list(returns_dict.keys())
    corr = np.corrcoef(matrix.T).tolist() if len(symbols_in) > 1 else [[1.0]]

    mc_final = []
    for _ in range(500):
        path = np.random.choice(portfolio_returns, size=horizon_days, replace=True)
        mc_final.append(float(np.prod(1 + path) - 1))
    mc_final.sort()
    mc_5th = mc_final[int(0.05 * len(mc_final))]
    mc_50th = mc_final[len(mc_final) // 2]
    mc_95th = mc_final[int(0.95 * len(mc_final))]

    mean_daily = float(portfolio_returns.mean()) if len(portfolio_returns) else 0.0
    std_daily = float(portfolio_returns.std()) if len(portfolio_returns) else 0.0
    annualized_return = (1.0 + mean_daily) ** 252 - 1.0
    annualized_vol = std_daily * float(np.sqrt(252))

    return {
        "symbols": symbols_in,
        "weights": w.tolist(),
        "metrics": {
            "var95": round(var_95 * 100, 2),
            "var99": round(var_99 * 100, 2),
            "maxDrawdown": round(max_drawdown * 100, 2),
            "sharpeRatio": round(sharpe, 2),
            "annualizedReturn": round(annualized_return * 100, 2),
            "annualizedVolatility": round(annualized_vol * 100, 2),
        },
        "var95": round(var_95 * 100, 2),
        "var99": round(var_99 * 100, 2),
        "maxDrawdown": round(max_drawdown * 100, 2),
        "sharpeRatio": round(sharpe, 2),
        "annualizedReturn": round(annualized_return * 100, 2),
        "annualizedVolatility": round(annualized_vol * 100, 2),
        "correlationMatrix": corr,
        "monteCarlo": {
            "horizonDays": horizon_days,
            "simulations": 500,
            "p5": round(mc_5th * 100, 2),
            "p50": round(mc_50th * 100, 2),
            "p95": round(mc_95th * 100, 2),
        },
        "monteCarloP5": round(mc_5th * 100, 2),
        "monteCarloMedian": round(mc_50th * 100, 2),
        "monteCarloP95": round(mc_95th * 100, 2),
        "riskLevel": "Low" if var_95 > -0.01 else "Medium" if var_95 > -0.025 else "High",
        "demoBasket": used_demo,
        "illustrative": used_demo,
        "disclaimer": (
            "Educational example using RELIANCE, TCS and INFY — not your paper book."
            if used_demo
            else "Educational only — not a SEBI risk report or a forecast."
        ),
    }


# Names kept for callers that still import the private aliases.
_illustrative_risk_payload = illustrative_risk_payload
_build_risk_payload_from_returns = build_risk_payload_from_returns
