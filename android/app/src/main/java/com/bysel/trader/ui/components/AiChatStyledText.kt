package com.bysel.trader.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.bysel.trader.ui.theme.LocalAppTheme

private val SECTION_HEADERS = setOf(
    "direct answer",
    "why",
    "action",
    "action legend",
    "action legend (paper practice)",
    "sentiment analysis",
    "key levels",
    "key levels & tape",
    "quick math",
    "quantitative stack",
    "full math",
    "meaning",
)

private val ACTION_TOKENS = listOf(
    "STRONG BUY",
    "STRONG SELL",
    "ACCUMULATE",
    "BUY",
    "SELL",
    "TRIM",
    "HOLD",
    "WAIT",
    "NEUTRAL",
)

/**
 * Renders AI assistant markdown-ish replies with clearer hierarchy:
 * bold sections, colored action states, softer body copy, numeric monospace for ₹ amounts.
 */
@Composable
fun AiChatStyledText(
    text: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false,
) {
    val theme = LocalAppTheme.current
    val bodyColor = if (isUser) theme.onPrimary else theme.text
    val mutedColor = if (isUser) theme.onPrimary.copy(alpha = 0.85f) else theme.textSecondary
    val primary = if (isUser) theme.onPrimary else theme.primary
    val positive = theme.positive
    val negative = theme.negative
    val caution = Color(0xFFFFB74D)

    val annotated = remember(text, isUser, bodyColor, mutedColor, primary, positive, negative) {
        if (isUser) {
            AnnotatedString(text)
        } else {
            styleAiChatText(
                raw = text,
                bodyColor = bodyColor,
                mutedColor = mutedColor,
                primary = primary,
                positive = positive,
                negative = negative,
                caution = caution,
            )
        }
    }

    val style = if (isUser) {
        MaterialTheme.typography.bodyMedium.copy(color = bodyColor)
    } else {
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.5.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.15.sp,
            color = bodyColor,
            lineBreak = LineBreak.Paragraph,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Proportional,
                trim = LineHeightStyle.Trim.None,
            ),
        )
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
    )
}

internal fun styleAiChatText(
    raw: String,
    bodyColor: Color,
    mutedColor: Color,
    primary: Color,
    positive: Color,
    negative: Color,
    caution: Color,
): AnnotatedString {
    val cleaned = raw
        .replace("\r\n", "\n")
        .replace(Regex("(?m)^[ \\t]+"), "") // keep bullets readable; trim leading spaces per line lightly
        .trim()

    return buildAnnotatedString {
        val lines = cleaned.split('\n')
        lines.forEachIndexed { index, line ->
            appendStyledLine(
                line = line,
                bodyColor = bodyColor,
                mutedColor = mutedColor,
                primary = primary,
                positive = positive,
                negative = negative,
                caution = caution,
            )
            if (index < lines.lastIndex) append('\n')
        }
    }
}

