"""BYSEL Backend API.

Importing this package must stay cheap. Building the FastAPI app (and every
route) happens only when something asks for `app` — `uvicorn app:app` or
`from app import app`. Unit tests can import helpers like `risk_lab_payload`
without 280 FastAPI deprecation warnings.
"""

from .ism_bootstrap import ensure_ism_on_path

ensure_ism_on_path()

__all__ = ["app"]


def __getattr__(name: str):
    if name == "app":
        from .main import app as _fastapi_app
        return _fastapi_app
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
