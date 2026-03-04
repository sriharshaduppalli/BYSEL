# BYSEL Android Application - Bug Audit Report
**Date:** March 3, 2026  
**Version:** 2.6.18 (Code: 58)  
**Audited By:** AI Code Review  

---

## 🔴 CRITICAL BUGS (Must Fix)

### 1. **Memory Leak - Coroutine Job Not Cancelled in ViewModel**
**File:** `TradingViewModel.kt` Lines 380-440  
**Severity:** CRITICAL  
**Issue:** The `autoRefreshJob` coroutine may not be properly cancelled when the ViewModel is cleared, causing potential memory leaks.

**Current Code:**
```kotlin
fun startFastRefresh(intervalMs: Long = FAST_REFRESH_INTERVAL, symbols: List<String> = defaultSymbols) {
    if (autoRefreshJob?.isActive == true) return
    if (!_fastRefreshEnabled.value) return
    autoRefreshJob = viewModelScope.launch {
        while (isActive) {
            // ... refresh logic
        }
    }
}
```

**Problem:** While `stopFastRefresh()` cancels the job, if the ViewModel is destroyed without calling `stopFastRefresh()`, the job continues.

**Fix:** Override `onCleared()` in ViewModel:
```kotlin
override fun onCleared() {
    super.onCleared()
    stopFastRefresh()
}
```

---

### 2. **Race Condition in Pagination Loading**
**File:** `TradingViewModel.kt` Line 266  
**Severity:** CRITICAL  
**Issue:** The `loadingPage` flag is not thread-safe and can cause race conditions.

**Current Code:**
```kotlin
private var loadingPage = false

fun loadNextQuotesPage() {
    if (loadingPage) return
    loadingPage = true
    viewModelScope.launch {
        // ... async operations
        loadingPage = false
    }
}
```

**Problem:** Multiple rapid calls to `loadNextQuotesPage()` can bypass the check before `loadingPage` is set to true.

**Fix:** Use atomic operations or StateFlow:
```kotlin
private val _loadingPage = MutableStateFlow(false)

fun loadNextQuotesPage() {
    if (!_loadingPage.compareAndSet(false, true)) return
    viewModelScope.launch {
        try {
            // ... pagination logic
        } finally {
            _loadingPage.value = false
        }
    }
}
```

---

### 3. **collectLatest Inside Launch Creates Infinite Loop Risk**
**File:** `TradingViewModel.kt` Lines 245-265  
**Severity:** HIGH  
**Issue:** Using `collectLatest` inside `viewModelScope.launch` without proper scope management.

**Current Code:**
```kotlin
fun refreshQuotes() {
    viewModelScope.launch {
        repository.getQuotes(defaultSymbols).collectLatest { result ->
            when (result) {
                is Result.Loading -> _isLoading.value = true
                // ...
            }
        }
        currentPage = 0
        loadNextQuotesPage()
    }
}
```

**Problem:** `collectLatest` is a terminal operator that collects forever. The code after it (`currentPage = 0`) will never execute unless the flow completes or is cancelled.

**Fix:** Collect in init block or use different pattern:
```kotlin
fun refreshQuotes() {
    viewModelScope.launch {
        repository.getQuotes(defaultSymbols).collect { result ->
            when (result) {
                is Result.Loading -> _isLoading.value = true
                is Result.Success -> {
                    _quotes.value = result.data
                    _isLoading.value = false
                    // Reset paging after success
                    currentPage = 0
                    loadNextQuotesPage()
                }
                is Result.Error -> {
                    _error.value = result.message
                    _isLoading.value = false
                }
            }
        }
    }
}
```

---

### 4. **Alert Notifications Not Deactivated After Triggering**
**File:** `TradingViewModel.kt` Lines 490-500  
**Severity:** HIGH  
**Issue:** Alerts trigger repeatedly every refresh cycle after threshold is crossed.

**Current Code:**
```kotlin
private fun evaluateAlerts(quotesNow: List<Quote>) {
    val activeAlerts = _alerts.value.filter { it.isActive }
    if (activeAlerts.isEmpty()) return
    val map = quotesNow.associateBy { it.symbol }
    for (a in activeAlerts) {
        val q = map[a.symbol] ?: continue
        val price = q.last
        when (a.alertType.uppercase()) {
            "ABOVE" -> if (price >= a.thresholdPrice) alertsManager.sendPriceAlert(a, price)
            "BELOW" -> if (price <= a.thresholdPrice) alertsManager.sendPriceAlert(a, price)
        }
    }
}
```

**Problem:** No call to deactivate alert after triggering, causing spam notifications.

**Fix:** Deactivate alert after triggering:
```kotlin
when (a.alertType.uppercase()) {
    "ABOVE" -> if (price >= a.thresholdPrice) {
        alertsManager.sendPriceAlert(a, price)
        viewModelScope.launch {
            repository.deactivateAlert(a.id)
        }
    }
    "BELOW" -> if (price <= a.thresholdPrice) {
        alertsManager.sendPriceAlert(a, price)
        viewModelScope.launch {
            repository.deactivateAlert(a.id)
        }
    }
}
```

