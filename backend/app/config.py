import os
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

def _select_default_sqlite_path() -> Path:
	backend_db = Path(__file__).resolve().parents[1] / "bysel.db"
	root_db = Path(__file__).resolve().parents[2] / "bysel.db"
	candidates = [backend_db, root_db]
	existing = [path for path in candidates if path.exists()]
	if not existing:
		return backend_db
	return max(existing, key=lambda path: path.stat().st_mtime)


_DEFAULT_SQLITE_DB = _select_default_sqlite_path()
_DEFAULT_DATABASE_URL = f"sqlite:///{_DEFAULT_SQLITE_DB.as_posix()}"
DATABASE_URL = os.getenv("DATABASE_URL", _DEFAULT_DATABASE_URL)
API_HOST = os.getenv("API_HOST", "0.0.0.0")
API_PORT = int(os.getenv("API_PORT", 8000))
DEBUG = os.getenv("DEBUG", "True").lower() == "true"

SYMBOLS = ["RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN", "WIPRO", "BAJAJFINSV", "HDFC", "LT", "MARUTI"]
