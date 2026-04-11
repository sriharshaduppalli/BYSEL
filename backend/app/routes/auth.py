from fastapi import APIRouter, Depends, HTTPException, Header, Request
from sqlalchemy import text, func
from sqlalchemy.orm import Session
from passlib.context import CryptContext
from pydantic import BaseModel
from email.message import EmailMessage
from ..config import DEBUG
from ..database.db import SessionLocal, UserModel, WalletModel, RefreshTokenModel, PasswordResetTokenModel, OTPModel
from datetime import datetime, timedelta
from typing import List
from collections import defaultdict, deque
from threading import Lock
import logging
import base64
import hashlib
import hmac
import json
import os
import smtplib
import time
import secrets
import urllib.error
import urllib.request
import urllib.parse

try:
    import bcrypt as bcrypt_lib
except Exception:
    bcrypt_lib = None

router = APIRouter()
logger = logging.getLogger(__name__)

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
AUTH_SECRET = os.getenv("AUTH_SECRET", "bysel-dev-secret-change-me")
ACCESS_TOKEN_TTL_SECONDS = int(os.getenv("ACCESS_TOKEN_TTL_SECONDS", "900"))
REFRESH_TOKEN_TTL_SECONDS = int(os.getenv("REFRESH_TOKEN_TTL_SECONDS", "2592000"))
LOGIN_RATE_LIMIT_ATTEMPTS = int(os.getenv("LOGIN_RATE_LIMIT_ATTEMPTS", "6"))
LOGIN_RATE_LIMIT_WINDOW_SECONDS = int(os.getenv("LOGIN_RATE_LIMIT_WINDOW_SECONDS", "60"))
REFRESH_RATE_LIMIT_ATTEMPTS = int(os.getenv("REFRESH_RATE_LIMIT_ATTEMPTS", "12"))
REFRESH_RATE_LIMIT_WINDOW_SECONDS = int(os.getenv("REFRESH_RATE_LIMIT_WINDOW_SECONDS", "60"))
LOGIN_LOCKOUT_FAILURES = int(os.getenv("LOGIN_LOCKOUT_FAILURES", "5"))
LOGIN_LOCKOUT_WINDOW_SECONDS = int(os.getenv("LOGIN_LOCKOUT_WINDOW_SECONDS", "300"))
LOGIN_LOCKOUT_DURATION_SECONDS = int(os.getenv("LOGIN_LOCKOUT_DURATION_SECONDS", "300"))
AUTH_DEBUG_ENDPOINTS_ENABLED = os.getenv("AUTH_DEBUG_ENDPOINTS_ENABLED", "false").lower() == "true"
AUTH_DEBUG_TOKEN = os.getenv("AUTH_DEBUG_TOKEN", "")
REFRESH_TOKEN_RETENTION_DAYS = int(os.getenv("REFRESH_TOKEN_RETENTION_DAYS", "30"))
MAX_ACTIVE_SESSIONS_PER_USER = int(os.getenv("MAX_ACTIVE_SESSIONS_PER_USER", "5"))
REFRESH_TOKEN_REPLAY_GRACE_SECONDS = int(os.getenv("REFRESH_TOKEN_REPLAY_GRACE_SECONDS", "15"))
PASSWORD_RESET_TOKEN_TTL_SECONDS = int(os.getenv("PASSWORD_RESET_TOKEN_TTL_SECONDS", "900"))
RATE_LIMIT_BUCKET_MAX_KEYS = int(os.getenv("RATE_LIMIT_BUCKET_MAX_KEYS", "5000"))
RATE_LIMIT_PRUNE_INTERVAL_SECONDS = int(os.getenv("RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "30"))
SUPPORT_EMAIL = os.getenv("BYSEL_SUPPORT_EMAIL", "support@bysel.com").strip() or "support@bysel.com"
SMTP_HOST = os.getenv("SMTP_HOST", "").strip()
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USERNAME = os.getenv("SMTP_USERNAME", "").strip()
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")
SMTP_FROM_EMAIL = os.getenv("SMTP_FROM_EMAIL", SUPPORT_EMAIL).strip() or SUPPORT_EMAIL
SMTP_USE_TLS = os.getenv("SMTP_USE_TLS", "true").lower() == "true"
PASSWORD_RESET_DEBUG_RESPONSE_ENABLED = os.getenv(
    "AUTH_PASSWORD_RESET_DEBUG_RESPONSE",
    "true" if DEBUG else "false"
).lower() == "true"

# SMS Configuration — Fast2SMS (primary, free for Indian numbers) + Twilio (fallback)
FAST2SMS_API_KEY = os.getenv("FAST2SMS_API_KEY", "").strip()
TWILIO_ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID", "").strip()
TWILIO_AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN", "").strip()
TWILIO_PHONE_NUMBER = os.getenv("TWILIO_PHONE_NUMBER", "").strip()
OTP_TTL_SECONDS = int(os.getenv("OTP_TTL_SECONDS", "300"))  # 5 minutes
OTP_MAX_ATTEMPTS = int(os.getenv("OTP_MAX_ATTEMPTS", "3"))

_login_rate_buckets: dict[str, deque[float]] = defaultdict(deque)
_refresh_rate_buckets: dict[str, deque[float]] = defaultdict(deque)
_login_failure_buckets: dict[str, deque[float]] = defaultdict(deque)
_login_lockouts: dict[str, float] = {}
_rate_limit_lock = Lock()
_rate_limit_last_prune_at = 0.0
_metrics_lock = Lock()
_auth_metrics: dict[str, int] = defaultdict(int)

class UserRegister(BaseModel):
    username: str
    email: str
    password: str

class UserLogin(BaseModel):
    username: str
    password: str


class TokenRefreshRequest(BaseModel):
    refreshToken: str


class AuthResponse(BaseModel):
    status: str
    user_id: int
    access_token: str
    refresh_token: str


class LogoutResponse(BaseModel):
    status: str
    message: str


class LogoutRequest(BaseModel):
    refreshToken: str


class PasswordResetRequest(BaseModel):
    identifier: str


class PasswordResetConfirmRequest(BaseModel):
    token: str
    newPassword: str


class PasswordResetRequestResponse(BaseModel):
    status: str
    message: str
    delivery: str | None = None
    reset_code: str | None = None
    expires_in_seconds: int | None = None


class PasswordResetConfirmResponse(BaseModel):
    status: str
    message: str


class ChangePasswordRequest(BaseModel):
    currentPassword: str
    newPassword: str


class SessionInfo(BaseModel):
    session_id: int
    created_at: str
    expires_at: str
    last_used_at: str | None = None
    client_ip: str | None = None
    device_info: str | None = None


class SessionsResponse(BaseModel):
    status: str
    sessions: List[SessionInfo]


class SendOTPRequest(BaseModel):
    mobile_number: str


class VerifyOTPRequest(BaseModel):
    mobile_number: str
    otp: str


class OTPResponse(BaseModel):
    status: str
    message: str
    otp_id: str | None = None
    expires_in_seconds: int | None = None


def _hash_password(password: str) -> str:
    if bcrypt_lib is not None:
        try:
            return bcrypt_lib.hashpw(password.encode(), bcrypt_lib.gensalt()).decode()
        except Exception as exc:
            logger.exception("auth.password.hash_bcrypt_failed reason=%s", str(exc))

    try:
        return pwd_context.hash(password)
    except Exception as exc:
        logger.exception("auth.password.hash_passlib_failed reason=%s", str(exc))
        raise HTTPException(status_code=500, detail="Password hashing unavailable")


def _verify_password(password: str, password_hash: str) -> bool:
    if password_hash.startswith("$2") and bcrypt_lib is not None:
        try:
            return bcrypt_lib.checkpw(password.encode(), password_hash.encode())
        except Exception as exc:
            logger.exception("auth.password.verify_bcrypt_failed reason=%s", str(exc))

    try:
        return pwd_context.verify(password, password_hash)
    except Exception as exc:
        logger.exception("auth.password.verify_passlib_failed reason=%s", str(exc))
        return False


def _b64_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def _b64_decode(data: str) -> bytes:
    padding = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + padding)


def _sign(payload_b64: str) -> str:
    signature = hmac.new(AUTH_SECRET.encode(), payload_b64.encode(), hashlib.sha256).digest()
    return _b64_encode(signature)


def create_token(user_id: int, token_type: str, ttl_seconds: int, token_version: int) -> str:
    payload = {
        "uid": user_id,
        "typ": token_type,
        "ver": token_version,
        "jti": secrets.token_urlsafe(16),
        "exp": int(time.time()) + ttl_seconds,
        "iat": int(time.time()),
    }
    payload_b64 = _b64_encode(json.dumps(payload, separators=(",", ":")).encode())
    signature_b64 = _sign(payload_b64)
    return f"{payload_b64}.{signature_b64}"


def _hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def _generate_password_reset_code() -> str:
    return secrets.token_hex(4).upper()


def _create_auth_tokens(user_id: int, token_version: int) -> tuple[str, str]:
    access_token = create_token(
        user_id=user_id,
        token_type="access",
        ttl_seconds=ACCESS_TOKEN_TTL_SECONDS,
        token_version=token_version,
    )
    refresh_token = create_token(
        user_id=user_id,
        token_type="refresh",
        ttl_seconds=REFRESH_TOKEN_TTL_SECONDS,
        token_version=token_version,
    )
    return access_token, refresh_token


def _smtp_password_reset_configured() -> bool:
    return bool(SMTP_HOST and SMTP_FROM_EMAIL)


def _send_password_reset_email(recipient_email: str, username: str, reset_code: str) -> bool:
    if not _smtp_password_reset_configured():
        return False

    minutes = max(1, PASSWORD_RESET_TOKEN_TTL_SECONDS // 60)
    message = EmailMessage()
    message["Subject"] = "BYSEL password reset code"
    message["From"] = SMTP_FROM_EMAIL
    message["To"] = recipient_email
    message.set_content(
        "\n".join(
            [
                f"Hi {username},",
                "",
                f"Use this BYSEL password reset code to set a new password: {reset_code}",
                f"This code expires in {minutes} minute(s).",
                "",
                "If you did not request this, you can ignore this email.",
            ]
        )
    )

    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=20) as server:
            server.ehlo()
            if SMTP_USE_TLS:
                server.starttls()
                server.ehlo()
            if SMTP_USERNAME:
                server.login(SMTP_USERNAME, SMTP_PASSWORD)
            server.send_message(message)
        return True
    except Exception as exc:
        logger.exception(
            "auth.password_reset.email_failed email=%s reason=%s",
            _mask_identifier(recipient_email),
            str(exc),
        )
        return False


def _send_otp_fast2sms(mobile_number: str, otp_code: str) -> bool:
    """Send OTP via Fast2SMS (free for Indian numbers)."""
    if not FAST2SMS_API_KEY:
        logger.warning("auth.otp.fast2sms_no_api_key")
        return False

    logger.info("auth.otp.fast2sms_attempt mobile_number=%s api_key_len=%d api_key_prefix=%s",
                mobile_number, len(FAST2SMS_API_KEY), FAST2SMS_API_KEY[:8] + "...")

    # Fast2SMS expects 10-digit Indian number without country code
    phone = mobile_number.replace("+91", "").replace(" ", "").strip()
    if len(phone) != 10 or not phone.isdigit():
        logger.warning("auth.otp.fast2sms_invalid_number mobile_number=%s phone=%s", mobile_number, phone)
        return False

    try:
        url = "https://www.fast2sms.com/dev/bulkV2"
        payload = urllib.parse.urlencode({
            "variables_values": otp_code,
            "route": "otp",
            "numbers": phone,
        }).encode("utf-8")

        req = urllib.request.Request(
            url,
            data=payload,
            headers={
                "authorization": FAST2SMS_API_KEY,
                "Content-Type": "application/x-www-form-urlencoded",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read().decode("utf-8")
            body = json.loads(raw)

        logger.info("auth.otp.fast2sms_response mobile_number=%s status=%s body=%s",
                    mobile_number, resp.status if hasattr(resp, 'status') else 'unknown', raw[:500])

        if body.get("return") is True or body.get("status_code") == 200:
            logger.info("auth.otp.fast2sms_sent mobile_number=%s request_id=%s", mobile_number, body.get("request_id"))
            return True
        else:
            logger.warning("auth.otp.fast2sms_rejected mobile_number=%s body=%s", mobile_number, body)
            return False

    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace") if exc.fp else "no body"
        logger.exception("auth.otp.fast2sms_http_error mobile_number=%s status=%s reason=%s body=%s",
                         mobile_number, exc.code, str(exc), error_body[:500])
        return False
    except Exception as exc:
        logger.exception("auth.otp.fast2sms_error mobile_number=%s reason=%s", mobile_number, str(exc))
        return False


def _send_otp_twilio(mobile_number: str, otp_code: str) -> bool:
    """Send OTP via Twilio (international fallback)."""
    if not TWILIO_ACCOUNT_SID or not TWILIO_AUTH_TOKEN or not TWILIO_PHONE_NUMBER:
        return False

    try:
        from twilio.rest import Client
        from twilio.base.exceptions import TwilioException

        client = Client(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN)
        message = client.messages.create(
            body=f"Your BYSEL verification code is: {otp_code}. This code expires in 5 minutes.",
            from_=TWILIO_PHONE_NUMBER,
            to=mobile_number,
        )
        logger.info("auth.otp.twilio_sent mobile_number=%s message_sid=%s", mobile_number, message.sid)
        return True
    except ImportError:
        logger.error("auth.otp.twilio_not_installed")
        return False
    except Exception as exc:
        logger.exception("auth.otp.twilio_error mobile_number=%s reason=%s", mobile_number, str(exc))
        return False


def _send_otp_sms(mobile_number: str, otp_code: str) -> bool:
    """Send OTP via SMS — tries Fast2SMS first, then Twilio."""
    if _send_otp_fast2sms(mobile_number, otp_code):
        return True
    if _send_otp_twilio(mobile_number, otp_code):
        return True
    logger.warning("auth.otp.all_sms_providers_failed mobile_number=%s", mobile_number)
    return False


def _device_info(request: Request | None) -> str | None:
    if request is None:
        return None
    user_agent = request.headers.get("user-agent", "").strip()
    if not user_agent:
        return None
    return user_agent[:200]


def _persist_refresh_token(db: Session, user_id: int, refresh_token: str, request: Request | None = None) -> None:
    now = datetime.utcnow()
    token_hash = _hash_token(refresh_token)
    token_row = RefreshTokenModel(
        user_id=user_id,
        token_hash=token_hash,
        expires_at=now + timedelta(seconds=REFRESH_TOKEN_TTL_SECONDS),
        client_ip=_client_ip(request) if request is not None else None,
        device_info=_device_info(request),
    )
    db.add(token_row)
    try:
        db.commit()
    except Exception as exc:
        db.rollback()
        logger.exception("auth.session.persist_failed user_id=%s reason=%s", user_id, str(exc))
        db.execute(
            text(
                """
                INSERT INTO refresh_tokens (
                    user_id,
                    token_hash,
                    expires_at,
                    created_at,
                    used_at,
                    revoked_at,
                    replaced_by_hash,
                    last_used_at,
                    client_ip,
                    device_info
                ) VALUES (
                    :user_id,
                    :token_hash,
                    :expires_at,
                    :created_at,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    :client_ip,
                    :device_info
                )
                """
            ),
            {
                "user_id": user_id,
                "token_hash": token_hash,
                "expires_at": now + timedelta(seconds=REFRESH_TOKEN_TTL_SECONDS),
                "created_at": now,
                "client_ip": _client_ip(request) if request is not None else None,
                "device_info": _device_info(request),
            },
        )
        db.commit()
        token_row = db.query(RefreshTokenModel).filter(
            RefreshTokenModel.user_id == user_id,
            RefreshTokenModel.token_hash == token_hash,
        ).first()

    if token_row is not None:
        _enforce_active_session_cap(db, user_id, protected_session_id=token_row.id)


def _enforce_rate_limit(
    key: str,
    buckets: dict[str, deque[float]],
    max_attempts: int,
    window_seconds: int,
    message: str
) -> None:
    now = time.time()
    with _rate_limit_lock:
        _prune_rate_limit_state_locked(now)
        bucket = buckets[key]
        while bucket and bucket[0] <= now - window_seconds:
            bucket.popleft()

        if not bucket:
            buckets.pop(key, None)
            bucket = buckets[key]

        if len(bucket) >= max_attempts:
            raise HTTPException(status_code=429, detail=message)

        bucket.append(now)

        # Keep this specific bucket map capped immediately even between periodic global prune ticks.
        if len(buckets) > max(1, RATE_LIMIT_BUCKET_MAX_KEYS):
            _prune_bucket_locked(buckets, window_seconds, now)


def _prune_bucket_locked(buckets: dict[str, deque[float]], window_seconds: int, now: float) -> None:
    expiry_cutoff = now - max(1, int(window_seconds))
    empty_keys: list[str] = []
    for bucket_key, bucket in buckets.items():
        while bucket and bucket[0] <= expiry_cutoff:
            bucket.popleft()
        if not bucket:
            empty_keys.append(bucket_key)

    for bucket_key in empty_keys:
        buckets.pop(bucket_key, None)

    overflow = len(buckets) - max(1, RATE_LIMIT_BUCKET_MAX_KEYS)
    if overflow > 0:
        # Evict least-recently-seen buckets first to bound memory under client/IP churn.
        least_recent = sorted(
            buckets.items(),
            key=lambda item: item[1][-1] if item[1] else -1.0,
        )
        for bucket_key, _ in least_recent[:overflow]:
            buckets.pop(bucket_key, None)


def _prune_rate_limit_state_locked(now: float) -> None:
    global _rate_limit_last_prune_at
    prune_interval = max(0, int(RATE_LIMIT_PRUNE_INTERVAL_SECONDS))
    if prune_interval > 0 and (now - _rate_limit_last_prune_at) < prune_interval:
        return

    _prune_bucket_locked(_login_rate_buckets, LOGIN_RATE_LIMIT_WINDOW_SECONDS, now)
    _prune_bucket_locked(_refresh_rate_buckets, REFRESH_RATE_LIMIT_WINDOW_SECONDS, now)
    _prune_bucket_locked(_login_failure_buckets, LOGIN_LOCKOUT_WINDOW_SECONDS, now)

    expired_lockouts = [
        bucket_key
        for bucket_key, locked_until in _login_lockouts.items()
        if locked_until <= now
    ]
    for bucket_key in expired_lockouts:
        _login_lockouts.pop(bucket_key, None)
        _login_failure_buckets.pop(bucket_key, None)

    _rate_limit_last_prune_at = now


def _mask_identifier(value: str) -> str:
    normalized = value.strip()
    if not normalized:
        return "unknown"
    if len(normalized) <= 2:
        return "**"
    return f"{normalized[:2]}***"


def _normalize_username(value: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise HTTPException(status_code=400, detail="Username is required")
    return normalized


def _normalize_email(value: str) -> str:
    normalized = value.strip().lower()
    if not normalized:
        raise HTTPException(status_code=400, detail="Email is required")
    return normalized


def _normalize_identifier(value: str) -> str:
    normalized = value.strip()
    if not normalized:
        raise HTTPException(status_code=400, detail="Username or email is required")
    return normalized


def _client_ip(request: Request) -> str:
    return request.client.host if request.client else "unknown"


def _require_debug_access(debug_token: str | None) -> None:
    if not AUTH_DEBUG_ENDPOINTS_ENABLED:
        raise HTTPException(status_code=404, detail="Not found")

    if AUTH_DEBUG_TOKEN and debug_token != AUTH_DEBUG_TOKEN:
        raise HTTPException(status_code=403, detail="Forbidden")


def _bucket_snapshot(buckets: dict[str, deque[float]], window_seconds: int) -> dict:
    now = time.time()
    with _rate_limit_lock:
        _prune_rate_limit_state_locked(now)
        active_entries = []
        total_hits = 0
        for bucket_key, bucket in buckets.items():
            while bucket and bucket[0] <= now - window_seconds:
                bucket.popleft()

            if bucket:
                count = len(bucket)
                total_hits += count
                active_entries.append({
                    "key_hint": _mask_identifier(bucket_key),
                    "hits": count,
                })

    active_entries.sort(key=lambda item: item["hits"], reverse=True)
    return {
        "active_keys": len(active_entries),
        "total_hits": total_hits,
        "top_keys": active_entries[:20],
    }


def _metric_inc(metric: str, value: int = 1) -> None:
    with _metrics_lock:
        _auth_metrics[metric] += value


def _metrics_snapshot() -> dict:
    with _metrics_lock:
        return dict(_auth_metrics)


def _enforce_login_lockout(key: str) -> None:
    now = time.time()
    with _rate_limit_lock:
        _prune_rate_limit_state_locked(now)
        locked_until = _login_lockouts.get(key)
        if locked_until is None:
            return

        if locked_until <= now:
            _login_lockouts.pop(key, None)
            _login_failure_buckets.pop(key, None)
            return

        remaining = int(locked_until - now)
        raise HTTPException(
            status_code=429,
            detail=f"Too many failed login attempts. Try again in {remaining} seconds."
        )


def _record_login_failure(key: str) -> bool:
    now = time.time()
    with _rate_limit_lock:
        _prune_rate_limit_state_locked(now)
        bucket = _login_failure_buckets[key]
        while bucket and bucket[0] <= now - LOGIN_LOCKOUT_WINDOW_SECONDS:
            bucket.popleft()

        bucket.append(now)
        if len(bucket) < LOGIN_LOCKOUT_FAILURES:
            return False

        _login_lockouts[key] = now + LOGIN_LOCKOUT_DURATION_SECONDS
        return True


def _record_login_success(key: str) -> None:
    with _rate_limit_lock:
        _login_failure_buckets.pop(key, None)
        _login_lockouts.pop(key, None)


def _login_lockout_snapshot() -> dict:
    now = time.time()
    active = []
    with _rate_limit_lock:
        expired_keys = [key for key, locked_until in _login_lockouts.items() if locked_until <= now]
        for key in expired_keys:
            _login_lockouts.pop(key, None)
            _login_failure_buckets.pop(key, None)

        for key, locked_until in _login_lockouts.items():
            active.append({
                "key_hint": _mask_identifier(key),
                "remaining_seconds": int(locked_until - now)
            })

    active.sort(key=lambda item: item["remaining_seconds"], reverse=True)
    return {
        "active_lockouts": len(active),
        "entries": active[:20],
    }


def _prune_stale_refresh_tokens(db: Session) -> int:
    now = datetime.utcnow()
    retention_cutoff = now - timedelta(days=REFRESH_TOKEN_RETENTION_DAYS)

    stale_rows = db.query(RefreshTokenModel).filter(
        (RefreshTokenModel.expires_at <= now) |
        (
            (RefreshTokenModel.revoked_at.is_not(None) | RefreshTokenModel.used_at.is_not(None)) &
            (RefreshTokenModel.created_at <= retention_cutoff)
        )
    )

    deleted_count = stale_rows.count()
    if deleted_count > 0:
        stale_rows.delete(synchronize_session=False)
        db.commit()
        _metric_inc("refresh_tokens.pruned", deleted_count)
        logger.info("auth.maintenance.prune_refresh_tokens deleted=%s", deleted_count)

    return deleted_count


def _enforce_active_session_cap(db: Session, user_id: int, protected_session_id: int | None = None) -> int:
    if MAX_ACTIVE_SESSIONS_PER_USER <= 0:
        return 0

    now = datetime.utcnow()
    active_sessions = db.query(RefreshTokenModel).filter(
        RefreshTokenModel.user_id == user_id,
        RefreshTokenModel.revoked_at.is_(None),
        RefreshTokenModel.used_at.is_(None),
        RefreshTokenModel.expires_at > now
    ).order_by(RefreshTokenModel.created_at.asc()).all()

    if len(active_sessions) <= MAX_ACTIVE_SESSIONS_PER_USER:
        return 0

    overflow = len(active_sessions) - MAX_ACTIVE_SESSIONS_PER_USER
    eviction_candidates = [
        session for session in active_sessions
        if session.id != protected_session_id
    ]

    if not eviction_candidates:
        return 0

    to_revoke = eviction_candidates[:overflow]
    for session in to_revoke:
        session.revoked_at = now

    evicted_count = len(to_revoke)
    db.commit()
    _metric_inc("sessions.evicted", evicted_count)
    logger.info(
        "auth.sessions.evicted user_id=%s evicted=%s max_allowed=%s protected_session_id=%s",
        user_id,
        evicted_count,
        MAX_ACTIVE_SESSIONS_PER_USER,
        protected_session_id,
    )
    return evicted_count


def _is_benign_refresh_replay(stored_token: RefreshTokenModel, request: Request | None, now: datetime) -> bool:
    if stored_token.used_at is None:
        return False

    replay_marker = stored_token.last_used_at or stored_token.used_at
    replay_age_seconds = (now - replay_marker).total_seconds()
    if replay_age_seconds > REFRESH_TOKEN_REPLAY_GRACE_SECONDS:
        return False

    if request is None:
        return False

    # Match on whichever client markers were captured for the token.
    if stored_token.client_ip:
        request_ip = _client_ip(request)
        if request_ip != stored_token.client_ip:
            return False

    if stored_token.device_info:
        request_device = _device_info(request)
        if request_device != stored_token.device_info:
            return False

    if not stored_token.client_ip and not stored_token.device_info:
        return False

    return True


def _reset_debug_state() -> None:
    global _rate_limit_last_prune_at
    with _rate_limit_lock:
        _login_rate_buckets.clear()
        _refresh_rate_buckets.clear()
        _login_failure_buckets.clear()
        _login_lockouts.clear()
        _rate_limit_last_prune_at = 0.0

    with _metrics_lock:
        _auth_metrics.clear()


def _session_health_snapshot(db: Session) -> dict:
    now = datetime.utcnow()
    expiring_cutoff = now + timedelta(minutes=15)
    recent_cutoff = now - timedelta(hours=24)

    active_filters = (
        RefreshTokenModel.revoked_at.is_(None),
        RefreshTokenModel.used_at.is_(None),
        RefreshTokenModel.expires_at > now,
    )

    active_sessions_total = db.query(RefreshTokenModel).filter(*active_filters).count()
    active_sessions_expiring_15m = db.query(RefreshTokenModel).filter(
        *active_filters,
        RefreshTokenModel.expires_at <= expiring_cutoff,
    ).count()

    revoked_last_24h = db.query(RefreshTokenModel).filter(
        RefreshTokenModel.revoked_at.is_not(None),
        RefreshTokenModel.revoked_at >= recent_cutoff,
    ).count()
    used_last_24h = db.query(RefreshTokenModel).filter(
        RefreshTokenModel.used_at.is_not(None),
        RefreshTokenModel.used_at >= recent_cutoff,
    ).count()

    top_active_sessions = db.query(
        RefreshTokenModel.user_id,
        func.count(RefreshTokenModel.id).label("active_session_count")
    ).filter(
        *active_filters
    ).group_by(
        RefreshTokenModel.user_id
    ).order_by(
        func.count(RefreshTokenModel.id).desc()
    ).limit(10).all()

    return {
        "active_sessions_total": active_sessions_total,
        "active_sessions_expiring_15m": active_sessions_expiring_15m,
        "revoked_last_24h": revoked_last_24h,
        "used_last_24h": used_last_24h,
        "max_active_sessions_per_user": MAX_ACTIVE_SESSIONS_PER_USER,
        "top_users_by_active_sessions": [
            {
                "user_id": int(row.user_id),
                "active_session_count": int(row.active_session_count),
            }
            for row in top_active_sessions
        ],
    }


def verify_token(token: str, expected_type: str | None = None) -> dict:
    try:
        payload_b64, signature_b64 = token.split(".", 1)
    except ValueError:
        raise HTTPException(status_code=401, detail="Malformed token")

    expected_signature = _sign(payload_b64)
    if not hmac.compare_digest(signature_b64, expected_signature):
        raise HTTPException(status_code=401, detail="Invalid token signature")

    try:
        payload = json.loads(_b64_decode(payload_b64).decode())
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid token payload")

    now = int(time.time())
    if int(payload.get("exp", 0)) <= now:
        raise HTTPException(status_code=401, detail="Token expired")

    token_type = payload.get("typ")
    if expected_type and token_type != expected_type:
        raise HTTPException(status_code=401, detail="Invalid token type")

    user_id = payload.get("uid")
    if not isinstance(user_id, int):
        raise HTTPException(status_code=401, detail="Invalid token user")

    token_version = payload.get("ver", 0)
    if not isinstance(token_version, int):
        raise HTTPException(status_code=401, detail="Invalid token version")

    token_id = payload.get("jti")
    if token_id is not None and not isinstance(token_id, str):
        raise HTTPException(status_code=401, detail="Invalid token id")

    return payload


def validate_access_token_and_get_user(token: str, db: Session) -> UserModel:
    payload = verify_token(token, expected_type="access")
    resolved_user_id = int(payload["uid"])
    token_version = int(payload.get("ver", 0))

    user = db.query(UserModel).filter(UserModel.id == resolved_user_id).first()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid credentials")

    current_token_version = int(getattr(user, "token_version", 0) or 0)
    if token_version != current_token_version:
        raise HTTPException(status_code=401, detail="Session invalidated")

    return user


def _get_user_id_from_authorization(authorization: str | None) -> int:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing authentication")

    token = authorization.split(" ", 1)[1].strip()
    db: Session = SessionLocal()
    try:
        user = validate_access_token_and_get_user(token, db)
        return int(user.id)
    finally:
        db.close()

@router.post("/register", response_model=AuthResponse)
def register(user: UserRegister, request: Request):
    normalized_username = _normalize_username(user.username)
    normalized_email = _normalize_email(user.email)
    raw_password = str(user.password)
    if len(raw_password) > 72:
        _metric_inc("register.failure_password_too_long")
        raise HTTPException(status_code=400, detail="Password must be 72 characters or less.")
    password = raw_password[:72]

    stage = "init"
    db: Session = SessionLocal()
    try:
        stage = "prune_refresh_tokens"
        _prune_stale_refresh_tokens(db)

        stage = "check_existing_user"
        existing = db.query(UserModel).filter(
            (func.lower(UserModel.username) == normalized_username.lower()) |
            (func.lower(UserModel.email) == normalized_email)
        ).first()
        if existing:
            _metric_inc("register.failure_user_exists")
            raise HTTPException(status_code=400, detail="Username or email already exists")

        stage = "hash_password"
        hashed = _hash_password(password)

        stage = "insert_user"
        new_user = UserModel(
            username=normalized_username,
            email=normalized_email,
            password_hash=hashed,
            created_at=datetime.utcnow(),
        )
        db.add(new_user)
        db.commit()
        db.refresh(new_user)

        user_id = new_user.id

        stage = "init_wallet"
        try:
            wallet = WalletModel(user_id=user_id, balance=0.0)
            db.add(wallet)
            db.commit()
        except Exception as exc:
            db.rollback()
            logger.exception("auth.register.wallet_init_failed user_id=%s reason=%s", user_id, str(exc))

        stage = "create_tokens"
        token_version = int(getattr(new_user, "token_version", 0) or 0)
        access_token, refresh_token = _create_auth_tokens(user_id=user_id, token_version=token_version)

        stage = "persist_refresh_token"
        try:
            _persist_refresh_token(db, user_id=user_id, refresh_token=refresh_token, request=request)
        except Exception as exc:
            db.rollback()
            logger.exception("auth.register.session_init_failed user_id=%s reason=%s", user_id, str(exc))

        stage = "respond"
        _metric_inc("register.success")
        logger.info("auth.register.success user_id=%s username=%s", user_id, _mask_identifier(normalized_username))
        return AuthResponse(
            status="ok",
            user_id=user_id,
            access_token=access_token,
            refresh_token=refresh_token,
        )
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("auth.register.failed stage=%s reason=%s", stage, str(exc))
        raise HTTPException(status_code=500, detail=f"Register failed at stage: {stage}")
    finally:
        db.close()

@router.post("/login", response_model=AuthResponse)
def login(user: UserLogin, request: Request):
    client_ip = _client_ip(request)
    normalized_username = _normalize_username(user.username)
    username_key = normalized_username.lower()
    login_key = f"{client_ip}:{username_key}"
    try:
        _enforce_login_lockout(login_key)
    except HTTPException:
        _metric_inc("login.lockout_blocked")
        logger.warning("auth.login.lockout_blocked ip=%s username=%s", client_ip, _mask_identifier(normalized_username))
        raise

    try:
        _enforce_rate_limit(
            key=login_key,
            buckets=_login_rate_buckets,
            max_attempts=LOGIN_RATE_LIMIT_ATTEMPTS,
            window_seconds=LOGIN_RATE_LIMIT_WINDOW_SECONDS,
            message="Too many login attempts. Please try again shortly."
        )
    except HTTPException:
        _metric_inc("login.rate_limited")
        logger.warning("auth.login.rate_limited ip=%s username=%s", client_ip, _mask_identifier(normalized_username))
        raise

    if len(user.password) > 72:
        _metric_inc("login.failure_password_too_long")
        raise HTTPException(status_code=400, detail="Password must be 72 characters or less.")
    db: Session = SessionLocal()
    try:
        _prune_stale_refresh_tokens(db)
        db_user = db.query(UserModel).filter(
            (func.lower(UserModel.username) == username_key) |
            (func.lower(UserModel.email) == username_key)
        ).first()
        if not db_user or not _verify_password(user.password, db_user.password_hash):
            _metric_inc("login.failure_invalid_credentials")
            lockout_triggered = _record_login_failure(login_key)
            if lockout_triggered:
                _metric_inc("login.lockout_triggered")
                logger.warning("auth.login.lockout_triggered ip=%s username=%s", client_ip, _mask_identifier(normalized_username))
            logger.warning("auth.login.failed ip=%s username=%s reason=invalid_credentials", client_ip, _mask_identifier(normalized_username))
            raise HTTPException(status_code=401, detail="Invalid username or password")

        token_version = int(getattr(db_user, "token_version", 0) or 0)
        access_token, refresh_token = _create_auth_tokens(user_id=db_user.id, token_version=token_version)
        try:
            _persist_refresh_token(db, user_id=db_user.id, refresh_token=refresh_token, request=request)
        except Exception as exc:
            db.rollback()
            logger.exception("auth.login.session_init_failed user_id=%s reason=%s", db_user.id, str(exc))

        _record_login_success(login_key)
        _metric_inc("login.success")
        logger.info("auth.login.success ip=%s user_id=%s username=%s", client_ip, db_user.id, _mask_identifier(normalized_username))
        return AuthResponse(
            status="ok",
            user_id=db_user.id,
            access_token=access_token,
            refresh_token=refresh_token,
        )
    finally:
        db.close()


@router.post("/password-reset/request", response_model=PasswordResetRequestResponse)
def request_password_reset(body: PasswordResetRequest):
    identifier = _normalize_identifier(body.identifier)
    generic_message = "If an account exists, you will receive a password reset code shortly."
    support_message = f"Password reset is currently unavailable in-app. Contact {SUPPORT_EMAIL}."

    if not _smtp_password_reset_configured() and not PASSWORD_RESET_DEBUG_RESPONSE_ENABLED:
        _metric_inc("password_reset.request_support_only")
        return PasswordResetRequestResponse(
            status="ok",
            message=support_message,
            delivery="support",
        )

    db: Session = SessionLocal()
    now = datetime.utcnow()

    try:
        normalized_identifier = identifier.lower()
        db_user = db.query(UserModel).filter(
            (func.lower(UserModel.username) == normalized_identifier) |
            (func.lower(UserModel.email) == normalized_identifier)
        ).first()

        if not db_user:
            _metric_inc("password_reset.request_unknown_user")
            return PasswordResetRequestResponse(
                status="ok",
                message=generic_message,
                delivery="email",
                expires_in_seconds=PASSWORD_RESET_TOKEN_TTL_SECONDS,
            )

        db.query(PasswordResetTokenModel).filter(
            PasswordResetTokenModel.user_id == db_user.id,
            PasswordResetTokenModel.used_at.is_(None),
            PasswordResetTokenModel.revoked_at.is_(None),
        ).update({"revoked_at": now}, synchronize_session=False)

        reset_code = _generate_password_reset_code()
        db.add(
            PasswordResetTokenModel(
                user_id=db_user.id,
                token_hash=_hash_token(reset_code),
                expires_at=now + timedelta(seconds=PASSWORD_RESET_TOKEN_TTL_SECONDS),
            )
        )
        db.commit()

        if _send_password_reset_email(db_user.email, db_user.username, reset_code):
            _metric_inc("password_reset.request_email_sent")
            logger.info(
                "auth.password_reset.requested user_id=%s identifier=%s delivery=email",
                db_user.id,
                _mask_identifier(identifier),
            )
            return PasswordResetRequestResponse(
                status="ok",
                message=generic_message,
                delivery="email",
                expires_in_seconds=PASSWORD_RESET_TOKEN_TTL_SECONDS,
            )

        if PASSWORD_RESET_DEBUG_RESPONSE_ENABLED:
            _metric_inc("password_reset.request_debug_code")
            logger.info(
                "auth.password_reset.requested user_id=%s identifier=%s delivery=debug",
                db_user.id,
                _mask_identifier(identifier),
            )
            return PasswordResetRequestResponse(
                status="ok",
                message="Reset code generated. Use the code below to set a new password.",
                delivery="debug",
                reset_code=reset_code,
                expires_in_seconds=PASSWORD_RESET_TOKEN_TTL_SECONDS,
            )

        _metric_inc("password_reset.request_delivery_unavailable")
        return PasswordResetRequestResponse(
            status="ok",
            message=support_message,
            delivery="support",
        )
    finally:
        db.close()


@router.post("/password-reset/confirm", response_model=PasswordResetConfirmResponse)
def confirm_password_reset(body: PasswordResetConfirmRequest):
    reset_token = body.token.strip().upper()
    if not reset_token:
        raise HTTPException(status_code=400, detail="Reset code is required")

    raw_password = str(body.newPassword)
    if len(raw_password) < 6:
        raise HTTPException(status_code=400, detail="Password must be at least 6 characters")
    if len(raw_password) > 72:
        raise HTTPException(status_code=400, detail="Password must be 72 characters or less.")

    db: Session = SessionLocal()
    now = datetime.utcnow()

    try:
        token_hash = _hash_token(reset_token)
        token_row = db.query(PasswordResetTokenModel).filter(
            PasswordResetTokenModel.token_hash == token_hash,
            PasswordResetTokenModel.used_at.is_(None),
            PasswordResetTokenModel.revoked_at.is_(None),
        ).first()

        if not token_row:
            _metric_inc("password_reset.confirm_invalid_token")
            raise HTTPException(status_code=400, detail="Invalid or expired reset code")

        if token_row.expires_at <= now:
            token_row.revoked_at = now
            db.commit()
            _metric_inc("password_reset.confirm_expired")
            raise HTTPException(status_code=400, detail="Invalid or expired reset code")

        db_user = db.query(UserModel).filter(UserModel.id == token_row.user_id).first()
        if not db_user:
            _metric_inc("password_reset.confirm_missing_user")
            raise HTTPException(status_code=404, detail="Account not found")

        db_user.password_hash = _hash_password(raw_password)
        db_user.token_version = int(getattr(db_user, "token_version", 0) or 0) + 1

        token_row.used_at = now
        token_row.revoked_at = now

        db.query(PasswordResetTokenModel).filter(
            PasswordResetTokenModel.user_id == db_user.id,
            PasswordResetTokenModel.id != token_row.id,
            PasswordResetTokenModel.used_at.is_(None),
            PasswordResetTokenModel.revoked_at.is_(None),
        ).update({"revoked_at": now}, synchronize_session=False)

        db.query(RefreshTokenModel).filter(
            RefreshTokenModel.user_id == db_user.id,
            RefreshTokenModel.revoked_at.is_(None),
        ).update({"revoked_at": now}, synchronize_session=False)

        db.commit()
        _metric_inc("password_reset.confirm_success")
        logger.info("auth.password_reset.confirmed user_id=%s", db_user.id)
        return PasswordResetConfirmResponse(
            status="ok",
            message="Password updated successfully. Sign in with your new password.",
        )
    finally:
        db.close()


@router.post("/change-password", response_model=AuthResponse)
def change_password(
    body: ChangePasswordRequest,
    request: Request,
    authorization: str | None = Header(default=None, alias="Authorization"),
):
    user_id = _get_user_id_from_authorization(authorization)

    current_password = str(body.currentPassword)
    new_password = str(body.newPassword)
    if len(new_password) < 6:
        raise HTTPException(status_code=400, detail="Password must be at least 6 characters")
    if len(new_password) > 72:
        raise HTTPException(status_code=400, detail="Password must be 72 characters or less.")
    if current_password == new_password:
        raise HTTPException(status_code=400, detail="New password must be different from current password")

    db: Session = SessionLocal()
    now = datetime.utcnow()

    try:
        db_user = db.query(UserModel).filter(UserModel.id == user_id).first()
        if not db_user:
            _metric_inc("password_change.missing_user")
            raise HTTPException(status_code=404, detail="Account not found")

        if not _verify_password(current_password, db_user.password_hash):
            _metric_inc("password_change.invalid_current_password")
            raise HTTPException(status_code=401, detail="Current password is incorrect")

        db_user.password_hash = _hash_password(new_password)
        db_user.token_version = int(getattr(db_user, "token_version", 0) or 0) + 1
        next_token_version = int(db_user.token_version)

        db.query(RefreshTokenModel).filter(
            RefreshTokenModel.user_id == user_id,
            RefreshTokenModel.revoked_at.is_(None),
        ).update({"revoked_at": now}, synchronize_session=False)

        db.query(PasswordResetTokenModel).filter(
            PasswordResetTokenModel.user_id == user_id,
            PasswordResetTokenModel.used_at.is_(None),
            PasswordResetTokenModel.revoked_at.is_(None),
        ).update({"revoked_at": now}, synchronize_session=False)

        access_token, refresh_token = _create_auth_tokens(
            user_id=user_id,
            token_version=next_token_version,
        )
        _persist_refresh_token(db, user_id=user_id, refresh_token=refresh_token, request=request)

        _metric_inc("password_change.success")
        logger.info("auth.password_change.success user_id=%s", user_id)
        return AuthResponse(
            status="ok",
            user_id=user_id,
            access_token=access_token,
            refresh_token=refresh_token,
        )
    finally:
        db.close()


@router.post("/refresh", response_model=AuthResponse)
def refresh_token(body: TokenRefreshRequest, request: Request):
    client_ip = _client_ip(request)
    refresh_key = f"{client_ip}:{_hash_token(body.refreshToken)}"
    try:
        _enforce_rate_limit(
            key=refresh_key,
            buckets=_refresh_rate_buckets,
            max_attempts=REFRESH_RATE_LIMIT_ATTEMPTS,
            window_seconds=REFRESH_RATE_LIMIT_WINDOW_SECONDS,
            message="Too many token refresh requests. Please try again shortly."
        )
    except HTTPException:
        _metric_inc("refresh.rate_limited")
        logger.warning("auth.refresh.rate_limited ip=%s", client_ip)
        raise

    try:
        payload = verify_token(body.refreshToken, expected_type="refresh")
    except HTTPException as exc:
        _metric_inc("refresh.failure_token_verify")
        logger.warning("auth.refresh.failed ip=%s reason=%s", client_ip, exc.detail)
        raise
    user_id = int(payload["uid"])
    token_version = int(payload.get("ver", 0))

    db: Session = SessionLocal()
    now = datetime.utcnow()
    incoming_token_hash = _hash_token(body.refreshToken)

    try:
        _prune_stale_refresh_tokens(db)
        db_user = db.query(UserModel).filter(UserModel.id == user_id).first()
        if not db_user:
            _metric_inc("refresh.failure_invalid_user")
            raise HTTPException(status_code=401, detail="Invalid credentials")

        current_token_version = int(getattr(db_user, "token_version", 0) or 0)
        if token_version != current_token_version:
            _metric_inc("refresh.failure_token_version_mismatch")
            raise HTTPException(status_code=401, detail="Session invalidated")

        stored_token = db.query(RefreshTokenModel).filter(
            RefreshTokenModel.user_id == user_id,
            RefreshTokenModel.token_hash == incoming_token_hash
        ).first()

        if not stored_token:
            _metric_inc("refresh.failure_invalid_token")
            logger.warning("auth.refresh.failed ip=%s user_id=%s reason=invalid_refresh_token", client_ip, user_id)
            raise HTTPException(status_code=401, detail="Invalid refresh token")

        if stored_token.used_at is not None:
            if _is_benign_refresh_replay(stored_token, request, now):
                _metric_inc("refresh.failure_reuse_grace")
                logger.info(
                    "auth.refresh.failed ip=%s user_id=%s reason=refresh_token_replay_within_grace",
                    client_ip,
                    user_id,
                )
                raise HTTPException(status_code=401, detail="Refresh token already rotated")

            db_user.token_version = current_token_version + 1
            db.query(RefreshTokenModel).filter(
                RefreshTokenModel.user_id == user_id,
                RefreshTokenModel.revoked_at.is_(None)
            ).update({"revoked_at": now}, synchronize_session=False)
            db.commit()
            _metric_inc("refresh.failure_reuse_detected")
            logger.warning("auth.refresh.failed ip=%s user_id=%s reason=token_reuse_detected", client_ip, user_id)
            raise HTTPException(status_code=401, detail="Refresh token reuse detected")

        if stored_token.revoked_at is not None:
            _metric_inc("refresh.failure_revoked_token")
            logger.warning("auth.refresh.failed ip=%s user_id=%s reason=revoked_refresh_token", client_ip, user_id)
            raise HTTPException(status_code=401, detail="Refresh token revoked")

        if stored_token.expires_at <= now:
            stored_token.used_at = now
            stored_token.revoked_at = now
            db.commit()
            _metric_inc("refresh.failure_expired")
            logger.warning("auth.refresh.failed ip=%s user_id=%s reason=refresh_token_expired", client_ip, user_id)
            raise HTTPException(status_code=401, detail="Refresh token expired")

        access_token, new_refresh_token = _create_auth_tokens(
            user_id=user_id,
            token_version=current_token_version,
        )
        new_refresh_hash = _hash_token(new_refresh_token)

        stored_token.used_at = now
        stored_token.last_used_at = now
        stored_token.revoked_at = now
        stored_token.replaced_by_hash = new_refresh_hash

        new_session = RefreshTokenModel(
            user_id=user_id,
            token_hash=new_refresh_hash,
            expires_at=now + timedelta(seconds=REFRESH_TOKEN_TTL_SECONDS),
            client_ip=client_ip,
            device_info=_device_info(request),
        )
        db.add(new_session)
        db.commit()
        _enforce_active_session_cap(db, user_id, protected_session_id=new_session.id)
        _metric_inc("refresh.success")
        logger.info("auth.refresh.success ip=%s user_id=%s", client_ip, user_id)
    finally:
        db.close()

    return AuthResponse(
        status="ok",
        user_id=user_id,
        access_token=access_token,
        refresh_token=new_refresh_token,
    )


@router.post("/logout", response_model=LogoutResponse)
def logout(body: LogoutRequest, authorization: str | None = Header(default=None, alias="Authorization")):
    user_id = _get_user_id_from_authorization(authorization)
    db: Session = SessionLocal()
    now = datetime.utcnow()
    token_hash = _hash_token(body.refreshToken)

    try:
        token_row = db.query(RefreshTokenModel).filter(
            RefreshTokenModel.user_id == user_id,
            RefreshTokenModel.token_hash == token_hash
        ).first()

        if token_row and token_row.revoked_at is None:
            token_row.revoked_at = now
            db.commit()
            _metric_inc("logout.success_current_device")
            logger.info("auth.logout.success user_id=%s scope=current_device", user_id)
        else:
            _metric_inc("logout.noop_current_device")
            logger.info("auth.logout.noop user_id=%s scope=current_device", user_id)
    finally:
        db.close()

    return LogoutResponse(status="ok", message="Logged out successfully")


@router.post("/logout-all", response_model=LogoutResponse)
def logout_all_devices(authorization: str | None = Header(default=None, alias="Authorization")):
    user_id = _get_user_id_from_authorization(authorization)
    db: Session = SessionLocal()
    now = datetime.utcnow()

    try:
        db_user = db.query(UserModel).filter(UserModel.id == user_id).first()
        if db_user:
            db_user.token_version = int(getattr(db_user, "token_version", 0) or 0) + 1
        db.query(RefreshTokenModel).filter(
            RefreshTokenModel.user_id == user_id,
            RefreshTokenModel.revoked_at.is_(None)
        ).update({"revoked_at": now}, synchronize_session=False)
        db.commit()
        _metric_inc("logout.success_all_devices")
        logger.info("auth.logout.success user_id=%s scope=all_devices", user_id)
    finally:
        db.close()

    return LogoutResponse(status="ok", message="Logged out from all devices")


@router.get("/debug/rate-limits")
def auth_debug_rate_limits(x_debug_token: str | None = Header(default=None, alias="X-Debug-Token")):
    _require_debug_access(x_debug_token)
    return {
        "status": "ok",
        "metrics": _metrics_snapshot(),
        "login": {
            "attempt_limit": LOGIN_RATE_LIMIT_ATTEMPTS,
            "window_seconds": LOGIN_RATE_LIMIT_WINDOW_SECONDS,
            **_bucket_snapshot(_login_rate_buckets, LOGIN_RATE_LIMIT_WINDOW_SECONDS),
        },
        "refresh": {
            "attempt_limit": REFRESH_RATE_LIMIT_ATTEMPTS,
            "window_seconds": REFRESH_RATE_LIMIT_WINDOW_SECONDS,
            **_bucket_snapshot(_refresh_rate_buckets, REFRESH_RATE_LIMIT_WINDOW_SECONDS),
        },
        "login_lockout_policy": {
            "failure_threshold": LOGIN_LOCKOUT_FAILURES,
            "failure_window_seconds": LOGIN_LOCKOUT_WINDOW_SECONDS,
            "lockout_duration_seconds": LOGIN_LOCKOUT_DURATION_SECONDS,
            **_login_lockout_snapshot(),
        },
        "session_policy": {
            "max_active_sessions_per_user": MAX_ACTIVE_SESSIONS_PER_USER
        }
    }


@router.post("/debug/rate-limits/reset")
def auth_debug_rate_limits_reset(x_debug_token: str | None = Header(default=None, alias="X-Debug-Token")):
    _require_debug_access(x_debug_token)
    _reset_debug_state()
    return {
        "status": "ok",
        "message": "Auth debug metrics and rate-limit buckets reset"
    }


@router.get("/debug/session-health")
def auth_debug_session_health(x_debug_token: str | None = Header(default=None, alias="X-Debug-Token")):
    _require_debug_access(x_debug_token)
    db: Session = SessionLocal()
    try:
        snapshot = _session_health_snapshot(db)
    finally:
        db.close()

    return {
        "status": "ok",
        "metrics": _metrics_snapshot(),
        "session_health": snapshot,
    }


@router.get("/sessions", response_model=SessionsResponse)
def list_sessions(authorization: str | None = Header(default=None, alias="Authorization")):
    user_id = _get_user_id_from_authorization(authorization)
    db: Session = SessionLocal()
    now = datetime.utcnow()

    try:
        active_sessions = db.query(RefreshTokenModel).filter(
            RefreshTokenModel.user_id == user_id,
            RefreshTokenModel.revoked_at.is_(None),
            RefreshTokenModel.used_at.is_(None),
            RefreshTokenModel.expires_at > now
        ).order_by(RefreshTokenModel.created_at.desc()).all()
    finally:
        db.close()

    return SessionsResponse(
        status="ok",
        sessions=[
            SessionInfo(
                session_id=session.id,
                created_at=session.created_at.isoformat(),
                expires_at=session.expires_at.isoformat(),
                last_used_at=session.last_used_at.isoformat() if session.last_used_at else None,
                client_ip=session.client_ip,
                device_info=session.device_info,
            )
            for session in active_sessions
        ]
    )


@router.delete("/sessions/{session_id}", response_model=LogoutResponse)
def revoke_session(session_id: int, authorization: str | None = Header(default=None, alias="Authorization")):
    user_id = _get_user_id_from_authorization(authorization)
    db: Session = SessionLocal()
    now = datetime.utcnow()

    try:
        session = db.query(RefreshTokenModel).filter(
            RefreshTokenModel.id == session_id,
            RefreshTokenModel.user_id == user_id
        ).first()

        if not session:
            raise HTTPException(status_code=404, detail="Session not found")

        if session.revoked_at is None:
            session.revoked_at = now
            db.commit()
    finally:
        db.close()

    return LogoutResponse(status="ok", message="Session revoked")


@router.get("/otp-debug")
def otp_debug():
    """Temporary diagnostic endpoint to check Fast2SMS config."""
    result = {
        "fast2sms_key_set": bool(FAST2SMS_API_KEY),
        "fast2sms_key_len": len(FAST2SMS_API_KEY),
        "fast2sms_key_prefix": FAST2SMS_API_KEY[:8] + "..." if len(FAST2SMS_API_KEY) > 8 else "(too short)",
        "twilio_configured": bool(TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN and TWILIO_PHONE_NUMBER),
    }
    # Test Fast2SMS API connectivity (without sending a real SMS)
    if FAST2SMS_API_KEY:
        try:
            req = urllib.request.Request(
                "https://www.fast2sms.com/dev/wallet",
                headers={"authorization": FAST2SMS_API_KEY},
                method="GET",
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                body = json.loads(resp.read().decode("utf-8"))
                result["fast2sms_wallet"] = body
        except urllib.error.HTTPError as exc:
            error_body = exc.read().decode("utf-8", errors="replace") if exc.fp else "no body"
            result["fast2sms_wallet_error"] = f"HTTP {exc.code}: {error_body[:300]}"
        except Exception as exc:
            result["fast2sms_wallet_error"] = str(exc)
    return result


@router.post("/send-otp", response_model=OTPResponse)
def send_otp(request: SendOTPRequest):
    """Send OTP to mobile number for authentication"""
    # Basic validation
    if not request.mobile_number or len(request.mobile_number) < 10:
        raise HTTPException(status_code=400, detail="Invalid mobile number")

    # Normalize mobile number (add +91 prefix if not present for Indian numbers)
    mobile_number = request.mobile_number.strip()
    if not mobile_number.startswith('+'):
        if mobile_number.startswith('91') and len(mobile_number) == 12:
            mobile_number = f"+{mobile_number}"
        elif len(mobile_number) == 10:
            mobile_number = f"+91{mobile_number}"
        else:
            raise HTTPException(status_code=400, detail="Invalid mobile number format")

    db: Session = SessionLocal()
    try:
        # Clean up expired OTPs
        now = datetime.utcnow()
        db.query(OTPModel).filter(OTPModel.expires_at <= now).delete()
        db.commit()

        # Check rate limiting - max 3 OTP requests per mobile number per hour
        recent_otps = db.query(OTPModel).filter(
            OTPModel.mobile_number == mobile_number,
            OTPModel.created_at >= now - timedelta(hours=1)
        ).count()

        if recent_otps >= 3:
            raise HTTPException(status_code=429, detail="Too many OTP requests. Please try again later.")

        # Generate a 6-digit OTP
        otp = str(secrets.randbelow(900000) + 100000)
        otp_hash = _hash_token(otp)
        otp_id = secrets.token_hex(16)

        # Store OTP in database
        otp_record = OTPModel(
            mobile_number=mobile_number,
            otp_code=otp,  # Store plain OTP for debugging (in production, only store hash)
            otp_hash=otp_hash,
            expires_at=now + timedelta(seconds=OTP_TTL_SECONDS),
            created_at=now
        )
        db.add(otp_record)
        db.commit()

        # Send SMS
        sms_sent = _send_otp_sms(mobile_number, otp)

        if not sms_sent:
            logger.warning("auth.otp.sms_failed_fallback mobile_number=%s otp=%s", mobile_number, otp)
            print(f"FALLBACK: OTP for {mobile_number} is {otp}")

        logger.info("auth.otp.sent mobile_number=%s otp_id=%s sms_sent=%s", mobile_number, otp_id, sms_sent)

        msg = "OTP sent successfully" if sms_sent else "OTP created but SMS delivery failed — check Fast2SMS account"
        return OTPResponse(
            status="ok" if sms_sent else "sms_failed",
            message=msg,
            otp_id=otp_id,
            expires_in_seconds=OTP_TTL_SECONDS
        )

    finally:
        db.close()


@router.post("/verify-otp", response_model=AuthResponse)
def verify_otp(request: VerifyOTPRequest):
    """Verify OTP and authenticate user"""
    # Basic validation
    if not request.mobile_number or not request.otp:
        raise HTTPException(status_code=400, detail="Mobile number and OTP are required")

    if len(request.otp) != 6 or not request.otp.isdigit():
        raise HTTPException(status_code=400, detail="Invalid OTP format")

    # Normalize mobile number
    mobile_number = request.mobile_number.strip()
    if not mobile_number.startswith('+'):
        if mobile_number.startswith('91') and len(mobile_number) == 12:
            mobile_number = f"+{mobile_number}"
        elif len(mobile_number) == 10:
            mobile_number = f"+91{mobile_number}"
        else:
            raise HTTPException(status_code=400, detail="Invalid mobile number format")

    db: Session = SessionLocal()
    try:
        now = datetime.utcnow()

        # Find the most recent unused OTP for this mobile number
        otp_record = db.query(OTPModel).filter(
            OTPModel.mobile_number == mobile_number,
            OTPModel.used_at.is_(None),
            OTPModel.expires_at > now
        ).order_by(OTPModel.created_at.desc()).first()

        if not otp_record:
            raise HTTPException(status_code=400, detail="OTP not found or expired")

        # Check attempts
        if otp_record.attempts >= OTP_MAX_ATTEMPTS:
            raise HTTPException(status_code=400, detail="Too many failed attempts")

        # Verify OTP
        otp_hash = _hash_token(request.otp)
        if otp_hash != otp_record.otp_hash:
            # Increment attempts
            otp_record.attempts += 1
            db.commit()
            remaining_attempts = OTP_MAX_ATTEMPTS - otp_record.attempts
            if remaining_attempts > 0:
                raise HTTPException(status_code=400, detail=f"Invalid OTP. {remaining_attempts} attempts remaining")
            else:
                raise HTTPException(status_code=400, detail="Invalid OTP. No attempts remaining")

        # Mark OTP as used
        otp_record.used_at = now
        db.commit()

        # Find or create user
        user = db.query(UserModel).filter(UserModel.mobile_number == mobile_number).first()

        if not user:
            # Create new user for OTP login
            username = f"mobile_{mobile_number.replace('+', '')}"
            email = f"{username}@bysel.com"

            # Check if username/email already exists
            existing = db.query(UserModel).filter(
                (UserModel.username == username) | (UserModel.email == email)
            ).first()

            if existing:
                # Update existing user with mobile number
                existing.mobile_number = mobile_number
                user = existing
            else:
                # Create new user
                user = UserModel(
                    username=username,
                    email=email,
                    mobile_number=mobile_number,
                    password_hash=_hash_password(secrets.token_hex(16)),  # Random password
                    created_at=now,
                )
                db.add(user)
                db.commit()
                db.refresh(user)

                # Create wallet for new user
                wallet = WalletModel(
                    user_id=user.id,
                    balance=10000.0,  # Starting balance
                    updated_at=now
                )
                db.add(wallet)
                db.commit()

        # Generate tokens
        token_version = int(getattr(user, "token_version", 0) or 0)
        access_token, refresh_token = _create_auth_tokens(user_id=user.id, token_version=token_version)

        logger.info("auth.otp.login_success user_id=%s mobile_number=%s", user.id, mobile_number)

        return AuthResponse(
            status="ok",
            user_id=user.id,
            access_token=access_token,
            refresh_token=refresh_token
        )

    finally:
        db.close()
