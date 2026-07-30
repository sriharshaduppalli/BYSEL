package com.bysel.trader.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme

private val TRACE_ID_PATTERN =
    Regex("(?i)(?:trace(?:\\s*id)?|traceId)\\s*[:=]\\s*([A-Za-z0-9._-]+)")

private val TRACE_LINE_PATTERN =
    Regex("(?im)^\\s*(?:trace(?:\\s*id)?|traceId)\\s*[:=]\\s*[A-Za-z0-9._-]+\\s*$")

private val TRACE_INLINE_PATTERN =
    Regex("(?i)\\s*[(\\[]?\\s*(?:trace(?:\\s*id)?|traceId)\\s*[:=]\\s*[A-Za-z0-9._-]+\\s*[)\\]]?")

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

/** User-facing copy without support-trace noise. */
fun sanitizeErrorMessageForDisplay(message: String?): String {
    if (message.isNullOrBlank()) return "Something went wrong. Please try again."
    val cleaned = message
        .lines()
        .filterNot { TRACE_LINE_PATTERN.matches(it.trim()) }
        .joinToString("\n")
        .replace(TRACE_INLINE_PATTERN, "")
        .replace(Regex("\\n{2,}"), "\n")
        .trim()
    return cleaned.ifBlank { "Something went wrong. Please try again." }
}

@Composable
fun TraceAwareErrorSnackbar(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTraceAction: ((String) -> Unit)? = null,
    traceActionLabel: String = "Support Lookup",
) {
    val theme = LocalAppTheme.current
    val clipboardManager = LocalClipboardManager.current
    val traceId = remember(error) { extractTraceIdFromMessage(error) }
    val displayMessage = remember(error) { sanitizeErrorMessageForDisplay(error) }
    val isLight = theme.surface.luminance() > 0.5f
    val container = if (isLight) {
        theme.negative.copy(alpha = 0.10f)
    } else {
        theme.card
    }
    val borderColor = theme.negative.copy(alpha = if (isLight) 0.35f else 0.45f)

    Snackbar(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
        containerColor = container,
        contentColor = theme.text,
        actionContentColor = theme.primary,
        dismissActionContentColor = theme.textSecondary,
        shape = RoundedCornerShape(8.dp),
        action = if (traceId != null) {
            {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(traceId))
                    onTraceAction?.invoke(traceId)
                }) {
                    Text(
                        text = if (onTraceAction != null) traceActionLabel else "Copy Trace",
                        color = theme.primary,
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            null
        },
        dismissAction = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = theme.textSecondary, fontSize = 12.sp)
            }
        },
    ) {
        Text(
            text = displayMessage,
            color = theme.text,
            fontSize = 13.sp,
        )
    }
}
