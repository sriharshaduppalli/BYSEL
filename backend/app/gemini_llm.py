"""
LLM integration for BYSEL AI assistant.
Providers (tried in order):
  1. HuggingFace Serverless Inference — NO API KEY NEEDED  [PRIMARY]
  2. Google Gemini  (GEMINI_API_KEY)                       [SECONDARY]
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

# ── HuggingFace Serverless Inference (PRIMARY — NO KEY NEEDED) ──

# Models to try in order. These run on HF's free serverless infra.
# Using the /v1/chat/completions endpoint (OpenAI-compatible).
HF_MODELS: List[str] = [
    "mistralai/Mistral-7B-Instruct-v0.3",
    "HuggingFaceH4/zephyr-7b-beta",
    "microsoft/Phi-3-mini-4k-instruct",
]


def _ask_hf_sync(prompt: str, model: str) -> Dict:
    """Call HuggingFace Inference API. Works WITHOUT a token (lower rate limits)."""
    headers = {"Content-Type": "application/json"}

    # Use token if available for higher rate limits, but works without it
    hf_token = os.environ.get("HF_TOKEN", "").strip()
    if hf_token:
        headers["Authorization"] = f"Bearer {hf_token}"

    # Try the chat/completions endpoint first (TGI-backed models)
    payload = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.7,
        "max_tokens": 1024,
    }).encode()

    url = f"https://router.huggingface.co/hf-inference/models/{model}/v1/chat/completions"
    req = urllib_request.Request(url, data=payload, headers=headers, method="POST")

    try:
        with urllib_request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read())
        text = data["choices"][0]["message"]["content"].strip()
        if not text:
            return {"error": f"Empty response from {model}"}
        return {"answer": text, "source": f"hf-{model.split('/')[-1]}"}
    except urllib_error.HTTPError as e:
        body = e.read().decode(errors="replace")[:500]
        logger.warning("HF %s HTTP %s: %s", model, e.code, body)

        # 503 = model loading, include estimated time
        if e.code == 503:
            return {"error": f"HF {model}: model loading (cold start)"}
        return {"error": f"HF {model} HTTP {e.code}: {body[:200]}"}
    except Exception as e:
        logger.warning("HF %s error: %s", model, e)
        return {"error": f"HF {model}: {str(e)}"}


def _try_all_hf_models(prompt: str) -> Dict:
    """Try each HuggingFace model in order until one works."""
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
    """Always true — HuggingFace works without any API key."""
    return True


async def ask_gemini(query: str, context: Optional[str] = None) -> Dict:
    """
    Try HuggingFace first (no key needed), then Gemini as fallback.
    Returns {"answer": str, "source": "hf-<model>"|"gemini"} on success,
    or {"error": str} if all providers fail.
    """
    prompt_parts = []
    if context:
        prompt_parts.append(f"Market context:\n{context}\n\n")
    prompt_parts.append(f"User query: {query}")
    full_prompt = "".join(prompt_parts)

    # 1) Try HuggingFace (no API key required, works for free)
    hf_result = _try_all_hf_models(full_prompt)
    if "answer" in hf_result:
        return hf_result
    hf_err = hf_result.get("error", "unknown")
    logger.info("HF failed, trying Gemini: %s", hf_err)

    # 2) Try Gemini (secondary, needs GEMINI_API_KEY)
    gemini_err = "not configured"
    if os.environ.get("GEMINI_API_KEY"):
        result = await _ask_gemini(full_prompt)
        if "answer" in result:
            return result
        gemini_err = result.get("error", "unknown")

    return {"error": f"HuggingFace: {hf_err} | Gemini: {gemini_err}"}
