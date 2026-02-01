"""BYSEL Backend API"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import logging
from app.routes import router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="BYSEL Mock Backend",
    description="Mock trading backend for BYSEL",
    version="1.0.0"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routes
app.include_router(router)

@app.on_event("startup")
async def startup_event():
    logger.info("BYSEL Backend starting up...")

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("BYSEL Backend shutting down...")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)
