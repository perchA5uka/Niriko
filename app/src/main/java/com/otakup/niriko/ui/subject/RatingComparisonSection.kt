package com.otakup.niriko.ui.subject

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.ui.components.GlassSectionCard
import top.yukonga.miuix.kmp.blur.Backdrop

/** 评分对比卡片：显示 Bangumi / Bilibili 社区评分 + 我的评分。 */
@Composable
fun RatingComparisonSection(
    subject: SubjectEntity,
    myRating: Float?,
    modifier: Modifier = Modifier,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    /** 争议度标签（评分分布标准差 → 「异口同声…厨黑大战」）。 */
    disputeLabel: String? = null,
    /** 本地库内同类型百分位（0-100）。口径是"你的收藏库"，不是全网排名。 */
    localPercentile: Int? = null,
) {
    val bgmRating = subject.ratingScore
    val biliRating = subject.biliScore
    val biliTotal = subject.biliRatingTotal
    // 无任何可展示数据则整卡隐藏
    if (bgmRating == null && myRating == null && biliRating == null && biliTotal == null) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "评分",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        GlassSectionCard(
            backdrop = glassBackdrop,
            isScrolling = isScrolling,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // 我的评分
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("我的评分", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = myRating?.let { "%.1f".format(it) } ?: "未评分",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (myRating != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Bangumi 社区评分
                    if (bgmRating != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Bangumi", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "%.1f".format(bgmRating),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            subject.ratingTotal?.let { total ->
                                Text(
                                    "$total 人评价",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Bilibili 社区评分
                    if (biliRating != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Bilibili", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "%.1f".format(biliRating),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            if (biliTotal != null) {
                                Text(
                                    text = "$biliTotal 人评价",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // 阶段 7：争议度 + 本地库内百分位（都是纯本地计算，零请求）
                val insights = buildList {
                    disputeLabel?.let { add("评分争议度：$it") }
                    localPercentile?.let { add("本地库内百分位：$it%") }
                }
                if (insights.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    insights.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (localPercentile != null) {
                        Text(
                            text = "百分位基于你自己的收藏库同类作品，非全站排名",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 社区评分分布柱状图（1-10 分各档投票数）。使用 Compose Canvas 绘制。 */
@Composable
fun RatingDistributionChart(
    distribution: Map<Int, Int>, // key: 分数(1-10), value: 票数
    modifier: Modifier = Modifier,
    /** 嵌入横滑卡片时为 true：不自带标题、不加 16dp 横向边距。 */
    compact: Boolean = false,
) {
    if (distribution.isEmpty()) return

    val maxCount = distribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    // Paint 复用（每帧 new Paint 产生 GC 尖峰；实例在 composable 层 remember 一次）
    val labelPaint = remember { android.graphics.Paint() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.padding(horizontal = 16.dp)),
    ) {
        if (!compact) {
            Spacer(Modifier.height(8.dp))
            Text(
                "评分分布",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val barCount = 10
            val barWidth = size.width / barCount * 0.7f
            val gap = size.width / barCount * 0.3f
            val chartHeight = size.height - 20f

            for (i in 1..10) {
                val count = distribution[i] ?: 0
                val barHeight = (count.toFloat() / maxCount) * chartHeight
                val x = (i - 1) * (barWidth + gap) + gap / 2
                val y = size.height - barHeight - 20f

                // 条形
                drawRect(
                    color = primaryColor.copy(alpha = 0.7f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                )

                // 分数标签（复用 Paint，仅更新 color）
                labelPaint.color = onSurfaceColor.toArgb()
                labelPaint.textSize = 24f
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(
                    "$i",
                    x + barWidth / 2,
                    size.height - 4f,
                    labelPaint,
                )
            }
        }
    }
}