---

## 🟠 HIGH PRIORITY BUGS

### 5. **Unsafe lateinit Variable**
**File:** `TradingViewModel.kt` Line 95  
**Severity:** HIGH  
**Issue:** `lateinit var alertsManager` is unnecessarily lateinit since it's initialized in `init{}`.

**Current Code:**
```kotlin
private lateinit var alertsManager: AlertsManager

init {
    loadAchievements()
    alertsManager = AlertsManager(getApplication())
    // ...
}
```

**Fix:** Use direct initialization:
```kotlin
private val alertsManager: AlertsManager = AlertsManager(getApplication())
```

---

### 6. **Search Cache Not Bounded**
**File:** `TradingViewModel.kt` Line 328  
**Severity:** MEDIUM-HIGH  
**Issue:** `searchCache` map can grow unbounded, causing memory issues.

**Current Code:**
```kotlin
private val searchCache = mutableMapOf<String, List<StockSearchResult>>()
```

**Fix:** Use LRU cache with size limit:
```kotlin
private val searchCache = object : LinkedHashMap<String, List<StockSearchResult>>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, List<StockSearchResult>>): Boolean {
        return size > 50 // Keep max 50 cached searches
    }
}
```

---

### 7. **Missing Error Handling in Flow Collection**
**File:** `TradingViewModel.kt` Lines 145-149  
**Severity:** MEDIUM-HIGH  
**Issue:** Error in alert flow collection is silently swallowed.

**Current Code:**
```kotlin
viewModelScope.launch {
    try {
        repository.getActiveAlerts().collectLatest { list -> _alerts.value = list }
    } catch (_: Exception) { }
}
```

**Fix:** Log errors or handle gracefully:
```kotlin
viewModelScope.launch {
    repository.getActiveAlerts()
        .catch { e -> 
            Log.e("TradingViewModel", "Error collecting alerts", e)
            emit(emptyList()) // Emit empty list on error
        }
        .collectLatest { list -> _alerts.value = list }
}
```

---

### 8. **SharedPreferences Not Thread-Safe**
**File:** `TradingViewModel.kt` Multiple locations  
**Severity:** MEDIUM  
**Issue:** Multiple threads reading/writing SharedPreferences without synchronization.

**Problem:** SharedPreferences.edit().putX().apply() is called from coroutines which may run on different threads.

**Fix:** Use DataStore instead of SharedPreferences (modern Android recommended):
```kotlin
// Replace SharedPreferences with DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bysel_settings")
```

---

## 🟡 MEDIUM PRIORITY BUGS

### 9. **Unused Function Parameter**
**File:** `TradingScreen.kt` Line 40  
**Severity:** LOW  
**Issue:** `quotes` parameter is never used in the composable.

**Fix:** Remove unused parameter or use it:
```kotlin
@Composable
fun TradingScreen(
    // quotes: List<Quote>, // REMOVE THIS
    isLoading: Boolean,
    error: String?,
    // ... other params
```

---

### 10. **Unused Variable**
**File:** `CandlestickChart.kt` Line 33  
**Severity:** LOW  
**Issue:** `scrollState` is declared but never used.

**Fix:** Either remove it or implement horizontal scrolling:
```kotlin
// Remove this line if not needed:
// val scrollState = rememberScrollState()
```

---

### 11. **Missing Null Safety Check**
**File:** `TradingViewModel.kt` Line 405  
**Severity:** MEDIUM  
**Issue:** Battery status intent can be null but is used with safe call only.

**Current Code:**
```kotlin
val intent = getApplication<Application>().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
```

**Problem:** While safe call is used, returning false on any error might not be the desired behavior during actual charging.

**Fix:** Add better error handling:
```kotlin
private fun isDeviceCharging(): Boolean {
    return try {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = getApplication<Application>().registerReceiver(null, filter)
        if (batteryStatus == null) {
            Log.w("TradingViewModel", "Could not read battery status")
            return true // Default to true to avoid blocking refresh
        }
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        status == BatteryManager.BATTERY_STATUS_CHARGING || 
        status == BatteryManager.BATTERY_STATUS_FULL
    } catch (e: Exception) {
        Log.e("TradingViewModel", "Error checking charging status", e)
        true // Default to true on error
    }
}
```

---

### 12. **Demo Account Initialization Logic Flaw**
**File:** `TradingViewModel.kt` Lines 207-227  
**Severity:** MEDIUM  
**Issue:** Demo account initialization doesn't handle Loading state properly.

**Current Code:**
```kotlin
when (val r = repository.getWallet()) {
    is Result.Success -> {
        if (r.data.balance > 0.0) return@launch
    }
    is Result.Error -> {
        // If error fetching wallet, we proceed to demo initialization as a fallback
    }
    else -> { /* proceed to demo */ }
}
```

