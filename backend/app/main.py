"""BYSEL Backend API"""

import os
import time

from .ism_bootstrap import ensure_ism_on_path

ensure_ism_on_path()
from collections import deque
from threading import Lock
from uuid import uuid4

from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import logging
from .database.db import OrderModel, SessionLocal
from .routes import router
from .routes.auth import router as auth_router
from .routes.streaming import get_stream_metrics_snapshot, router as streaming_router
from .routes.ai_v2 import router as ai_v2_router
from .routes.trade_journal import journal_router
from .routes.stock_notes import router as stock_notes_router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

TRACE_HEADER = "X-Trace-Id"
PROCESS_TIME_HEADER = "X-Process-Time-Ms"
SLOW_REQUEST_THRESHOLD_MS = 1200.0
HTTP_METRICS_WINDOW = int(os.getenv("HTTP_METRICS_WINDOW", "2000"))

_HTTP_METRICS_LOCK = Lock()
_HTTP_LATENCY_MS: deque[float] = deque(maxlen=HTTP_METRICS_WINDOW)
_HTTP_ORDER_LATENCY_MS: deque[float] = deque(maxlen=HTTP_METRICS_WINDOW)
_HTTP_COUNTERS: dict[str, int] = {
    "total": 0,
    "errors": 0,
    "server_errors": 0,
    "order_requests": 0,
    "order_errors": 0,
}


def _is_order_execution_path(path: str, method: str) -> bool:
    if method.upper() != "POST":
        return False
    if path in {"/order", "/trade/buy", "/trade/sell", "/orders/advanced"}:
        return True
    return path.startswith("/orders/baskets/") and path.endswith("/execute")


def _safe_rate(numerator: int, denominator: int) -> float:
    if denominator <= 0:
        return 0.0
    return round((float(numerator) / float(denominator)) * 100.0, 2)


def _percentile(samples: list[float], percentile: float) -> float | None:
    if not samples:
        return None
    if len(samples) == 1:
        return round(float(samples[0]), 2)

    ordered = sorted(float(item) for item in samples)
    rank = (len(ordered) - 1) * (percentile / 100.0)
    lower_index = int(rank)
    upper_index = min(lower_index + 1, len(ordered) - 1)
    fraction = rank - lower_index
    value = ordered[lower_index] + (ordered[upper_index] - ordered[lower_index]) * fraction
    return round(value, 2)


def _latency_stats(samples: list[float]) -> dict[str, float | None]:
    return {
        "p50": _percentile(samples, 50.0),
        "p95": _percentile(samples, 95.0),
        "p99": _percentile(samples, 99.0),
    }


def _record_http_metrics(path: str, method: str, status_code: int, duration_ms: float) -> None:
    with _HTTP_METRICS_LOCK:
        _HTTP_COUNTERS["total"] += 1
        if status_code >= 400:
            _HTTP_COUNTERS["errors"] += 1
        if status_code >= 500:
            _HTTP_COUNTERS["server_errors"] += 1

        _HTTP_LATENCY_MS.append(float(duration_ms))

        if _is_order_execution_path(path, method):
            _HTTP_COUNTERS["order_requests"] += 1
            _HTTP_ORDER_LATENCY_MS.append(float(duration_ms))
            if status_code >= 400:
                _HTTP_COUNTERS["order_errors"] += 1


def _http_metrics_snapshot() -> dict:
    with _HTTP_METRICS_LOCK:
        counters = dict(_HTTP_COUNTERS)
        http_latencies = list(_HTTP_LATENCY_MS)
        order_latencies = list(_HTTP_ORDER_LATENCY_MS)

    return {
        "counters": counters,
        "httpLatencyMs": _latency_stats(http_latencies),
        "orderLatencyMs": _latency_stats(order_latencies),
        "httpSamples": len(http_latencies),
        "orderSamples": len(order_latencies),
    }


def _order_outcome_snapshot() -> dict[str, int | float]:
    db = SessionLocal()
    try:
        total = int(db.query(OrderModel).count())
        completed = int(
            db.query(OrderModel)
            .filter(OrderModel.status.in_(["COMPLETED", "TRIGGER_EXECUTED"]))
            .count()
        )
        rejected = int(db.query(OrderModel).filter(OrderModel.status == "REJECTED").count())
        pending = int(db.query(OrderModel).filter(OrderModel.status == "PENDING").count())
        cancelled = int(db.query(OrderModel).filter(OrderModel.status == "CANCELLED").count())
    finally:
        db.close()

    return {
        "total": total,
        "completed": completed,
        "rejected": rejected,
        "pending": pending,
        "cancelled": cancelled,
        "successRatePct": _safe_rate(completed, total),
    }


