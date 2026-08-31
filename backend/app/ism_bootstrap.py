"""Put the pinned Indian_stock_market package on sys.path.

BYSEL tracks tag bysel-ism-v2026.08.31 via backend/vendor/indian_stock_market.
"""
from __future__ import annotations

import sys
from pathlib import Path

_BACKEND_ROOT = Path(__file__).resolve().parent.parent
_VENDOR_SRC = _BACKEND_ROOT / "vendor" / "indian_stock_market" / "src"
_LOCAL_PKG = _BACKEND_ROOT / "indian_stock_llm"


def ism_package_dir() -> Path:
    if (_VENDOR_SRC / "indian_stock_llm" / "__init__.py").is_file():
        return _VENDOR_SRC / "indian_stock_llm"
    return _LOCAL_PKG


def ensure_ism_on_path() -> Path:
    pkg = ism_package_dir()
    src = str(pkg.parent)
    if src not in sys.path:
        sys.path.insert(0, src)
    return pkg
