from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime

class QuoteBase(BaseModel):
    symbol: str
    last: float
    pctChange: float

class QuoteCreate(QuoteBase):
    pass

class Quote(QuoteBase):
    id: Optional[int] = None
    timestamp: datetime = None

    class Config:
        from_attributes = True

class HoldingBase(BaseModel):
    symbol: str
    qty: int
    avgPrice: float
    last: float
    pnl: float

class HoldingCreate(HoldingBase):
    pass

class Holding(HoldingBase):
    id: Optional[int] = None

    class Config:
        from_attributes = True

class AlertBase(BaseModel):
    symbol: str
    thresholdPrice: float
    alertType: str

class AlertCreate(AlertBase):
    pass

class Alert(AlertBase):
    id: int
    isActive: bool = True
    createdAt: datetime = None

    class Config:
        from_attributes = True

class OrderBase(BaseModel):
    symbol: str
    qty: int
    side: str

class Order(OrderBase):
    pass

class OrderResponse(BaseModel):
    status: str
    order: Order
    message: Optional[str] = None

class HealthCheck(BaseModel):
    status: str
    version: str = "1.0.0"
