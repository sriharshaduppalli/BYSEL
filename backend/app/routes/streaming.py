import asyncio
import json
import os
import time
from threading import Lock

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from ..market_data import fetch_quotes, get_default_symbols

router = APIRouter()

STREAM_PUSH_INTERVAL_MS = int(os.getenv("STREAM_PUSH_INTERVAL_MS", "1200"))
STREAM_MAX_SYMBOLS = int(os.getenv("STREAM_MAX_SYMBOLS", "30"))

_stream_lock = Lock()
_stream_metrics: dict[str, int | str | float | None] = {
    "active_connections": 0,
    "total_connections": 0,
    "total_disconnects": 0,
    "quotes_messages_sent": 0,
    "quotes_rows_sent": 0,
    "subscriptions_updated": 0,
    "receive_errors": 0,
    "send_errors": 0,
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


def _parse_subscription_payload(payload: str) -> list[str] | None:
    text_payload = payload.strip()
    if not text_payload:
        return None

    if text_payload.lower().startswith("subscribe:"):
        raw_symbols = text_payload.split(":", 1)[1]
        parsed = _normalize_symbols(raw_symbols.split(","))
        return parsed if parsed else None

    try:
        decoded = json.loads(text_payload)
    except Exception:
        return None

    if isinstance(decoded, list):
        parsed = _normalize_symbols([str(item) for item in decoded])
        return parsed if parsed else None

    if isinstance(decoded, dict):
        action = str(decoded.get("action", "")).strip().lower()
        if action and action not in {"subscribe", "sub"}:
            return None
        symbols = decoded.get("symbols")
        if not isinstance(symbols, list):
            return None
        parsed = _normalize_symbols([str(item) for item in symbols])
        return parsed if parsed else None

    return None


@router.get("/stream/health")
def stream_health() -> dict:
    snapshot = _metric_snapshot()
    snapshot["push_interval_ms"] = STREAM_PUSH_INTERVAL_MS
    snapshot["max_symbols_per_connection"] = STREAM_MAX_SYMBOLS
    return {
        "status": "ok",
        "stream": snapshot,
    }


@router.websocket("/ws/quotes")
async def stream_quotes(websocket: WebSocket):
    await websocket.accept()
    _metric_inc("active_connections")
    _metric_inc("total_connections")

    symbols = _normalize_symbols(get_default_symbols())
    if not symbols:
        symbols = ["RELIANCE", "TCS", "INFY"]

    try:
        await websocket.send_json(
            {
                "type": "subscribed",
                "symbols": symbols,
                "intervalMs": STREAM_PUSH_INTERVAL_MS,
                "source": "bysel-backend",
            }
        )

        while True:
            try:
                incoming = await asyncio.wait_for(
                    websocket.receive_text(),
                    timeout=max(STREAM_PUSH_INTERVAL_MS / 1000.0, 0.5),
                )
                updated_symbols = _parse_subscription_payload(incoming)
                if updated_symbols:
                    symbols = updated_symbols
                    _metric_inc("subscriptions_updated")
                    await websocket.send_json(
                        {
                            "type": "subscribed",
                            "symbols": symbols,
                            "intervalMs": STREAM_PUSH_INTERVAL_MS,
                            "source": "bysel-backend",
                        }
                    )
            except asyncio.TimeoutError:
                pass
            except WebSocketDisconnect as disconnect:
                _set_metric("last_disconnect_code", disconnect.code)
                _set_metric("last_disconnect_reason", "client_disconnected")
                break
            except Exception as exc:
                _metric_inc("receive_errors")
                _set_metric("last_error", f"receive_error:{str(exc)}")

            quote_rows = fetch_quotes(symbols)
            payload = {
                "type": "quotes",
                "quotes": quote_rows,
                "timestamp": int(time.time() * 1000),
            }

            try:
                await websocket.send_json(payload)
                _metric_inc("quotes_messages_sent")
                _metric_inc("quotes_rows_sent", len(quote_rows))
                _set_metric("last_quotes_sent_at", int(time.time() * 1000))
            except WebSocketDisconnect as disconnect:
                _set_metric("last_disconnect_code", disconnect.code)
                _set_metric("last_disconnect_reason", "client_disconnected")
                break
            except Exception as exc:
                _metric_inc("send_errors")
                _set_metric("last_error", f"send_error:{str(exc)}")
                break
    finally:
        _metric_inc("total_disconnects")
        _metric_inc("active_connections", -1)
