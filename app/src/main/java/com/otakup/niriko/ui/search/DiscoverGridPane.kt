package com.otakup.niriko.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.model.search.TrendingMode

/**
 * 发现页「宫格版」首页（第 5 轮 D28）。
 *
 * 注意：**没有「每日放送」入口** —— 用户明确指出「当季热门是显示热门作品的功能、
 * 不是播放时间表，播放时间在统计页已经有相应功能了」，因此那一页与它的数据通路已整体移除。
 *
 * ## 对齐的是什么
 *
 * Bangumi-master 的发现页**不是**一排顶栏标签，而是：
 * 头部一个 `MENU_MAP` 驱动的**菜单宫格**（可自定义排序/显隐）+ 下方的横向内容区块，
 * 每个宫格项进入**独立页面**（`screens/discovery/{rank,anime,calendar,...}/`）。
 *
 * 这里复刻的是它的**形态**：宫格入口 + 横向预览，点进去看全屏列表。
 *
 * ## 为什么只有 5 个入口
 *
 * Bangumi-master 的默认菜单有 10 项，但其中的「每日放送 / 标签 / 系列 / 目录 / 索引」
 * 各自需要一条独立的数据通路（`/calendar` 分组页、标签聚合、系列聚合、目录/索引接口）。
 * 本批先把**已经能跑通**的入口摆进宫格，**不放死按钮** ——
 * 一个点了没反应的格子比少一个格子更糟。其余入口排在后续批次。
 */
@Composable
fun DiscoverGridPane(
    state: SubjectSearchUiState,
    onPickMode: (TrendingMode) -> Unit,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = "发现",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "从这里进入各个入口；右上角按钮可切回卡片视图。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // —— 菜单宫格（每行 3 个） ——
        items(GRID_ENTRIES.chunked(3)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { entry ->
                    GridCell(
                        entry = entry,
                        modifier = Modifier.weight(1f),
                        onClick = { onPickMode(entry.mode) },
                    )
                }
                // 补齐空位，保证最后一行格子宽度与上面一致（不会被拉伸）
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // —— 本季横向预览（复用卡片视图已经拉到的数据，零额外请求） ——
        if (state.trendingResults.isNotEmpty()) {
            item {
                Text(
                    text = "本季人气",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.trendingResults.take(12), key = { it.subjectId }) { subject ->
                        Column(
                            modifier = Modifier
                                .width(96.dp)
                                .clickable { onSubjectClick(subject.subjectId) },
                        ) {
                            AsyncImage(
                                model = subject.coverUrl,
                                contentDescription = subject.displayTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Text(
                                text = subject.displayTitle,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )

                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "宫格目前收录 3 个入口（第 6 轮删掉了找条目与评分月刊）。" +
                    "「标签 / 系列 / 目录 / 索引」需要各自的数据通路，排在后续批次 —— 不摆点了没反应的死格子。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一个宫格入口。 */
private data class GridEntry(val mode: TrendingMode, val icon: ImageVector, val label: String)

private val GRID_ENTRIES = listOf(
    GridEntry(TrendingMode.SEASONAL, Icons.Default.PlayArrow, "本季热门"),
    GridEntry(TrendingMode.ALL_TIME, Icons.AutoMirrored.Filled.List, "排行榜"),
    GridEntry(TrendingMode.STEAM, Icons.Default.VideogameAsset, "Steam"),
)

@Composable
private fun GridCell(entry: GridEntry, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(text = entry.label, style = MaterialTheme.typography.labelMedium)
    }
}
