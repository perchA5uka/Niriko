package com.otakup.niriko.ui.subject

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.calculator.EpisodeRatingAnalyzer
import com.otakup.niriko.data.calculator.TrendDirection
import kotlin.math.abs
import kotlin.math.max

/**
 * 番剧评分走势曲线（Compose Canvas 手绘，**不引入第三方图表库**）。
 *
 * 与项目既有的 `RatingDistributionChart`、StatsScreen 环形图保持同一技术路线：
 * 零依赖、完全可控、体积不增加。
 *
 * 绘制内容：
 * - 渐变面积底 + 折线 + 圆点
 * - **虚线平均线**（全季平均分）
 * - 可选**移动平均线**（默认窗口 3，用于抹平单集噪声）
 * - 最高分 / 最低分高亮
 * - 点选：显示「第 N 集 · 标题 · 分数（票数）」
 *
 * Y 轴范围按数据自适应（留 12% 余量），避免 7.5-8.1 这种窄区间被压成一条直线；
 * 但至少覆盖 ±0.5 分，避免"看起来起伏巨大"的视觉误导。
 */
@Composable
fun EpisodeRatingChart(
    points: List<EpisodeRatingAnalyzer.RatingPoint>,
    modifier: Modifier = Modifier,
    showMovingAverage: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    averageColor: Color = MaterialTheme.colorScheme.tertiary,
    movingAverageColor: Color = MaterialTheme.colorScheme.secondary,
) {
    if (points.size < 2) return
    val sorted = remember(points) { points.sortedBy { it.ep } }
    val stats = remember(sorted) { EpisodeRatingAnalyzer.analyze(sorted) }
    var selectedIndex by remember(sorted) { mutableStateOf<Int?>(null) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        // —— Y 轴范围 ——
        val scores = sorted.map { it.score }
        val dataMin = scores.min()
        val dataMax = scores.max()
        val halfSpan = max((dataMax - dataMin) / 2f, 0.5f)
        val center = (dataMax + dataMin) / 2f
        val yMin = (center - halfSpan * 1.35f).coerceAtLeast(0f)
        val yMax = (center + halfSpan * 1.35f).coerceAtMost(10f)
        val span = (yMax - yMin).takeIf { it > 0.01f } ?: 1f

        Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .pointerInput(sorted) {
                        detectTapGestures { offset ->
                            val step = size.width.toFloat() / max(sorted.size - 1, 1).toFloat()
                            val idx = (offset.x / step).toInt().coerceIn(0, sorted.lastIndex)
                            selectedIndex = if (selectedIndex == idx) null else idx
                        }
                    },
            ) {
                val leftPad = 8f
                val rightPad = 8f
                val topPad = 14f
                val bottomPad = 22f
                val chartW = size.width - leftPad - rightPad
                val chartH = size.height - topPad - bottomPad

                fun xOf(index: Int): Float =
                    leftPad + if (sorted.size == 1) chartW / 2f else chartW * index / (sorted.size - 1).toFloat()

                fun yOf(score: Float): Float =
                    topPad + chartH * (1f - (score - yMin) / span)

                // ① 参考网格（3 条）
                for (i in 0..2) {
                    val y = topPad + chartH * i / 2f
                    drawLine(
                        color = outline.copy(alpha = 0.5f),
                        start = Offset(leftPad, y),
                        end = Offset(size.width - rightPad, y),
                        strokeWidth = 1f,
                    )
                }

                // ② 渐变面积
                val areaPath = Path().apply {
                    moveTo(xOf(0), yOf(sorted[0].score))
                    for (i in 1 until sorted.size) lineTo(xOf(i), yOf(sorted[i].score))
                    lineTo(xOf(sorted.lastIndex), topPad + chartH)
                    lineTo(xOf(0), topPad + chartH)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                        startY = topPad,
                        endY = topPad + chartH,
                    ),
                )

                // ③ 平均线（虚线）
                val avgY = yOf(stats.average)
                drawLine(
                    color = averageColor.copy(alpha = 0.85f),
                    start = Offset(leftPad, avgY),
                    end = Offset(size.width - rightPad, avgY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                )

                // ④ 移动平均线
                if (showMovingAverage && stats.movingAverage.size == sorted.size) {
                    val maPath = Path().apply {
                        moveTo(xOf(0), yOf(stats.movingAverage[0]))
                        for (i in 1 until sorted.size) lineTo(xOf(i), yOf(stats.movingAverage[i]))
                    }
                    drawPath(
                        path = maPath,
                        color = movingAverageColor.copy(alpha = 0.9f),
                        style = Stroke(width = 3f),
                    )
                }

                // ⑤ 折线
                val linePath = Path().apply {
                    moveTo(xOf(0), yOf(sorted[0].score))
                    for (i in 1 until sorted.size) lineTo(xOf(i), yOf(sorted[i].score))
                }
                drawPath(path = linePath, color = color, style = Stroke(width = 4f))

                // ⑥ 圆点 + 最高/最低高亮
                sorted.forEachIndexed { index, point ->
                    val center = Offset(xOf(index), yOf(point.score))
                    val isExtreme = point.epId == stats.max?.epId || point.epId == stats.min?.epId
                    val isSelected = selectedIndex == index
                    val r = if (isSelected) 9f else if (isExtreme) 7f else 4.5f
                    drawCircle(color = surfaceVariant, radius = r + 2f, center = center)
                    drawCircle(
                        color = if (isExtreme) averageColor else color,
                        radius = r,
                        center = center,
                    )
                }

                // ⑦ 选中竖线
                selectedIndex?.let { idx ->
                    val x = xOf(idx)
                    drawLine(
                        color = onSurface.copy(alpha = 0.35f),
                        start = Offset(x, topPad),
                        end = Offset(x, topPad + chartH),
                        strokeWidth = 2f,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        val selected = selectedIndex?.let { sorted.getOrNull(it) }
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "第 ${formatEpisodeNumber(selected.ep)} 集",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "%.1f".format(selected.score) +
                        (selected.votes?.let { " · ${it} 票" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        } else {
            Text(
                text = "点按曲线查看单集",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }

        Spacer(Modifier.height(8.dp))
        // —— 结论条 ——
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChartStat("平均", "%.2f".format(stats.average))
            stats.max?.let { ChartStat("最高", "%.1f".format(it.score) + " (EP${formatEpisodeNumber(it.ep)})") }
            stats.min?.let { ChartStat("最低", "%.1f".format(it.score) + " (EP${formatEpisodeNumber(it.ep)})") }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChartStat("趋势", trendLabel(stats.direction) + " ${"%+.2f".format(stats.slope)}/集")
            ChartStat(
                "波动",
                "%.2f".format(stats.volatility) +
                    "（" + EpisodeRatingAnalyzer.volatilityLabel(stats.volatility) + "）",
            )
            ChartStat("集数", "${stats.count}")
        }
        // 「高光回 / 崩坏回」：与均值偏离超过 0.5 分的极端集
        // （EpisodeRatingAnalyzer 里早已实现，此前一直没接到 UI）
        val highlights = remember(sorted) { EpisodeRatingAnalyzer.highlights(sorted) }
        val bestEpisode = highlights.first.firstOrNull()
        val worstEpisode = highlights.second.firstOrNull()
        if (bestEpisode != null || worstEpisode != null) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                bestEpisode?.let {
                    ChartStat("高光回", "EP${formatEpisodeNumber(it.ep)} · %.1f".format(it.score))
                }
                worstEpisode?.let {
                    ChartStat("崩坏回", "EP${formatEpisodeNumber(it.ep)} · %.1f".format(it.score))
                }
            }
        }
    }
}

@Composable
private fun ChartStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun trendLabel(direction: TrendDirection): String = when (direction) {
    TrendDirection.RISING -> "▲ 上升"
    TrendDirection.FALLING -> "▼ 下降"
    TrendDirection.FLAT -> "— 平稳"
}

/** 集号展示：整数不带小数点，半集（如 5.5）保留一位。 */
fun formatEpisodeNumber(ep: Double): String =
    if (abs(ep - ep.toInt()) < 0.01) ep.toInt().toString() else "%.1f".format(ep)
