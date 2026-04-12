"""
LLM integration for BYSEL AI assistant.
Providers (tried in order):
  1. HuggingFace Inference API (HF_TOKEN)  — free tier  [PRIMARY]
  2. Google Gemini  (GEMINI_API_KEY)                     [SECONDARY]
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

# ── HuggingFace Inference API (PRIMARY) ────────────────

HF_MODELS: List[str] = [
    "meta-llama/Llama-3.1-8B-Instruct",
    "mistralai/Mistral-7B-Instruct-v0.3",
    "Qwen/Qwen2.5-7B-Instruct",
]

HF_BASE_URL = "https://router.huggingface.co/v1/chat/completions"


def _ask_hf_sync(prompt: str, model: str) -> Dict:
    """Call HuggingFace Inference API (OpenAI-compatible)."""
    api_key = os.environ.get("HF_TOKEN", "").strip()
    if not api_key:
        return {"error": "HF_TOKEN not configured"}

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
        HF_BASE_URL,
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
        return {"answer": text, "source": f"hf-{model.split('/')[-1]}"}
    except urllib_error.HTTPError as e:
        body = e.read().decode(errors="replace")[:500]
        logger.warning("HF %s HTTP %s: %s", model, e.code, body)
        return {"error": f"HF {model} HTTP {e.code}: {body[:200]}"}
    except Exception as e:
        logger.warning("HF %s error: %s", model, e)
        return {"error": f"HF {model}: {str(e)}"}


def _try_all_hf_models(prompt: str) -> Dict:
    """Try each HuggingFace model in order until one works."""
    api_key = os.environ.get("HF_TOKEN", "").strip()
    if not api_key:
        return {"error": "HF_TOKEN not configured"}

    last_err = ""
    for model in HF_MODELS:
        result = _ask_hf_sync(prompt, model)
        if "answer" in result:
            logger.info("HF model %s succeeded", model)
            return result
        last_err = result.get("error", "unknown")
        logger.info("HF model %s failed: %s — trying next", model, last_err)

    return {"error": f"All HF models failed. Last: {last_err}"}


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
        bool(os.environ.get("HF_TOKEN", "").strip())
        or _get_gemini_model() is not None
    )


async def ask_gemini(query: str, context: Optional[str] = None) -> Dict:
    """
    Try HuggingFace first (primary), then Gemini as fallback.
    Returns {"answer": str, "source": "hf-<model>"|"gemini"} on success,
    or {"error": str} if all providers fail.
    """
    prompt_parts = []
    if context:
        prompt_parts.append(f"Market context:\n{context}\n\n")
    prompt_parts.append(f"User query: {query}")
    full_prompt = "".join(prompt_parts)

    # 1) Try HuggingFace (primary — free tier, multiple models)
    hf_err = "not configured"
    if os.environ.get("HF_TOKEN", "").strip():
        result = _try_all_hf_models(full_prompt)
        if "answer" in result:
            return result
        hf_err = result.get("error", "unknown")
        logger.info("HF failed, trying Gemini fallback: %s", hf_err)

    # 2) Try Gemini (secondary)
    gemini_err = "not configured"
    if os.environ.get("GEMINI_API_KEY"):
        result = await _ask_gemini(full_prompt)
        if "answer" in result:
            return result
        gemini_err = result.get("error", "unknown")
        logger.info("Gemini also failed: %s", gemini_err)

    return {"error": f"HuggingFace: {hf_err} | Gemini: {gemini_err}"}
