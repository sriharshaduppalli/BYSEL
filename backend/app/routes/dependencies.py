import os

from fastapi import Header, HTTPException, Depends
from sqlalchemy.orm import Session
from ..database.db import SessionLocal, UserModel
from .auth import validate_access_token_and_get_user


def _is_truthy_env(value: str | None) -> bool:
    return (value or "").strip().lower() in {"1", "true", "yes", "on"}


ALLOW_USER_ID_HEADER_FALLBACK = _is_truthy_env(os.getenv("ALLOW_USER_ID_HEADER_FALLBACK"))

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def get_current_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
    # Accept both FastAPI-normalized "user-id" and literal "user_id" from Android.
    user_id: int | None = Header(default=None, alias="user-id"),
    user_id_underscore: int | None = Header(default=None, alias="user_id", convert_underscores=False),
    db: Session = Depends(get_db)
):
    resolved_user_id: int | None = None

    if authorization and authorization.startswith("Bearer "):
        token = authorization.split(" ", 1)[1].strip()
        user = validate_access_token_and_get_user(token, db)
        resolved_user_id = int(user.id)
    elif ALLOW_USER_ID_HEADER_FALLBACK:
        resolved_user_id = user_id if user_id is not None else user_id_underscore

    if resolved_user_id is None:
        raise HTTPException(status_code=401, detail="Missing authentication")

    user = db.query(UserModel).filter(UserModel.id == resolved_user_id).first()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    return user


def get_optional_current_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
    db: Session = Depends(get_db),
):
    """Return authenticated user when a Bearer token is present; otherwise None.

    Used for endpoints that personalise when logged in (e.g. /ai/ask) but still
    allow anonymous website demos.
    """
    if not authorization or not authorization.startswith("Bearer "):
        return None
    try:
        token = authorization.split(" ", 1)[1].strip()
        if not token:
            return None
        return validate_access_token_and_get_user(token, db)
    except HTTPException:
        return None
    except Exception:
        return None
