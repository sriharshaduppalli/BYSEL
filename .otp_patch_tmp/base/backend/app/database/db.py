from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, create_engine, text, inspect as sa_inspect
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from datetime import datetime
from pathlib import Path
import logging
import os
import sqlite3

logger = logging.getLogger(__name__)

def _select_default_sqlite_path() -> Path:
    override_path = os.getenv("SQLITE_DB_PATH", "").strip()
    if override_path:
        return Path(override_path).expanduser().resolve()

    backend_db = Path(__file__).resolve().parents[2] / "bysel.db"
    # Always prefer backend-local DB to avoid cwd-dependent auth splits.
    return backend_db


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
    user_id = Column(Integer, nullable=False, index=True, default=1)
    quantity = Column(Integer)
    side = Column(String)  # BUY or SELL
    order_type = Column(String, default="MARKET")
    validity = Column(String, default="DAY")
    limit_price = Column(Float, nullable=True)
    trigger_price = Column(Float, nullable=True)
    basket_id = Column(Integer, nullable=True)
    tag = Column(String, nullable=True)
    price = Column(Float, default=0.0)
    total = Column(Float, default=0.0)
    status = Column(String, default="COMPLETED")
    idempotency_key = Column(String, nullable=True, index=True)
    request_fingerprint = Column(String, nullable=True, index=True)
    trace_id = Column(String, nullable=True, index=True)
    created_at = Column(DateTime, default=datetime.utcnow)


class TriggerOrderModel(Base):
    __tablename__ = "trigger_orders"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True, default=1)
    symbol = Column(String, nullable=False, index=True)
    quantity = Column(Integer, nullable=False)
    side = Column(String, nullable=False)
    order_type = Column(String, nullable=False, default="LIMIT")
    validity = Column(String, nullable=False, default="GTC")
    limit_price = Column(Float, nullable=True)
    trigger_price = Column(Float, nullable=True)
    status = Column(String, nullable=False, default="PENDING")
    tag = Column(String, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class BasketOrderModel(Base):
    __tablename__ = "basket_orders"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True, default=1)
    name = Column(String, nullable=False)
    status = Column(String, nullable=False, default="DRAFT")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class BasketOrderLegModel(Base):
    __tablename__ = "basket_order_legs"
    id = Column(Integer, primary_key=True, index=True)
    basket_id = Column(Integer, nullable=False, index=True)
    symbol = Column(String, nullable=False, index=True)
    quantity = Column(Integer, nullable=False)
    side = Column(String, nullable=False)
    order_type = Column(String, nullable=False, default="MARKET")
    validity = Column(String, nullable=False, default="DAY")
    limit_price = Column(Float, nullable=True)
    trigger_price = Column(Float, nullable=True)
    tag = Column(String, nullable=True)


class FamilyMemberModel(Base):
    __tablename__ = "family_members"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True, default=1)
    name = Column(String, nullable=False)
    relation = Column(String, nullable=False)
    equity_value = Column(Float, nullable=False, default=0.0)
    mutual_fund_value = Column(Float, nullable=False, default=0.0)
    us_value = Column(Float, nullable=False, default=0.0)
    cash_value = Column(Float, nullable=False, default=0.0)
    liabilities_value = Column(Float, nullable=False, default=0.0)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class GoalPlanModel(Base):
    __tablename__ = "goal_plans"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True, default=1)
    goal_name = Column(String, nullable=False)
    target_amount = Column(Float, nullable=False)
    current_amount = Column(Float, nullable=False, default=0.0)
    target_date = Column(String, nullable=False)
    monthly_contribution = Column(Float, nullable=False, default=0.0)
    risk_profile = Column(String, nullable=False, default="MODERATE")
    linked_instruments = Column(String, nullable=False, default="")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


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