def _resolve_allowed_origins() -> list[str]:
    # Marketing site + local web clients — always merged unless origins are "*".
    marketing_and_local = [
        "https://byseltrader.com",
        "https://www.byseltrader.com",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://10.0.2.2:3000",
        "http://10.0.2.2:5173",
    ]

    raw_origins = os.getenv("BYSEL_ALLOWED_ORIGINS", "").strip()
    if raw_origins:
        origins = [origin.strip() for origin in raw_origins.split(",") if origin.strip()]
        if origins:
            if len(origins) == 1 and origins[0] == "*":
                return origins
            merged: list[str] = []
            for origin in [*origins, *marketing_and_local]:
                if origin and origin not in merged:
                    merged.append(origin)
            return merged

    return marketing_and_local


allowed_origins = _resolve_allowed_origins()
allow_all_origins = len(allowed_origins) == 1 and allowed_origins[0] == "*"
allow_credentials = not allow_all_origins

if allow_all_origins:
    logger.warning("BYSEL_ALLOWED_ORIGINS is set to '*'; credentialed cross-origin requests are disabled")

@asynccontextmanager
async def _lifespan(_app: FastAPI):
    logger.info("BYSEL Backend starting up...")
    try:
        from .llm_integration import _LLM_DATA, _LLM_PKG
        logger.info("LLM pkg: %s (exists=%s)", _LLM_PKG, _LLM_PKG.exists())
        logger.info("LLM data: %s (exists=%s)", _LLM_DATA, _LLM_DATA.exists())
    except Exception as e:
        logger.error("LLM startup check failed: %s", e)
    try:
        from .routes import _kick_background_warmup
        _kick_background_warmup(force=True)
    except Exception as e:
        logger.warning("Startup quote warmup skipped: %s", e)
    yield
    logger.info("BYSEL Backend shutting down...")


app = FastAPI(
    title="BYSEL Backend API",
    description="Trading backend for BYSEL",
    version="1.0.0",
    lifespan=_lifespan,
)


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    # Provider / proxy 429s must not surface as a dead AI chat bubble.
    if exc.status_code == 429 and request.url.path.startswith("/ai"):
        logger.warning("ai.rate_limited path=%s detail=%s", request.url.path, exc.detail)
        return JSONResponse(
            status_code=200,
            content={
                "answer": (
                    "The AI provider is rate-limited right now. "
                    "Wait a few seconds and try again — quotes, education, and rule-based answers still work."
                ),
                "source": "rate-limit",
                "tier_requested": "auto",
            },
        )
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail},
        headers=dict(exc.headers or {}),
    )


@app.middleware("http")
async def trace_context_middleware(request: Request, call_next):
    trace_id = (request.headers.get(TRACE_HEADER) or f"trc-{uuid4().hex[:16]}").strip()
    request.state.trace_id = trace_id
    started_at = time.perf_counter()

    try:
        response = await call_next(request)
    except Exception:
        logger.exception("Unhandled request error trace_id=%s path=%s", trace_id, request.url.path)
        raise

    duration_ms = (time.perf_counter() - started_at) * 1000.0
    response.headers[TRACE_HEADER] = trace_id
    response.headers[PROCESS_TIME_HEADER] = f"{duration_ms:.1f}"

    if response.status_code >= 500:
        logger.error(
            "http.5xx trace_id=%s method=%s path=%s status=%s duration_ms=%.1f",
            trace_id,
            request.method,
            request.url.path,
            response.status_code,
            duration_ms,
        )
    elif duration_ms >= SLOW_REQUEST_THRESHOLD_MS:
        logger.warning(
            "Slow request trace_id=%s method=%s path=%s status=%s duration_ms=%.1f",
            trace_id,
            request.method,
            request.url.path,
            response.status_code,
            duration_ms,
        )

    _record_http_metrics(
        path=request.url.path,
        method=request.method,
        status_code=response.status_code,
        duration_ms=duration_ms,
    )

    return response

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=allow_credentials,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Include main and auth routes
app.include_router(router)
app.include_router(auth_router, prefix="/auth")
app.include_router(streaming_router)
app.include_router(ai_v2_router)  # Enhanced AI analysis endpoints
app.include_router(journal_router)  # AI Trade Journal
app.include_router(stock_notes_router)

