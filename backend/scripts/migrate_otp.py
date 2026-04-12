#!/usr/bin/env python3
"""
Database migration script to add OTP support and mobile_number field to users table.
Run this script to update the database schema.
"""

import sys
import os
from pathlib import Path

# Add the app directory to the Python path
backend_dir = Path(__file__).parent
app_dir = backend_dir / "app"
sys.path.insert(0, str(backend_dir))
sys.path.insert(0, str(app_dir))

from sqlalchemy import create_engine, text
from database.db import DATABASE_URL

def migrate_database():
    """Add mobile_number column to users table and create otps table"""

    engine = create_engine(DATABASE_URL)

    with engine.connect() as conn:
        # Add mobile_number column to users table
        try:
            print("Adding mobile_number column to users table...")
            conn.execute(text("ALTER TABLE users ADD COLUMN mobile_number VARCHAR UNIQUE"))
            conn.commit()
            print("✓ Added mobile_number column")
        except Exception as e:
            print(f"Note: mobile_number column may already exist: {e}")

        # Create otps table
        try:
            print("Creating otps table...")
            conn.execute(text("""
                CREATE TABLE otps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    mobile_number VARCHAR NOT NULL,
                    otp_code VARCHAR NOT NULL,
                    otp_hash VARCHAR UNIQUE NOT NULL,
                    expires_at DATETIME NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    used_at DATETIME,
                    attempts INTEGER DEFAULT 0
                )
            """))
            conn.execute(text("CREATE INDEX ix_otps_mobile_number ON otps(mobile_number)"))
            conn.execute(text("CREATE UNIQUE INDEX ix_otps_otp_hash ON otps(otp_hash)"))
            conn.commit()
            print("✓ Created otps table")
        except Exception as e:
            print(f"Note: otps table may already exist: {e}")

        print("Migration completed successfully!")

if __name__ == "__main__":
    migrate_database()