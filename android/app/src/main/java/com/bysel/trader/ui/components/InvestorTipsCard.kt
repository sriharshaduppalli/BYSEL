package com.bysel.trader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
                Text(
                    text = topicLabel.ifBlank { "Education" },
                    fontSize = 11.sp,
                    color = theme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Learn",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = theme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
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
            tips.take(if (compact) 2 else 3).forEach { tip ->
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tip.body,
                        fontSize = 11.sp,
                        color = theme.textSecondary,
                        maxLines = if (compact) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
