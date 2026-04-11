import asyncio
import json
import logging
import os
import time
from collections import deque
from threading import Lock
from uuid import uuid4

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..market_data import fetch_quotes, get_default_symbols

router = APIRouter()
logger = logging.getLogger(__name__)
TRACE_HEADER = "X-Trace-Id"

STREAM_PUSH_INTERVAL_MS = int(os.getenv("STREAM_PUSH_INTERVAL_MS", "1200"))
STREAM_MAX_SYMBOLS = int(os.getenv("STREAM_MAX_SYMBOLS", "30"))
STREAM_RESUME_BUFFER_SIZE = int(os.getenv("STREAM_RESUME_BUFFER_SIZE", "180"))

_stream_lock = Lock()
_stream_sequence = 0
_stream_history: deque[dict] = deque(maxlen=STREAM_RESUME_BUFFER_SIZE)
_stream_metrics: dict[str, int | str | float | None] = {
    "active_connections": 0,
    "total_connections": 0,
    "total_disconnects": 0,
    "last_trace_id": None,
    "quotes_messages_sent": 0,
    "quotes_rows_sent": 0,
    "subscriptions_updated": 0,
    "receive_errors": 0,
    "send_errors": 0,
    "resume_requests": 0,
    "resume_events_sent": 0,
    "last_resume_from_sequence": None,
    "last_sequence_sent": 0,
    "last_disconnect_code": None,
    "last_disconnect_reason": None,
    "last_error": None,
    "last_quotes_sent_at": None,
}


def _set_metric(name: str, value: int | str | float | None) -> None:
    with _stream_lock:
        _stream_metrics[name] = value


def _metric_inc(name: str, value: int = 1) -> None:
    with _stream_lock:
        current = int(_stream_metrics.get(name, 0) or 0)
        _stream_metrics[name] = current + value


def _metric_snapshot() -> dict[str, int | str | float | None]:
    with _stream_lock:
        return dict(_stream_metrics)


def get_stream_metrics_snapshot() -> dict[str, int | str | float | None]:
    snapshot = _metric_snapshot()
    snapshot["push_interval_ms"] = STREAM_PUSH_INTERVAL_MS
    snapshot["max_symbols_per_connection"] = STREAM_MAX_SYMBOLS
    snapshot["resume_buffer_size"] = STREAM_RESUME_BUFFER_SIZE
    return snapshot


def _next_stream_sequence() -> int:
    global _stream_sequence
    with _stream_lock:
        _stream_sequence += 1
        return _stream_sequence


def _latest_stream_sequence() -> int:
    with _stream_lock:
        return int(_stream_sequence)


def _record_stream_payload(payload: dict) -> None:
    with _stream_lock:
        _stream_history.append(dict(payload))
        _stream_metrics["last_sequence_sent"] = int(payload.get("sequence") or 0)


def _stream_events_after(sequence: int) -> tuple[list[dict], int]:
    with _stream_lock:
        events = [dict(item) for item in _stream_history if int(item.get("sequence") or 0) > sequence]
        latest = int(_stream_sequence)
    return events, latest


def _parse_resume_sequence(raw: object) -> int | None:
    if raw is None:
        return None
    text = str(raw).strip()
    if not text:
        return None
    try:
        value = int(text)
    except Exception:
        return None
    if value < 0:
        return None
    return value


def _normalize_symbols(symbols: list[str]) -> list[str]:
    normalized: list[str] = []
    seen: set[str] = set()
    for raw_symbol in symbols:
        symbol = str(raw_symbol or "").strip().upper()
        if not symbol or symbol in seen:
            continue
        normalized.append(symbol)
        seen.add(symbol)
        if len(normalized) >= STREAM_MAX_SYMBOLS:
            break
    return normalized


def _resolve_stream_trace_id(websocket: WebSocket) -> str:
    header_trace = websocket.headers.get(TRACE_HEADER)
    query_trace = websocket.query_params.get("traceId") or websocket.query_params.get("trace_id")
    resolved = (header_trace or query_trace or f"trc-{uuid4().hex[:16]}").strip()
    if not resolved:
        resolved = f"trc-{uuid4().hex[:16]}"
    return resolved[:96]


def _parse_subscription_payload(payload: str) -> tuple[list[str] | None, int | None]:
    text_payload = payload.strip()
    if not text_payload:
        return None, None

    if text_payload.lower().startswith("subscribe:"):
        raw_symbols = text_payload.split(":", 1)[1]
        parsed = _normalize_symbols(raw_symbols.split(","))
        return (parsed if parsed else None), None

    if text_payload.lower().startswith("resume:"):
        raw_sequence = text_payload.split(":", 1)[1]
        return None, _parse_resume_sequence(raw_sequence)

    try:
        decoded = json.loads(text_payload)
    except Exception:
        return None, None

    if isinstance(decoded, list):
        parsed = _normalize_symbols([str(item) for item in decoded])
        return (parsed if parsed else None), None

    if isinstance(decoded, dict):
        action = str(decoded.get("action", "")).strip().lower()
        if action and action not in {"subscribe", "sub", "resume", "resync"}:
            return None, None

        resume_sequence = _parse_resume_sequence(
            decoded.get("sinceSequence")
            if "sinceSequence" in decoded
            else decoded.get("sinceSeq")
            if "sinceSeq" in decoded
            else decoded.get("resumeFromSequence")
            if "resumeFromSequence" in decoded
            else decoded.get("fromSequence")
        )

        symbols = decoded.get("symbols")
        if isinstance(symbols, list):
            parsed = _normalize_symbols([str(item) for item in symbols])
            return (parsed if parsed else None), resume_sequence

        return None, resume_sequence

    return None, None


