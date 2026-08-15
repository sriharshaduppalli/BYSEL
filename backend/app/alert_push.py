"""Evaluate stored price alerts and send FCM when a threshold is crossed.

This is not a Yahoo tick stream. Evaluation runs when quotes are already
being fetched (app open / warmup). The Android 15-minute WorkManager job
remains the offline fallback.
"""

from __future__ import annotations

import logging
from typing import Iterable

from sqlalchemy.orm import Session

from .database.db import AlertModel, DeviceTokenModel, SessionLocal

logger = logging.getLogger(__name__)

_SUFFIXES = (".NS", ".BO", ".BSE")


def _symbol_key(symbol: str) -> str:
    raw = (symbol or "").strip().upper()
    for suffix in _SUFFIXES:
        if raw.endswith(suffix):
            return raw[: -len(suffix)]
    return raw


def _price_map(quotes: Iterable[dict] | None) -> dict[str, float]:
    prices: dict[str, float] = {}
    for quote in quotes or []:
        if not isinstance(quote, dict):
            continue
        symbol = str(quote.get("symbol") or "")
        try:
            last = float(quote.get("last") or 0.0)
        except (TypeError, ValueError):
            continue
        if last <= 0 or not symbol.strip():
            continue
        prices[_symbol_key(symbol)] = last
    return prices


def _threshold_hit(alert_type: str, price: float, threshold: float) -> bool:
    kind = (alert_type or "").strip().upper()
    if kind == "ABOVE":
        return price >= threshold
    if kind == "BELOW":
        return price <= threshold
    return False


def _send_fcm(token: str, alert: AlertModel, price: float) -> bool:
    try:
        from firebase_admin import messaging
        from .routes.auth import _get_firebase_app
    except Exception as exc:
        logger.warning("alert_push.fcm_import_failed reason=%s", exc)
        return False

    app = _get_firebase_app()
    if app is None:
        return False

    message = messaging.Message(
        data={
            "symbol": str(alert.symbol or ""),
            "price": f"{price:.2f}",
            "alertId": str(alert.id),
            "alertType": str(alert.alert_type or ""),
            "threshold": f"{float(alert.threshold_price or 0.0):.2f}",
        },
        token=token,
    )
    try:
        messaging.send(message, app=app)
        return True
    except Exception as exc:
        logger.warning("alert_push.fcm_send_failed alert_id=%s reason=%s", alert.id, exc)
        return False


def evaluate_price_alerts(
    db: Session | None = None,
    quotes: Iterable[dict] | None = None,
    symbols: Iterable[str] | None = None,
) -> dict:
    """Deactivate crossed alerts and push FCM when a device token exists."""
    own_session = db is None
    session = db or SessionLocal()
    checked = 0
    triggered = 0
    pushed = 0
    try:
        prices = _price_map(quotes)
        symbol_filter = {_symbol_key(s) for s in (symbols or []) if s and str(s).strip()}
        query = session.query(AlertModel).filter(AlertModel.is_active.is_(True))
        alerts = query.all()
        for alert in alerts:
            key = _symbol_key(alert.symbol or "")
            if symbol_filter and key not in symbol_filter:
                continue
            price = prices.get(key)
            if price is None:
                continue
            checked += 1
            if not _threshold_hit(alert.alert_type, price, float(alert.threshold_price or 0.0)):
                continue
            alert.is_active = False
            triggered += 1
            tokens = []
            if alert.user_id is not None:
                tokens = (
                    session.query(DeviceTokenModel)
                    .filter(DeviceTokenModel.user_id == alert.user_id)
                    .all()
                )
            for row in tokens:
                if row.token and _send_fcm(row.token, alert, price):
                    pushed += 1
        session.commit()
    except Exception as exc:
        session.rollback()
        logger.warning("alert_push.evaluate_failed reason=%s", exc)
    finally:
        if own_session:
            session.close()
    if triggered:
        logger.info(
            "alert_push.evaluated checked=%s triggered=%s pushed=%s",
            checked,
            triggered,
            pushed,
        )
    return {"checked": checked, "triggered": triggered, "pushed": pushed}


def evaluate_active_alert_symbols() -> dict:
    """Warm path: fetch quotes for armed alerts, then evaluate."""
    session = SessionLocal()
    try:
        symbols = [
            row[0]
            for row in session.query(AlertModel.symbol)
            .filter(AlertModel.is_active.is_(True))
            .distinct()
            .limit(80)
            .all()
            if row and row[0]
        ]
    finally:
        session.close()
    if not symbols:
        return {"checked": 0, "triggered": 0, "pushed": 0}
    try:
        from .market_data import fetch_quotes

        quotes = fetch_quotes(symbols)
    except Exception as exc:
        logger.warning("alert_push.quote_fetch_failed reason=%s", exc)
        return {"checked": 0, "triggered": 0, "pushed": 0}
    return evaluate_price_alerts(quotes=quotes, symbols=symbols)
