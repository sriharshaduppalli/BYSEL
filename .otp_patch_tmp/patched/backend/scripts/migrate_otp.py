#!/usr/bin/env python3
"""Apply the minimal schema changes required for OTP login."""

from pathlib import Path
import sys

from sqlalchemy import create_engine, text


BACKEND_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_DIR))

from app.database.db import DATABASE_URL


def migrate_database() -> None:
    engine = create_engine(DATABASE_URL)

    with engine.begin() as connection:
        try:
            connection.execute(text("ALTER TABLE users ADD COLUMN mobile_number VARCHAR"))
        except Exception:
            pass

        connection.execute(
            text(
                """
                CREATE TABLE IF NOT EXISTS otps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    mobile_number VARCHAR NOT NULL,
                    otp_hash VARCHAR NOT NULL,
                    expires_at DATETIME NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    used_at DATETIME,
                    attempts INTEGER NOT NULL DEFAULT 0
                )
                """
            )
        )
        connection.execute(text("CREATE INDEX IF NOT EXISTS ix_otps_mobile_number ON otps(mobile_number)"))
        connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_otps_otp_hash ON otps(otp_hash)"))
        connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_mobile_number ON users(mobile_number) WHERE mobile_number IS NOT NULL"))


if __name__ == "__main__":
    migrate_database()