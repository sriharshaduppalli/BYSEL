package com.bysel.trader

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.bysel.trader.ai.OnDeviceLlmManager
import com.bysel.trader.data.auth.AuthSessionManager

/**
 * Process-wide hooks for Play memory vitals and session restore.
 * Gemma stays on disk; the native engine is released when the UI is hidden.
 */
class ByselApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AuthSessionManager.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    OnDeviceLlmManager.releaseEngine()
                }
            }
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            OnDeviceLlmManager.releaseEngine()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        OnDeviceLlmManager.releaseEngine()
    }
}