async def _send_replay_events(websocket: WebSocket, since_sequence: int, trace_id: str) -> int:
    events, latest_sequence = _stream_events_after(since_sequence)
    await websocket.send_json(
        {
            "type": "replay",
            "fromSequence": since_sequence,
            "latestSequence": latest_sequence,
            "count": len(events),
            "traceId": trace_id,
            "source": "bysel-backend",
        }
    )
    for event in events:
        replay_payload = dict(event)
        replay_payload["isReplay"] = True
        await websocket.send_json(replay_payload)
    return len(events)


@router.get("/stream/health")
def stream_health() -> dict:
    snapshot = get_stream_metrics_snapshot()
    return {
        "status": "ok",
        "stream": snapshot,
    }


@router.websocket("/ws/quotes")
async def stream_quotes(websocket: WebSocket):
    await websocket.accept()
    stream_trace_id = _resolve_stream_trace_id(websocket)

    _metric_inc("active_connections")
    _metric_inc("total_connections")
    _set_metric("last_trace_id", stream_trace_id)

    logger.info(
        "quotes_stream.connect trace_id=%s client=%s",
        stream_trace_id,
        str(websocket.client),
    )

    resume_from_sequence = _parse_resume_sequence(
        websocket.query_params.get("sinceSeq") or websocket.query_params.get("sinceSequence")
    )

    symbols = _normalize_symbols(get_default_symbols())
    if not symbols:
        symbols = ["RELIANCE", "TCS", "INFY"]

    try:
        await websocket.send_json(
            {
                "type": "subscribed",
                "symbols": symbols,
                "intervalMs": STREAM_PUSH_INTERVAL_MS,
                "traceId": stream_trace_id,
                "source": "bysel-backend",
                "latestSequence": _latest_stream_sequence(),
            }
        )

        if resume_from_sequence is not None:
            _metric_inc("resume_requests")
            _set_metric("last_resume_from_sequence", resume_from_sequence)
            replayed_count = await _send_replay_events(websocket, resume_from_sequence, stream_trace_id)
            if replayed_count > 0:
                _metric_inc("resume_events_sent", replayed_count)

        while True:
            try:
                incoming = await asyncio.wait_for(
                    websocket.receive_text(),
                    timeout=max(STREAM_PUSH_INTERVAL_MS / 1000.0, 0.5),
                )
                updated_symbols, resume_from = _parse_subscription_payload(incoming)
                if updated_symbols:
                    symbols = updated_symbols
                    _metric_inc("subscriptions_updated")
                    await websocket.send_json(
                        {
                            "type": "subscribed",
                            "symbols": symbols,
                            "intervalMs": STREAM_PUSH_INTERVAL_MS,
                            "traceId": stream_trace_id,
                            "source": "bysel-backend",
                            "latestSequence": _latest_stream_sequence(),
                        }
                    )

                if resume_from is not None:
                    _metric_inc("resume_requests")
                    _set_metric("last_resume_from_sequence", resume_from)
                    replayed_count = await _send_replay_events(websocket, resume_from, stream_trace_id)
                    if replayed_count > 0:
                        _metric_inc("resume_events_sent", replayed_count)
            except asyncio.TimeoutError:
                pass
            except WebSocketDisconnect as disconnect:
                _set_metric("last_disconnect_code", disconnect.code)
                _set_metric("last_disconnect_reason", "client_disconnected")
                logger.info(
                    "quotes_stream.disconnect trace_id=%s code=%s reason=client_disconnected",
                    stream_trace_id,
                    disconnect.code,
                )
                break
            except Exception as exc:
                _metric_inc("receive_errors")
                _set_metric("last_error", f"receive_error:{str(exc)}")
                logger.warning(
                    "quotes_stream.receive_error trace_id=%s error=%s",
                    stream_trace_id,
                    str(exc),
                )

            quote_rows = fetch_quotes(symbols)
            sequence = _next_stream_sequence()
            payload = {
                "type": "quotes",
                "quotes": quote_rows,
                "sequence": sequence,
                "timestamp": int(time.time() * 1000),
            }

            try:
                await websocket.send_json(payload)
                _metric_inc("quotes_messages_sent")
                _metric_inc("quotes_rows_sent", len(quote_rows))
                _set_metric("last_quotes_sent_at", int(time.time() * 1000))
                _record_stream_payload(payload)
            except WebSocketDisconnect as disconnect:
                _set_metric("last_disconnect_code", disconnect.code)
                _set_metric("last_disconnect_reason", "client_disconnected")
                logger.info(
                    "quotes_stream.disconnect trace_id=%s code=%s reason=client_disconnected",
                    stream_trace_id,
                    disconnect.code,
                )
                break
            except Exception as exc:
                _metric_inc("send_errors")
                _set_metric("last_error", f"send_error:{str(exc)}")
                logger.warning(
                    "quotes_stream.send_error trace_id=%s error=%s",
                    stream_trace_id,
                    str(exc),
                )
                break
    finally:
        _metric_inc("total_disconnects")
        _metric_inc("active_connections", -1)
        logger.info("quotes_stream.closed trace_id=%s", stream_trace_id)
