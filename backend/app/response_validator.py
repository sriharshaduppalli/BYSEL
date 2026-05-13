"""
Response Validator for BYSEL AI
Ensures all responses contain complete, structured analysis with required fields.
Validates against mandatory completeness requirements for Indian stock analysis.
"""

from typing import Dict, List, Tuple, Any
import logging

logger = logging.getLogger(__name__)


class ResponseValidator:
    """
    Validates that AI responses include all required fields for comprehensive stock analysis.
    Enforces: technical indicators, fundamentals, trading levels, sentiment, signal, confidence.
    """

    # Required fields for complete analysis response
    REQUIRED_TOP_LEVEL_FIELDS = {
        "query": str,
        "symbol": str,
        "timestamp": str,
        "analysis": dict,
        "recommendation": dict,
        "suggestions": list,
        "disclaimer": str,
        "data_quality": str,
    }

    # Required fields in analysis.technical
    REQUIRED_TECHNICAL_FIELDS = {
        "rsi": (int, float),
        "rsi_signal": str,  # e.g., "overbought", "neutral", "oversold"
        "macd": str,  # "bullish", "bearish", "neutral"
        "macd_signal": str,
        "bollinger_bands": str,  # e.g., "upper_half", "below_lower"
        "bollinger_position": (int, float),  # % position in band
        "moving_average_trend": str,  # "strong_bullish", "bullish", "neutral", etc.
        "technical_trend": str,
        "summary": str,
    }

    # Required fields in analysis.fundamental
    REQUIRED_FUNDAMENTAL_FIELDS = {
        "pe_ratio": (int, float, type(None)),
        "pe_sector_avg": (int, float, type(None)),
        "market_cap": str,
        "dividend_yield": (int, float, type(None)),
        "week_52_low": (int, float, str),
        "week_52_high": (int, float, str),
        "week_52_position": str,
        "earnings_date": str,
        "summary": str,
    }

    # Required fields in analysis.trading_levels
    REQUIRED_TRADING_LEVELS = {
        "support_1": (int, float),
        "support_2": (int, float),
        "resistance_1": (int, float),
        "resistance_2": (int, float),
        "stop_loss": (int, float),
        "take_profit_1m": (int, float),
    }

    # Required fields in analysis.sentiment
    REQUIRED_SENTIMENT_FIELDS = {
        "overall": str,  # "positive", "negative", "neutral"
        "score": (int, float),  # -1.0 to 1.0
        "positive_pct": (int, float),
        "negative_pct": (int, float),
        "neutral_pct": (int, float),
        "recent_events": list,
        "macro_factors": list,
        "summary": str,
    }

    # Required fields in recommendation
    REQUIRED_RECOMMENDATION_FIELDS = {
        "signal": str,  # "BUY", "SELL", "HOLD", etc.
        "confidence": (int, float),  # 0-100
        "confidence_level": str,  # "HIGH", "MEDIUM", "LOW"
        "target_price": (int, float, type(None)),
        "time_horizon": str,  # "1 week", "1 month", etc.
        "why_confident": str,
        "key_reasons": list,  # At least 2-3 reasons
        "key_risks": list,  # At least 2-3 risks
    }

    @staticmethod
    def validate_response(response: Dict) -> Tuple[bool, List[str]]:
        """
        Validate response completeness.
        Returns (is_valid, list_of_missing_fields)
        """
        missing = []

        # Check top-level structure
        for field, expected_type in ResponseValidator.REQUIRED_TOP_LEVEL_FIELDS.items():
            if field not in response:
                missing.append(f"top_level: missing '{field}'")
            elif response[field] is None:
                missing.append(f"top_level: '{field}' is None")

        # Check analysis.technical
        if "analysis" in response and isinstance(response["analysis"], dict):
            if "technical" in response["analysis"]:
                tech = response["analysis"]["technical"]
                for field, expected_type in ResponseValidator.REQUIRED_TECHNICAL_FIELDS.items():
                    if field not in tech:
                        missing.append(f"technical: missing '{field}'")
                    elif tech[field] is None:
                        missing.append(f"technical: '{field}' is None")
            else:
                missing.append("analysis: missing 'technical' section")

            # Check analysis.fundamental
            if "fundamental" in response["analysis"]:
                fund = response["analysis"]["fundamental"]
                for field, expected_type in ResponseValidator.REQUIRED_FUNDAMENTAL_FIELDS.items():
                    if field not in fund:
                        missing.append(f"fundamental: missing '{field}'")
                    elif fund[field] is None and type(None) not in expected_type if isinstance(expected_type, tuple) else True:
                        missing.append(f"fundamental: '{field}' is None")
            else:
                missing.append("analysis: missing 'fundamental' section")

            # Check analysis.trading_levels
            if "trading_levels" in response["analysis"]:
                tl = response["analysis"]["trading_levels"]
                for field in ResponseValidator.REQUIRED_TRADING_LEVELS.keys():
                    if field not in tl:
                        missing.append(f"trading_levels: missing '{field}'")
                    elif tl[field] is None:
                        missing.append(f"trading_levels: '{field}' is None")
            else:
                missing.append("analysis: missing 'trading_levels' section")

            # Check analysis.sentiment
            if "sentiment" in response["analysis"]:
                sent = response["analysis"]["sentiment"]
                for field in ResponseValidator.REQUIRED_SENTIMENT_FIELDS.keys():
                    if field not in sent:
                        missing.append(f"sentiment: missing '{field}'")
                    elif sent[field] is None:
                        missing.append(f"sentiment: '{field}' is None")
            else:
                missing.append("analysis: missing 'sentiment' section")

        # Check recommendation structure
        if "recommendation" in response and isinstance(response["recommendation"], dict):
            rec = response["recommendation"]
            for field in ResponseValidator.REQUIRED_RECOMMENDATION_FIELDS.keys():
                if field not in rec:
                    missing.append(f"recommendation: missing '{field}'")
                elif rec[field] is None:
                    missing.append(f"recommendation: '{field}' is None")
                elif field == "key_reasons" and len(rec.get("key_reasons", [])) < 2:
                    missing.append(f"recommendation: '{field}' has fewer than 2 items")
                elif field == "key_risks" and len(rec.get("key_risks", [])) < 2:
                    missing.append(f"recommendation: '{field}' has fewer than 2 items")
        else:
            missing.append("top_level: missing or invalid 'recommendation' section")

        # Validate signal is valid
        if "recommendation" in response:
            signal = response["recommendation"].get("signal", "").upper()
            if signal not in ["BUY", "SELL", "HOLD", "STRONG_BUY", "STRONG_SELL"]:
                missing.append(f"recommendation: signal '{signal}' is not valid BUY/SELL/HOLD")

        # Validate confidence is 0-100
        if "recommendation" in response:
            confidence = response["recommendation"].get("confidence")
            if not isinstance(confidence, (int, float)) or confidence < 0 or confidence > 100:
                missing.append(f"recommendation: confidence must be 0-100, got {confidence}")

        return (len(missing) == 0, missing)

    @staticmethod
    def augment_incomplete_response(
        response: Dict, raw_data: Dict
    ) -> Dict:
        """
        Fill missing fields in response with data from raw analysis.
        Attempts to complete incomplete LLM responses.
        """
        if "analysis" not in response:
            response["analysis"] = {}

        # Augment technical
        if "technical" not in response["analysis"]:
            response["analysis"]["technical"] = _format_technical_data(
                raw_data.get("technical", {})
            )

        # Augment fundamental
        if "fundamental" not in response["analysis"]:
            response["analysis"]["fundamental"] = _format_fundamental_data(
                raw_data.get("fundamental", {})
            )

        # Augment trading levels
        if "trading_levels" not in response["analysis"]:
            response["analysis"]["trading_levels"] = raw_data.get("trading_levels", {})

        # Augment sentiment
        if "sentiment" not in response["analysis"]:
            response["analysis"]["sentiment"] = _format_sentiment_data(
                raw_data.get("sentiment", {})
            )

        # Augment recommendation if missing signal/confidence
        if "recommendation" not in response:
            response["recommendation"] = {}

        rec = response["recommendation"]
        if "signal" not in rec or not rec.get("signal"):
            rec["signal"] = raw_data.get("recommendation", {}).get("signal", "HOLD")
        if "confidence" not in rec or rec.get("confidence") is None:
            rec["confidence"] = raw_data.get("recommendation", {}).get("confidence", 50)

        return response

    @staticmethod
    def get_missing_fields_summary(missing: List[str]) -> str:
        """Convert missing fields list to human-readable summary."""
        if not missing:
            return "✅ All fields present"

        grouped = {}
        for item in missing:
            section = item.split(":")[0]
            grouped.setdefault(section, []).append(item)

        summary = []
        for section, items in grouped.items():
            summary.append(f"{section}: {len(items)} missing")

        return "⚠️ Incomplete: " + ", ".join(summary)


