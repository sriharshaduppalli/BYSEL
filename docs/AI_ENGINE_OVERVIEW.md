# BYSEL AI Engine - Comprehensive Overview

**Generated:** March 27, 2026  
**Main File:** `/BYSEL/backend/app/ai_engine.py`

---

## 1. CURRENT STRUCTURE & ARCHITECTURE

### Core Components
The AI engine is a **lightweight, on-device system** with no external AI API dependencies. All analysis runs locally using historical market data from `yfinance`.

**File Organization:**
- **Main engine:** `BYSEL/backend/app/ai_engine.py` (~2800+ lines)
- **API endpoints:** `BYSEL/backend/app/routes/__init__.py` (routes starting at line ~1600)
- **Data source:** Market data module (`market_data.py`) for Indian stocks
- **UI:** Android Compose screens in `BYSEL/android/app/src/main/java/com/bysel/trader/ui/screens/`

### Core Modules Hierarchy
```
ai_engine.py
├── Technical Indicators
│   ├── RSI (Relative Strength Index)
│   ├── MACD (Moving Average Convergence Divergence)
│   ├── Bollinger Bands
│   └── Moving Averages (5, 10, 20, 50, 200-day)
├── Price Prediction Engine
│   ├── Linear Regression (40% weight)
│   ├── Exponential Smoothing (35% weight)
│   └── Momentum-based (25% weight)
├── Stock Analysis & Scoring
│   └── Composite score (0-100) combining technical + fundamental
├── Natural Language Processing
│   ├── Symbol extraction & aliases
│   └── Query classification & routing
├── News & Sentiment Analysis
│   └── Headline classification (positive/negative/mixed)
└── Advanced Analytics
    ├── Recommendations engine
    ├── Trade accuracy backtesting
    ├── Risk/drawdown analysis
    └── Sector rotation signals
```

---

## 2. QUERIES & DATA PROCESSING

### Supported Query Types

#### **Stock Analysis & Recommendation Queries**
- "Should I buy RELIANCE?" → Direct recommendation with confidence
- "Analyze TCS" → Comprehensive technical + fundamental analysis
- "Is INFY overvalued?" → Valuation comparison against peers & historical ranges
- "Best pharma stocks under 500" → Stock screening with filters
- "Predict Tata Motors price" → AI price prediction with confidence intervals

#### **Comparative Queries**
- "Compare TCS and INFY" → Side-by-side technical, fundamental, and valuation
- "Compare sector leaders" → Peer comparison within same sector
- "TCS vs INFY by growth and margins" → Custom metric comparison

#### **Technical Analysis Queries**
- "Technical setup for NIFTY IT stocks" → Sector-wide technical screening
- "Stocks near breakout today" → Breakout pattern detection
- "RSI and MACD for RELIANCE" → Specific indicator analysis
- "Support and resistance levels" → Level identification & charting data

#### **Trading & Risk Queries**
- "Entry signals for SBIN" → Entry conditions + risk metrics
- "SIP recommendation for HDFCBANK" → Systematic investment plan analysis
- "Volatility and downside risk" → Risk assessment + volatility metrics
- "When should I buy at dip?" → Optimal entry price calculation

#### **Market-Wide Queries**
- "Market overview today" → Market breadth, sentiment, sector moods
- "Best stocks to buy this month" → Top recommendations by timeframe
- "Sector rotation signals" → Which sectors to rotate into/out of
- "Earnings calendar" → Upcoming earnings dates with volatility alerts

### Stock Data Input

**Data Source:** `yfinance` library with 1-year historical data minimum
- **Price data:** Daily OHLC (Open, High, Low, Close)
- **Volume:** Daily trading volume
- **Fundamental data:** P/E, Market Cap, Book Value, Dividend Yield, ROE, Debt/Equity
- **Metadata:** Company name, sector, industry, 52-week range, revenue growth

**Coverage:** Indian stocks (NSE/BSE) via catalog `INDIAN_STOCKS` with 500+ symbols
- Banks (HDFCBANK, ICICIBANK, KOTAKBANK, AXISBANK, SBIN, PNB, etc.)
- IT (TCS, INFY, WIPRO, HCLTECH, TECHM, LTIM, etc.)
- Energy (RELIANCE, ONGC, BPCL, IOC, ADANIGREEN, NTPC, etc.)
- Auto (MARUTI, TATAMOTORS, BAJAJ-AUTO, HEROMOTOCO, etc.)
- Pharma (SUNPHARMA, DRREDDY, CIPLA, LUPIN, DIVISLAB, etc.)
- FMCG, Metals, Infra, Real Estate, Defence sectors

