package com.otakup.niriko.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.model.SuggestionItem
import com.otakup.niriko.ui.components.LiquidGlassSurface

/**
 * 搜索建议浮层。
 * 显示在搜索框下方，包含历史关键字和本地/远程作品建议。
 */
@Composable
fun SuggestionDropdown(
    suggestions: List<SuggestionItem>,
    onHistoryClick: (String) -> Unit,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    // 静态高光 Brush（composable 层 remember，避免 drawBehind 每帧分配）
    val highlightBrush = remember {
        Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
        )
    }

    // 液态玻璃浮层（真折射）：backdrop 为静态渐变"封面墙"（模拟页面背后内容，不随击键重建）。
    // 渲染链在 remember 中缓存（仅尺寸/参数变化重建），击键只重绘内容层 → 恢复真玻璃且
    // 性能可控（旧实现每次击键重建 RenderEffect backdrop，打字 20 字符 = 20 次全层 blur）。
    // API 33+ 折射/色散；31-32 高斯模糊；26-30 tint 降级。
    LiquidGlassSurface(
        backdrop = { m ->
            // 玻璃背后的静态内容源：柔和渐变（折射采样的底图）
            Box(
                modifier = m.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        ),
                    ),
                ),
            )
        },
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                // 顶部受光高光（静态 Brush，无每帧分配）
                drawRect(brush = highlightBrush)
            },
        )
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            suggestions.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                when (item) {
                    is SuggestionItem.HistoryKeyword -> {
                        HistorySuggestionRow(
                            keyword = item.keyword,
                            onClick = { onHistoryClick(item.keyword) },
                        )
                    }
                    is SuggestionItem.LocalSubject -> {
                        SubjectSuggestionRow(
                            title = item.subject.displayTitle,
                            subtitle = item.subject.title.takeIf { item.subject.titleCN != null },
                            typeLabel = item.subject.type.label,
                            imageUrl = item.subject.coverUrl,
                            onClick = { onSubjectClick(item.subject.subjectId) },
                        )
                    }
                    is SuggestionItem.RemoteSuggestion -> {
                        SubjectSuggestionRow(
                            title = item.subject.displayTitle,
                            subtitle = item.subject.title.takeIf { item.subject.titleCN != null },
                            typeLabel = item.subject.type.label,
                            imageUrl = item.subject.coverUrl,
                            onClick = { onSubjectClick(item.subject.subjectId) },
                            isRemote = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySuggestionRow(
    keyword: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = keyword,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubjectSuggestionRow(
    title: String,
    subtitle: String?,
    typeLabel: String,
    imageUrl: String?,
    onClick: () -> Unit,
    isRemote: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面缩略图
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "$title 封面",
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "默认封面",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (isRemote) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "网络",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
