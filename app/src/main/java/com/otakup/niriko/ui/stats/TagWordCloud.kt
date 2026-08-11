package com.otakup.niriko.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.data.model.stats.TagStat

/**
 * 标签词云。按使用频率映射字体大小，点击可筛选。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagWordCloud(
    tags: List<TagStat>,
    onTagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return

    val maxCount = tags.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "标签词云",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { tag ->
                // 字体大小映射：12sp (最少) ~ 28sp (最多)
                val ratio = tag.count.toFloat() / maxCount
                val fontSize = (12f + ratio * 16f).coerceIn(12f, 28f)
                val weight = if (ratio > 0.6f) FontWeight.Bold
                    else if (ratio > 0.3f) FontWeight.Medium
                    else FontWeight.Normal
                val alpha = (0.5f + ratio * 0.5f).coerceIn(0.5f, 1f)

                Text(
                    text = tag.name,
                    fontSize = fontSize.sp,
                    fontWeight = weight,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onTagClick(tag.name) },
                )
            }
        }
    }
}