---

## 3. ALGORITHMS & MODELS USED

### A. Technical Indicators (Calculation Methods)

#### **RSI (Relative Strength Index)**
```
Algorithm: 14-period standard RSI
Formula: RSI = 100 - (100 / (1 + RS))
         where RS = average_gains / average_losses
Interpretation:
- >70: Overbought (potential sell signal)
- <30: Oversold (potential buy signal)
- 40-60: Neutral zone
```

#### **MACD (Moving Average Convergence Divergence)**
```
Algorithm: 12-period & 26-period exponential moving averages
Components:
- MACD line = 12-day EMA - 26-day EMA
- Signal line = 9-period EMA of MACD
- Histogram = MACD - Signal line
Trends:
- Bullish: MACD > Signal line
- Bearish: MACD < Signal line
```

#### **Bollinger Bands**
```
Algorithm: 20-day SMA with ±2 standard deviations
Components:
- Upper band = SMA + 2×StdDev
- Middle band = 20-day SMA
- Lower band = SMA - 2×StdDev
Interpretation:
- Above upper: Overbought (reversal risk)
- Below lower: Oversold (bounce opportunity)
- Squeeze: Low volatility, breakout coming
```

#### **Moving Averages (Trend Identification)**
```
Periods calculated: 5, 10, 20, 50, 200-day
- 5/10-day: Short-term momentum
- 20-day: Medium-term trend
- 50/200-day: Long-term trend strength
Trend detection: Golden cross (50MA > 200MA) bullish, Death cross bearish
```

### B. Price Prediction Engine (Ensemble Model)

**Architecture:** Weighted ensemble of 3 methods

#### **Method 1: Linear Regression (40% weight)**
```python
# Fits line to 90-day price window
# Extrapolates trend forward
m, b = np.polyfit(days, prices[-90:], 1)
predicted = m * (90 + days_ahead) + b
```
- **Good at:** Trending markets
- **Fails at:** Mean-reverting or volatile markets

#### **Method 2: Exponential Smoothing - Holt's Method (35% weight)**
```python
# Double exponential smoothing with level + trend
alpha = 0.3 (level smoothing)
beta = 0.1 (trend smoothing)
```
- **Good at:** Captures trend direction + momentum
- **Fails at:** Structural breaks

#### **Method 3: Momentum-Based Prediction (25% weight)**
```python
# Rate of change momentum over 20 days
momentum = (price[-1] - price[-20]) / price[-20]
# Damped for longer horizons (mean reversion tendency)
damping = 0.7^(days_ahead / 30)
```
- **Good at:** Short-term reversals & volatility
- **Fails at:** Extended trends

**Ensemble Forecast:**
```
predicted_price = 0.40×LR + 0.35×ES + 0.25×Momentum
```

**Confidence Intervals:**
```
volatility = std(returns[-60:])
confidence_range = volatility × sqrt(days_ahead) × current_price
interval = [predicted - range, predicted + range]
```

**Time Horizons:** 7-day, 30-day, 90-day predictions with widening confidence bands

### C. Stock Scoring System (0-100)

**Components (~function `_compute_stock_score`):**
- **Technical metrics (40%):** RSI, MACD trend, Bollinger position, MA alignment
- **Momentum (20%):** Recent price strength, volume trends
- **Valuation (25%):** P/E vs sector, ROE, dividend yield
- **Growth (15%):** Revenue growth rate, historical performance

**Signal Generation:**
```
STRONG_BUY: All 3 time horizons up (≥70% confidence)
BUY:       2+ predictions up (≥60% confidence)
HOLD:      Neutral signals or conflicting
SELL:      2+ predictions down
STRONG_SELL: All down (≥70% confidence)
```

### D. Natural Language Understanding

