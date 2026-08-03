"""Multi-horizon prediction head for the Indian Stock Market assistant.

Produces calibrated, risk-aware directional signals (intraday, swing, medium-term)
from quantitative trade plans + grounded KB context.
No guaranteed-return claims; every signal includes an uncertainty note.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Any, Optional

if TYPE_CHECKING:
    from .knowledge_base import KnowledgeItem

# Content signals used for directional scoring
_BULLISH_TAGS = frozenset({"earnings", "guidance", "momentum", "fundamental", "growth"})
_BEARISH_TAGS = frozenset({"risk", "uncertainty", "volatility", "regulation", "compliance"})
_NONE_PLACEHOLDER = "none"
_BULLISH_CONTENT = (
    "strong earnings",
    "deal win",
    "beat estimate",
    "guidance upgrade",
    "momentum",
    "sector tailwind",
    "positive",
    "growth",
    "recovery",
)
_BEARISH_CONTENT = (
    "miss estimate",
    "guidance cut",
    "margin pressure",
    "headwind",
    "volatility",
    "uncertainty",
    "slowdown",
    "decline",
    "risk",
)

_BASE_PROB = 0.50
_SIGNAL_STEP = 0.05
_MAX_PROB = 0.75
_MIN_PROB = 0.25

_ACTION_SCORE = {
    "BUY": 3,
    "ACCUMULATE": 2,
    "HOLD": 0,
    "WAIT": 0,
    "TRIM": -2,
    "SELL": -3,
}


@dataclass(frozen=True)
class HorizonSignal:
    """Directional signal for a single time horizon."""

    direction: str  # "bullish" | "bearish" | "neutral"
    probability: float  # calibrated probability [0, 1]
    rationale: str  # human-readable explanation with uncertainty note


@dataclass(frozen=True)
class PredictionSignals:
    """Multi-horizon prediction output produced by PredictionEngine."""

    intraday: HorizonSignal
    swing: HorizonSignal
    medium_term: HorizonSignal
    key_signals: tuple[str, ...]
    overall_confidence: float


class PredictionEngine:
    """Hybrid prediction engine: trade-plan math first, KB tags as secondary."""

    def _score_items(
        self, context_items: list[KnowledgeItem]
    ) -> tuple[int, int, list[str]]:
        bullish = 0
        bearish = 0
        signals: list[str] = []
        for item in context_items:
            tags = set(item.tags)
            content_lower = item.content.lower()
            b_tag = len(tags & _BULLISH_TAGS)
            r_tag = len(tags & _BEARISH_TAGS)
            b_content = sum(1 for s in _BULLISH_CONTENT if s in content_lower)
            r_content = sum(1 for s in _BEARISH_CONTENT if s in content_lower)
            if b_tag + b_content > r_tag + r_content:
                bullish += 1
                signals.append(f"Bullish: {item.title}")
            elif r_tag + r_content > b_tag + b_content:
                bearish += 1
                signals.append(f"Bearish risk: {item.title}")
        return bullish, bearish, signals

    @staticmethod
    def _direction_and_prob(net: float) -> tuple[str, float]:
        probability = _BASE_PROB + _SIGNAL_STEP * net
        probability = min(_MAX_PROB, max(_MIN_PROB, probability))
        if net > 0.5:
            return "bullish", probability
        if net < -0.5:
            return "bearish", min(_MAX_PROB, max(_MIN_PROB, 1.0 - probability + _BASE_PROB - 0.5))
        return "neutral", 0.50

    @staticmethod
    def _build_signal(direction: str, probability: float, horizon: str, calc_note: str) -> HorizonSignal:
        ind_note = (
            f"; indicator context: {calc_note}"
            if calc_note and calc_note.strip().lower() != _NONE_PLACEHOLDER
            else ""
        )
        rationale = (
            f"{horizon} outlook is {direction} (estimated probability {probability:.0%}) "
            f"based on quantitative trade plan + grounded context{ind_note}. "
            "This is an estimate, not a guarantee. Validate with live NSE/BSE data before trading."
        )
        return HorizonSignal(direction=direction, probability=round(probability, 4), rationale=rationale)

    @staticmethod
    def _plan_net(trade_plan: Optional[dict[str, Any]], p0_math: Optional[dict[str, Any]]) -> tuple[float, list[str]]:
        notes: list[str] = []
        net = 0.0
        plan = trade_plan or {}
        if not plan and isinstance(p0_math, dict):
            plan = p0_math.get("trade_plan") or {}
        action = str(plan.get("action") or "").upper()
        if action in _ACTION_SCORE:
            net += _ACTION_SCORE[action]
            notes.append(f"Trade plan={action} (score={plan.get('score')})")
        rsi = None
        if isinstance(p0_math, dict):
            rsi = p0_math.get("wilder_rsi_14")
            st = (p0_math.get("supertrend") or {}).get("direction")
            macd_h = (p0_math.get("macd") or {}).get("histogram")
            rs20 = (p0_math.get("vs_nifty") or {}).get("rs_20d")
            if st == "bullish":
                net += 1
                notes.append("Supertrend bullish")
            elif st == "bearish":
                net -= 1
                notes.append("Supertrend bearish")
            if isinstance(macd_h, (int, float)):
                net += 0.5 if macd_h > 0 else -0.5
            if isinstance(rs20, (int, float)):
                if rs20 >= 1.03:
                    net += 0.5
                elif rs20 <= 0.97:
                    net -= 0.5
        if isinstance(rsi, (int, float)):
            if rsi >= 70:
                net -= 1.5
                notes.append(f"Wilder RSI {rsi:.1f} overbought")
            elif rsi <= 30:
                net += 1.0
                notes.append(f"Wilder RSI {rsi:.1f} oversold")
        return net, notes

    def predict(
        self,
        context_items: list[KnowledgeItem],
        deterministic_note: str = "",
        resolved_entity: dict | None = None,
        trade_plan: dict | None = None,
        p0_math: dict | None = None,
    ) -> PredictionSignals:
        """Generate multi-horizon prediction signals from plan + grounded context."""
        bullish, bearish, key_signals = self._score_items(context_items)
        kb_net = float(bullish - bearish)

        plan_net, plan_notes = self._plan_net(trade_plan, p0_math)
        # Quant plan dominates; KB nudges lightly.
        net = plan_net + 0.35 * kb_net
        key_signals = list(plan_notes) + key_signals

        if resolved_entity:
            entity_label = (
                f"{resolved_entity.get('symbol', '')} ({resolved_entity.get('company_name', '')})"
            ).strip()
            if entity_label and entity_label != "()":
                key_signals.insert(0, f"Entity context: {entity_label}")

        # Horizon compression: shorter horizons more sensitive to RSI/momentum.
        rsi = None
        if isinstance(p0_math, dict):
            rsi = p0_math.get("wilder_rsi_14")
        intra_net = net
        if isinstance(rsi, (int, float)) and rsi >= 68:
            intra_net -= 1.0
        swing_net = net * 0.85
        mt_net = net * 0.65
        # Longer horizons lean more on valuation cues in the plan action.
        action = str((trade_plan or (p0_math or {}).get("trade_plan") or {}).get("action") or "").upper()
        if action in {"BUY", "ACCUMULATE"}:
            mt_net += 0.5
        if action in {"SELL", "TRIM"}:
            mt_net -= 0.5

        intraday_dir, intraday_prob = self._direction_and_prob(intra_net)
        swing_dir, swing_prob = self._direction_and_prob(swing_net)
        mt_dir, mt_prob = self._direction_and_prob(mt_net)

        calc_context = deterministic_note.split("\n")[0] if deterministic_note else ""
        if plan_notes:
            calc_context = (calc_context + "; " if calc_context else "") + "; ".join(plan_notes[:3])

        intraday_signal = self._build_signal(intraday_dir, intraday_prob, "Intraday", calc_context)
        swing_signal = self._build_signal(swing_dir, swing_prob, "Swing (1-5 days)", calc_context)
        mt_signal = self._build_signal(mt_dir, mt_prob, "Medium-term (1-3 months)", calc_context)

        plan_conf = float((trade_plan or {}).get("confidence") or 0.0)
        overall_confidence = max(
            0.10,
            min(0.75, 0.25 + 0.06 * len(context_items) + 0.35 * plan_conf),
        )
        if (p0_math or {}).get("data_quality", {}).get("degraded"):
            overall_confidence = min(overall_confidence, 0.55)

        return PredictionSignals(
            intraday=intraday_signal,
            swing=swing_signal,
            medium_term=mt_signal,
            key_signals=tuple(key_signals[:6]),
            overall_confidence=round(overall_confidence, 4),
        )
