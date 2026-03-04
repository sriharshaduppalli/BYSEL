from fastapi import APIRouter, Depends, HTTPException, Header, Request
from sqlalchemy import text
from sqlalchemy.orm import Session
from passlib.context import CryptContext
from pydantic import BaseModel
from ..database.db import SessionLocal, UserModel, WalletModel, RefreshTokenModel
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
import time
import secrets

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

_login_rate_buckets: dict[str, deque[float]] = defaultdict(deque)
_refresh_rate_buckets: dict[str, deque[float]] = defaultdict(deque)
_login_failure_buckets: dict[str, deque[float]] = defaultdict(deque)
_login_lockouts: dict[str, float] = {}
_rate_limit_lock = Lock()
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
        bucket = buckets[key]
        while bucket and bucket[0] <= now - window_seconds:
            bucket.popleft()

        if len(bucket) >= max_attempts:
            raise HTTPException(status_code=429, detail=message)

        bucket.append(now)


def _mask_identifier(value: str) -> str:
    normalized = value.strip()
    if not normalized:
        return "unknown"
    if len(normalized) <= 2:
        return "**"
    return f"{normalized[:2]}***"


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


def _reset_debug_state() -> None:
    with _rate_limit_lock:
        _login_rate_buckets.clear()
        _refresh_rate_buckets.clear()
        _login_failure_buckets.clear()
        _login_lockouts.clear()

    with _metrics_lock:
        _auth_metrics.clear()


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
        existing = db.query(UserModel).filter((UserModel.username == user.username) | (UserModel.email == user.email)).first()
        if existing:
            _metric_inc("register.failure_user_exists")
            raise HTTPException(status_code=400, detail="Username or email already exists")

        stage = "hash_password"
        hashed = _hash_password(password)

        stage = "insert_user"
        new_user = UserModel(username=user.username, email=user.email, password_hash=hashed, created_at=datetime.utcnow())
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
        logger.info("auth.register.success user_id=%s username=%s", user_id, _mask_identifier(user.username))
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
    username_key = user.username.strip().lower()
    login_key = f"{client_ip}:{username_key}"
    try:
        _enforce_login_lockout(login_key)
    except HTTPException:
        _metric_inc("login.lockout_blocked")
        logger.warning("auth.login.lockout_blocked ip=%s username=%s", client_ip, _mask_identifier(user.username))
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
        logger.warning("auth.login.rate_limited ip=%s username=%s", client_ip, _mask_identifier(user.username))
        raise

    if len(user.password) > 72:
        _metric_inc("login.failure_password_too_long")
        raise HTTPException(status_code=400, detail="Password must be 72 characters or less.")
    db: Session = SessionLocal()
    try:
        _prune_stale_refresh_tokens(db)
        db_user = db.query(UserModel).filter(UserModel.username == user.username).first()
        if not db_user or not _verify_password(user.password, db_user.password_hash):
            _metric_inc("login.failure_invalid_credentials")
            lockout_triggered = _record_login_failure(login_key)
            if lockout_triggered:
                _metric_inc("login.lockout_triggered")
                logger.warning("auth.login.lockout_triggered ip=%s username=%s", client_ip, _mask_identifier(user.username))
            logger.warning("auth.login.failed ip=%s username=%s reason=invalid_credentials", client_ip, _mask_identifier(user.username))
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
        logger.info("auth.login.success ip=%s user_id=%s username=%s", client_ip, db_user.id, _mask_identifier(user.username))
        return AuthResponse(
            status="ok",
            user_id=db_user.id,
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
            db_user.token_version = current_token_version + 1
            db.query(RefreshTokenModel).filter(
                RefreshTokenModel.user_id == user_id,
                RefreshTokenModel.revoked_at.is_(None)
            ).update({"revoked_at": now}, synchronize_session=False)
            db.commit()
            _metric_inc("refresh.failure_invalid_token")
            logger.warning("auth.refresh.failed ip=%s user_id=%s reason=invalid_refresh_token", client_ip, user_id)
            raise HTTPException(status_code=401, detail="Invalid refresh token")

        if stored_token.revoked_at is not None or stored_token.used_at is not None:
            db_user.token_version = current_token_version + 1
            db.query(RefreshTokenModel).filter(
                RefreshTokenModel.user_id == user_id,
                RefreshTokenModel.revoked_at.is_(None)
            ).update({"revoked_at": now}, synchronize_session=False)
            db.commit()
            _metric_inc("refresh.failure_reuse_detected")
            logger.warning("auth.refresh.failed ip=%s user_id=%s reason=token_reuse_detected", client_ip, user_id)
            raise HTTPException(status_code=401, detail="Refresh token reuse detected")

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
