"""BYSEL Backend API"""

import os
import time
from collections import deque
from threading import Lock
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
import logging
from .database.db import OrderModel, SessionLocal
from .routes import router
from .routes.auth import router as auth_router
from .routes.streaming import get_stream_metrics_snapshot, router as streaming_router
from .routes.ai_v2 import router as ai_v2_router

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
    raw_origins = os.getenv("BYSEL_ALLOWED_ORIGINS", "").strip()
    if raw_origins:
        origins = [origin.strip() for origin in raw_origins.split(",") if origin.strip()]
        if origins:
            return origins

    # Safe local defaults for Android emulator and local web clients.
    return [
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://10.0.2.2:3000",
        "http://10.0.2.2:5173",
    ]


allowed_origins = _resolve_allowed_origins()
allow_all_origins = len(allowed_origins) == 1 and allowed_origins[0] == "*"
allow_credentials = not allow_all_origins

if allow_all_origins:
    logger.warning("BYSEL_ALLOWED_ORIGINS is set to '*'; credentialed cross-origin requests are disabled")

app = FastAPI(
    title="BYSEL Backend API",
    description="Trading backend for BYSEL",
    version="1.0.0"
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

    if duration_ms >= SLOW_REQUEST_THRESHOLD_MS:
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


@app.on_event("startup")
async def startup_event():
    logger.info("BYSEL Backend starting up...")

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("BYSEL Backend shutting down...")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