private fun AnnotatedString.Builder.appendStyledLine(
    line: String,
    bodyColor: Color,
    mutedColor: Color,
    primary: Color,
    positive: Color,
    negative: Color,
    caution: Color,
) {
    val trimmed = line.trimEnd()
    if (trimmed.isBlank()) {
        append("")
        return
    }

    // Bullet rows — soft accent on the marker
    val bulletMatch = Regex("""^([•\-\*])\s+(.*)$""").find(trimmed)
    val content = if (bulletMatch != null) {
        withStyle(SpanStyle(color = primary.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold)) {
            append("• ")
        }
        bulletMatch.groupValues[2]
    } else {
        trimmed
    }

    // Leading **Section:** label, or **Title** rest
    val labeledSection = Regex("""^\*\*(.+?):\*\*\s*(.*)$""").find(content)
    val titleSection = if (labeledSection == null) {
        Regex("""^\*\*(.+?)\*\*\s*(.*)$""").find(content)
    } else {
        null
    }
    val sectionMatch = labeledSection ?: titleSection

    if (sectionMatch != null) {
        val heading = sectionMatch.groupValues[1].trim()
        val rest = sectionMatch.groupValues[2]
        val headingKey = heading.lowercase()
        val isLabeled = labeledSection != null
        val isSection = isLabeled ||
            SECTION_HEADERS.any { headingKey == it || headingKey.startsWith("$it ") }

        withStyle(
            SpanStyle(
                color = if (isSection || isLabeled) primary else bodyColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isLabeled) 15.sp else 15.sp,
            )
        ) {
            append(heading)
            if (isLabeled) append(":")
        }
        if (rest.isNotEmpty()) {
            append(if (isLabeled) " " else " ")
            appendInlineMarkup(
                rest,
                bodyColor = bodyColor,
                mutedColor = mutedColor,
                primary = primary,
                positive = positive,
                negative = negative,
                caution = caution,
                emphasizeActions = isLabeled && (
                    headingKey.startsWith("action") || headingKey.startsWith("direct")
                    ),
            )
        }
        return
    }

    // Plain "Action legend:" without markdown stars
    val plainSection = Regex("""^(Direct answer|Why|Action|Action legend|Sentiment analysis|Key levels.*|Quick math|Quantitative stack|Meaning)\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)
        .find(content)
    if (plainSection != null) {
        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) {
            append(plainSection.groupValues[1].trim())
            append(":")
        }
        val rest = plainSection.groupValues[2]
        if (rest.isNotEmpty()) {
            append(" ")
            appendInlineMarkup(
                rest,
                bodyColor = bodyColor,
                mutedColor = mutedColor,
                primary = primary,
                positive = positive,
                negative = negative,
                caution = caution,
                emphasizeActions = true,
            )
        }
        return
    }

    appendInlineMarkup(
        content,
        bodyColor = bodyColor,
        mutedColor = mutedColor,
        primary = primary,
        positive = positive,
        negative = negative,
        caution = caution,
        emphasizeActions = true,
    )
}

private fun AnnotatedString.Builder.appendInlineMarkup(
    text: String,
    bodyColor: Color,
    mutedColor: Color,
    primary: Color,
    positive: Color,
    negative: Color,
    caution: Color,
    emphasizeActions: Boolean,
) {
    // Tokenize by **bold**, _italic_, then color actions / rupee amounts inside plain runs.
    val tokenRe = Regex("""(\*\*[^*]+\*\*|_[^_\n]+_)""")
    var cursor = 0
    for (match in tokenRe.findAll(text)) {
        if (match.range.first > cursor) {
            appendColoredPlain(
                text.substring(cursor, match.range.first),
                bodyColor = bodyColor,
                mutedColor = mutedColor,
                positive = positive,
                negative = negative,
                caution = caution,
                emphasizeActions = emphasizeActions,
            )
        }
        val token = match.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                val inner = token.removePrefix("**").removeSuffix("**")
                withStyle(SpanStyle(color = bodyColor, fontWeight = FontWeight.SemiBold)) {
                    appendColoredPlain(
                        inner,
                        bodyColor = bodyColor,
                        mutedColor = mutedColor,
                        positive = positive,
                        negative = negative,
                        caution = caution,
                        emphasizeActions = emphasizeActions,
                    )
                }
            }
            token.startsWith("_") && token.endsWith("_") -> {
                val inner = token.removePrefix("_").removeSuffix("_")
                withStyle(SpanStyle(color = mutedColor, fontStyle = FontStyle.Italic, fontSize = 12.5.sp)) {
                    append(inner)
                }
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        appendColoredPlain(
            text.substring(cursor),
            bodyColor = bodyColor,
            mutedColor = mutedColor,
            positive = positive,
            negative = negative,
            caution = caution,
            emphasizeActions = emphasizeActions,
        )
    }
}

private fun AnnotatedString.Builder.appendColoredPlain(
    text: String,
    bodyColor: Color,
    mutedColor: Color,
    positive: Color,
    negative: Color,
    caution: Color,
    emphasizeActions: Boolean,
) {
    if (text.isEmpty()) return

    // Highlight action labels and ₹ / plain money figures.
    val pattern = buildString {
        append("""(?i)\b(?:""")
        append(ACTION_TOKENS.joinToString("|") { Regex.escape(it) })
        append(""")\b|₹\s?[\d,]+(?:\.\d+)?|[\d,]+(?:\.\d+)?%""")
    }
    val re = Regex(pattern)
    var cursor = 0
    for (match in re.findAll(text)) {
        if (match.range.first > cursor) {
            withStyle(SpanStyle(color = bodyColor)) {
                append(text.substring(cursor, match.range.first))
            }
        }
        val token = match.value
        val upper = token.uppercase()
        when {
            emphasizeActions && ACTION_TOKENS.any { upper == it || upper.startsWith("$it ") } -> {
                val color = actionColor(upper, positive, negative, caution, bodyColor)
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                    append(token)
                }
            }
            token.contains('₹') || token.endsWith('%') -> {
                withStyle(
                    SpanStyle(
                        color = bodyColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    )
                ) {
                    append(token)
                }
            }
            else -> withStyle(SpanStyle(color = bodyColor)) { append(token) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        // Soft-mute long legend / disclaimer tails
        val tail = text.substring(cursor)
        val color = if (tail.contains("paper practice", ignoreCase = true) ||
            tail.contains("not SEBI", ignoreCase = true) ||
            tail.contains("Educational", ignoreCase = true)
        ) {
            mutedColor
        } else {
            bodyColor
        }
        withStyle(SpanStyle(color = color)) { append(tail) }
    }
}

private fun actionColor(
    upper: String,
    positive: Color,
    negative: Color,
    caution: Color,
    fallback: Color,
): Color = when {
    upper.contains("ACCUMULATE") || upper.contains("BUY") -> positive
    upper.contains("TRIM") -> caution
    upper.contains("SELL") -> negative
    upper.contains("HOLD") || upper.contains("WAIT") || upper.contains("NEUTRAL") -> caution.copy(alpha = 0.85f)
    else -> fallback
}
