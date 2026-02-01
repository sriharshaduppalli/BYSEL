# BYSEL Architecture & Design

## System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     ANDROID CLIENT                          │
├─────────────────────────────────────────────────────────────┤
│  Presentation Layer (Jetpack Compose UI)                   │
│  ├── WatchlistScreen                                       │
│  ├── PortfolioScreen                                       │
│  └── AlertsScreen                                          │
├─────────────────────────────────────────────────────────────┤
│  ViewModel Layer (MVVM)                                     │
│  └── TradingViewModel (StateFlow management)               │
├─────────────────────────────────────────────────────────────┤
│  Repository Layer (Data source abstraction)                │
│  └── TradingRepository (combines API & Cache)              │
├─────────────────────────────────────────────────────────────┤
│  Data Layer (Dual sources)                                 │
│  ├── API Client (Retrofit + RetrofitClient)                │
│  └── Local Cache (Room Database)                           │
│      ├── QuoteDao                                           │
│      ├── HoldingDao                                         │
│      └── AlertDao                                           │
└─────────────────────────────────────────────────────────────┘
                           │
                    HTTP/REST API
                           │
┌─────────────────────────────────────────────────────────────┐
│                    BACKEND API (FastAPI)                    │
├─────────────────────────────────────────────────────────────┤
│  Route Layer                                                │
│  ├── /quotes → Get stock quotes                            │
│  ├── /holdings → Get user holdings                         │
│  ├── /order → Place buy/sell orders                        │
│  └── /health → Health check                                │
├─────────────────────────────────────────────────────────────┤
│  Service Layer                                              │
│  └── trading.py (Business logic)                           │
│      ├── generate_mock_quote()                             │
│      ├── get_quotes()                                      │
│      ├── get_holdings()                                    │
│      └── place_order()                                     │
├─────────────────────────────────────────────────────────────┤
│  Data Layer (SQLAlchemy)                                   │
│  ├── QuoteModel                                            │
│  ├── HoldingModel                                          │
│  ├── AlertModel                                            │
│  └── OrderModel                                            │
├─────────────────────────────────────────────────────────────┤
│  Database                                                   │
│  └── SQLite (bysel.db)                                     │
└─────────────────────────────────────────────────────────────┘
```

---

## Android Architecture

### MVVM (Model-View-ViewModel)

#### View Layer (Jetpack Compose)
```kotlin
// MainActivity.kt
@Composable
fun BYSELApp(viewModel: TradingViewModel) {
    // Observes StateFlow from ViewModel
    val quotes by viewModel.quotes.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    
    // Updates UI reactively
    WatchlistScreen(quotes = quotes, ...)
}
```

#### ViewModel Layer
```kotlin
// TradingViewModel.kt
class TradingViewModel(private val repository: TradingRepository) : ViewModel() {
    // Manages state
    private val _quotes = MutableStateFlow<List<Quote>>(emptyList())
    val quotes: StateFlow<List<Quote>> = _quotes.asStateFlow()
    
    // Business logic
    fun refreshQuotes() {
        viewModelScope.launch {
            repository.getQuotes(defaultSymbols).collect { result ->
                when(result) {
                    is Result.Success -> _quotes.value = result.data
                    is Result.Error -> _error.value = result.message
                }
            }
        }
    }
}
```

#### Repository Layer
```kotlin
// TradingRepository.kt
class TradingRepository(private val database: BYSELDatabase) {
    // Combines API and Cache
    fun getQuotes(symbols: List<String>): Flow<Result<List<Quote>>> = flow {
        try {
            // Fetch from API
            val quotes = apiService.getQuotes(symbolString)
            // Cache in database
            database.quoteDao().insertQuotes(quotes)
            emit(Result.Success(quotes))
        } catch (e: Exception) {
            // On error, try to use cache
            emit(Result.Error(e.message))
        }
    }
}
```

#### Data Layer

**Remote (API):**
```kotlin
// RetrofitClient.kt
interface BYSELApiService {
    @GET("/quotes")
    suspend fun getQuotes(@Query("symbols") symbols: String): List<Quote>
}
```

**Local (Database):**
```kotlin
// BYSELDatabase.kt
@Entity(tableName = "quotes")
data class Quote(
    @PrimaryKey val symbol: String,
    val last: Double,
    val pctChange: Double
)

