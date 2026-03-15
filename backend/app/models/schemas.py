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
    orderType: str = "MARKET"
    validity: str = "DAY"
    limitPrice: Optional[float] = None
    triggerPrice: Optional[float] = None
    tag: Optional[str] = None
    idempotencyKey: Optional[str] = None
    idempotencyKey: Optional[str] = None

class Order(OrderBase):
    pass

class OrderResponse(BaseModel):
    status: str
    order: Order
    message: Optional[str] = None
    orderId: Optional[int] = None
    executedPrice: Optional[float] = None
    total: Optional[float] = None
    orderStatus: Optional[str] = None
    traceId: Optional[str] = None
    idempotencyKey: Optional[str] = None
    isDuplicate: bool = False
    errorCode: Optional[str] = None

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


class OrderTraceLookupResponse(BaseModel):
    orderId: int
    traceId: str
    symbol: str
    side: str
    quantity: int
    orderType: str
    validity: str
    status: str
    executedPrice: float
    total: float
    idempotencyKey: Optional[str] = None
    createdAt: str
    message: str

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


class MarketNewsHeadline(BaseModel):
    symbol: str
    title: str
    source: str = ""
    publishedAt: str = ""
    publishedLabel: str = ""
    link: str = ""


class MarketNewsResponse(BaseModel):
    headlines: List[MarketNewsHeadline]
    symbolsConsidered: List[str]
    generatedAt: str


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


class MutualFundCompareResponse(BaseModel):
    funds: List[MutualFund]
    bestReturns1YSchemeCode: Optional[str] = None
    bestReturns3YSchemeCode: Optional[str] = None
    bestReturns5YSchemeCode: Optional[str] = None
    lowestRiskSchemeCode: Optional[str] = None
    summary: str


class MutualFundRecommendationItem(BaseModel):
    schemeCode: str
    schemeName: str
    category: str
    nav: float
    navDate: str
    fundHouse: Optional[str] = None
    riskLevel: Optional[str] = None
    suitabilityScore: float
    rationale: str


class MutualFundRecommendationResponse(BaseModel):
    riskProfile: str
    goal: Optional[str] = None
    horizonYears: int
    recommendations: List[MutualFundRecommendationItem]
    generatedAt: str


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


class AdvancedOrderResponse(BaseModel):
    status: str
    orderId: Optional[int] = None
    order: Order
    message: str
    executedPrice: Optional[float] = None
    triggerStatus: Optional[str] = None
    riskFlags: List[str] = []


class TriggerOrderSummary(BaseModel):
    id: int
    symbol: str
    qty: int
    side: str
    orderType: str
    validity: str
    limitPrice: Optional[float] = None
    triggerPrice: Optional[float] = None
    status: str
    createdAt: str


class BasketOrderLegRequest(BaseModel):
    symbol: str
    qty: int
    side: str
    orderType: str = "MARKET"
    validity: str = "DAY"
    limitPrice: Optional[float] = None
    triggerPrice: Optional[float] = None
    tag: Optional[str] = None


class BasketOrderRequest(BaseModel):
    name: str
    legs: List[BasketOrderLegRequest]


class BasketLegExecution(BaseModel):
    symbol: str
    side: str
    qty: int
    status: str
    message: str
    orderId: Optional[int] = None


class BasketOrderResponse(BaseModel):
    basketId: int
    name: str
    status: str
    message: str
    legResults: List[BasketLegExecution] = []


class OptionContract(BaseModel):
    strike: float
    callLtp: float
    putLtp: float
    callOi: int
    putOi: int
    callOiChange: int
    putOiChange: int
    impliedVolatility: float
    callDelta: float
    putDelta: float
    gamma: float
    theta: float
    vega: float


class OptionChainResponse(BaseModel):
    symbol: str
    expiry: str
    spot: float
    generatedAt: str
    contracts: List[OptionContract]


class StrategyLeg(BaseModel):
    optionType: str
    side: str
    strike: float
    premium: float
    quantity: int = 1
    lotSize: int = 1


class StrategyPreviewRequest(BaseModel):
    symbol: str
    spot: float
    legs: List[StrategyLeg]


class StrategyPayoffPoint(BaseModel):
    spot: float
    payoff: float


class StrategyPreviewResponse(BaseModel):
    symbol: str
    maxProfit: float
    maxLoss: float
    breakevenPoints: List[float]
    marginEstimate: float
    riskRewardRatio: float
    payoffCurve: List[StrategyPayoffPoint]
    notes: List[str]


class FamilyMemberRequest(BaseModel):
    name: str
    relation: str
    equityValue: float = 0.0
    mutualFundValue: float = 0.0
    usValue: float = 0.0
    cashValue: float = 0.0
    liabilitiesValue: float = 0.0


class FamilyMemberSummary(BaseModel):
    id: int
    name: str
    relation: str
    netWorth: float
    totalAssets: float
    liabilitiesValue: float


class FamilyDashboardResponse(BaseModel):
    userId: int
    consolidatedNetWorth: float
    totalAssets: float
    totalLiabilities: float
    allocation: dict[str, float]
    members: List[FamilyMemberSummary]


class GoalPlanRequest(BaseModel):
    goalName: str
    targetAmount: float
    targetDate: str
    monthlyContribution: float = 0.0
    riskProfile: str = "MODERATE"


class GoalLinkRequest(BaseModel):
    instruments: List[str]
    incrementAmount: float = 0.0


class GoalPlanResponse(BaseModel):
    id: int
    goalName: str
    targetAmount: float
    currentAmount: float
    targetDate: str
    monthlyContribution: float
    progressPercent: float
    riskProfile: str
    linkedInstruments: List[str]


class CopilotSignal(BaseModel):
    verdict: str
    confidence: int
    flags: List[str]
    guidance: List[str]


class PreTradeChargeBreakdown(BaseModel):
    brokerage: float
    exchangeFee: float
    gst: float
    stampDuty: float
    totalCharges: float


class PreTradeEstimateRequest(BaseModel):
    order: Order
    walletBalance: Optional[float] = None
    marketOpen: Optional[bool] = None


class PreTradeEstimateResponse(BaseModel):
    symbol: str
    side: str
    qty: int
    orderType: str
    executionPrice: float
    livePrice: float
    tradeValue: float
    charges: PreTradeChargeBreakdown
    netAmount: float
    walletBalance: float
    walletUtilizationPct: float
    canAfford: bool
    impactTag: str
    warnings: List[str]
    signal: CopilotSignal


class CopilotPreTradeRequest(BaseModel):
    order: Order
    walletBalance: Optional[float] = None
    marketOpen: Optional[bool] = None


class CopilotPostTradeRequest(BaseModel):
    orderId: int
    note: Optional[str] = None


class CopilotPostTradeResponse(BaseModel):
    summary: str
    pnlNow: float
    coaching: List[str]


class CopilotPortfolioActionsResponse(BaseModel):
    actions: List[str]
    priority: str
    rationale: str
