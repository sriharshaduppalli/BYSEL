from __future__ import annotations

import re
import threading
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Any

from .acceptance import ProductionAcceptanceCriteria, SUPPORTED_QUERY_CATEGORIES
from .calculations import DeterministicCalculator, PandasTaIndicatorCalculator
from .config import AssistantConfig, default_config, runtime_config_from_env
from .data_layer import EnterpriseDataLayer
from .knowledge_base import (
    HeuristicReranker,
    HttpEmbeddingProvider,
    HttpReranker,
    InMemoryVectorIndex,
    KnowledgeBase,
    LocalHashEmbeddingProvider,
    MLReranker,
    SentenceTransformerEmbeddingProvider,
)
from .builtin_knowledge import builtin_knowledge_items
from .learning_loop import ContinualLearningManager, DailyFeedbackAnalyzer
from .model_serving import HttpModelBackend, ModelOrchestrator, TemplateModelBackend
from .prediction import PredictionEngine, PredictionSignals
from .safety import SafetyPolicy

INTENT_KEYWORDS = {
    "price_action": {"price", "target", "entry", "exit", "support", "resistance"},
    "prediction": {"predict", "prediction", "forecast", "tomorrow", "next", "week"},
    "fundamentals": {
        "pe", "pb", "valuation", "fundamental", "fundamentals", "profit", "profits",
        "balance", "sheet", "roe", "roce", "eps", "dividend",
    },
    "events_news": {"news", "event", "result", "results", "quarter", "guidance", "sebi", "regulation"},
    "portfolio": {"portfolio", "allocation", "risk", "diversification", "sip"},
    "stock_analysis": {"analyze", "analysis", "technical", "trend", "momentum", "checklist"},
    "market_calculations": {
        "calculate", "calculation", "cagr", "return", "volatility", "beta", "indicator",
        "indicators", "rsi", "sma", "ema", "macd", "bollinger", "formula", "equation",
        "atr", "vwap", "sharpe", "peg", "define", "explain", "meaning",
    },
}
MAX_CONFIDENCE = 0.95
BASE_CONTEXT_CONFIDENCE = 0.65
CONFIDENCE_PER_CONTEXT_ITEM = 0.1
_SPACY_PIPELINE = None


def _regex_tokens(query: str) -> set[str]:
    return {m.group(0) for m in re.finditer(r"[a-z0-9]+", query.lower())}


def _nlp_tokens(query: str, backend: str) -> set[str]:
    selected = (backend or "auto").strip().lower()
    if selected not in {"auto", "spacy", "nltk", "basic"}:
        selected = "auto"
    if selected in {"auto", "spacy"}:
        global _SPACY_PIPELINE
        try:
            if _SPACY_PIPELINE is None:
                import spacy  # type: ignore

                _SPACY_PIPELINE = spacy.blank("en")
            doc = _SPACY_PIPELINE(query.lower())
            return {
                (token.lemma_ or token.text).lower()
                for token in doc
                if not token.is_space and not token.is_punct and token.text.strip()
            }
        except Exception:
            if selected == "spacy":
                return _regex_tokens(query)
    if selected in {"auto", "nltk"}:
        try:
            from nltk.tokenize import TweetTokenizer  # type: ignore

            tokenizer = TweetTokenizer(strip_handles=True, reduce_len=True)
            return {token.lower() for token in tokenizer.tokenize(query) if re.fullmatch(r"[a-z0-9]+", token.lower())}
        except Exception:
            return _regex_tokens(query)
    return _regex_tokens(query)


@dataclass(frozen=True)
class AssistantResponse:
    intent: str
    answer: str
    confidence: float = 0.0
    citations: tuple[str, ...] = ()
    disclaimer: str = ""
    safe_for_trading_advice: bool = False
    policy_reason: str = ""
    category: str = "stocks"
    prediction_signals: dict | None = None
    diagnostics: dict | None = None