# Public legal pages for Play Console / in-app "About" links.
# Files live in backend/static/legal/{privacy,terms,licenses}.html
from pathlib import Path
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

_STATIC_DIR = Path(__file__).resolve().parent.parent / "static"
_LEGAL_DIR = _STATIC_DIR / "legal"


@app.get("/legal/{doc_name}")
async def legal_document(doc_name: str):
    allowed = {"privacy": "privacy.html", "terms": "terms.html", "licenses": "licenses.html"}
    filename = allowed.get(doc_name.lower().strip())
    if not filename:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Not found")
    path = _LEGAL_DIR / filename
    if not path.is_file():
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Legal document missing on server")
    return FileResponse(path, media_type="text/html; charset=utf-8")


if _STATIC_DIR.is_dir():
    app.mount("/static", StaticFiles(directory=str(_STATIC_DIR)), name="static")


@app.get("/metrics/slo")
async def slo_metrics_endpoint() -> dict:
    http_snapshot = _http_metrics_snapshot()
    order_outcomes = _order_outcome_snapshot()
    stream_snapshot = get_stream_metrics_snapshot()

    counters = http_snapshot["counters"]
    stream_messages = int(stream_snapshot.get("quotes_messages_sent", 0) or 0)
    stream_send_errors = int(stream_snapshot.get("send_errors", 0) or 0)
    stream_total = stream_messages + stream_send_errors

    return {
        "status": "ok",
        "generatedAtMs": int(time.time() * 1000),
        "slo": {
            "http": {
                "totalRequests": int(counters["total"]),
                "errorRatePct": _safe_rate(int(counters["errors"]), int(counters["total"])),
                "serverErrorRatePct": _safe_rate(int(counters["server_errors"]), int(counters["total"])),
                "latencyMs": http_snapshot["httpLatencyMs"],
                "windowSize": int(http_snapshot["httpSamples"]),
            },
            "orderRequests": {
                "totalRequests": int(counters["order_requests"]),
                "errorRatePct": _safe_rate(int(counters["order_errors"]), int(counters["order_requests"])),
                "latencyMs": http_snapshot["orderLatencyMs"],
                "windowSize": int(http_snapshot["orderSamples"]),
            },
            "orderOutcomes": order_outcomes,
            "quotesStream": {
                "messagesSent": stream_messages,
                "sendErrors": stream_send_errors,
                "errorRatePct": _safe_rate(stream_send_errors, stream_total),
                "rowsSent": int(stream_snapshot.get("quotes_rows_sent", 0) or 0),
                "activeConnections": int(stream_snapshot.get("active_connections", 0) or 0),
                "subscriptionsUpdated": int(stream_snapshot.get("subscriptions_updated", 0) or 0),
                "resumeEventsSent": int(stream_snapshot.get("resume_events_sent", 0) or 0),
                "lastSequenceSent": int(stream_snapshot.get("last_sequence_sent", 0) or 0),
            },
            "targets": {
                "crashFreeSessionsMinPct": 99.8,
                "orderSuccessRateMinPct": 99.5,
                "quoteLatencyP95MaxMs": 300,
            },
        },
    }


@app.get("/ai/enricher-test/{symbol}")
async def enricher_test(symbol: str):
    import traceback, asyncio
    error = None
    raw = {}
    try:
        from .stock_enricher import _fetch_yfinance
        loop = asyncio.get_event_loop()
        raw = await loop.run_in_executor(None, _fetch_yfinance, symbol.upper())
    except Exception:
        error = traceback.format_exc()
    return {"ok": bool(raw), "symbol": symbol.upper(), "data": raw, "error": error}

@app.get("/ai/llm-status")
def llm_status():
    import traceback
    from .llm_integration import _LLM_DATA
    error = None
    available = False
    try:
        from .llm_integration import llm_available
        available = llm_available()
    except Exception as e:
        error = traceback.format_exc()
    return {
        "llm_data_path": str(_LLM_DATA),
        "llm_data_exists": _LLM_DATA.exists(),
        "llm_available": available,
        "error": error,
    }

@app.get("/ai/groq-status")
def groq_status():
    import traceback, os
    error = None
    available = False
    key_set = bool(os.environ.get("GROQ_API_KEY"))
    try:
        from .groq_llm import groq_available
        available = groq_available()
    except Exception:
        error = traceback.format_exc()
    return {
        "groq_key_set": key_set,
        "groq_available": available,
        "error": error,
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
