"""
LLM integration for BYSEL AI assistant.
Providers (tried in order):
  1. Groq / Llama   (GROQ_API_KEY)  — free 30 RPM / 14,400 RPD  [PRIMARY]
  2. Google Gemini  (GEMINI_API_KEY) [SECONDARY]
Falling back to the rule-based ai_engine when neither is available.
"""

import os
import json
import logging
from typing import Dict, List, Optional
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

# ── Groq (PRIMARY) ─────────────────────────────────────

# Models to try in order — first available one wins
GROQ_MODELS: List[str] = [
    "llama-3.3-70b-versatile",
    "llama-3.1-70b-versatile",
    "llama3-70b-8192",
    "mixtral-8x7b-32768",
    "llama3-8b-8192",
]


def _ask_groq_sync(prompt: str, model: str) -> Dict:
    """Call Groq REST API for a specific model."""
    api_key = os.environ.get("GROQ_API_KEY", "").strip()
    if not api_key:
        return {"error": "Groq not configured"}

    payload = json.dumps({
        "model": model,
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
            return {"error": f"Empty response from {model}"}
        return {"answer": text, "source": f"groq-{model}"}
    except urllib_error.HTTPError as e:
        body = e.read().decode(errors="replace")[:500]
        logger.warning("Groq %s HTTP %s: %s", model, e.code, body)
        return {"error": f"Groq {model} HTTP {e.code}: {body[:200]}"}
    except Exception as e:
        logger.warning("Groq %s error: %s", model, e)
        return {"error": f"Groq {model}: {str(e)}"}


def _try_all_groq_models(prompt: str) -> Dict:
    """Try each Groq model in order until one works."""
    api_key = os.environ.get("GROQ_API_KEY", "").strip()
    if not api_key:
        return {"error": "Groq not configured"}

    last_err = ""
    for model in GROQ_MODELS:
        result = _ask_groq_sync(prompt, model)
        if "answer" in result:
            logger.info("Groq model %s succeeded", model)
            return result
        last_err = result.get("error", "unknown")
        logger.info("Groq model %s failed: %s — trying next", model, last_err)

    return {"error": f"All Groq models failed. Last: {last_err}"}


def list_groq_models() -> Dict:
    """List available Groq models (for diagnostics)."""
    api_key = os.environ.get("GROQ_API_KEY", "").strip()
    if not api_key:
        return {"error": "GROQ_API_KEY not set"}

    req = urllib_request.Request(
        "https://api.groq.com/openai/v1/models",
        headers={
            "Authorization": f"Bearer {api_key}",
        },
        method="GET",
    )
    try:
        with urllib_request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read())
        models = [m["id"] for m in data.get("data", [])]
        return {"models": sorted(models)}
    except urllib_error.HTTPError as e:
        body = e.read().decode(errors="replace")[:500]
        return {"error": f"HTTP {e.code}: {body[:300]}"}
    except Exception as e:
        return {"error": str(e)}


# ── Gemini (SECONDARY) ────────────────────────────────

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


# ── Public interface ───────────────────────────────────

def gemini_available() -> bool:
    """Check if at least one LLM provider is configured."""
    return (
        bool(os.environ.get("GROQ_API_KEY", "").strip())
        or _get_gemini_model() is not None
    )


async def ask_gemini(query: str, context: Optional[str] = None) -> Dict:
    """
    Try Groq first (primary), then Gemini as fallback.
    Returns {"answer": str, "source": "groq-<model>"|"gemini"} on success,
    or {"error": str} if all providers fail.
    """
    prompt_parts = []
    if context:
        prompt_parts.append(f"Market context:\n{context}\n\n")
    prompt_parts.append(f"User query: {query}")
    full_prompt = "".join(prompt_parts)

    # 1) Try Groq (primary — free tier, multiple models)
    groq_err = "not configured"
    if os.environ.get("GROQ_API_KEY", "").strip():
        result = _try_all_groq_models(full_prompt)
        if "answer" in result:
            return result
        groq_err = result.get("error", "unknown")
        logger.info("Groq failed, trying Gemini fallback: %s", groq_err)

    # 2) Try Gemini (secondary)
    gemini_err = "not configured"
    if os.environ.get("GEMINI_API_KEY"):
        result = await _ask_gemini(full_prompt)
        if "answer" in result:
            return result
        gemini_err = result.get("error", "unknown")
        logger.info("Gemini also failed: %s", gemini_err)

    return {"error": f"Groq: {groq_err} | Gemini: {gemini_err}"}