class StockMarketAssistant:
    """Production-hardened assistant scaffold for Indian stocks."""

    def __init__(self, config: AssistantConfig | None = None):
        self.config = config or runtime_config_from_env(default_config())
        self.criteria = ProductionAcceptanceCriteria(
            max_latency_ms=self.config.max_latency_ms,
            min_uptime=self.config.min_uptime,
            max_cost_per_query=self.config.max_cost_per_query,
            groundedness_min=self.config.groundedness_min,
        )
        if self.config.embedding_endpoint:
            embedding_provider = HttpEmbeddingProvider(
                endpoint=self.config.embedding_endpoint,
                api_key=self.config.embedding_api_key,
                provider=self.config.embedding_provider,
                model=self.config.embedding_model,
            )
        elif self.config.embedding_local_model:
            embedding_provider = SentenceTransformerEmbeddingProvider(self.config.embedding_local_model)
        else:
            embedding_provider = LocalHashEmbeddingProvider()

        if self.config.reranker_endpoint:
            reranker = HttpReranker(
                endpoint=self.config.reranker_endpoint,
                api_key=self.config.reranker_api_key,
                provider=self.config.reranker_provider,
                model=self.config.reranker_model,
            )
        elif self.config.reranker_local_model_path:
            reranker = MLReranker(self.config.reranker_local_model_path)
        else:
            reranker = HeuristicReranker()

        try:
            self.knowledge_base = KnowledgeBase.from_json(
                self.config.knowledge_base_path,
                embedding_provider=embedding_provider,
                vector_index=InMemoryVectorIndex(),
                reranker=reranker,
                extra_items=builtin_knowledge_items(),
            )
        except Exception as exc:
            raise ValueError(
                "Failed to load knowledge base. Check AssistantConfig.knowledge_base_path and retrieval config."
            ) from exc
        self.data_layer = EnterpriseDataLayer(self.config)
        if self.config.background_refresh_enabled:
            self.data_layer.start_background_refresh(self.config.background_refresh_interval_seconds)
        self.learning_manager = ContinualLearningManager(self.config.feedback_log_path, async_logging=True)
        self.feedback_analyzer = DailyFeedbackAnalyzer(self.config.feedback_log_path)
        self.safety_policy = SafetyPolicy(self.config.policy_audit_log_path)
        self.prediction_engine = PredictionEngine()
        primary_model = (
            HttpModelBackend(
                endpoint=self.config.model_endpoint,
                api_key=self.config.model_api_key,
                provider=self.config.model_provider,
                model=self.config.model_name,
                model_name=self.config.model_name or "remote-llm",
            )
            if self.config.model_endpoint
            else TemplateModelBackend()
        )
        self.model_orchestrator = ModelOrchestrator(
            primary=primary_model,
            fallback=TemplateModelBackend(),
            timeout_seconds=self.config.model_timeout_seconds,
        )
        self._nightly_refresh_stop: threading.Event | None = None
        if self.config.nightly_refresh_enabled:
            self._start_nightly_refresh(self.config.nightly_refresh_hour_utc)

    def _start_nightly_refresh(self, hour_utc: int) -> None:
        """Launch a daemon thread that calls trigger_index_refresh once per day at *hour_utc* UTC."""
        stop_event = threading.Event()
        self._nightly_refresh_stop = stop_event

        def _loop() -> None:
            while not stop_event.is_set():
                now = datetime.now(timezone.utc)
                next_run = now.replace(hour=hour_utc, minute=0, second=0, microsecond=0)
                if next_run <= now:
                    next_run = next_run + timedelta(days=1)
                wait_secs = (next_run - now).total_seconds()
                if stop_event.wait(wait_secs):
                    break
                try:
                    self.trigger_index_refresh()
                except Exception:
                    pass

        t = threading.Thread(target=_loop, daemon=True)
        t.start()

    def trigger_index_refresh(self) -> None:
        """Rebuild the knowledge-base embedding index in-place.

        Call this after adding new knowledge items or swapping the embedding provider.
        It is also invoked automatically by the nightly refresh daemon when
        ``nightly_refresh_enabled`` is ``True``.
        """
        self.knowledge_base.refresh_index()

    def classify_intent(self, query: str) -> str:
        tokens = _regex_tokens(query) | _nlp_tokens(query, self.config.nlp_backend)
        scores: dict[str, int] = {}
        for intent, keywords in INTENT_KEYWORDS.items():
            scores[intent] = len(tokens & keywords)
        best_intent, best_score = max(scores.items(), key=lambda pair: pair[1], default=("general_query", 0))
        if best_score == 0:
            return "general_query"
        top_scores = sorted(scores.values(), reverse=True)
        if len(top_scores) > 1 and top_scores[0] == top_scores[1]:
            if (
                "calculate" in tokens
                or "cagr" in tokens
                or "return" in tokens
                or "rsi" in tokens
                or "sma" in tokens
                or "ema" in tokens
                or "macd" in tokens
                or "bollinger" in tokens
            ):
                return "market_calculations"
            if "predict" in tokens or "forecast" in tokens:
                return "prediction"
            return "general_query"
        return best_intent

    def _extract_citations(self, context_items: list[Any]) -> tuple[str, ...]:
        return tuple(dict.fromkeys(item.source for item in context_items if getattr(item, "source", None)))

    def _extract_numbers(self, query: str) -> list[float]:
        return [float(m.group(0)) for m in re.finditer(r"-?\d+(?:\.\d+)?", query)]

    def _deterministic_calculation(self, query: str) -> str | None:
        q = query.lower()
        numbers = self._extract_numbers(query)
        try:
            if "cagr" in q and len(numbers) >= 3:
                start, end, years = numbers[0], numbers[1], numbers[2]
                cagr = DeterministicCalculator.cagr(start=start, end=end, years=years)
                return f"CAGR is {cagr:.2f}% (from start={start}, end={end}, years={years})."
            if "return" in q and len(numbers) >= 2:
                buy, sell = numbers[0], numbers[1]
                absolute_return = DeterministicCalculator.absolute_return(buy=buy, sell=sell)
                return f"Absolute return is {absolute_return:.2f}% (buy={buy}, sell={sell})."
        except ValueError:
            return None
        return None

    def _category_for_intent(self, intent: str) -> str:
        mapping = {
            "general_query": "stocks",
            "events_news": "nse_bse_sebi",
            "fundamentals": "analysis",
            "stock_analysis": "analysis",
            "market_calculations": "calculations",
            "prediction": "prediction_guidance",
            "portfolio": "analysis",
            "price_action": "analysis",
        }
        category = mapping.get(intent, "stocks")
        return category if category in SUPPORTED_QUERY_CATEGORIES else "stocks"

    def _policy_disclaimer(self, intent: str) -> str:
        if intent in {"prediction", "price_action"}:
            return (
                "This response is informational, not investment advice. "
                "Use risk controls and verify with live NSE/BSE data before trading."
            )
        return "This response is informational and should be validated against live market data."

    def ask(self, query: str) -> AssistantResponse:
        intent = self.classify_intent(query)
        category = self._category_for_intent(intent)
        factual_intents = {"fundamentals", "events_news", "market_calculations", "stock_analysis"}
        policy_decision = self.safety_policy.evaluate(query)
        if not policy_decision.allowed:
            return AssistantResponse(
                intent=intent,
                category=category,
                answer=(
                    "I can’t help with that request. It may violate safe-use or compliance rules. "
                    "Please ask for a risk-aware market explanation instead."
                ),
                confidence=0.0,
                citations=(),
                disclaimer=self._policy_disclaimer(intent),
                safe_for_trading_advice=False,
                policy_reason=policy_decision.reason,
            )
        self.learning_manager.record_feedback(query=query, intent=intent)
        self.learning_manager.record_anonymized_feedback(query=query, intent=intent)

        # Prefer deterministic education pack for definitions / equations.
        education = None
        try:
            from app.market_education import get_education_answer

            education = get_education_answer(query)
        except Exception:
            education = None
        if education:
            return AssistantResponse(
                intent="market_calculations",
                category="calculations",
                answer=education,
                confidence=0.92,
                citations=("bysel_market_education",),
                disclaimer=self._policy_disclaimer("market_calculations"),
                safe_for_trading_advice=False,
                policy_reason=policy_decision.reason,
            )

        readiness = self.data_layer.readiness_report()
        # Local JSON enterprise feeds are often marked partial/stale — do not block
        # educational/analysis answers when the knowledge pack is available.
        if (
            self.config.require_ready_data_for_factual
            and intent in factual_intents
            and not readiness.ready
            and not self.knowledge_base.items
        ):
            return AssistantResponse(
                intent=intent,
                category=category,
                answer=(
                    "Data readiness gate blocked this response for factual safety. "
                    f"Blockers: {', '.join(readiness.blockers) or 'unknown'}."
                ),
                confidence=0.0,
                citations=(),
                disclaimer=self._policy_disclaimer(intent),
                safe_for_trading_advice=False,
                policy_reason="Data readiness gate blocked factual response",
            )
        resolved_entity = self.data_layer.resolve_entity(query)
        # Soft retrieval — do not hard-filter by tag (that emptied results before).
        context_items = self.knowledge_base.search(
            query,
            top_k=self.config.top_k_context,
            min_score=self.config.min_retrieval_score,
            metadata_filters=None,
            intent=intent,
        )

        # Inject resolved instrument facts into grounding context.
        if resolved_entity:
            from .knowledge_base import KnowledgeItem as _KI

            entity_blurb = (
                f"Matched instrument: {resolved_entity.get('symbol')} — "
                f"{resolved_entity.get('company_name', '')} "
                f"(exchange={resolved_entity.get('exchange', 'NSE')}, "
                f"ISIN={resolved_entity.get('isin', 'n/a')})."
            )
            context_items = [
                _KI(
                    id=f"entity_{resolved_entity.get('symbol', 'x')}",
                    title=f"Instrument {resolved_entity.get('symbol')}",
                    content=entity_blurb,
                    tags=["symbols", "stocks"],
                    source="instrument_master",
                ),
                *context_items,
            ][: self.config.top_k_context + 1]

        citations = self._extract_citations(context_items)
        deterministic_note = ""
        if intent == "market_calculations":
            note_lines: list[str] = []
            deterministic = self._deterministic_calculation(query)
            if deterministic:
                note_lines.append(f"Deterministic calculation: {deterministic}")
            elif "cagr" in query.lower() or "return" in query.lower():
                note_lines.append(
                    "Deterministic calculation unavailable: provide valid positive numeric inputs "
                    "(for CAGR: start, end, years; for return: buy, sell)."
                )
            indicator_note = PandasTaIndicatorCalculator.indicator_note(query)
            if indicator_note:
                note_lines.append(f"Indicator calculation: {indicator_note}")
            elif PandasTaIndicatorCalculator.indicator_requested(query):
                note_lines.append(
                    "Indicator calculation unavailable: provide an indicator query with explicit price series."
                )
            deterministic_note = "\n".join(note_lines)

        if context_items:
            context_text = "\n".join(
                f"- **{getattr(item, 'title', 'Insight')}**: {item.content}" for item in context_items
            )
            readiness_note = (
                f"refreshed_at={self.data_layer.snapshot.refreshed_at}; "
                f"stale={self.data_layer.snapshot.stale_feeds or ('none',)}; "
                f"partial={self.data_layer.snapshot.partial_feeds or ('none',)}; "
                f"entity={resolved_entity if resolved_entity else 'None'}; "
                f"kb_items={len(self.knowledge_base.items)}"
            )
            prompt = self.model_orchestrator.compose_prompt(
                query=query,
                intent=intent,
                category=category,
                context_text=context_text,
                citations=citations,
                deterministic_note=deterministic_note,
                policy_disclaimer=self._policy_disclaimer(intent),
                readiness_note=readiness_note,
            )
            generated = self.model_orchestrator.generate(
                prompt,
                require_citations=False,
                citations=citations,
            )
            prediction_note = (
                "Prediction factors considered: live news sentiment, sector momentum, corporate events, "
                "macro-rate signals, and liquidity conditions.\n"
                if intent == "prediction"
                else ""
            )
            prediction_signals: dict | None = None
            if intent == "prediction":
                signals = self.prediction_engine.predict(
                    context_items=context_items,
                    deterministic_note=deterministic_note,
                    resolved_entity=resolved_entity,
                )
                prediction_signals = {
                    "intraday": {
                        "direction": signals.intraday.direction,
                        "probability": signals.intraday.probability,
                        "rationale": signals.intraday.rationale,
                    },
                    "swing": {
                        "direction": signals.swing.direction,
                        "probability": signals.swing.probability,
                        "rationale": signals.swing.rationale,
                    },
                    "medium_term": {
                        "direction": signals.medium_term.direction,
                        "probability": signals.medium_term.probability,
                        "rationale": signals.medium_term.rationale,
                    },
                    "key_signals": list(signals.key_signals),
                    "overall_confidence": signals.overall_confidence,
                }
            answer_parts = [generated.answer.strip()]
            if deterministic_note:
                answer_parts.append(deterministic_note)
            if prediction_note.strip():
                answer_parts.append(prediction_note.strip())
            answer_parts.append(
                "⚠️ Disclaimer: Educational / informational only — validate with live NSE/BSE data before decisions."
            )
            answer = "\n\n".join(part for part in answer_parts if part)

            diagnostics = {
                "intent": intent,
                "category": category,
                "latency_mode": generated.latency_mode,
                "model_backend": generated.model_name,
                "data_refresh_timestamp": str(self.data_layer.snapshot.refreshed_at),
                "data_lineage_verified": self.data_layer.validate_snapshot(),
                "stale_feeds": list(self.data_layer.snapshot.stale_feeds) if self.data_layer.snapshot.stale_feeds else [],
                "partial_feeds": list(self.data_layer.snapshot.partial_feeds) if self.data_layer.snapshot.partial_feeds else [],
                "resolved_entity": resolved_entity if resolved_entity else "None",
                "prediction_note": prediction_note.strip() if prediction_note else None,
                "deterministic_note": deterministic_note if deterministic_note else None,
                "kb_size": len(self.knowledge_base.items),
            }

            confidence = min(MAX_CONFIDENCE, BASE_CONTEXT_CONFIDENCE + CONFIDENCE_PER_CONTEXT_ITEM * len(context_items))
        else:
            answer = (
                "I could not find enough domain context in the local Indian-market knowledge pack. "
                "Try asking about an indicator (RSI, MACD, P/E), a sector (banking, IT, pharma), "
                "or a specific NSE symbol."
            )
            confidence = 0.25
            prediction_signals = None
            diagnostics = {
                "intent": intent,
                "category": category,
                "latency_mode": self.config.latency_mode,
                "kb_size": len(self.knowledge_base.items),
                "model_backend": "fallback_no_context",
                "resolved_entity": resolved_entity if resolved_entity else "None",
                "error_reason": "insufficient_domain_context",
            }

        # Soft grounding — don't zero out confident KB answers just for missing citation tags.
        if intent in factual_intents and not citations and not resolved_entity and confidence < 0.4:
            answer = (
                "I need a clearer stock symbol or topic (for example RSI, P/E, banking sector, or RELIANCE) "
                "to give a grounded Indian-market answer."
            )
            confidence = min(confidence, 0.3)
            if diagnostics:
                diagnostics["error_reason"] = "insufficient_citations_for_factual_intent"

        if confidence < self.config.min_confidence_threshold and not context_items and intent in factual_intents:
            answer = (
                f"Low-confidence response ({confidence:.2f}) withheld for safety. "
                "Please refine the question with a symbol, indicator, or sector."
            )
            citations = ()
            if diagnostics:
                diagnostics["error_reason"] = "low_confidence_safety_threshold"

        return AssistantResponse(
            intent=intent,
            answer=answer,
            confidence=confidence,
            citations=citations,
            disclaimer=self._policy_disclaimer(intent),
            safe_for_trading_advice=False,
            policy_reason=policy_decision.reason,
            category=category,
            prediction_signals=prediction_signals,
            diagnostics=diagnostics,
        )

    def query(self, query: str) -> dict[str, Any]:
        response = self.ask(query)
        feedback_metrics = self.learning_manager.feedback_metrics()
        safety_metrics = self.safety_policy.audit_summary()
        return {
            "intent": response.intent,
            "answer": response.answer,
            "confidence": response.confidence,
            "citations": list(response.citations),
            "disclaimer": response.disclaimer,
            "safe_for_trading_advice": response.safe_for_trading_advice,
            "policy_reason": response.policy_reason,
            "category": response.category,
            "prediction_signals": response.prediction_signals,
            "diagnostics": response.diagnostics,
            "acceptance": {
                "accuracy_min": self.criteria.accuracy_min,
                "groundedness_min": self.criteria.groundedness_min,
                "max_latency_ms": self.criteria.max_latency_ms,
                "min_uptime": self.criteria.min_uptime,
                "max_cost_per_query": self.criteria.max_cost_per_query,
                "safety_compliance_min": self.criteria.safety_compliance_min,
            },
            "monitoring": {
                **feedback_metrics,
                **safety_metrics,
            },
            "data_integrity": {
                "stale_feeds": list(self.data_layer.snapshot.stale_feeds),
                "partial_feeds": list(self.data_layer.snapshot.partial_feeds),
                "connector_status": self.data_layer.snapshot.connector_status,
                "background_refresh_errors": list(self.data_layer.background_refresh_errors()),
            },
            "contract": {
                "version": self.config.api_contract_version,
                "target_use_cases": ["grounded_qna", "risk_aware_guidance"],
                "prohibited_use_cases": ["trade_execution", "guaranteed_return_advice"],
            },
        }
