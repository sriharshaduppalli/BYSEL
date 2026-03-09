from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, create_engine, text, inspect as sa_inspect
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from datetime import datetime
from pathlib import Path
import os

def _select_default_sqlite_path() -> Path:
    backend_db = Path(__file__).resolve().parents[2] / "bysel.db"
    root_db = Path(__file__).resolve().parents[3] / "bysel.db"
    candidates = [backend_db, root_db]
    existing = [path for path in candidates if path.exists()]
    if not existing:
        return backend_db
    return max(existing, key=lambda path: path.stat().st_mtime)


_DEFAULT_SQLITE_DB = _select_default_sqlite_path()
_DEFAULT_DATABASE_URL = f"sqlite:///{_DEFAULT_SQLITE_DB.as_posix()}"
DATABASE_URL = os.getenv("DATABASE_URL", _DEFAULT_DATABASE_URL)

engine = create_engine(
    DATABASE_URL, 
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {}
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

class QuoteModel(Base):
    __tablename__ = "quotes"
    id = Column(Integer, primary_key=True, index=True)
    symbol = Column(String, unique=True, index=True)
    last_price = Column(Float)
    pct_change = Column(Float)
    updated_at = Column(DateTime, default=datetime.utcnow)

class HoldingModel(Base):
    __tablename__ = "holdings"
    id = Column(Integer, primary_key=True, index=True)
    symbol = Column(String, index=True)
    quantity = Column(Integer)
    avg_price = Column(Float)
    last_price = Column(Float)
    pnl = Column(Float)
    created_at = Column(DateTime, default=datetime.utcnow)

class AlertModel(Base):
    __tablename__ = "alerts"
    id = Column(Integer, primary_key=True, index=True)
    symbol = Column(String, index=True)
    threshold_price = Column(Float)
    alert_type = Column(String)  # ABOVE or BELOW
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)

class OrderModel(Base):
    __tablename__ = "orders"
    id = Column(Integer, primary_key=True, index=True)
    symbol = Column(String, index=True)
    quantity = Column(Integer)
    side = Column(String)  # BUY or SELL
    price = Column(Float, default=0.0)
    total = Column(Float, default=0.0)
    status = Column(String, default="COMPLETED")
    idempotency_key = Column(String, nullable=True, index=True)
    request_fingerprint = Column(String, nullable=True, index=True)
    trace_id = Column(String, nullable=True, index=True)
    created_at = Column(DateTime, default=datetime.utcnow)


# --- User Model ---
class UserModel(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String, unique=True, index=True, nullable=False)
    email = Column(String, unique=True, index=True, nullable=False)
    password_hash = Column(String, nullable=False)
    token_version = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)

# --- Wallet Model (per user) ---
class WalletModel(Base):
    __tablename__ = "wallet"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True)
    balance = Column(Float, default=0.0)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class RefreshTokenModel(Base):
    __tablename__ = "refresh_tokens"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True)
    token_hash = Column(String, unique=True, index=True, nullable=False)
    expires_at = Column(DateTime, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    used_at = Column(DateTime, nullable=True)
    last_used_at = Column(DateTime, nullable=True)
    revoked_at = Column(DateTime, nullable=True)
    replaced_by_hash = Column(String, nullable=True)
    client_ip = Column(String, nullable=True)
    device_info = Column(String, nullable=True)


class MutualFundModel(Base):
    __tablename__ = "mutual_funds"
    id = Column(Integer, primary_key=True, index=True)
    scheme_code = Column(String, unique=True, index=True, nullable=False)
    scheme_name = Column(String, nullable=False)
    category = Column(String, index=True, nullable=False)
    nav = Column(Float, default=0.0)
    nav_date = Column(String, nullable=False)
    returns_1y = Column(Float, nullable=True)
    returns_3y = Column(Float, nullable=True)
    returns_5y = Column(Float, nullable=True)
    fund_house = Column(String, nullable=True)
    risk_level = Column(String, nullable=True)


class SipPlanModel(Base):
    __tablename__ = "sip_plans"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True)
    scheme_code = Column(String, nullable=False, index=True)
    scheme_name = Column(String, nullable=False)
    amount = Column(Float, nullable=False)
    frequency = Column(String, default="MONTHLY")
    day_of_month = Column(Integer, default=5)
    next_installment_date = Column(String, nullable=False)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)


class IPOModel(Base):
    __tablename__ = "ipos"
    id = Column(Integer, primary_key=True, index=True)
    ipo_id = Column(String, unique=True, index=True, nullable=False)
    company_name = Column(String, nullable=False)
    symbol = Column(String, index=True, nullable=False)
    status = Column(String, index=True, nullable=False)
    issue_open_date = Column(String, nullable=False)
    issue_close_date = Column(String, nullable=False)
    listing_date = Column(String, nullable=True)
    price_band_min = Column(Float, nullable=True)
    price_band_max = Column(Float, nullable=True)
    lot_size = Column(Integer, nullable=True)


class IPOApplicationModel(Base):
    __tablename__ = "ipo_applications"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True)
    ipo_id = Column(String, nullable=False, index=True)
    lots = Column(Integer, nullable=False)
    bid_price = Column(Float, nullable=False)
    upi_id = Column(String, nullable=False)
    status = Column(String, default="PENDING")
    created_at = Column(DateTime, default=datetime.utcnow)


class ETFModel(Base):
    __tablename__ = "etfs"
    id = Column(Integer, primary_key=True, index=True)
    symbol = Column(String, unique=True, index=True, nullable=False)
    name = Column(String, nullable=False)
    category = Column(String, index=True, nullable=False)
    last = Column(Float, default=0.0)
    pct_change = Column(Float, default=0.0)
    aum_cr = Column(Float, nullable=True)
    expense_ratio = Column(Float, nullable=True)

Base.metadata.create_all(bind=engine)


def _table_has_column(table_name: str, column_name: str) -> bool:
    inspector = sa_inspect(engine)
    if table_name not in inspector.get_table_names():
        return False
    existing_columns = {column["name"] for column in inspector.get_columns(table_name)}
    return column_name in existing_columns


def _ensure_column(table_name: str, column_name: str, column_ddl: str) -> None:
    if _table_has_column(table_name, column_name):
        return
    with engine.connect() as connection:
        connection.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {column_ddl}"))
        connection.commit()


def _ensure_refresh_token_columns() -> None:
    _ensure_column("refresh_tokens", "last_used_at", "last_used_at TIMESTAMP NULL")
    _ensure_column("refresh_tokens", "client_ip", "client_ip VARCHAR NULL")
    _ensure_column("refresh_tokens", "device_info", "device_info VARCHAR NULL")


def _ensure_user_columns() -> None:
    _ensure_column("users", "token_version", "token_version INTEGER NOT NULL DEFAULT 0")


def _ensure_order_columns() -> None:
    _ensure_column("orders", "idempotency_key", "idempotency_key VARCHAR NULL")
    _ensure_column("orders", "request_fingerprint", "request_fingerprint VARCHAR NULL")
    _ensure_column("orders", "trace_id", "trace_id VARCHAR NULL")


_ensure_refresh_token_columns()
_ensure_user_columns()
_ensure_order_columns()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
