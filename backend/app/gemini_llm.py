"""
LLM integration for BYSEL AI assistant.
Providers (tried in order):
  1. Google Gemini  (GEMINI_API_KEY)
  2. Groq / Llama   (GROQ_API_KEY)  — free 30 RPM / 14,400 RPD
Falling back to the rule-based ai_engine when neither is available.
"""

import os
import json
import logging
from typing import Dict, Optional
from urllib import request as urllib_request, error as urllib_error

logger = logging.getLogger(__name__)

_gemini_model = None

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

# ── Gemini ─────────────────────────────────────────────

def _get_gemini_model():
    """Lazy-initialize the Gemini model."""
    global _gemini_model
    if _gemini_model is not None:
        return _gemini_model

    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        return None

    try:
        import google.generativeai as genai
        genai.configure(api_key=api_key)
        _gemini_model = genai.GenerativeModel(
            "gemini-2.0-flash-lite",
            system_instruction=SYSTEM_PROMPT,
        )
        logger.info("Gemini model initialized")
        return _gemini_model
    except Exception as e:
        logger.error("Gemini init failed: %s", e)
        return None


async def _ask_gemini(prompt: str) -> Dict:
    model = _get_gemini_model()
    if model is None:
        return {"error": "Gemini not configured"}
    try:
        response = await model.generate_content_async(
            prompt,
            generation_config={"temperature": 0.7, "max_output_tokens": 1024},
        )
        text = response.text.strip() if response.text else ""
        if not text:
            return {"error": "Empty Gemini response"}
        return {"answer": text, "source": "gemini"}
    except Exception as e:
        logger.warning("Gemini error: %s", e)
        return {"error": str(e)}


# ── Groq (Llama 3.3 70B) ──────────────────────────────

def _ask_groq_sync(prompt: str) -> Dict:
    """Call Groq REST API (no SDK needed). Free tier: 30 RPM, 14400 RPD."""
    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        return {"error": "Groq not configured"}

    payload = json.dumps({
        "model": "llama-3.3-70b-versatile",
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.7,
        "max_tokens": 1024,
    }).encode()

    req = urllib_request.Request(
        "https://api.groq.com/openai/v1/chat/completions",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with urllib_request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read())
        text = data["choices"][0]["message"]["content"].strip()
        if not text:
            return {"error": "Empty Groq response"}
        return {"answer": text, "source": "groq-llama"}
    except urllib_error.HTTPError as e:
        body = e.read().decode(errors="replace")[:300]
        logger.warning("Groq HTTP %s: %s", e.code, body)
        return {"error": f"Groq HTTP {e.code}"}
    except Exception as e:
        logger.warning("Groq error: %s", e)
        return {"error": str(e)}


# ── Public interface ───────────────────────────────────

def gemini_available() -> bool:
    """Check if at least one LLM provider is configured."""
    return (
        _get_gemini_model() is not None
        or bool(os.environ.get("GROQ_API_KEY"))
    )


async def ask_gemini(query: str, context: Optional[str] = None) -> Dict:
    """
    Try Gemini first, then Groq as fallback.
    Returns {"answer": str, "source": "gemini"|"groq-llama"} on success,
    or {"error": str} if all providers fail.
    """
    prompt_parts = []
    if context:
        prompt_parts.append(f"Market context:\n{context}\n\n")
    prompt_parts.append(f"User query: {query}")
    full_prompt = "".join(prompt_parts)

    # 1) Try Gemini
    gemini_err = "not configured"
    if os.environ.get("GEMINI_API_KEY"):
        result = await _ask_gemini(full_prompt)
        if "answer" in result:
            return result
        gemini_err = result.get("error", "unknown")
        logger.info("Gemini failed, trying Groq fallback: %s", gemini_err)

    # 2) Try Groq (sync call, fast enough at ~200ms)
    if os.environ.get("GROQ_API_KEY"):
        result = _ask_groq_sync(full_prompt)
        if "answer" in result:
            return result
        logger.info("Groq also failed: %s", result.get("error", ""))
        return {"error": f"Gemini: {gemini_err} | Groq: {result.get('error', 'unknown')}"}

    return {"error": f"Gemini: {gemini_err} | Groq not configured"}
