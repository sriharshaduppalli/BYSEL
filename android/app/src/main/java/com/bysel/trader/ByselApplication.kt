package com.bysel.trader

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * App-process lifecycle hooks for pausing live quotes when BYSEL is backgrounded
 * (including when another Activity like UPI is *not* the reason — ProcessLifecycle
 * only fires when the whole app leaves the foreground).
 */
class ByselApplication : Application() {
    @Volatile
    var onAppForeground: (() -> Unit)? = null

    @Volatile
    var onAppBackground: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    onAppForeground?.invoke()
                }

                override fun onStop(owner: LifecycleOwner) {
                    onAppBackground?.invoke()
                }
            }
        )
    }

    companion object {
        @Volatile
        private var instance: ByselApplication? = null

        fun get(): ByselApplication? = instance
    }
}