**Symbol Extraction:** Regex + normalized phrase matching
- Handles aliases: "TCS", "tata consultancy", "tata consultancy services"
- Supports company suffixes stripping: "Reliance Industries Ltd" → "RELIANCE"
- Maintains `_SYMBOL_ALIAS_INDEX` with 500+ aliases

**Query Classification Engine:**
```
Keywords detected → Response type:
├── "predict", "forecast", "target" → PREDICTION type
├── "compare", "vs", "versus" → COMPARISON type
├── "technical", "rsi", "macd", "breakout" → TECHNICAL_SCREENING
├── "overvalued", "cheap", "fair value" → VALUATION_ANALYSIS
├── "recommend", "best", "top" → RECOMMENDATION list
├── "market", "sector", "nifty" → MARKET_OVERVIEW
└── Single stock + no keywords → ANALYSIS type
```

---

## 4. LIMITATIONS & TODO ITEMS

### Known Limitations

**1. Model Accuracy Constraints**
```
- Accuracy capped at 50-85% (conservative estimate)
- Historical backtest on last 30 days only
- Does NOT account for earnings surprises, geopolitical events, or structural shifts
- Linear regression weak on volatile stocks
- Exponential smoothing assumes trend continuation (fails on reversals)
```

**2. Data Quality Issues**
```
- yfinance data may lag real-time by several minutes
- penny stocks or illiquid stocks may have sparse/inaccurate data
- Dividend & split adjustments handled by yfinance (occasionally delayed)
- International events affecting India markets not natively captured
```

**3. Fundamental Data Gaps**
```
- P/E and ROE may be stale (depends on yfinance refresh rate)
- Book value not always available
- Sector/industry classification may be inaccurate for conglomerates
- Small-cap companies may have incomplete metadata
```

**4. News/Sentiment Analysis**
```
- Headline sentiment is rule-based keyword matching (not ML-based NLP)
- Only 5 recent headlines considered (temporal bias)
- No understanding of headline context or sarcasm
- Foreign language financial news not supported
```

**5. Backtesting & Accuracy Tracking**
```
- Trade accuracy only tracked for recommended symbols
- 30-day window may not capture market regime changes
- Survivor bias if old recommendations removed from tracking
- Sample size too small (5-25 trades) for statistical significance
```

### TODO / Enhancement Opportunities

**Phase 1 - High Value (Performance Wins)**
- [ ] Implement real-time WebSocket data (instead of yfinance polling)
- [ ] Add earnings date / guidance detection to adjust predictions
- [ ] Support options implied volatility for risk calibration
- [ ] Backtest ensemble weights on last 2 years of data (optimize allocation)
- [ ] Cache miss tracking for P/E and other fundamental data

**Phase 2 - Medium Value (Feature Additions)**
- [ ] ML-based headline sentiment (fine-tuned DistilBERT per sector)
- [ ] Sector rotation backtest with 3-month forward returns validation
- [ ] Support relative strength vs peer group (not just market)
- [ ] Correlation-based portfolio risk assessment
- [ ] Stop loss adjustment based on realized volatility

**Phase 3 - Nice-to-Have (UX Improvements)**
- [ ] Dark cycle & seasonal pattern detection (Q4 rally bias)
- [ ] Volume profile analysis for liquidity
- [ ] Insider trading signals (if available in India)
- [ ] Analyst consensus vs AI prediction divergence alerts
- [ ] Batch analysis for watchlists (10+ stocks in single request)

**Code TODOs Found:**
```python
# From ai_engine.py:
- Model performance tracking structure exists but not fully utilized
- Backtesting cache refreshes daily but results not exposed
- Trade accuracy lock exists but race conditions possible under high load
- News cache TTL increased from 15→45 min (perf optimization)
- No garbage collection for old cached predictions (could grow unbounded)
```

---

## 5. UI COMPONENTS DISPLAYING AI RESULTS

### Android Screens (Jetpack Compose)

#### **A. AI Assistant Screen**
**File:** `AiAssistantScreen.kt` (Lines 1-600+)
```kotlin
@Composable
fun AiAssistantScreen(
    chatHistory: List<ChatMessage>,
    isLoading: Boolean,
    onSendQuery: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    selectedSymbol: String? = null
)
```
- **Displays:** Chat conversation with AI responses
- **Features:**
  - Auto-scroll to latest message
  - Adaptive suggestions based on conversation history
  - Query categories: Valuation, Prediction, Comparison, Recommendation, Technical
  - Request/response indicators (gradient header with Psychology icon)
  - Markdown support for formatted responses

