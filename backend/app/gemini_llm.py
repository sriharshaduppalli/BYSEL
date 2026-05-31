"""
Google Gemini LLM integration for BYSEL AI assistant.
Runs Google's Gemma 4 models on Gemini API.
Set GEMINI_API_KEY environment variable to enable.
Falls back gracefully when the key is absent.
"""

import os
import logging
from typing import Dict, Optional

logger = logging.getLogger(__name__)

_client = None
_DEFAULT_MODEL = "gemma-4-26b-a4b-it"


def _get_client():
    """Initialize Gemini client if API key is available."""
    global _client
    if _client is not None:
        return _client

    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        logger.info("GEMINI_API_KEY not set — Gemini LLM disabled")
        return None

    try:
        from google import genai
        client = genai.Client(api_key=api_key)
        logger.info("Gemini client initialized (model=%s)", _DEFAULT_MODEL)
        _client = client
        return _client
    except Exception as e:
        logger.error("Failed to init Gemini client: %s", e)
        return None


def gemini_available() -> bool:
    """Check if Gemini LLM is configured and available."""
    return _get_client() is not None


async def ask_gemini(
    query: str,
    context: Optional[Dict] = None,
    system_prompt: str = "",
) -> Dict:
    """
    Send query to Gemini with optional context using Gemma 4 model.

    Returns:
    {
        "answer": str,
        "error": str (optional)
    }
    """
    client = _get_client()
    if client is None:
        return {"error": "Gemini not configured"}

    try:
        from google.genai import types

        # Build the prompt with context
        prompt_parts = []

        if context:
            prompt_parts.append(_format_gemini_context(context))

        prompt_parts.append(f"User Query: {query}")

        full_prompt = "\n".join(prompt_parts)

        logger.info("GEMINI DEBUG: Sending query to Gemini (prompt length: %d)", len(full_prompt))

        # Build config with system instruction if provided
        config = types.GenerateContentConfig()
        if system_prompt:
            config.system_instruction = system_prompt

        # Call Gemini API (note: Gemini doesn't support async, so we run it sync)
        response = client.models.generate_content(
            model=_DEFAULT_MODEL,
            contents=full_prompt,
            config=config,
        )

        if response and response.text:
            logger.info("GEMINI DEBUG: Gemini returned answer (%d chars)", len(response.text))
            return {"answer": response.text}
        else:
            logger.info("GEMINI DEBUG: Gemini returned empty")
            return {"error": "Gemini returned empty response"}

    except Exception as e:
        logger.error("Gemini API error: %s", e)
        return {"error": str(e)}


def _format_gemini_context(context: Optional[Dict]) -> str:
    """Format context for Gemini prompt."""
    if not context or not isinstance(context, dict):
        return ""

    parts = []

    # Symbol and price
    symbol = context.get("symbol")
    if symbol:
        parts.append(f"Stock Symbol: {symbol}")

    current_price = context.get("current_price")
    if current_price:
        parts.append(f"Current Price: ₹{current_price}")

    # Technical data
    technical = context.get("technical", {})
    if technical:
        parts.append("\nTechnical Indicators:")
        if technical.get("rsi"):
            parts.append(f"  RSI: {technical['rsi']}")
        if technical.get("trend"):
            parts.append(f"  Trend: {technical['trend']}")
        if technical.get("macd"):
            parts.append(f"  MACD: {technical['macd']}")

    # Fundamental data
    fundamental = context.get("fundamental", {})
    if fundamental:
        parts.append("\nFundamental Data:")
        if fundamental.get("pe_ratio"):
            parts.append(f"  P/E Ratio: {fundamental['pe_ratio']}")
        if fundamental.get("market_cap"):
            parts.append(f"  Market Cap: {fundamental['market_cap']}")
        if fundamental.get("dividend_yield"):
            parts.append(f"  Dividend Yield: {fundamental['dividend_yield']}%")

    # Trading levels
    trading_levels = context.get("trading_levels", {})
    if trading_levels:
        parts.append("\nTrading Levels:")
        if trading_levels.get("support_1"):
            parts.append(f"  Support 1: ₹{trading_levels['support_1']}")
        if trading_levels.get("resistance_1"):
            parts.append(f"  Resistance 1: ₹{trading_levels['resistance_1']}")

    # Sentiment
    sentiment = context.get("sentiment", {})
    if sentiment:
        parts.append("\nMarket Sentiment:")
        if sentiment.get("overall"):
            parts.append(f"  Overall: {sentiment['overall']}")
        if sentiment.get("score"):
            parts.append(f"  Score: {sentiment['score']}")

    # News summary
    if context.get("news_summary"):
        parts.append(f"\nRecent News:\n{context['news_summary']}")

    return "\n".join(parts)
