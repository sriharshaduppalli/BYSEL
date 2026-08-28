package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bysel.trader.data.models.InvestorTip
import com.bysel.trader.data.models.InvestorTopicInfo
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.theme.byselSectionSurface

@Composable
fun InvestorTipsCard(
    title: String = "Investor Tips",
    topicLabel: String,
    tips: List<InvestorTip>,
    disclaimer: String,
    loading: Boolean = false,
    topics: List<InvestorTopicInfo> = emptyList(),
    selectedTopic: String? = null,
    onTopicSelected: ((String) -> Unit)? = null,
    compact: Boolean = false,
    paperNote: String = "",
    sampleSize: Int = 0,
    learnLinks: List<HabitLearnLink> = emptyList(),
    onLearnQuery: ((String) -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .byselSectionSurface(RoundedCornerShape(14.dp))
            .padding(if (compact) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = if (compact) 14.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                if (onLearnQuery == null) {
                    Text(
                        text = topicLabel.ifBlank { "Education" },
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onLearnQuery == null) {
                Text(
                    text = if (tips.any { it.source.equals("paper", true) }) "Paper book" else "Learn",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = theme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        if (onLearnQuery != null) {
            val links = learnLinks.ifEmpty {
                val topicLink = HabitLiteracyCatalog.investorLinksFor(selectedTopic).firstOrNull()
                if (topicLink != null && tips.isEmpty()) {
                    listOf(topicLink)
                } else {
                    tips.map { tip ->
                        HabitLearnLink(
                            title = tip.title,
                            learnQuery = HabitLiteracyCatalog.tipLearnQuery(tip.title, tip.body),
                        )
                    }
                }
            }
            links.forEach { link ->
                TextButton(
                    onClick = { onLearnQuery(link.learnQuery) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = "Learn: ${link.title}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.primary,
                    )
                }
            }
            return
        }

        if (onTopicSelected != null && topics.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .exclusiveHorizontalScroll(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topics.forEach { topic ->
                    FilterChip(
                        selected = topic.id.equals(selectedTopic, ignoreCase = true),
                        onClick = { onTopicSelected(topic.id) },
                        label = {
                            Text(topic.label, maxLines = 1)
                        },
                    )
                }
            }
        }

        if (loading && tips.isEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = theme.primary,
            )
        } else {
            tips.take(if (compact) 3 else 4).forEach { tip ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.surface.copy(alpha = 0.55f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = tip.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.text,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tip.body,
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                        lineHeight = 15.sp,
                        maxLines = if (compact) 3 else 4,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = buildString {
                        if (tip.source.equals("paper", true)) append("From your paper book")
                        else append("Topic cue")
                        if (!tip.evidence.isNullOrBlank()) append(" · ${tip.evidence}")
                    }
                    Text(
                        text = meta,
                        fontSize = 10.sp,
                        color = theme.primary.copy(alpha = 0.85f),
                        lineHeight = 13.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val note = when {
            paperNote.isNotBlank() -> paperNote
            sampleSize > 0 -> "Based on $sampleSize paper fills. Educational — not live demat."
            else -> ""
        }
        if (note.isNotBlank()) {
            Text(
                text = note,
                fontSize = 10.sp,
                color = theme.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = disclaimer,
            fontSize = 10.sp,
            color = theme.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
