import os
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

def _select_default_sqlite_path() -> Path:
	override_path = os.getenv("SQLITE_DB_PATH", "").strip()
	if override_path:
		return Path(override_path).expanduser().resolve()

	backend_db = Path(__file__).resolve().parents[1] / "bysel.db"
	# Always prefer backend-local DB to avoid cwd-dependent auth splits.
	return backend_db


_DEFAULT_SQLITE_DB = _select_default_sqlite_path()
_DEFAULT_DATABASE_URL = f"sqlite:///{_DEFAULT_SQLITE_DB.as_posix()}"
DATABASE_URL = os.getenv("DATABASE_URL", _DEFAULT_DATABASE_URL)
API_HOST = os.getenv("API_HOST", "0.0.0.0")
API_PORT = int(os.getenv("API_PORT", 8000))
DEBUG = os.getenv("DEBUG", "True").lower() == "true"

SYMBOLS = ["RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN", "WIPRO", "BAJAJFINSV", "HDFC", "LT", "MARUTI"]
