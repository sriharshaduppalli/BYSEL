"""
LLM integration for BYSEL AI assistant.
Uses Google Gemini REST API directly (no Cloudflare issues).
Tries multiple Gemini models — each model has its own daily free quota.
"""

import os
import json
import logging
from typing import Dict, List, Optional
from urllib import request as urllib_request, error as urllib_error

logger = logging.getLogger(__name__)

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

# Each model has its own daily free quota (1500 RPD each).
# Try them in order — if one is exhausted, the next one should work.
GEMINI_MODELS: List[str] = [
    "gemini-2.0-flash",
    "gemini-2.0-flash-lite",
    "gemini-1.5-flash",
    "gemini-1.5-flash-8b",
]

GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"


def _call_gemini_rest(prompt: str, model: str, api_key: str) -> Dict:
    """Call Gemini REST API directly (no SDK, no Cloudflare issues)."""
    url = f"{GEMINI_API_BASE}/{model}:generateContent?key={api_key}"

    payload = json.dumps({
        "system_instruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.7,
            "maxOutputTokens": 1024,
        },
    }).encode()

    req = urllib_request.Request(
        url, data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib_request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read())

        # Extract text from Gemini response
        candidates = data.get("candidates", [])
        if not candidates:
            return {"error": f"{model}: no candidates returned"}
        parts = candidates[0].get("content", {}).get("parts", [])
        text = "".join(p.get("text", "") for p in parts).strip()
        if not text:
            return {"error": f"{model}: empty response"}
        return {"answer": text, "source": f"gemini-{model}"}

    except urllib_error.HTTPError as e:
        body = e.read().decode(errors="replace")[:500]
        if e.code == 429:
            logger.info("Gemini %s quota exhausted, trying next model", model)
            return {"error": f"{model}: quota exhausted"}
        logger.warning("Gemini %s HTTP %s: %s", model, e.code, body[:200])
        return {"error": f"{model} HTTP {e.code}: {body[:150]}"}
    except Exception as e:
        logger.warning("Gemini %s error: %s", model, e)
        return {"error": f"{model}: {str(e)}"}


# ── Public interface ───────────────────────────────────

def gemini_available() -> bool:
    """Check if Gemini API key is configured."""
    return bool(os.environ.get("GEMINI_API_KEY", "").strip())


async def ask_gemini(query: str, context: Optional[str] = None) -> Dict:
    """
    Try multiple Gemini models in order (each has separate daily quota).
    Returns {"answer": str, "source": "gemini-<model>"} on success,
    or {"error": str} if all models fail.
    """
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        return {"error": "GEMINI_API_KEY not set"}

    prompt_parts = []
    if context:
        prompt_parts.append(f"Market context:\n{context}\n\n")
    prompt_parts.append(f"User query: {query}")
    full_prompt = "".join(prompt_parts)

    last_err = ""
    for model in GEMINI_MODELS:
        result = _call_gemini_rest(full_prompt, model, api_key)
        if "answer" in result:
            return result
        last_err = result.get("error", "unknown")

    return {"error": f"All Gemini models exhausted. Last: {last_err}"}
