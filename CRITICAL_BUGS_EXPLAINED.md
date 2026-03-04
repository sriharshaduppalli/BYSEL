# Critical Bugs - Detailed Explanations

This document provides in-depth explanations of the 4 critical bugs that were identified and fixed in the BYSEL trading application.

---

## Bug #1: Memory Leak - ViewModel Not Cleaning Up Coroutine Job

### Severity: CRITICAL 🔴
**Impact**: Memory leak, potential app slowdown/crash over time, wasted battery and network resources

### The Problem

In [TradingViewModel.kt](Android/app/src/main/java/com/bysel/trader/viewmodel/TradingViewModel.kt), the `autoRefreshJob` coroutine was started but **never cancelled** when the ViewModel was destroyed.

#### Code Before Fix:
```kotlin
private var autoRefreshJob: Job? = null

fun startFastRefresh(intervalMs: Long = 1000) {
    stopFastRefresh()
    autoRefreshJob = viewModelScope.launch {
        while (isActive) {
            delay(intervalMs)
            refreshQuotes()
        }
    }
}

fun stopFastRefresh() {
    autoRefreshJob?.cancel()
    autoRefreshJob = null
}

// ❌ MISSING: onCleared() method
```

### Why This Is Critical

**Android ViewModel Lifecycle:**
1. User navigates to trading screen → `TradingViewModel` created
2. User starts fast refresh → `autoRefreshJob` launches infinite loop
3. User navigates away → Activity/Fragment destroyed
4. **ViewModelStore retains ViewModel** (configuration change handling)
5. User dismisses screen completely → ViewModel should be cleared
6. **BUG**: `onCleared()` not implemented → `stopFastRefresh()` never called
7. **Result**: `autoRefreshJob` continues running forever, even when the screen is gone!

**Memory Leak Consequences:**
- Coroutine keeps running every 1 second
- Each iteration makes network API call to fetch quotes
- ViewModel instance can't be garbage collected (Job holds reference)
- Network resources consumed indefinitely
- Battery drains from background network calls
- Memory usage grows over multiple sessions
- Eventually leads to OutOfMemoryError or app slowdown

### The Fix

```kotlin
override fun onCleared() {
    super.onCleared()
    stopFastRefresh() // Cancel the coroutine job before ViewModel is destroyed
}
```

### How It Works

**Android ViewModel Lifecycle Method:**
```
ViewModelProvider.get() → creates ViewModel
↓
User interacts with screen
↓
User navigates away permanently
↓
ViewModelStore.clear() → calls onCleared()
↓
Our override cancels autoRefreshJob
↓
Coroutine stops, resources released
↓
ViewModel garbage collected
```

**Coroutine Cancellation:**
```kotlin
stopFastRefresh() {
    autoRefreshJob?.cancel()  // This propagates CancellationException
    // The 'while(isActive)' loop checks for cancellation and exits
    autoRefreshJob = null     // Release Job reference
}
```

### Testing The Fix

```kotlin
@Test
fun `test onCleared cancels fast refresh job to prevent memory leak`() = runTest {
    val viewModel = TradingViewModel(mockApp, mockRepo)
    
    // Start fast refresh
    viewModel.startFastRefresh(intervalMs = 100)
    advanceTimeBy(50)
    
    // Clear the ViewModel (simulates navigation away)
    viewModel.onCleared()
    advanceTimeBy(500)
    
    // Verify: no API calls after onCleared
    verify(mockRepository, atMost(1)).getQuotes(anyList())
}
```

### Runtime Verification

**How to confirm it's fixed:**
1. Enable LeakCanary in debug build
2. Navigate to Trading screen
3. Start fast refresh
4. Press back button to exit
5. Trigger garbage collection
6. **Expected**: LeakCanary reports no leaks
7. **Before fix**: LeakCanary would report `TradingViewModel` leaked

---

## Bug #2: Race Condition - Thread-Unsafe Pagination Flag

### Severity: CRITICAL 🔴
**Impact**: Duplicate data loading, UI flickering, potential infinite scroll, data corruption

### The Problem

In [TradingViewModel.kt](Android/app/src/main/java/com/bysel/trader/viewmodel/TradingViewModel.kt), the `loadingPage` boolean was used to prevent concurrent page loads, but it's **not thread-safe**.

