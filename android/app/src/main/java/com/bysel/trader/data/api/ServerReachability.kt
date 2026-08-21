package com.bysel.trader.data.api

/**
 * Process-wide view of whether the market host recently answered.
 *
 * Used so a 15s quote timeout is not labeled "Server is waking up" when
 * /health or another call already succeeded quickly in this session.
 */
object ServerReachability {
    const val FAST_ROUNDTRIP_MS = 3_000L
    const val WARM_WINDOW_MS = 8 * 60_000L
    const val WAKE_HINT_DELAY_MS = 2_500L

    @Volatile
    private var lastSuccessAtMs: Long = 0L

    @Volatile
    private var lastFastSuccessAtMs: Long = 0L

    @Volatile
    private var wakeStartedAtMs: Long = 0L

    @Synchronized
    fun resetForTests() {
        lastSuccessAtMs = 0L
        lastFastSuccessAtMs = 0L
        wakeStartedAtMs = 0L
    }

    fun markWakeStarted() {
        if (isLikelyWarm()) return
        if (wakeStartedAtMs == 0L) {
            wakeStartedAtMs = System.currentTimeMillis()
        }
    }

    fun markSuccess(elapsedMs: Long) {
        val now = System.currentTimeMillis()
        lastSuccessAtMs = now
        if (elapsedMs in 0 until FAST_ROUNDTRIP_MS) {
            lastFastSuccessAtMs = now
        }
        wakeStartedAtMs = 0L
    }

    fun isLikelyWarm(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (lastSuccessAtMs <= 0L) return false
        return nowMs - lastSuccessAtMs < WARM_WINDOW_MS
    }

    fun isLikelyColdStart(nowMs: Long = System.currentTimeMillis()): Boolean =
        !isLikelyWarm(nowMs)

    fun hadFastSuccessRecently(nowMs: Long = System.currentTimeMillis()): Boolean {
        return lastFastSuccessAtMs > 0L && nowMs - lastFastSuccessAtMs < WARM_WINDOW_MS
    }
}
