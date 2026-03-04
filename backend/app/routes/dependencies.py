from fastapi import Header, HTTPException, Depends
from sqlalchemy.orm import Session
from ..database.db import SessionLocal, UserModel
from .auth import validate_access_token_and_get_user

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def get_current_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
    user_id: int | None = Header(default=None),
    db: Session = Depends(get_db)
):
    resolved_user_id: int | None = None

    if authorization and authorization.startswith("Bearer "):
        token = authorization.split(" ", 1)[1].strip()
        user = validate_access_token_and_get_user(token, db)
        resolved_user_id = int(user.id)
    elif user_id is not None:
        resolved_user_id = user_id

    if resolved_user_id is None:
        raise HTTPException(status_code=401, detail="Missing authentication")

    user = db.query(UserModel).filter(UserModel.id == resolved_user_id).first()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    return user
