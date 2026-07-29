# BYSEL Indian-Market LLM — Architecture Plan

Status: living plan. Last updated 2026-07-29 after knowledge-pack v2
(Hinglish jargon, F&O/tax/corporate-actions literacy, paper-practice coaching)
and analysis-library integration (`ta` / optional `pandas-ta`, `nsepython`,
live OHLCV indicators for RSI/MACD/ATR/etc.).

## What already exists (do not rebuild)

| Layer | What it is today | Role to keep |
|-------|------------------|--------------|
| Android on-device | MediaPipe Gemma 2B (`gemma_2b_it_cpu_int4.bin`, ~1.4 GB) | Offline fallback |
| Server chat | `POST /ai/ask` → Groq → Gemini → **Indian Stock LLM knowledge pack** → rule engine | Primary online chat |
| Education pack | `app/market_education.py` equations/glossary (RSI, MACD, P/E, F&O, circuits, …) | Highest-precision term/formula answers |
| Local ISM tier | `indian_stock_llm/` + `builtin_knowledge.py` RAG + template composer | Grounded offline/no-key tier |
| Instrument master | Synced from `INDIAN_STOCKS` / NSE catalog on load | Symbol/company grounding |
| Enhanced analysis | `POST /api/ai/v2/analyze-with-explanation` | Structured confidence / sentiment cards |
| Rule engine | `ai_engine.py` | Deterministic safety net + pre-trade checks |

## Production knowledge pack (shipped 2026-07-28)

- Expanded glossary/equations in `market_education.py`
- Built-in RAG corpus: equations, NSE/BSE/SEBI mechanics, F&O/Greeks, FII/DII,
  sector primers, analysis checklists, symbol literacy
- Readiness gate no longer blocks local KB answers
- Soft retrieval (no hard tag filter that emptied results)
- `/ai/ask` uses `normalized_query` + optional symbol/price context
- Instrument master auto-synced for entity grounding

Honest label: this is a **production-ready grounded Indian-market knowledge
assistant**, not a GPU-finetuned neural LLM. Groq remains the generative primary
when available; ISM tier is the reliable domain fallback.

## Shipped — knowledge pack v2 + paper-practice coaching (2026-07-29)

- `bysel_builtin_v2` RAG items: Hinglish retail jargon (demat, circuit, T+1, STT,
  delivery vs intraday), F&O lot/expiry/margin risk, STCG/LTCG educational basics,
  corporate-actions literacy, paper journaling / no-chase coaching, portfolio
  practice stance, and an explicit SEBI educational disclaimer item
- Intent keywords + light Hinglish normalizer before classify (`kharid`/`bech`/…)
- Groq `_BASE_SYSTEM_PROMPT` PAPER PRACTICE section (simulation-first, process over
  tip certainty, never claim SEBI RA status)
- Android AI empty-state / suggestion chips: practice-oriented prompts
- `/ai/recommendations`: left as-is (5-min cache already present; no risky refactor)

## Shipped — closed-loop RAG learning (2026-07-29)

Honest status (not LoRA auto-train):

- **Semantic MiniLM** if `sentence-transformers` is installed (else hash-embedding
  fallback). Opt-in via `ISM_EMBEDDING_LOCAL_MODEL` / production auto-detect in
  `llm_integration.py`.
- **Feedback → learned KB**: `FeedbackLearningPipeline` promotes frequent TSV
  queries and high-confidence grounded answers into `learned_knowledge.json`
  (educational coaching / truncated answers). Improves retrieval over time;
  it does **not** fine-tune neural LLM weights.
- **Embedding cache** on disk (`embedding_cache.json`) keyed by item id.
- **Live quote grounding** when an instrument resolves (`live_quote_v1` via
  `fetch_quote` / yfinance).
- Nightly `trigger_index_refresh` can promote feedback + rebuild the index.


## Shipped reliability (Phase −1) — done 2026-07-27

These were blocking “first reply always fails / feels broken”:

- Dedicated AI OkHttp client with **90s** timeouts (market APIs stay at 25s)
- Background `/health` warmup on app start to reduce Render cold-start pain
- Chat always answers via `/ai/ask` first; enhanced v2 cards enrich in background
- Real timeout / offline error copy instead of generic “couldn't process that”
- Loading hint after 5s: “Waking AI server…”

Remaining reliability debt: Render **free tier still sleeps**; paid hosting is
still Phase 0.

## The gap a custom LLM should fill

Groq Llama is a general model. It is only “Indian-market aware” because the
prompt and the yfinance enricher shove context into it. That means:

1. Weak recall of NSE/BSE listing quirks, SEBI circulars, circuit limits, F&O
   settlement, GST/STT charge structures.
2. No durable memory of BYSEL-specific trade journal patterns.
3. Hash-embedding RAG recovers keyword-ish snippets, not semantic ones.
4. On-device Gemma still runs **before** the server when downloaded — so a
   custom server model alone will not improve that offline path until Phase 3.

## Recommended architecture (phased)

### Phase 0 — Hosting that can breathe (1–2 days)

Render free plan + ephemeral disk cannot host weights and will keep cold-starting.
Move production to a paid Render instance **or** Cloud Run with a volume, and add
`sentence-transformers` + a small embedding model to `requirements.txt`.
Without this, Phases 1–3 are local-only demos.