class PasswordResetTokenModel(Base):
    __tablename__ = "password_reset_tokens"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True)
    token_hash = Column(String, unique=True, index=True, nullable=False)
    expires_at = Column(DateTime, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    used_at = Column(DateTime, nullable=True)
    revoked_at = Column(DateTime, nullable=True)


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
    inspector = sa_inspect(engine)
    if table_name not in inspector.get_table_names():
        return
    if _table_has_column(table_name, column_name):
        return
    with engine.connect() as connection:
        connection.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {column_ddl}"))
        connection.commit()


def _ensure_refresh_token_columns() -> None:
    _ensure_column("refresh_tokens", "user_id", "user_id INTEGER NULL")
    _ensure_column("refresh_tokens", "token_hash", "token_hash VARCHAR NULL")
    _ensure_column("refresh_tokens", "expires_at", "expires_at TIMESTAMP NULL")
    _ensure_column("refresh_tokens", "created_at", "created_at TIMESTAMP NULL")
    _ensure_column("refresh_tokens", "used_at", "used_at TIMESTAMP NULL")
    _ensure_column("refresh_tokens", "revoked_at", "revoked_at TIMESTAMP NULL")
    _ensure_column("refresh_tokens", "replaced_by_hash", "replaced_by_hash VARCHAR NULL")
    _ensure_column("refresh_tokens", "last_used_at", "last_used_at TIMESTAMP NULL")
    _ensure_column("refresh_tokens", "client_ip", "client_ip VARCHAR NULL")
    _ensure_column("refresh_tokens", "device_info", "device_info VARCHAR NULL")


def _ensure_user_columns() -> None:
    _ensure_column("users", "email", "email VARCHAR NULL")
    _ensure_column("users", "password_hash", "password_hash VARCHAR NULL")
    _ensure_column("users", "created_at", "created_at TIMESTAMP NULL")
    _ensure_column("users", "token_version", "token_version INTEGER NOT NULL DEFAULT 0")


def _ensure_order_columns() -> None:
    _ensure_column("orders", "user_id", "user_id INTEGER NOT NULL DEFAULT 1")
    _ensure_column("orders", "order_type", "order_type VARCHAR NULL")
    _ensure_column("orders", "validity", "validity VARCHAR NULL")
    _ensure_column("orders", "limit_price", "limit_price FLOAT NULL")
    _ensure_column("orders", "trigger_price", "trigger_price FLOAT NULL")
    _ensure_column("orders", "basket_id", "basket_id INTEGER NULL")
    _ensure_column("orders", "tag", "tag VARCHAR NULL")
    _ensure_column("orders", "idempotency_key", "idempotency_key VARCHAR NULL")
    _ensure_column("orders", "request_fingerprint", "request_fingerprint VARCHAR NULL")
    _ensure_column("orders", "trace_id", "trace_id VARCHAR NULL")


def _active_sqlite_db_path() -> Path | None:
    if engine.url.get_backend_name() != "sqlite":
        return None

    raw_path = engine.url.database
    if not raw_path or raw_path == ":memory:":
        return None

    return Path(raw_path).expanduser().resolve()


def _legacy_sqlite_candidates(active_db_path: Path) -> list[Path]:
    candidates: list[Path] = []
    seen: set[Path] = set()

    # Include nearby ancestors (repo root and workspace root) where legacy DBs commonly lived.
    for ancestor in list(active_db_path.parents)[:4]:
        candidate = (ancestor / "bysel.db").resolve()
        if candidate == active_db_path or not candidate.exists() or candidate in seen:
            continue
        seen.add(candidate)
        candidates.append(candidate)

    return candidates


def _table_names(conn: sqlite3.Connection) -> set[str]:
    rows = conn.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()
    return {row["name"] for row in rows}


def _merge_legacy_auth_rows_into_active_db() -> None:
    active_db_path = _active_sqlite_db_path()
    if active_db_path is None or not active_db_path.exists():
        return

    legacy_paths = _legacy_sqlite_candidates(active_db_path)
    if not legacy_paths:
        return

    dst_conn = sqlite3.connect(str(active_db_path))
    dst_conn.row_factory = sqlite3.Row

    try:
        dst_tables = _table_names(dst_conn)
        if "users" not in dst_tables or "wallet" not in dst_tables:
            return

        existing_keys: set[str] = set()
        for row in dst_conn.execute("SELECT username, email FROM users").fetchall():
            username = (row["username"] or "").strip().lower()
            email = (row["email"] or "").strip().lower()
            if username:
                existing_keys.add(username)
            if email:
                existing_keys.add(email)

        for src_path in legacy_paths:
            src_conn: sqlite3.Connection | None = None
            try:
                src_conn = sqlite3.connect(str(src_path))
                src_conn.row_factory = sqlite3.Row

                src_tables = _table_names(src_conn)
                if "users" not in src_tables:
                    continue

                migrated_users = 0
                migrated_wallets = 0
                migrated_user_map: dict[int, int] = {}

                user_rows = src_conn.execute("SELECT * FROM users").fetchall()
                for user_row in user_rows:
                    row_keys = set(user_row.keys())
                    username = (user_row["username"] if "username" in row_keys else "") or ""
                    email = (user_row["email"] if "email" in row_keys else "") or ""
                    password_hash = (user_row["password_hash"] if "password_hash" in row_keys else "") or ""
                    token_version = int((user_row["token_version"] if "token_version" in row_keys else 0) or 0)
                    created_at = user_row["created_at"] if "created_at" in row_keys else None

                    username = username.strip()
                    email = email.strip().lower()
                    password_hash = password_hash.strip()

                    if not username or not email or not password_hash:
                        continue

                    username_key = username.lower()
                    email_key = email.lower()
                    if username_key in existing_keys or email_key in existing_keys:
                        continue

                    cursor = dst_conn.execute(
                        """
                        INSERT INTO users (username, email, password_hash, token_version, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        (username, email, password_hash, token_version, created_at),
                    )
                    migrated_users += 1
                    existing_keys.add(username_key)
                    existing_keys.add(email_key)
                    migrated_user_map[int(user_row["id"])] = int(cursor.lastrowid)

                if migrated_user_map and "wallet" in src_tables:
                    for src_user_id, dst_user_id in migrated_user_map.items():
                        has_wallet = dst_conn.execute(
                            "SELECT 1 FROM wallet WHERE user_id = ? LIMIT 1",
                            (dst_user_id,),
                        ).fetchone()
                        if has_wallet is not None:
                            continue

                        wallet_row = src_conn.execute(
                            "SELECT * FROM wallet WHERE user_id = ? ORDER BY id ASC LIMIT 1",
                            (src_user_id,),
                        ).fetchone()
                        if wallet_row is None:
                            continue

                        wallet_keys = set(wallet_row.keys())
                        balance = float((wallet_row["balance"] if "balance" in wallet_keys else 0.0) or 0.0)
                        updated_at = wallet_row["updated_at"] if "updated_at" in wallet_keys else None
                        dst_conn.execute(
                            "INSERT INTO wallet (user_id, balance, updated_at) VALUES (?, ?, ?)",
                            (dst_user_id, balance, updated_at),
                        )
                        migrated_wallets += 1

                if migrated_users > 0 or migrated_wallets > 0:
                    dst_conn.commit()
                    logger.warning(
                        "db.auth_legacy_merge.applied source=%s users=%s wallets=%s target=%s",
                        str(src_path),
                        migrated_users,
                        migrated_wallets,
                        str(active_db_path),
                    )
            except Exception as exc:
                logger.exception(
                    "db.auth_legacy_merge.failed source=%s target=%s reason=%s",
                    str(src_path),
                    str(active_db_path),
                    str(exc),
                )
            finally:
                if src_conn is not None:
                    src_conn.close()
    finally:
        dst_conn.close()


_ensure_refresh_token_columns()
_ensure_user_columns()
_ensure_order_columns()
_merge_legacy_auth_rows_into_active_db()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