**Key Subcomponents:**
- `ChatBubble`: User (right-aligned, primary color) vs AI response (left-aligned, card color)
- `SuggestionChip`: Quick follow-up prompts with icons
- `AdaptiveSuggestionsStrip`: Context-aware next prompts

#### **B. Premium Recommendations Screen**
**File:** `PremiumRecommendationsScreen.kt`
```kotlin
@Composable
fun PremiumRecommendationsScreen(
    onSymbolClick: (String) -> Unit,
    isLoading: Boolean = false,
    onRefresh: () -> Unit = {}
)
```
- **Displays:** Top stocks to buy across timeframes (1-day, 1-month, 3-month, sector rotation)
- **Features:**
  - Tab navigation for different timeframes
  - Premium glassmorphism cards with transparency
  - Mock data structure: Symbol, Price, Recommendation, Confidence, Targets, Risk Score
  - Bottom sheet for detailed analysis
  - Sector rotation view

**Key Subcomponents:**
- `PremiumStockRecommendationCard`: Displays symbol, current price, targets, confidence %
- `PremiumStatsRow`: Key metrics aggregated (model accuracy, win rate, Sharpe ratio)
- `AdvancedTabNavigator`: Premium tab UI with active underline

#### **C. Stock Detail Screen (Premium)**
**File:** `PremiumStockDetailScreen.kt`
```kotlin
@Composable
fun PremiumStockDetailScreen(
    symbol: String = "RELIANCE",
    onNavigateBack: () -> Unit = {},
    onBuyClick: (String) -> Unit = {}
)
```
- **Displays:** Comprehensive analysis for single stock
- **Features:**
  - Price header with 52-week range visualization
  - Recommendation summary badge (BUY, STRONG_BUY, HOLD, SELL)
  - Action confidence percentage
  - Multiple analysis tabs:
    - Technical indicators (RSI, MACD, Bollinger visualization)
    - Fundamental metrics (P/E, ROE, Debt/Equity)
    - AI predictions (7-day, 30-day, 90-day targets)
    - News & headlines
    - Trade levels (SL/TP)
  - Floating action button for buy action

**Key Subcomponents:**
- `StockPriceHeader`: Live price + % change + trend indicator
- `RecommendationSummary`: Action confidence badge
- `AnalysisTabNavigator`: Technical, Fundamental, Predictions, News, Levels
- `TechnicalIndicatorsPanel`: RSI gauge + MACD chart + Bollinger visualization

#### **D. Dashboard Screen (Premium)**
**File:** `PremiumDashboardScreen.kt`
```kotlin
@Composable
fun PremiumDashboardScreen(
    onNavigateToRecommendations: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToPortfolio: () -> Unit,
    onSymbolClick: (String) -> Unit
)
```
- **Displays:** Trading dashboard with key metrics
- **Features:**
  - Portfolio summary card (total value, P&L, % change)
  - Key metrics bar (NIFTY level, market mood, sector leaders)
  - Dashboard tabs:
    - Today's Top Movers (gainers/losers)
    - Portfolio Holdings (AI-scored recommendations)
    - Watchlist (saved symbols)
    - Performance Analytics (Win rate, Sharpe ratio, drawdown)
  - Navigation to recommendations, portfolio, watchlist

**Key Subcomponents:**
- `PortfolioSummaryCard`: Total portfolio health visualization
- `KeyMetricsBar`: Market status indicators
- `DashboardTabs`: Tab navigation between sections
- `TodaysTopMoversSection`: Live market movers with sentiment

#### **E. Signal Lab Screen**
**File:** `SignalLabScreen.kt`
```kotlin
@Composable
fun SignalLabScreen(
    quotes: List<Quote>,
    heatmap: MarketHeatmap?,
    backendBuckets: List<SignalLabBucketFeed>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSymbol: (String) -> Unit
)
```
- **Displays:** Technical signal buckets (Intraday/Swing)
- **Features:**
  - Two timeframe modes: INTRADAY (tape action, volume), SWING (52-week structure, yields)
  - Sector filtering with live symbols count
  - Signal bucket grouping (Support, Resistance, Breakout, Reversal patterns)
  - Each candidate shows symbol, current price, confidence

