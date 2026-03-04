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
    timestamp: Optional[datetime] = None

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
    createdAt: Optional[datetime] = None

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

class AlertResponse(BaseModel):
    status: str
    message: str
    id: Optional[int] = None

class TradeHistory(BaseModel):
    id: int
    symbol: str
    side: str
    quantity: int
    price: float
    total: float
    timestamp: int

class PortfolioSummary(BaseModel):
    totalValue: float
    totalInvested: float
    totalPnL: float
    totalPnLPercent: float
    holdingsCount: int

class PortfolioValue(BaseModel):
    value: float
    invested: float
    pnl: float
    pnlPercent: float

class HealthCheck(BaseModel):
    status: str
    version: str = "1.0.0"

class Wallet(BaseModel):
    balance: float

class WalletTransaction(BaseModel):
    amount: float

class WalletResponse(BaseModel):
    status: str
    balance: float
    message: Optional[str] = None

class MarketStatus(BaseModel):
    isOpen: bool
    message: str
    nextOpen: Optional[str] = None
    nextClose: Optional[str] = None


class MutualFund(BaseModel):
    schemeCode: str
    schemeName: str
    category: str
    nav: float
    navDate: str
    returns1Y: Optional[float] = None
    returns3Y: Optional[float] = None
    returns5Y: Optional[float] = None
    fundHouse: Optional[str] = None
    riskLevel: Optional[str] = None


class SipPlanRequest(BaseModel):
    schemeCode: str
    amount: float
    frequency: str = "MONTHLY"
    dayOfMonth: int = 5


class SipPlanUpdateRequest(BaseModel):
    amount: Optional[float] = None
    frequency: Optional[str] = None
    dayOfMonth: Optional[int] = None
    isActive: Optional[bool] = None


class SipPlan(BaseModel):
    id: str
    schemeCode: str
    schemeName: str
    amount: float
    frequency: str
    nextInstallmentDate: str
    isActive: bool


class IPOListing(BaseModel):
    ipoId: str
    companyName: str
    symbol: str
    status: str
    issueOpenDate: str
    issueCloseDate: str
    listingDate: Optional[str] = None
    priceBandMin: Optional[float] = None
    priceBandMax: Optional[float] = None
    lotSize: Optional[int] = None


class IPOApplicationRequest(BaseModel):
    ipoId: str
    lots: int
    bidPrice: float
    upiId: str


class IPOApplicationResponse(BaseModel):
    applicationId: str
    status: str
    message: str


class IPOApplication(BaseModel):
    applicationId: str
    ipoId: str
    companyName: str
    lots: int
    bidPrice: float
    upiId: str
    status: str
    appliedAt: str


class ETFInstrument(BaseModel):
    symbol: str
    name: str
    category: str
    last: float
    pctChange: float
    aumCr: Optional[float] = None
    expenseRatio: Optional[float] = None
