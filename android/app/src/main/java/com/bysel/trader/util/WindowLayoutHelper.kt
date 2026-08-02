package com.bysel.trader.util

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        val activity = context as? Activity ?: return 0
        val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity)
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        return (metrics.bounds.width() / density).toInt()
    }
}

@Composable
fun rememberWindowLayoutInfo(wideBreakpointDp: Int = 600): WindowLayoutInfo {
    val context = LocalContext.current
    var info by remember {
        mutableStateOf(
            WindowLayoutInfo(
                widthDp = WindowLayoutHelper.currentWidthDp(context),
                isWide = WindowLayoutHelper.currentWidthDp(context) >= wideBreakpointDp,
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
                WindowInfoTracker.getOrCreate(activity)
                    .windowLayoutInfo(activity)
                    .collectLatest { layout ->
                        val widthDp = WindowLayoutHelper.currentWidthDp(activity)
                        val fold = layout.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .any { it.isSeparating }
                        info = WindowLayoutInfo(
                            widthDp = widthDp,
                            isWide = widthDp >= wideBreakpointDp,
                            isSeparatingFold = fold,
                        )
                    }
            }
            onDispose { scope.cancel() }
        }
    }

    return info
}