**Key Subcomponents:**
- `SignalLabHeroCard`: Hero stats (quote count, bucket count)
- `SignalLabFilterCard`: Timeframe + sector selector
- `SignalBucketCard`: Groups of similar technical setups

#### **F. Heatmap Screen**
**File:** `HeatmapScreen.kt`
```kotlin
@Composable
fun HeatmapScreen(
    heatmap: MarketHeatmap?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onStockClick: (String) -> Unit,
    heatmapInterval: Int = 1000  // 1-second real-time updates
)
```
- **Displays:** Market sector heatmap with color-coded performance
- **Features:**
  - Overall market mood indicator (bullish/bearish/neutral)
  - Market breadth card (advance/decline ratio)
  - Sector cards with: sector name, performance %, top 3 gainers/losers
  - Pull-to-refresh for manual update
  - Live 1-second interval auto-refresh

**Key Subcomponents:**
- `HeatmapHeader`: Market mood + sentiment bar
- `MarketBreadthCard`: A/D ratio visualization
- `SectorHeatmapCard`: Sector performance + constituent stocks

#### **G. AI-Related Card Components**
**File:** `PremiumComponents.kt`
```kotlin
@Composable
fun GlassmorphismCard(...)  // Transparency + blur effect

@Composable
fun PremiumRecommendationCard(
    symbol: String,
    entrySignal: String,
    signalConfidence: Int,
    stopLoss: Double,
    takeProfit1/2/3: Double,
    riskRewardRatio: Double,
    ...
)
```
- **Displays:** Individual AI recommendation cards with:
  - Stock symbol + company name
  - Entry signal (STRONG_BUY, BUY, HOLD, SELL) with confidence %
  - Current price + 1-month target
  - Risk/reward visualization (bar chart)
  - Stop-loss & take-profit levels
  - Position sizing recommendations

---

## 6. API ENDPOINTS

**Base URL:** `http://backend:8000/ai/` (or deployed endpoint)

### Core AI Endpoints

| Endpoint | Method | Description | Response |
|----------|--------|-------------|----------|
| `/ai/analyze/{symbol}` | GET | Comprehensive stock analysis | Full analysis object with predictions, technical, fundamental |
| `/ai/analyze-fast/{symbol}` | GET | Ultra-fast (<1s) stock detail with 20s cache | Lightweight analysis for real-time updates |
| `/ai/predict/{symbol}` | GET | Price predictions (7d, 30d, 90d) | Predictions array with confidence intervals & direction |
| `/ai/recommendations` | GET | Top stocks to buy (limit param) | Recommendations list sorted by confidence |
| `/ai/trade-levels/{symbol}` | GET | Stop-loss & take-profit levels | SL/TP with entry signals & risk:reward |
| `/ai/drawdown-risk/{symbol}` | GET | Historical drawdown risk analysis | Drawdown %, current distance from peak, risk score |
| `/ai/relative-strength/{symbol}` | GET | Relative strength vs sector/market | RS index, peer comparison, percentile rank |
| `/ai/trade-accuracy` | GET | Backtesting accuracy (timeframe param) | Win rate, avg profit, Sharpe ratio, sample size |
| `/market/sector-rotation` | GET | Sector rotation signals | Accumulate/Hold/Reduce for each sector with momentum |
| `/market/earnings-calendar` | GET | Upcoming earnings dates | Next 30-90 days with pre-earnings volatility alerts |

### Example Responses

**`/ai/analyze/RELIANCE` Response:**
```json
{
  "symbol": "RELIANCE",
  "name": "Reliance Industries Limited",
  "currentPrice": 2850.50,
  "score": 72,
  "signal": "BUY",
  "summary": "Strong technical setup with bullish MACD and elevated RSI...",
  "technical": {
    "rsi": 64.2,
    "macd": { "macd": 125.5, "signal": 118.0, "trend": "bullish" },
    "bollinger": { "upper": 2900, "middle": 2850, "lower": 2800, "position": "middle" },
    "movingAverages": { "trend": "bullish", "5d": 2845, "20d": 2830, "50d": 2800 }
  },
  "fundamental": {
    "pe": 24.5,
    "roe": 16.8,
    "debtToEquity": 0.35,
    "dividend_yield": 2.8
  },
  "predictions": [
    { "horizon": "1 Week", "days": 7, "predictedPrice": 2950.0, "changePercent": 3.5, "direction": "up" },
    { "horizon": "1 Month", "days": 30, "predictedPrice": 3100.0, "changePercent": 8.7, "direction": "up" },
    { "horizon": "3 Months", "days": 90, "predictedPrice": 3350.0, "changePercent": 17.5, "direction": "up" }
  ],
  "modelAccuracy": 68.5,
  "disclaimer": "AI analysis for educational purposes only..."
}
```

