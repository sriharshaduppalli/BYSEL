"""Standalone IndicTrans2 worker — English → Telugu.

Run this beside BYSEL, never inside Cloud Run `bysel-services`.
Default model: ai4bharat/indictrans2-en-indic-dist-200M (CPU).
"""
from __future__ import annotations

import logging
import os
from functools import lru_cache
from typing import List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

logger = logging.getLogger("indic_trans_worker")
logging.basicConfig(level=logging.INFO)

DEFAULT_MODEL = "ai4bharat/indictrans2-en-indic-dist-200M"
SRC_LANG = "eng_Latn"
TGT_LANG = "tel_Telu"

app = FastAPI(title="BYSEL IndicTrans2", docs_url=None, redoc_url=None)


class TranslateRequest(BaseModel):
    texts: List[str] = Field(default_factory=list, max_length=32)
    src_lang: str = SRC_LANG
    tgt_lang: str = TGT_LANG


class TranslateResponse(BaseModel):
    translations: List[str]
    model: str


@lru_cache(maxsize=1)
def _load_stack():
    model_id = os.getenv("INDIC_TRANS_MODEL", DEFAULT_MODEL)
    device = os.getenv("INDIC_TRANS_DEVICE", "cpu")
    try:
        import torch
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
        try:
            from IndicTransToolkit.processor import IndicProcessor
        except ImportError:
            from IndicTransToolkit import IndicProcessor
    except Exception as exc:
        raise RuntimeError(
            "IndicTrans2 extras missing. pip install -r indic_trans_worker/requirements.txt"
        ) from exc

    tokenizer = AutoTokenizer.from_pretrained(model_id, trust_remote_code=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(model_id, trust_remote_code=True)
    model.eval()
    if device == "cuda" and torch.cuda.is_available():
        model = model.to("cuda")
        used = "cuda"
    else:
        used = "cpu"
    processor = IndicProcessor(inference=True)
    logger.info("IndicTrans2 ready model=%s device=%s", model_id, used)
    return tokenizer, model, processor, model_id, used


@app.get("/health")
def health() -> dict:
    return {"ok": True, "model": os.getenv("INDIC_TRANS_MODEL", DEFAULT_MODEL)}


@app.post("/translate", response_model=TranslateResponse)
def translate(req: TranslateRequest) -> TranslateResponse:
    if req.tgt_lang != TGT_LANG or req.src_lang != SRC_LANG:
        raise HTTPException(status_code=400, detail="Only eng_Latn → tel_Telu is enabled")
    texts = [str(item or "").strip() for item in req.texts]
    if not texts:
        return TranslateResponse(translations=[], model=os.getenv("INDIC_TRANS_MODEL", DEFAULT_MODEL))
    try:
        tokenizer, model, processor, model_id, device = _load_stack()
        import torch

        batch = processor.preprocess_batch(texts, src_lang=SRC_LANG, tgt_lang=TGT_LANG)
        encoded = tokenizer(
            batch,
            truncation=True,
            padding="longest",
            return_tensors="pt",
            max_length=256,
        )
        if device == "cuda":
            encoded = {key: value.to("cuda") for key, value in encoded.items()}
        with torch.no_grad():
            generated = model.generate(
                **encoded,
                num_beams=5,
                max_length=256,
                early_stopping=True,
            )
        decoded = tokenizer.batch_decode(
            generated,
            skip_special_tokens=True,
            clean_up_tokenization_spaces=True,
        )
        translations = processor.postprocess_batch(decoded, lang=TGT_LANG)
        return TranslateResponse(translations=list(translations), model=model_id)
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("IndicTrans2 translate failed")
        raise HTTPException(status_code=503, detail=str(exc)[:200]) from exc