Also fix `/ai/recommendations` (currently 500s / sync yfinance over ~15 stocks)
before wiring it into the UI.

### Phase 1 — Real RAG before any fine-tune (1 week) — **highest ROI**

Replace `LocalHashEmbeddingProvider` with a real sentence-transformer
(`BAAI/bge-small-en-v1.5` or `intfloat/multilingual-e5-small`). Persist the
vector index to disk (Chroma / FAISS file) so cold starts do not re-encode.

Expand the corpus under `backend/llm_data/`:

- SEBI circulars & RA guidelines (public PDFs → chunked Markdown)
- NSE/BSE holiday calendar + circuit-limit rules
- Charge schedule (STT, GST, brokerage) matching the app’s pre-trade estimate
- Sector primers (Banking, IT, Pharma, Auto, Energy)
- Curated Q&A from the existing rule-engine FAQ handlers

Keep Groq Llama as the generator. Measuring answer quality with a 50-question
Indian-market eval set before/after RAG will tell us if fine-tuning is even
needed.

### Phase 1b — Adjacent engine wins (from `AI_ENGINE_OVERVIEW.md`)

Can run in parallel with Phase 1; not a custom LLM, but improves answer quality:

- Cache / async the recommendations endpoint
- Earnings-date awareness in predictions
- Expose backtest / model-accuracy stats in Copilot / Signal Lab
- Batch watchlist analysis (today one symbol at a time)

### Phase 2 — Specialised generator (2–3 weeks) only if Phase 1 plateaus

Two viable paths; pick one after the eval:

**A. LoRA on a small open model (preferred for cost)**  
- Base: `Qwen2.5-3B-Instruct` or `Llama-3.2-3B-Instruct`
- Data: 5–10k instruction pairs built by `QAPairBuilder` from (a) RAG chunks,
  (b) historical `/ai/ask` logs (redact PII), (c) synthetic Q&A from
  `analyze_stock` outputs
- Serve via the existing `ISM_MODEL_ENDPOINT` HTTP backend so
  `TemplateModelBackend` is swapped for a real model without rewriting routes
- Insert into the `/ai/ask` chain **before** the rule-engine, **after** Groq
  fails / for `tier=indian-stock-llm`

**B. Distil / prompt-cache a hosted model**  
Keep Groq, but pin a longer system prompt + retrieved docs via prompt caching
if Groq adds it. Cheaper, less differentiation.

### Phase 3 — Align on-device with the same knowledge (optional, later)

MediaPipe Gemma cannot load LoRA adapters today. Options:

- Ship a distilled **Gemma 2B QLoRA** converted to MediaPipe `.bin` (heavy;
  re-download for users)
- Or keep on-device as a thin generalist and always prefer server when online
  (change `askAi` priority so on-device is last resort, not first)

**Quick win (half day):** flip priority now so online users always hit Groq /
RAG first; on-device only when offline or server fails.

### Phase 4 — Compliance wrapper (parallel, non-negotiable)

Every model answer already ends with a disclaimer in the Groq prompt. Harden it:

- Refuse guaranteed-return language (already partially in `SafetyPolicy`)
- Tag every answer with `source` (`groq` / `indian-stock-llm` / `rule-engine` /
  `on-device`) so the UI badge is always accurate
- Log prompts + answers for SEBI RA audit readiness (today `policy_audit.log`
  is configured but optional)

## What NOT to do

- Do not delete the rule engine — it is the only deterministic pre-trade gate.
- Do not wire `/ai/recommendations` into the UI until it is async / cached.
- Do not train on live user chat without consent + redaction.
- Do not attempt to host 7B+ weights on Render free.
- Do not start LoRA fine-tuning before a Phase 1 eval proves RAG is insufficient.

## Suggested order of work

| Priority | Item | Effort | Impact |
|----------|------|--------|--------|
| Done | Chat timeout / warmup / `/ai/ask`-first | — | First reply no longer falsely fails |
| Next | Phase 0 paid hosting (or at least always-on) | 1–2 days | Consistent latency |
| Next | Phase 3 quick win: server-first when online | 0.5 day | Better answers when Gemma is installed |
| Next | Phase 1 real RAG + corpus | ~1 week | Biggest quality jump without training |
| Parallel | Fix `/ai/recommendations` + cache | 1–2 days | Unlocks a real recs surface |
| Later | Phase 2 LoRA only if eval plateaus | 2–3 weeks | True custom Indian-market model |
| Always | Phase 4 compliance logging | ongoing | SEBI / Play readiness |

## First concrete milestone (when you say go)

1. Flip on-device priority (server-first when online).
2. Add `sentence-transformers` + FAISS/Chroma to backend deps (needs paid host).
3. Rebuild `llm_data/` with SEBI + market-mechanics docs.
4. Swap hash embeddings → real embeddings; persist index.
5. Build a 50-question eval harness against `/ai/ask`.
6. Only then decide whether LoRA fine-tuning is justified.

Expected outcome of Phase 1 alone: materially better Indian-market answers
with **no** new model training cost, while Groq remains the generator.
