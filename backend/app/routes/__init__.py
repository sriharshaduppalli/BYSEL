from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from ..database.db import get_db
from ..models.schemas import Quote, Holding, Order, OrderResponse, Alert, HealthCheck
from .trading import get_quotes, get_holdings, place_order

router = APIRouter()

@router.get("/quotes", response_model=list[Quote])
async def get_quotes_endpoint(
    symbols: str = Query(""),
    db: Session = Depends(get_db)
):
    """Get quotes for specified symbols"""
    return get_quotes(db, symbols)

@router.get("/holdings", response_model=list[Holding])
async def get_holdings_endpoint(db: Session = Depends(get_db)):
    """Get all holdings"""
    return get_holdings(db)

@router.post("/order", response_model=OrderResponse)
async def place_order_endpoint(order: Order, db: Session = Depends(get_db)):
    """Place a buy or sell order"""
    return place_order(db, order)

@router.get("/health", response_model=HealthCheck)
async def health_check():
    """Health check endpoint"""
    return HealthCheck(status="healthy", version="1.0.0")