def _format_technical_data(tech: Dict) -> Dict:
    """Format raw technical data into validator format."""
    return {
        "rsi": tech.get("rsi", 50),
        "rsi_signal": _interpret_rsi(tech.get("rsi", 50)),
        "macd": tech.get("macd", {}).get("trend", "neutral"),
        "macd_signal": tech.get("macd", {}).get("signal", 0),
        "bollinger_bands": tech.get("bollinger_bands", {}).get("position", "middle"),
        "bollinger_position": tech.get("bollinger_bands", {}).get("position_pct", 50),
        "moving_average_trend": tech.get("moving_averages", {}).get("trend", "neutral"),
        "technical_trend": tech.get("trend", "neutral"),
        "summary": tech.get("summary", "No summary available"),
    }


def _format_fundamental_data(fund: Dict) -> Dict:
    """Format raw fundamental data into validator format."""
    return {
        "pe_ratio": fund.get("pe_ratio"),
        "pe_sector_avg": fund.get("pe_sector_avg"),
        "market_cap": fund.get("market_cap", "N/A"),
        "dividend_yield": fund.get("dividend_yield"),
        "week_52_low": fund.get("52_week_low", "N/A"),
        "week_52_high": fund.get("52_week_high", "N/A"),
        "week_52_position": fund.get("52_week_position", "middle"),
        "earnings_date": fund.get("earnings_date", "TBD"),
        "summary": fund.get("summary", "No summary available"),
    }


def _format_sentiment_data(sent: Dict) -> Dict:
    """Format raw sentiment data into validator format."""
    return {
        "overall": sent.get("overall", "neutral"),
        "score": sent.get("score", 0),
        "positive_pct": sent.get("positive_pct", 0),
        "negative_pct": sent.get("negative_pct", 0),
        "neutral_pct": sent.get("neutral_pct", 0),
        "recent_events": sent.get("recent_events", []),
        "macro_factors": sent.get("macro_factors", []),
        "summary": sent.get("summary", "No summary available"),
    }


def _interpret_rsi(rsi: float) -> str:
    """Interpret RSI value into signal."""
    if rsi > 70:
        return "overbought"
    elif rsi > 60:
        return "slightly_overbought"
    elif rsi < 30:
        return "oversold"
    elif rsi < 40:
        return "slightly_oversold"
    else:
        return "neutral"
