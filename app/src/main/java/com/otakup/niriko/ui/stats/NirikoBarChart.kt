package com.otakup.niriko.ui.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.chartBarDefaultColor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent

/**
 * 单条柱数据（沿用旧 StatsScreen.BarChart 的入参形态）。
 *
 * [color] 为 [Color.Unspecified] 时使用主题派生的 [chartBarDefaultColor]。
 */
data class BarEntry(
    val label: String,
    val value: Float,
    val color: Color = Color.Unspecified,
)

/** 同一坐标系下的一条柱序列（分组柱的成员）。 */
data class BarSeries(
    val label: String,
    val values: List<Float>,
    val color: Color,
)

/** 柱状图上叠加的折线（月度累计值 / 评分差值）。 */
data class BarLine(
    val label: String,
    val values: List<Float>,
    val color: Color,
    val dashed: Boolean = false,
)

/**
 * Niriko 柱状图（Vico 3.2.0 承载）。
 *
 * 取代了原 StatsScreen 里手绘的 BarChart：
 * - 不再使用 `nativeCanvas.drawText` 与硬编码 #8C8C8C，轴标签/数值标签全部走 [MaterialTheme]；
 * - 颜色由调用方经 [BarSeries.color] 显式传入（ChartPalette 派生），不使用 Vico 默认色；
 * - [bars] 多于一条时是同一坐标系下的分组柱；
 * - [line] 非空时叠加一条折线，并启用右侧独立坐标轴（累计值 / 差值量纲与柱不同）。
 *
 * 横向：桶数超过 [bucketsPerScreen] 时按比例放大内容宽度并可横向滚动，柱宽不被压缩；
 * 纵向：Vico 只消费横向手势（[`rememberVicoScrollState`] 是 Horizontal 方向的 scrollable），
 * 因此外层 LazyColumn 的纵向滚动不受影响。
 */
@Composable
fun NirikoBarChart(
    labels: List<String>,
    bars: List<BarSeries>,
    modifier: Modifier = Modifier,
    line: BarLine? = null,
    columnWidth: Dp = 14.dp,
    bucketsPerScreen: Int = 12,
    chartHeight: Dp = 180.dp,
    showValueLabels: Boolean = bars.size == 1,
) {
    if (labels.isEmpty() || bars.isEmpty() || bars.all { it.values.isEmpty() }) return

    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisLineColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = axisLabelColor)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )

    val columnComponents = bars.map { bar ->
        rememberLineComponent(fill = Fill(bar.color), thickness = columnWidth)
    }
    val columnProvider = remember(columnComponents) {
        ColumnCartesianLayer.ColumnProvider.series(columnComponents)
    }
    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = columnProvider,
        columnCollectionSpacing = 10.dp,
        mergeMode = { ColumnCartesianLayer.MergeMode.Grouped(4.dp) },
        dataLabel = if (showValueLabels) {
            rememberTextComponent(style = valueStyle)
        } else {
            null
        },
    )

    val lineLayer = line?.let { barLine ->
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(barLine.color)),
                    stroke = if (barLine.dashed) {
                        LineCartesianLayer.LineStroke.Dashed(thickness = 2.dp)
                    } else {
                        LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp)
                    },
                ),
            ),
            verticalAxisPosition = Axis.Position.Vertical.End,
        )
    }

    val labelFormatter = remember(labels) {
        CartesianValueFormatter { _, value, _ -> labels.getOrElse(value.toInt()) { "" } }
    }

    val bottomAxis = HorizontalAxis.rememberBottom(
        line = rememberAxisLineComponent(fill = Fill(axisLineColor), thickness = 1.dp),
        label = rememberAxisLabelComponent(style = labelStyle),
        valueFormatter = labelFormatter,
    )
    val startAxis = VerticalAxis.rememberStart(
        line = null,
        tick = null,
        guideline = null,
        label = rememberAxisLabelComponent(style = labelStyle),
    )
    val endAxis = if (line != null) {
        VerticalAxis.rememberEnd(
            line = null,
            tick = null,
            guideline = null,
            label = rememberAxisLabelComponent(style = labelStyle),
        )
    } else {
        null
    }

    val chart = if (lineLayer != null && endAxis != null) {
        rememberCartesianChart(
            columnLayer,
            lineLayer,
            startAxis = startAxis,
            endAxis = endAxis,
            bottomAxis = bottomAxis,
        )
    } else {
        rememberCartesianChart(columnLayer, startAxis = startAxis, bottomAxis = bottomAxis)
    }

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(labels, bars, line) {
        producer.runTransaction {
            columnModel {
                bars.forEach { bar -> series(y = bar.values) }
            }
            line?.let { barLine ->
                lineModel { series(y = barLine.values) }
            }
        }
    }

    val zoom = remember(labels.size, bucketsPerScreen) {
        if (bucketsPerScreen > 0 && labels.size > bucketsPerScreen) {
            Zoom.fixed(labels.size / bucketsPerScreen.toFloat())
        } else {
            Zoom.Content
        }
    }
    val scrollState = rememberVicoScrollState(scrollEnabled = true)
    val zoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = zoom,
        minZoom = zoom,
        maxZoom = zoom,
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(chartHeight),
        scrollState = scrollState,
        zoomState = zoomState,
    )
}

/** 单序列柱状图：承接旧 `BarChart(data, modifier, ...)` 的调用点。 */
@Composable
fun NirikoBarChart(
    data: List<BarEntry>,
    modifier: Modifier = Modifier,
    columnWidth: Dp = 14.dp,
) {
    val color = data.firstOrNull { it.color != Color.Unspecified }?.color ?: chartBarDefaultColor()
    NirikoBarChart(
        labels = data.map { it.label },
        bars = listOf(BarSeries(label = "default", values = data.map { it.value }, color = color)),
        modifier = modifier,
        columnWidth = columnWidth,
    )
}