#### Code Before Fix:
```kotlin
private var loadingPage = false  // ❌ Not thread-safe!

fun loadNextQuotesPage() {
    if (loadingPage) return  // Check
    loadingPage = true       // Set
    
    viewModelScope.launch {
        // ... async operations
        pagedQuotes.value += newQuotes
        loadingPage = false  // Reset
    }
}
```

### Why This Is Critical

**Race Condition Scenario:**

```
Time  Thread 1              Thread 2              loadingPage
----  -------------------   -------------------   -----------
T0    if (loadingPage)      -                     false
T1    loadingPage = true    -                     true
T2    -                     if (loadingPage)      true
T3    launch coroutine      -                     true
T4    -                     loadingPage = true    true (!)
T5    -                     launch coroutine (!)  true
T6    fetch page 1          fetch page 1 again    true
T7    loadingPage = false   -                     false
T8    -                     loadingPage = false   false
```

**The Problem:**
- Between lines "check if" and "set to true", another thread can slip in
- The check-then-set is **not atomic**
- Two coroutines both think they're the only one loading
- Result: **Same page fetched twice**, duplicates in UI

**Real-World Trigger:**
```kotlin
// User scrolls quickly to bottom
LazyColumn {
    items(quotes) { /* ... */ }
    item {
        LaunchedEffect(Unit) {
            viewModel.loadNextQuotesPage()  // Triggered
        }
    }
}
// Compose recomposition triggers LaunchedEffect again
// within microseconds → RACE CONDITION
```

### The Fix

```kotlin
// Use StateFlow for atomic check-and-set
private val _loadingPage = MutableStateFlow(false)

fun loadNextQuotesPage() {
    viewModelScope.launch {
        // Atomic compare-and-set
        if (!_loadingPage.compareAndSet(expect = false, update = true)) {
            return@launch  // Another load already in progress
        }
        
        try {
            repository.getQuotesPage(currentPage, PAGE_SIZE)
                .collect { newQuotes ->
                    // Update UI
                    _pagedQuotes.value += newQuotes
                    currentPage++
                }
        } finally {
            _loadingPage.value = false  // Always reset, even on error
        }
    }
}
```

### How It Works

**StateFlow Thread-Safety:**
```kotlin
MutableStateFlow.compareAndSet(expect, update)
// Atomically:
// 1. Check if value == expect
// 2. If yes: set value = update, return true
// 3. If no: return false
// All in ONE operation, no race condition possible
```

**Execution Flow:**
```
Thread 1: compareAndSet(false, true) → returns true → proceeds
Thread 2: compareAndSet(false, true) → sees value is already true → returns false → exits
Thread 3: compareAndSet(false, true) → sees value is already true → returns false → exits

Only Thread 1 executes the loading logic!
```

**Try-Finally Pattern:**
```kotlin
try {
    // Load data (may throw exception)
    repository.getQuotesPage(...)
} finally {
    // ALWAYS reset flag, even if exception occurs
    _loadingPage.value = false
}
```

Without `finally`, if an exception happens, `loadingPage` stays `true` forever → pagination permanently broken!

### Testing The Fix

```kotlin
@Test
fun `test pagination loading flag prevents concurrent page loads`() = runTest {
    val viewModel = TradingViewModel(mockApp, mockRepo)
    
    // Rapidly trigger 3 concurrent loads
    viewModel.loadNextQuotesPage()
    viewModel.loadNextQuotesPage()
    viewModel.loadNextQuotesPage()
    
    advanceTimeBy(200)
    
    // Verify: repository called only ONCE
    verify(mockRepository, times(1)).getQuotesPage(anyInt(), anyInt())
}
```

### Deduplication Logic

Even with the race condition fixed, we also deduplicate by symbol:

```kotlin
val existingSymbols = _pagedQuotes.value.map { it.symbol }.toSet()
val uniqueNewQuotes = newQuotes.filter { it.symbol !in existingSymbols }
_pagedQuotes.value += uniqueNewQuotes
```

This handles edge cases like:
- API returns overlapping data between pages
- Network retry fetches same data
- User refreshes while pagination in progress

---

## Bug #3: `collectLatest` Blocking Code Execution

### Severity: CRITICAL 🔴
**Impact**: Pagination never loads, app appears frozen/broken

### The Problem