**`/ai/predict/TCS` Response:**
```json
{
  "symbol": "TCS",
  "currentPrice": 3720.25,
  "predictions": [
    {
      "horizon": "1 Week",
      "predictedPrice": 3820.50,
      "confidenceHigh": 3950.25,
      "confidenceLow": 3620.75,
      "changePercent": 2.7,
      "direction": "up"
    },
    ...
  ],
  "signal": "STRONG_BUY",
  "modelAccuracy": 71.2,
  "lastUpdated": "2026-03-27T10:45:32.123456"
}
```

---

## 7. CAPABILITIES SUMMARY

### ✅ What Works Well (Competitive Strengths)
1. **Lightweight & Fast** - No external API calls, runs entirely locally
2. **Comprehensive Analysis** - Technical + Fundamental + Predictions in one call
3. **Interpretable Signals** - Not a black box; each component is explainable
4. **Ensemble-based** - Diversified prediction using 3 complementary methods
5. **Adaptive UI** - Chat-based interface adapts suggestions based on context
6. **Real-time Dashboard** - <1s updates for market heatmaps & price tracking
7. **Backtesting Integrated** - Tracks accuracy & trade wins for transparency
8. **Indian Market Focused** - 500+ NSE/BSE stocks with sector peer mapping

### ⚠️ Current Gaps & Weaknesses
1. **No Event Risk Modeling** - Earnings surprises, news shocks not predicted
2. **No Options/Derivatives** - Stock analysis only, no option pricing
3. **Rule-based Sentiment** - Headline sentiment via keyword matching (not ML)
4. **Limited Macroeconomic Context** - Fed policy, currency, commodity shocks ignored
5. **Accuracy Ceiling ~70%** - Historical backtests show 65-75% directional accuracy max
6. **Survivor Bias** - Old recommendations no longer tracked
7. **No Multi-factor Quant** - Stock scores overly simplistic, not Fama-French based
8. **Backtesting Limited** - 30-day window not enough for trend detection
9. **No Correlation Matrix** - Portfolio tail risk not assessed
10. **Concurrency Issues** - Caching locks may cause delays under high load

---

## 8. CACHE STRATEGY & PERFORMANCE

**Multi-tier Caching:**

| Cache Layer | TTL | Max Size | Purpose |
|------------|-----|----------|---------|
| News Cache | 45 min | 200 symbols | Headline reuse for multiple stocks |
| Analysis Cache | 60 min | 500 symbols | Avoid re-computing full analysis |
| Prediction Cache | 60 min | 500  symbols | Price predictions expensive, reuse |
| Stock Detail Cache | 20 sec | Unlimited | <1s updates during market hours |
| Recommendations Cache | 5 min | Single | Top 10 stocks refreshed less often |
| Trade Levels Cache | 15 min | Unlimited | SL/TP volatility-sensitive |
| Backtesting Cache | 24 hours | Unlimited | Daily accuracy recalc |

**Performance Targets:**
- `/ai/analyze/{symbol}`: ~500ms-2s (first call), <50ms (cached)
- `/ai/analyze-fast/{symbol}`: <1s guaranteed (20s cache refresh)
- `/ai/predict/{symbol}`: ~800ms-3s (ensemble computation)
- `/ai/recommendations`: ~2-5s (scans top 20-25 stocks)

---

## Next Steps for Development

1. **Short-term:** Add event risk alerts (earnings calendar already implemented)
2. **Medium-term:** ML-based headline sentiment + correlation risk matrix
3. **Long-term:** Integrate a quantitative factor model (Fama-French extension for India)
