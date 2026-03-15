package com.bysel.trader.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

private val TRACE_ID_PATTERN =
    Regex("(?i)(?:trace(?:\\s*id)?|traceId)\\s*[:=]\\s*([A-Za-z0-9._-]+)")

fun extractTraceIdFromMessage(message: String?): String? {
    if (message.isNullOrBlank()) {
        return null
    }
    val match = TRACE_ID_PATTERN.find(message) ?: return null
    return match.groupValues.getOrNull(1)
        ?.trim()
        ?.trimEnd('.', ',', ';', ')', ']')
        ?.takeIf { it.isNotBlank() }
}

@Composable
fun TraceAwareErrorSnackbar(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTraceAction: ((String) -> Unit)? = null,
    traceActionLabel: String = "Support Lookup",
) {
    val clipboardManager = LocalClipboardManager.current
    val traceId = remember(error) { extractTraceIdFromMessage(error) }

    Snackbar(
        modifier = modifier,
        action = if (traceId != null) {
            {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(traceId))
                    onTraceAction?.invoke(traceId)
                }) {
                    Text(if (onTraceAction != null) traceActionLabel else "Copy Trace")
                }
            }
        } else {
            null
        },
        dismissAction = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
    ) {
        Text(error)
    }
}