package com.bysel.trader.util

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class WindowLayoutInfo(
    val widthDp: Int,
    val isWide: Boolean,
    val isSeparatingFold: Boolean,
)

object WindowLayoutHelper {
    fun currentWidthDp(context: Context): Int {
        return try {
            val activity = context as? Activity
            if (activity != null) {
                val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity)
                val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
                (metrics.bounds.width() / density).toInt()
            } else {
                context.resources.configuration.screenWidthDp
            }
        } catch (_: Exception) {
            context.resources.configuration.screenWidthDp
        }
    }
}

@Composable
fun rememberWindowLayoutInfo(wideBreakpointDp: Int = 600): WindowLayoutInfo {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var info by remember(configuration.screenWidthDp, wideBreakpointDp) {
        val widthDp = configuration.screenWidthDp.takeIf { it > 0 }
            ?: WindowLayoutHelper.currentWidthDp(context)
        mutableStateOf(
            WindowLayoutInfo(
                widthDp = widthDp,
                isWide = widthDp >= wideBreakpointDp,
                isSeparatingFold = false,
            )
        )
    }

    DisposableEffect(context, wideBreakpointDp) {
        val activity = context as? Activity
        if (activity == null) {
            onDispose { }
        } else {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            scope.launch {
                try {
                    WindowInfoTracker.getOrCreate(activity)
                        .windowLayoutInfo(activity)
                        .collectLatest { layout ->
                            val widthDp = WindowLayoutHelper.currentWidthDp(activity)
                                .takeIf { it > 0 }
                                ?: activity.resources.configuration.screenWidthDp
                            val fold = layout.displayFeatures
                                .filterIsInstance<FoldingFeature>()
                                .any { it.isSeparating }
                            info = WindowLayoutInfo(
                                widthDp = widthDp,
                                isWide = widthDp >= wideBreakpointDp,
                                isSeparatingFold = fold,
                            )
                        }
                } catch (_: Exception) {
                    // Window manager / sidecar can fail on some OEM builds — keep config fallback.
                }
            }
            onDispose { scope.cancel() }
        }
    }

    return info
}
