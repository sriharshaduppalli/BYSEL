from pydantic import BaseModel, ConfigDict
from typing import Optional, List, Dict
from datetime import datetime

class QuoteBase(BaseModel):
    symbol: str
    last: float
    pctChange: float

class QuoteCreate(QuoteBase):
    pass

class Quote(QuoteBase):
    id: Optional[int] = None
    # Epoch millis for Android clients. Prefer int over datetime so JSON never
    # serializes as an ISO string that Gson can't put into Long.
    timestamp: Optional[int] = None
    # Snapshot / fundamentals (optional — populated from Yahoo when available)
    open: Optional[float] = None
    prevClose: Optional[float] = None
    high: Optional[float] = None
    low: Optional[float] = None
    volume: Optional[int] = None
    avgVolume: Optional[int] = None
    marketCap: Optional[int] = None
    trailingPE: Optional[float] = None
    eps: Optional[float] = None
    fiftyTwoWeekHigh: Optional[float] = None
    fiftyTwoWeekLow: Optional[float] = None
    targetMeanPrice: Optional[float] = None
    bid: Optional[float] = None
    ask: Optional[float] = None
    dividendYield: Optional[float] = None
    fiftyDayAverage: Optional[float] = None
    twoHundredDayAverage: Optional[float] = None
    # Legacy aliases some clients still read
    previousClose: Optional[float] = None
    pe: Optional[float] = None

    model_config = ConfigDict(from_attributes=True)

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

    model_config = ConfigDict(from_attributes=True)

class AlertBase(BaseModel):
    symbol: str
    thresholdPrice: float
    alertType: str

class AlertCreate(AlertBase):
    pass

class Alert(AlertBase):
    id: int
    isActive: bool = True
    # Epoch millis for Android Long deserialization (ISO datetimes break Gson).
    createdAt: Optional[int] = None

    model_config = ConfigDict(from_attributes=True)

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


class HistoryCandle(BaseModel):
    timestamp: int
    open: float
    high: float
    low: float
    close: float
    volume: int


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


class IntradayTip(BaseModel):
    id: str
    title: str
    body: str
    category: str = "process"
    source: str = "session"
    evidence: Optional[str] = None


class IntradayTipsResponse(BaseModel):
    phase: str
    phaseLabel: str
    isOpen: bool = False
    mood: Optional[str] = None
    tips: List[IntradayTip] = []
    disclaimer: str = ""
    generatedAt: str = ""
    sampleSize: int = 0
    hasEnoughData: bool = False
    paperNote: str = ""


class InvestorTip(BaseModel):
    id: str
    title: str
    body: str
    category: str = "process"
    source: str = "topic"
    evidence: Optional[str] = None


class InvestorTopicInfo(BaseModel):
    id: str
    label: str


class InvestorTipsResponse(BaseModel):
    topic: str
    topicLabel: str
    tips: List[InvestorTip] = []
    topics: List[InvestorTopicInfo] = []
    disclaimer: str = ""
    generatedAt: str = ""
    sampleSize: int = 0
    hasEnoughData: bool = False
    paperNote: str = ""


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


class MarketMoverQuote(BaseModel):
    symbol: str
    name: str = ""
    last: float = 0.0
    pctChange: float = 0.0
    volume: int = 0


class MarketMoversResponse(BaseModel):
    gainers: List[MarketMoverQuote] = []
    losers: List[MarketMoverQuote] = []
    mostActive: List[MarketMoverQuote] = []
    universeSize: int = 0
    generatedAt: str = ""
    cached: bool = False


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
    callIv: Optional[float] = None
    putIv: Optional[float] = None


class OptionChainResponse(BaseModel):
    symbol: str
    expiry: str
    spot: float
    generatedAt: str
    contracts: List[OptionContract]
    source: str = "synthetic"
    pcr: Optional[float] = None
    ivSkew: Optional[float] = None
    atmIv: Optional[float] = None
    notes: List[str] = []


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


class FuturesContract(BaseModel):
    contractSymbol: str
    expiry: str
    lotSize: int
    last: float
    pctChange: float
    oi: int
    oiChange: int
    volume: int
    basis: float
    marginPct: float
    marginPerLot: float


class FuturesContractsResponse(BaseModel):
    symbol: str
    spot: float
    generatedAt: str
    contracts: List[FuturesContract]
    source: str = "synthetic"
    notes: List[str] = []


class FuturesTicketPreviewRequest(BaseModel):
    symbol: str
    expiry: str
    side: str
    lots: int = 1
    orderType: str = "MARKET"
    limitPrice: Optional[float] = None


class FuturesTicketPreviewResponse(BaseModel):
    contractSymbol: str
    symbol: str
    expiry: str
    side: str
    lots: int
    lotSize: int
    quantity: int
    referencePrice: float
    notionalValue: float
    estimatedMargin: float
    estimatedCharges: float
    maxLossBuffer: float
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


class InvestorHoldingDelta(BaseModel):
    symbol: str
    companyName: str
    action: str
    previousHoldingPct: float
    currentHoldingPct: float
    deltaPct: float
    commentary: str


class InvestorPortfolioChangeFeed(BaseModel):
    investorId: str
    investorName: str
    style: str
    quarterLabel: str
    changes: List[InvestorHoldingDelta] = []