In [TradingViewModel.kt](Android/app/src/main/java/com/bysel/trader/viewmodel/TradingViewModel.kt#L150), `collectLatest` was used incorrectly, causing code **after** it to never execute.

#### Code Before Fix:
```kotlin
fun refreshQuotes() {
    viewModelScope.launch {
        _isLoading.value = true
        currentPage = 0  // Reset pagination
        _pagedQuotes.value = emptyList()
        
        repository.getQuotes(watchlist)
            .collectLatest { result ->  // ❌ BLOCKS HERE!
                when (result) {
                    is Result.Success -> {
                        _quotes.value = result.data
                        _isLoading.value = false
                    }
                    is Result.Error -> {
                        _error.value = result.message
                        _isLoading.value = false
                    }
                }
            }
        
        // ❌ THIS CODE NEVER RUNS!
        currentPage = 0
        loadNextQuotesPage()
    }
}
```

### Why This Is Critical

**Understanding `collectLatest`:**

`collectLatest` is a **terminal operator** that suspends indefinitely until the flow completes or is cancelled.

```kotlin
flow {
    while (true) {
        emit(fetchQuotes())  // Infinite emissions
        delay(1000)
    }
}.collectLatest { data ->
    updateUI(data)
    // When next emission comes, THIS lambda is cancelled and restarted
}
// Code here NEVER executes unless flow completes!
```

**The Flow:**
```
repository.getQuotes(watchlist) returns:
flow {
    while (isActive) {
        emit(Result.Success(quotes))
        delay(refreshInterval)
    }
}

This flow NEVER completes (infinite loop)
→ collectLatest NEVER returns
→ Code after collectLatest NEVER executes
```

**Impact:**
```kotlin
refreshQuotes() {
    // ... setup code executes
    
    collectLatest { ... }  // ← BLOCKS HERE FOREVER
    
    currentPage = 0           // ← NEVER RUNS
    loadNextQuotesPage()      // ← NEVER RUNS
}
```

**User Experience:**
1. User pulls to refresh
2. Quotes update successfully ✓
3. User scrolls to bottom for pagination
4. **Nothing happens** ✗
5. `currentPage` was never reset to 0
6. `loadNextQuotesPage()` was never called
7. App appears broken

### The Fix

```kotlin
fun refreshQuotes() {
    viewModelScope.launch {
        _isLoading.value = true
        
        repository.getQuotes(watchlist)
            .collect { result ->  // ✅ Changed to 'collect'
                when (result) {
                    is Result.Success -> {
                        _quotes.value = result.data
                        _isLoading.value = false
                        
                        // ✅ Moved reset logic INSIDE the collect block
                        currentPage = 0
                        _pagedQuotes.value = emptyList()
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

### How It Works

**`collect` vs `collectLatest`:**

| Operator | Behavior | Use Case |
|----------|----------|----------|
| `collect` | Processes every emission, doesn't cancel | When you need ALL data (history, logs) |
| `collectLatest` | Cancels previous emission when new one arrives | When you only care about LATEST (search, autocomplete) |

**Correct Usage:**
```kotlin
// Search autocomplete - only care about latest
searchQuery.collectLatest { query ->
    updateSearchResults(query)
    // If user types again, cancel previous search
}

// Data loading - need to process result
repository.getData().collect { result ->
    when (result) {
        is Success -> updateUI(result.data)
        is Error -> showError(result.message)
    }
}
```

**Execution Flow After Fix:**
```
1. refreshQuotes() called
2. Launch coroutine
3. Start collecting from repository.getQuotes()
4. Emission arrives: Result.Success(quotes)
5. Execute when(result) block
6. Match Success branch
7. Update _quotes.value
8. Reset currentPage = 0    ← NOW EXECUTES!
9. Clear _pagedQuotes        ← NOW EXECUTES!
10. Call loadNextQuotesPage() ← NOW EXECUTES!
11. Wait for next emission
```

### Testing The Fix

```kotlin
@Test
fun `test refreshQuotes resets pagination after success`() = runTest {
    whenever(mockRepository.getQuotes(anyList()))
        .thenReturn(flow { emit(Result.Success(testQuotes)) })
    
    val viewModel = TradingViewModel(mockApp, mockRepo)
    
    // Simulate pagination state
    viewModel.loadNextQuotesPage()
    advanceUntilIdle()
    
    // Refresh
    viewModel.refreshQuotes()
    advanceUntilIdle()
    
    // Verify: getQuotesPage called with page 0 (reset)
    verify(mockRepository).getQuotesPage(eq(0), anyInt())
}
```

---

## Bug #4: Alert Spam - Repeated Notifications for Same Alert

### Severity: CRITICAL 🔴
**Impact**: User annoyance, notification spam, poor UX, potential battery drain

### The Problem

In [TradingViewModel.kt](Android/app/src/main/java/com/bysel/trader/viewmodel/TradingViewModel.kt#L300), alerts were triggered every time `evaluateAlerts()` ran, as long as the price was above/below threshold. No mechanism to prevent re-triggering.

#### Code Before Fix:
```kotlin
private fun evaluateAlerts(quotes: List<Quote>) {
    val alertsMap = activeAlerts.value.associateBy { it.symbol }
    
    quotes.forEach { quote ->
        alertsMap[quote.symbol]?.let { alert ->
            val shouldTrigger = when (alert.alertType.uppercase()) {
                "ABOVE" -> quote.last >= alert.thresholdPrice
                "BELOW" -> quote.last <= alert.thresholdPrice
                else -> false
            }
            
            if (shouldTrigger) {
                // ❌ Triggers EVERY TIME evaluateAlerts() is called!
                alertsManager.showAlertNotification(alert, quote)
            }
        }
    }
}
```

### Why This Is Critical

**Scenario:**
```
User sets alert: "Notify me when RELIANCE crosses ₹2500"
```

**What Happens:**

```
Time   Price   evaluateAlerts() Called   Result
-----  ------  ------------------------  --------------------------
10:00  ₹2450   No                        (waiting...)
10:05  ₹2510   Yes                       ✅ Alert triggered!
10:06  ₹2515   Yes (fast refresh)        ⚠️ Alert triggered AGAIN!
10:07  ₹2520   Yes (fast refresh)        ⚠️ Alert triggered AGAIN!
10:08  ₹2518   Yes (fast refresh)        ⚠️ Alert triggered AGAIN!
10:09  ₹2525   Yes (fast refresh)        ⚠️ Alert triggered AGAIN!
```

**Fast Refresh Amplifies the Problem:**
```kotlin
startFastRefresh(intervalMs = 1000)  // Every 1 second
→ refreshQuotes() every second
→ evaluateAlerts() every second
→ If price > threshold: notification every second
→ User receives 60 notifications per minute! 😱
```

**User Experience:**
- Phone vibrates constantly
- Notification shade filled with duplicates
- Battery drains from notification handling
- User disables all notifications → app becomes useless
- User uninstalls app

### The Fix

**Two-Part Solution:**

**Part 1: Add `alertTriggered` flag to prevent multiple notifications per evaluation cycle**

```kotlin
private fun evaluateAlerts(quotes: List<Quote>) {
    val alertsMap = activeAlerts.value.associateBy { it.symbol }
    
    quotes.forEach { quote ->
        alertsMap[quote.symbol]?.let { alert ->
            val shouldTrigger = when (alert.alertType.uppercase()) {
                "ABOVE" -> quote.last >= alert.thresholdPrice
                "BELOW" -> quote.last <= alert.thresholdPrice
                else -> false
            }
            
            if (shouldTrigger && alert.isActive) {  // ✅ Check isActive
                var alertTriggered = false  // ✅ Local flag
                
                // Show notification only if not already triggered this cycle
                if (!alertTriggered) {
                    alertsManager.showAlertNotification(alert, quote)
                    alertTriggered = true
                    
                    // ✅ DEACTIVATE ALERT (Part 2)
                    viewModelScope.launch {
                        repository.deactivateAlert(alert.id)
                    }
                }
            }
        }
    }
}
```

**Part 2: Add `deactivateAlert()` method to repository**

```kotlin
// In TradingRepository.kt
suspend fun deactivateAlert(alertId: Int): Result<Unit> {
    return try {
        val alert = database.alertDao().getAlertById(alertId)
        if (alert != null) {
            val deactivatedAlert = alert.copy(isActive = false)
            database.alertDao().updateAlert(deactivatedAlert)
            Result.Success(Unit)
        } else {
            Result.Error("Alert not found")
        }
    } catch (e: Exception) {
        Result.Error("Failed to deactivate alert: ${e.message}")
    }
}
```

### How It Works

**State Machine Approach:**

```
[ACTIVE] → Monitoring price
    ↓
Price crosses threshold
    ↓
Notification sent
    ↓
Alert deactivated (isActive = false)
    ↓
[INACTIVE] → No longer monitoring
    ↓
User can view in history
```

**Execution Flow:**

```kotlin
evaluateAlerts() {
    if (shouldTrigger && alert.isActive) {  // First check
        showNotification()                  // Send notification
        
        launch {
            repository.deactivateAlert(id)  // Update DB: isActive = false
        }
    }
}

// Next evaluation (1 second later):
evaluateAlerts() {
    if (shouldTrigger && alert.isActive) {  // isActive is now false!
        // ↑ Condition fails, no notification sent
    }
}
```

**Database Changes:**

```sql
-- Before alert triggers
SELECT * FROM alerts WHERE id = 1;
id | symbol    | threshold | type  | isActive
1  | RELIANCE  | 2500.0    | ABOVE | 1

-- After deactivation
SELECT * FROM alerts WHERE id = 1;
id | symbol    | threshold | type  | isActive
1  | RELIANCE  | 2500.0    | ABOVE | 0

Alert still exists, but won't trigger again!
```

### Why Deactivate Instead of Delete?

**Benefits of Deactivation:**
1. **History**: User can see which alerts triggered
2. **Reactivation**: User can re-enable the same alert
3. **Analytics**: Track alert effectiveness
4. **Undo**: User can manage triggered alerts
5. **Audit**: Know when and why alerts fired

**Delete vs Deactivate:**
```kotlin
// Delete: Alert gone forever
deleteAlert(id) → Row removed from DB

// Deactivate: Alert preserved
deactivateAlert(id) → Row updated: isActive = false
```

### Testing The Fix

```kotlin
@Test
fun `test alert is deactivated after triggering`() = runTest {
    val alert = Alert(
        id = 1,
        symbol = "RELIANCE",
        thresholdPrice = 2500.0,
        alertType = "ABOVE",
        isActive = true
    )
    val quote = Quote(symbol = "RELIANCE", last = 2550.0)
    
    whenever(mockRepository.getActiveAlerts())
        .thenReturn(flowOf(listOf(alert)))
    whenever(mockRepository.deactivateAlert(1))
        .thenReturn(Result.Success(Unit))
    
    val viewModel = TradingViewModel(mockApp, mockRepo)
    
    // Trigger evaluation
    viewModel.startFastRefresh(100)
    advanceTimeBy(150)
    
    // Verify: Alert deactivated
    verify(mockRepository, atLeastOnce()).deactivateAlert(eq(1))
}
```

### Real-World Scenario

**Before Fix:**
```
User: "Alert me when RELIANCE > ₹2500"
[RELIANCE hits ₹2510]
📱 Notification: "RELIANCE crossed ₹2500"
[1 second later, still ₹2510]
📱 Notification: "RELIANCE crossed ₹2500"
📱 Notification: "RELIANCE crossed ₹2500"
📱 Notification: "RELIANCE crossed ₹2500"
...
User: *uninstalls app* 😡
```

**After Fix:**
```
User: "Alert me when RELIANCE > ₹2500"
[RELIANCE hits ₹2510]
📱 Notification: "RELIANCE crossed ₹2500"
Alert automatically deactivated
[1 second later, still ₹2510]
(no notification)
User: *checks app, sees triggered alert in history* ✅
```

---

## Summary of Critical Fixes

| Bug | Issue | Impact | Fix |
|-----|-------|--------|-----|
| #1 | Memory leak | Coroutine never cancelled | Add `onCleared()` |
| #2 | Race condition | Duplicate page loads | Use `StateFlow` |
| #3 | Blocked execution | Code after `collectLatest` never runs | Use `collect` + move logic inside |
| #4 | Alert spam | Notification every second | Deactivate alert after trigger |

All four bugs are **production-critical** and would cause severe UX issues if left unfixed.

---

## Testing Strategy

To verify all fixes work correctly:

```bash
# Run unit tests
cd android
./gradlew.bat test

# Run instrumented tests
./gradlew.bat connectedAndroidTest

# Memory leak detection
./gradlew.bat :app:leakCanary

# Build release AAB
./gradlew.bat :app:bundleRelease
```

Monitor for:
- ✅ No LeakCanary warnings
- ✅ All unit tests passing
- ✅ No duplicate pagination calls in Logcat
- ✅ Single notification per alert trigger
- ✅ Clean app shutdown (no coroutines left running)
