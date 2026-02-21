from fastapi import Header, HTTPException, Depends
from sqlalchemy.orm import Session
from ..database.db import SessionLocal, UserModel

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def get_current_user(user_id: int = Header(...), db: Session = Depends(get_db)):
    user = db.query(UserModel).filter(UserModel.id == user_id).first()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid or missing user_id header")
    return user