**Problem:** `else` branch handles Loading state incorrectly - it should wait, not proceed.

**Fix:**
```kotlin
when (val r = repository.getWallet()) {
    is Result.Success -> {
        if (r.data.balance > 0.0) return@launch
        // Else initialize demo
    }
    is Result.Error -> {
        // Initialize demo as fallback
    }
    is Result.Loading -> {
        return@launch // Wait, don't initialize
    }
}
```

---

## 🔵 LOW PRIORITY ISSUES

### 13. **AndroidManifest AD_ID Permission Warning**
**File:** `AndroidManifest.xml` Line 7  
**Severity:** LOW  
**Issue:** Warning about removing AD_ID permission when no other declaration exists.

**Fix:** Simply remove the line if you're not using Google Ads:
```xml
<!-- Remove this line: -->
<!-- <uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" /> -->
```

---

### 14. **KAPT Processor Options Warning**
**File:** Build output  
**Severity:** LOW  
**Issue:** Unrecognized KAPT options causing build warnings.

**Fix:** Update `build.gradle.kts`:
```kotlin
kapt {
    correctErrorTypes = true
    arguments {
        arg("dagger.fastInit", "enabled")
    }
}
```

---

### 15. **Magic Numbers in Code**
**File:** `AlertsManager.kt` Line 16  
**Severity:** LOW  
**Issue:** Magic number `1000` used without explanation.

**Current Code:**
```kotlin
private const val NOTIF_ID_BASE = 1000

nm.notify(NOTIF_ID_BASE + (alert.id % 1000), notif)
```

**Fix:** Add comment or use constant:
```kotlin
private const val NOTIF_ID_BASE = 1000
private const val MAX_ALERT_NOTIFICATIONS = 1000

nm.notify(NOTIF_ID_BASE + (alert.id % MAX_ALERT_NOTIFICATIONS), notif)
```

---

## 🟢 POTENTIAL ENHANCEMENTS

### 16. **Missing Offline Support**
**Location:** Repository layer  
**Issue:** No offline-first strategy. App fails when network is unavailable.

**Suggestion:** Implement offline-first with Room cache:
```kotlin
fun getQuotes(symbols: List<String>): Flow<Result<List<Quote>>> = flow {
    // Emit cached data first
    emitAll(database.quoteDao().getQuotesBySymbols(symbols).map { Result.Success(it) })
    
    // Then fetch fresh data
    try {
        val quotes = apiService.getQuotes(symbols.joinToString(","))
        database.quoteDao().insertQuotes(quotes)
        emit(Result.Success(quotes))
    } catch (e: Exception) {
        emit(Result.Error(e.message ?: "Network error"))
    }
}
```

---

### 17. **No Request Deduplication**
**Location:** TradingRepository  
**Issue:** Multiple simultaneous requests for same data execute independently.

**Suggestion:** Use `shareIn` or implement request deduplication.

---

### 18. **Missing Input Validation**
**Location:** Various ViewModels  
**Issue:** No validation for user inputs (quantity, price, etc.).

**Example Fix:**
```kotlin
fun placeOrder(symbol: String, quantity: Int, side: String) {
    if (quantity <= 0) {
        _error.value = "Quantity must be positive"
        return
    }
    if (symbol.isBlank()) {
        _error.value = "Invalid symbol"
        return
    }
    // ... proceed with order
}
```

---

## 📊 SUMMARY

### Bug Count by Severity:
- 🔴 **CRITICAL:** 4 bugs
- 🟠 **HIGH:** 4 bugs
- 🟡 **MEDIUM:** 4 bugs
- 🔵 **LOW:** 3 bugs
- 🟢 **ENHANCEMENTS:** 3 items

### **Total Issues Found:** 18

---

## ✅ RECOMMENDED FIXES (Priority Order)

1. **IMMEDIATE (Critical):**
   - Fix memory leak in ViewModel (Bug #1)
   - Fix race condition in pagination (Bug #2)
   - Fix alert notification spam (Bug #4)
   - Fix collectLatest usage (Bug #3)

2. **SHORT TERM (High Priority):**
   - Remove unsafe lateinit (Bug #5)
   - Implement LRU cache for search (Bug #6)
   - Add error handling to flow collection (Bug #7)

3. **MEDIUM TERM:**
   - Migrate to DataStore (Bug #8)
   - Fix demo account logic (Bug #12)
   - Clean up unused code (Bugs #9, #10)

4. **LONG TERM (Enhancements):**
   - Implement offline-first architecture
   - Add input validation
   - Implement request deduplication

---

## 🛠️ TESTING RECOMMENDATIONS

1. **Add Unit Tests for:**
   - TradingViewModel coroutine lifecycle
   - Alert evaluation logic
   - Pagination logic

2. **Add Integration Tests for:**
   - Repository data flow
   - Database migrations
   - Network error handling

3. **Add UI Tests for:**
   - Fast refresh toggle behavior
   - Alert creation and triggering
   - Pagination scroll behavior

---

**End of Report**