@Dao
interface QuoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(quotes: List<Quote>)
    
    @Query("SELECT * FROM quotes WHERE symbol IN (:symbols)")
    fun getQuotesBySymbols(symbols: List<String>): Flow<List<Quote>>
}
```

---

## Backend Architecture

### Layered Architecture

```
┌──────────────────────────────┐
│      Route Layer             │
│  (FastAPI Endpoints)         │
└──────────────────┬───────────┘
                   │
┌──────────────────▼───────────┐
│   Schema/Model Layer         │
│  (Pydantic + SQLAlchemy)     │
└──────────────────┬───────────┘
                   │
┌──────────────────▼───────────┐
│   Business Logic Layer       │
│  (Services/Utils)            │
└──────────────────┬───────────┘
                   │
┌──────────────────▼───────────┐
│   Database Layer             │
│  (SQLAlchemy ORM)            │
└──────────────────┬───────────┘
                   │
┌──────────────────▼───────────┐
│   Database                   │
│  (SQLite)                    │
└──────────────────────────────┘
```

### File Structure

```
backend/
├── app/
│   ├── __init__.py              # FastAPI instance & middleware
│   ├── config.py                # Configuration & env variables
│   │
│   ├── models/
│   │   └── schemas.py           # Pydantic models (request/response)
│   │
│   ├── database/
│   │   └── db.py                # SQLAlchemy models & DB setup
│   │
│   └── routes/
│       ├── __init__.py          # Route handlers
│       └── trading.py           # Business logic
│
├── tests/
│   ├── __init__.py
│   └── test_api.py              # API tests
│
├── requirements.txt
├── Dockerfile
└── .env.example
```

### Code Flow Example

**Request:** `POST /order`

```python
# routes/__init__.py (Entry point)
@router.post("/order", response_model=OrderResponse)
async def place_order_endpoint(order: Order, db: Session = Depends(get_db)):
    return place_order(db, order)
    
# routes/trading.py (Business logic)
def place_order(db: Session, order: Order) -> OrderResponse:
    existing = db.query(HoldingModel).filter(...).first()  # DB query
    
    if order.side == "BUY":
        if existing:
            existing.quantity += order.qty
        else:
            new_holding = HoldingModel(...)  # Create new holding
            db.add(new_holding)
    
    db.commit()  # Persist to database
    return OrderResponse(status="ok", order=order)

# models/schemas.py (Data transfer)
class Order(BaseModel):
    symbol: str
    qty: int
    side: str
```

---

## Data Flow

### Watchlist Update Flow

```
1. User opens app
   ↓
2. MainActivity creates TradingViewModel
   ↓
3. TradingViewModel.init() calls refreshQuotes()
   ↓
4. ViewModel → Repository.getQuotes(["RELIANCE", "TCS", ...])
   ↓
5. Repository → API Client (Retrofit) → HTTP GET /quotes?symbols=...
   ↓
6. Backend → Generates mock quotes → Returns JSON
   ↓
7. Repository receives quotes
   ↓
8. Repository → Save to Room Database (QuoteDao.insertQuotes())
   ↓
9. Repository → Update StateFlow (_quotes.value = data)
   ↓
10. ViewModel StateFlow updated
    ↓
11. Compose UI automatically recomposes with new data
    ↓
12. User sees updated watchlist
```

### Order Placement Flow

```
1. User taps "Buy" button
   ↓
2. Compose calls ViewModel.placeOrder("TCS", 5, "BUY")
   ↓
