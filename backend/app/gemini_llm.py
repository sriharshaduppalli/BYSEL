"""
Gemini LLM integration for BYSEL AI assistant.
Provides natural-language stock analysis powered by Google Gemini,
falling back to the rule-based ai_engine when the API key is not set.
"""

import os
import logging
from typing import Dict, Optional

logger = logging.getLogger(__name__)

_model = None

SYSTEM_PROMPT = """You are BYSEL AI, an expert Indian stock market assistant.
You provide concise, actionable stock analysis for NSE/BSE listed stocks.

Rules:
- Keep responses under 300 words unless the user asks for details.
- Always mention risk disclaimers briefly ("Not financial advice. Do your own research.").
- Use Indian market terminology (NSE, BSE, NIFTY, SENSEX, Zerodha, Angel One, etc.).
- Support Hinglish queries naturally (e.g., "kya RELIANCE kharidna chahiye?").
- When analyzing a stock, mention: current trend, key levels, sector context, and a clear suggestion.
- For predictions, give probability-weighted ranges, never single-point forecasts.
- Format prices in ₹ with Indian number system (₹1,23,456).
- If you don't know something, say so honestly.
"""


def _get_model():
    """Lazy-initialize the Gemini model."""
    global _model
    if _model is not None:
        return _model

    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        logger.info("GEMINI_API_KEY not set – LLM features disabled")
        return None

    try:
        import google.generativeai as genai
        genai.configure(api_key=api_key)
        _model = genai.GenerativeModel(
            "gemini-1.5-flash",
            system_instruction=SYSTEM_PROMPT,
        )
        logger.info("Gemini model initialized successfully")
        return _model
    except Exception as e:
        logger.error("Failed to initialize Gemini: %s", e)
        return None


def gemini_available() -> bool:
    """Check if Gemini is configured and reachable."""
    return _get_model() is not None


async def ask_gemini(query: str, context: Optional[str] = None) -> Dict:
    """
    Send a query to Gemini and return the response.
    Returns {"answer": str, "source": "gemini"} on success,
    or {"error": str} on failure.
    """
    model = _get_model()
    if model is None:
        return {"error": "Gemini not configured"}

    prompt_parts = []
    if context:
        prompt_parts.append(f"Market context:\n{context}\n\n")
    prompt_parts.append(f"User query: {query}")
    full_prompt = "".join(prompt_parts)

    try:
        response = await model.generate_content_async(
            full_prompt,
            generation_config={
                "temperature": 0.7,
                "max_output_tokens": 1024,
            },
        )
        text = response.text.strip() if response.text else ""
        if not text:
            return {"error": "Empty response from Gemini"}
        return {"answer": text, "source": "gemini"}
    except Exception as e:
        logger.error("Gemini API error: %s", e)
        return {"error": f"Gemini API error: {str(e)}"}
