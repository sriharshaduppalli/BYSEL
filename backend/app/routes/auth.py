from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from passlib.context import CryptContext
from pydantic import BaseModel
from ..database.db import SessionLocal, UserModel, WalletModel
from datetime import datetime

router = APIRouter()

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

class UserRegister(BaseModel):
    username: str
    email: str
    password: str

class UserLogin(BaseModel):
    username: str
    password: str

@router.post("/register")
def register(user: UserRegister):
    # Ensure password is string and truncate to 72 chars
    password = str(user.password)[:72]
    if len(password) > 72:
        raise HTTPException(status_code=400, detail="Password must be 72 characters or less.")
    db: Session = SessionLocal()
    existing = db.query(UserModel).filter((UserModel.username == user.username) | (UserModel.email == user.email)).first()
    if existing:
        db.close()
        raise HTTPException(status_code=400, detail="Username or email already exists")
    hashed = pwd_context.hash(password)
    new_user = UserModel(username=user.username, email=user.email, password_hash=hashed, created_at=datetime.utcnow())
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    # Create wallet for new user with zero balance
    wallet = WalletModel(user_id=new_user.id, balance=0.0)
    db.add(wallet)
    db.commit()
    user_id = new_user.id
    db.close()
    return {"status": "ok", "user_id": user_id}

@router.post("/login")
def login(user: UserLogin):
    if len(user.password) > 72:
        raise HTTPException(status_code=400, detail="Password must be 72 characters or less.")
    db: Session = SessionLocal()
    db_user = db.query(UserModel).filter(UserModel.username == user.username).first()
    if not db_user or not pwd_context.verify(user.password, db_user.password_hash):
        db.close()
        raise HTTPException(status_code=401, detail="Invalid username or password")
    db.close()
    return {"status": "ok", "user_id": db_user.id}