3. ViewModel → Repository.placeOrder(Order("TCS", 5, "BUY"))
   ↓
4. Repository → API Client → HTTP POST /order
   ↓
5. Backend receives order
   ↓
6. Backend → Query holdings (HoldingModel)
   ↓
7. Backend → Update quantity or create new holding
   ↓
8. Backend → Persist to SQLite database
   ↓
9. Backend → Return OrderResponse
   ↓
10. Repository receives response
    ↓
11. ViewModel updates holdings StateFlow
    ↓
12. UI recomposes to show updated portfolio
```

---

## Technology Stack

### Android
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM
- **State Management:** StateFlow, MutableStateFlow
- **Database:** Room (SQLite)
- **Networking:** Retrofit + OkHttp
- **Coroutines:** Kotlin Coroutines
- **DI:** Hilt (ready for integration)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)

### Backend
- **Framework:** FastAPI
- **Language:** Python 3.11
- **ORM:** SQLAlchemy
- **Database:** SQLite
- **Server:** Uvicorn
- **Documentation:** Auto-generated by FastAPI

### DevOps
- **Containerization:** Docker, Docker Compose
- **CI/CD:** GitHub Actions
- **Version Control:** Git
- **App Distribution:** Google Play Store

---

## Key Design Patterns

### 1. Repository Pattern
Abstracts data sources (API, Database) behind single interface
```kotlin
interface TradingRepository {
    fun getQuotes(symbols: List<String>): Flow<Result<List<Quote>>>
}
```

### 2. MVVM Pattern
Separates UI logic from business logic
- **View:** Compose UI
- **ViewModel:** State management
- **Model:** Data classes

### 3. Flow-based Reactive Architecture
Reactive data flow using Kotlin Flows
```kotlin
repository.getQuotes(symbols)     // Returns Flow<Result>
    .collectLatest { result ->     // Observe changes
        when(result) { ... }
    }
```

### 4. Error Handling
Type-safe error handling using sealed classes
```kotlin
sealed class Result<out T> {
    object Loading : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}
```

### 5. Dependency Injection
Ready for Hilt integration (commented out in current code)
```kotlin
@Inject
lateinit var repository: TradingRepository
```

---

## Database Schema

### Quotes Table
```sql
CREATE TABLE quotes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT UNIQUE,
    last_price REAL,
    pct_change REAL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### Holdings Table
```sql
CREATE TABLE holdings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT,
    quantity INTEGER,
    avg_price REAL,
    last_price REAL,
    pnl REAL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### Alerts Table
```sql
CREATE TABLE alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT,
    threshold_price REAL,
    alert_type TEXT,
    is_active BOOLEAN DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### Orders Table
```sql
CREATE TABLE orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    symbol TEXT,
    quantity INTEGER,
    side TEXT,
    status TEXT DEFAULT 'COMPLETED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

---

## Security Considerations

1. **API Communication:** Uses HTTP (for demo), upgrade to HTTPS in production
2. **Keystore:** Signing certificate for Play Store
3. **Data Validation:** Pydantic models validate backend input
4. **Database:** SQLite suitable for local cache, use PostgreSQL for production backend
5. **CORS:** Enabled for mobile client in backend

---

## Performance Optimizations

1. **Caching:** Room database caches API responses
2. **Lazy Loading:** LazyColumn for efficient list rendering
3. **Database Indexing:** Symbols indexed for fast queries
4. **Connection Pooling:** OkHttp handles connection reuse
5. **ProGuard:** Minification for reduced APK size

---

## Future Enhancements

1. **Authentication:** JWT-based user authentication
2. **Real Data Integration:** Connect to actual stock market API
3. **Advanced Charts:** Add candlestick charts
4. **Push Notifications:** Real-time price alerts
5. **Web Dashboard:** React/Vue web app
6. **iOS App:** Swift/SwiftUI implementation
7. **Websockets:** Real-time price updates
8. **Machine Learning:** Price prediction models

