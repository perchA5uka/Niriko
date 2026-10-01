package com.otakup.niriko.ui.stats

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart

/** 环形图的一段（沿用旧 StatsScreen.DonutChart 的入参形态，字段不变）。 */
data class DonutSlice(
    val label: String,
    val value: Float,
    val color: Color,
)

/**
 * Niriko 环形图（Vico 3.2.0 承载），取代原 StatsScreen 里手绘 drawArc 的 DonutChart。
 *
 * - 每段颜色由调用方经 [DonutSlice.color] 显式传入（主题派生），不使用 Vico 默认色；
 * - 段间缝隙沿用旧实现的约 3 度（Vico 的 spacing 会按半径换算成角度，4.dp 在半径 80.dp 时约 3 度）；
 * - 内圈空洞按外圈直径的 [holeRatio] 计算，保持旧实现 strokeWidth = 0.15 * minDimension 的观感；
 * - 中心 [totalText] 与「总计」文案走主题排版与颜色，深色 / OLED 下同样可读。
 */
@Composable
fun NirikoDonutChart(
    data: List<DonutSlice>,
    totalText: String,
    modifier: Modifier = Modifier,
    holeRatio: Float = 0.7f,
) {
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) return

    val sliceProvider = remember(data) {
        PieChart.SliceProvider.series(data.map { PieChart.Slice(fill = Fill(it.color)) })
    }
    val producer = remember { PieChartModelProducer() }
    LaunchedEffect(data) {
        producer.runTransaction {
            pieSeries {
                series(data.map { it.value })
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val hole = minOf(maxWidth, maxHeight) * holeRatio
        val chart = rememberPieChart(
            sliceProvider = sliceProvider,
            spacing = 4.dp,
            outerSize = PieSize.Outer.Fill,
            innerSize = PieSize.Inner.fixed(hole),
            startAngle = -90f,
        )
        PieChartHost(
            chart = chart,
            modelProducer = producer,
            modifier = Modifier.fillMaxSize(),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "总计",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
