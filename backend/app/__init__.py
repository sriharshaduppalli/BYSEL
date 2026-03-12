"""BYSEL Backend API"""

import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import logging
from .routes import router
from .routes.auth import router as auth_router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def _resolve_allowed_origins() -> list[str]:
    raw_origins = os.getenv("BYSEL_ALLOWED_ORIGINS", "").strip()
    if raw_origins:
        origins = [origin.strip() for origin in raw_origins.split(",") if origin.strip()]
        if origins:
            return origins

    # Safe local defaults for Android emulator and local web clients.
    return [
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://10.0.2.2:3000",
        "http://10.0.2.2:5173",
    ]


allowed_origins = _resolve_allowed_origins()
allow_all_origins = len(allowed_origins) == 1 and allowed_origins[0] == "*"
allow_credentials = not allow_all_origins

if allow_all_origins:
    logger.warning("BYSEL_ALLOWED_ORIGINS is set to '*'; credentialed cross-origin requests are disabled")

app = FastAPI(
    title="BYSEL Backend API",
    description="Trading backend for BYSEL",
    version="1.0.0"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=allow_credentials,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Include main and auth routes
app.include_router(router)
app.include_router(auth_router, prefix="/auth")

@app.on_event("startup")
async def startup_event():
    logger.info("BYSEL Backend starting up...")

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("BYSEL Backend shutting down...")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