class SmartMoneyIdeaFeedCard(BaseModel):
    ideaId: str
    symbol: str
    companyName: str
    action: str
    confidence: int
    thesis: str
    whyNow: str
    riskNote: str
    tags: List[str] = []
    backingInvestors: List[str] = []


class InvestorPortfolioInsightsResponse(BaseModel):
    generatedAt: str
    quarterLabel: str
    portfolioChanges: List[InvestorPortfolioChangeFeed] = []
    ideas: List[SmartMoneyIdeaFeedCard] = []


class SignalLabCandidate(BaseModel):
    symbol: str
    companyName: str
    score: float
    confidence: int
    thesis: str
    tags: List[str] = []
    pctChange: float = 0.0
    volumeRatio: Optional[float] = None


class SignalLabBucketFeed(BaseModel):
    bucketId: str
    title: str
    thesis: str
    proxy: bool = False
    generatedAt: str
    candidates: List[SignalLabCandidate] = []
    notes: List[str] = []


class SignalLabBucketsResponse(BaseModel):
    generatedAt: str
    buckets: List[SignalLabBucketFeed] = []


class ScannerEducationFilter(BaseModel):
    id: str
    label: str
    applied: bool = False
    status: str = ""


class ScannerEducation(BaseModel):
    title: str
    summary: str
    filters: List[ScannerEducationFilter] = []
    scoreGuide: str = ""
    riskNote: str = ""
    disclaimer: str = ""
    dataLimits: str = ""


class ScannerMetrics(BaseModel):
    pe: Optional[float] = None
    roe: Optional[float] = None
    roce: Optional[float] = None
    debtToEquity: Optional[float] = None
    peg: Optional[float] = None
    rsi: Optional[float] = None
    fiftyDayAverage: Optional[float] = None
    twoHundredDayAverage: Optional[float] = None
    volumeRatio: Optional[float] = None
    sector: Optional[str] = None
    sectorPe: Optional[float] = None
    pledge: Optional[float] = None
    marginPct: Optional[float] = None
    marketCap: Optional[int] = None
    priceToSales: Optional[float] = None
    evEbitda: Optional[float] = None
    revenueGrowth: Optional[float] = None
    earningsGrowth: Optional[float] = None
    salesCagr: Optional[float] = None
    profitCagr: Optional[float] = None
    nseSectorPe: Optional[float] = None
    roceAvg: Optional[float] = None
    promoter: Optional[float] = None


class QualityScreenCheck(BaseModel):
    id: str = ""
    label: str = ""
    status: str = "skip"
    applied: bool = False
    value: Optional[float] = None
    note: str = ""


class QualityScreenResult(BaseModel):
    checks: List[QualityScreenCheck] = []
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    matches: bool = False
    summary: str = ""


class ScannerAnomaly(BaseModel):
    id: str = ""
    label: str = ""
    detail: str = ""


class ScannerPracticeSetup(BaseModel):
    kind: str = ""
    setupType: str = ""
    title: str = ""
    entry: Optional[float] = None
    stop: Optional[float] = None
    target: Optional[float] = None
    t1: Optional[float] = None
    t2: Optional[float] = None
    riskReward: Optional[float] = None
    momentumScore: Optional[int] = None
    note: str = "Paper — not advice. Practice levels only."
    winRate: Optional[float] = None
    winRateNote: str = "n/a until we have journal data"


class ScannerRow(BaseModel):
    symbol: str
    name: str = ""
    last: float = 0.0
    pctChange: float = 0.0
    byselScore: Optional[int] = None
    bysel_score: Optional[int] = None
    quality: Optional[int] = None
    valuation: Optional[int] = None
    value: Optional[int] = None
    trend: Optional[int] = None
    momentum: Optional[int] = None
    risk: Optional[int] = None
    riskLabel: str = "Risk —"
    overall: int = 0
    colorBand: str = "none"
    convictionLabel: str = ""
    score_label: str = "insufficient"
    scoreLabel: str = "insufficient"
    explanation: str = ""
    ai_summary: str = ""
    aiSummary: str = ""
    stance: List[str] = []
    pillars: Optional[dict] = None
    setup: Optional[ScannerPracticeSetup] = None
    why: str = ""
    metrics: ScannerMetrics = ScannerMetrics()
    qualityScreen: Optional[QualityScreenResult] = None
    missing: List[str] = []
    anomalies: List[ScannerAnomaly] = []


class ScannerResponse(BaseModel):
    mode: str
    generatedAt: str
    cacheTtlSeconds: int = 600
    universe: str = "NIFTY50 + watchlist catalog"
    universeSize: int = 0
    quotedCount: int = 0
    disclaimer: str = ""
    formulaNote: str = ""
    education: ScannerEducation
    rows: List[ScannerRow] = []
    cached: bool = False
    byMode: Dict[str, List[ScannerRow]] = {}


class ScoreHistoryPoint(BaseModel):
    date: str = ""
    byselScore: Optional[int] = None
    quality: Optional[int] = None
    valuation: Optional[int] = None
    trend: Optional[int] = None
    momentum: Optional[int] = None


class ScoreHistoryResponse(BaseModel):
    symbol: str
    days: int = 90
    points: List[ScoreHistoryPoint] = []
    pending: bool = True
    note: str = ""
