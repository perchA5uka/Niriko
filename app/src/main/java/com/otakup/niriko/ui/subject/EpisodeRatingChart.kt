package com.otakup.niriko.ui.subject

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.ui.components.LocalCardGlassLevel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.abs
import kotlin.math.max

/** 一屏最多可见的集数（捏合放大的下限 / 缩放的起点）。 */
private const val MAX_VISIBLE_EPISODES = 12

/** 低于该可用宽度时降级为手绘实现（窄屏放不下 Vico 的坐标轴与标记）。 */
private val MIN_VICO_WIDTH = 280.dp

/**
 * 番剧评分走势曲线（Vico 3.2.0 承载，手绘 Canvas 保留为降级实现）。
 *
 * 升级前的 KDoc 写的是「不引入第三方图表库」；该结论在 2026 UI 升级中被推翻并替换，理由：
 * 1. 手绘版本只有「点按」一种交互，无法提供捏合缩放 / 横向滚动，30 集以上的长番没法看清单集起伏；
 * 2. 手绘版本的刻度文字走 nativeCanvas + 手算字号，深色 / OLED 主题下不可读；
 *    Vico 的文本组件直接吃 Compose TextStyle，配色统一走 [MaterialTheme]（不使用 Vico 默认色）；
 * 3. 纵向手势安全：Vico 只消费横向手势（scrollable 是 Horizontal 方向，缩放手势也只在横向生效），
 *    因此外层 LazyColumn 的纵向滚动不受影响，图表无需额外禁用纵向滚动。
 *
 * 公开签名与旧版完全一致，调用方（EpisodeRatingSections）不需要改动；
 * [EpisodeRatingAnalyzer] 仍是唯一的数值来源（纯函数，本次未改动）。
 *
 * 绘制内容：
 * - 折线 + 渐变面积 + 圆点（最高 / 最低分高亮）
 * - 虚线平均线（全季平均分）与可选移动平均线（默认窗口 3）
 * - 点按选中某集：显示「第 N 集 · 标题 · 分数（票数）」，再次点按取消
 * - 捏合缩放：一屏可见 1~[MAX_VISIBLE_EPISODES] 集，超出部分横向滚动
 * - 低端档（[CardGlassLevel.OFF]）或可用宽度不足时走 [EpisodeRatingChartCanvas]
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
    val glassLevel = LocalCardGlassLevel.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        if (glassLevel == CardGlassLevel.OFF || availableWidth < MIN_VICO_WIDTH) {
            EpisodeRatingChartCanvas(
                points = points,
                modifier = Modifier.fillMaxWidth(),
                showMovingAverage = showMovingAverage,
                color = color,
                averageColor = averageColor,
                movingAverageColor = movingAverageColor,
            )
        } else {
            EpisodeRatingChartVico(
                points = points,
                modifier = Modifier.fillMaxWidth(),
                showMovingAverage = showMovingAverage,
                color = color,
                averageColor = averageColor,
                movingAverageColor = movingAverageColor,
            )
        }
    }
}

/**
 * Vico 3.2.0 实现：只负责把 [EpisodeRatingAnalyzer] 的结果画出来 + 交互。
 *
 * 配色全部显式传入（[color] / [averageColor] / [movingAverageColor] / 主题色），
 * 不依赖 Vico 默认主题色，避免深色 / OLED 下出现固定灰文字。
 */
@Composable
private fun EpisodeRatingChartVico(
    points: List<EpisodeRatingAnalyzer.RatingPoint>,
    modifier: Modifier,
    showMovingAverage: Boolean,
    color: Color,
    averageColor: Color,
    movingAverageColor: Color,
) {
    val sorted = remember(points) { points.sortedBy { it.ep } }
    val stats = remember(sorted) { EpisodeRatingAnalyzer.analyze(sorted) }
    var selectedIndex by remember(sorted) { mutableStateOf<Int?>(null) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val scores = remember(sorted) { sorted.map { it.score } }
    val showMa = showMovingAverage && stats.movingAverage.size == sorted.size
    val extremeIndices = remember(sorted, stats) {
        sorted.mapIndexedNotNull { index, point ->
            index.takeIf { point.epId == stats.max?.epId || point.epId == stats.min?.epId }
        }.toSet()
    }

    // —— Y 轴范围：与手绘版一致（至少 ±0.5 分，留 35% 余量，限制在 0..10）——
    val yMin = remember(scores) {
        val dataMin = scores.min()
        val dataMax = scores.max()
        val halfSpan = max((dataMax - dataMin) / 2f, 0.5f)
        val center = (dataMax + dataMin) / 2f
        (center - halfSpan * 1.35f).coerceAtLeast(0f).toDouble()
    }
    val yMax = remember(scores) {
        val dataMin = scores.min()
        val dataMax = scores.max()
        val halfSpan = max((dataMax - dataMin) / 2f, 0.5f)
        val center = (dataMax + dataMin) / 2f
        (center + halfSpan * 1.35f).coerceAtMost(10f).toDouble()
    }

    val normalPoint = LineCartesianLayer.Point(
        component = rememberShapeComponent(
            fill = Fill(color),
            shape = CircleShape,
            strokeFill = Fill(surfaceVariant),
            strokeThickness = 1.5.dp,
        ),
        size = 7.dp,
    )
    val extremePoint = LineCartesianLayer.Point(
        component = rememberShapeComponent(
            fill = Fill(averageColor),
            shape = CircleShape,
            strokeFill = Fill(surfaceVariant),
            strokeThickness = 1.5.dp,
        ),
        size = 11.dp,
    )
    val pointProvider = remember(normalPoint, extremePoint, extremeIndices) {
        EpisodeRatingPointProvider(normalPoint, extremePoint, extremeIndices)
    }

    val mainLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(color)),
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
        areaFill = LineCartesianLayer.AreaFill.single(
            Fill(
                Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                ),
            ),
        ),
        pointProvider = pointProvider,
    )
    val movingAverageLine = if (showMa) {
        LineCartesianLayer.rememberLine(
            fill = LineCartesianLayer.LineFill.single(Fill(movingAverageColor)),
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
        )
    } else {
        null
    }
    val averageLine = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(Fill(averageColor)),
        stroke = LineCartesianLayer.LineStroke.Dashed(thickness = 1.5.dp),
    )
    val lineProvider = remember(mainLine, movingAverageLine, averageLine) {
        LineCartesianLayer.LineProvider.series(listOfNotNull(mainLine, movingAverageLine, averageLine))
    }

    val lineLayer = rememberLineCartesianLayer(
        lineProvider = lineProvider,
        rangeProvider = remember(yMin, yMax) {
            CartesianLayerRangeProvider.fixed(minY = yMin, maxY = yMax)
        },
    )

    val episodeFormatter = remember(sorted) {
        CartesianValueFormatter { _, value, _ ->
            sorted.getOrNull(value.toInt())?.let { formatEpisodeNumber(it.ep) } ?: ""
        }
    }
    val bottomAxis = HorizontalAxis.rememberBottom(
        line = rememberAxisLineComponent(fill = Fill(outline.copy(alpha = 0.6f)), thickness = 1.dp),
        label = rememberAxisLabelComponent(style = labelStyle),
        valueFormatter = episodeFormatter,
        tick = null,
        guideline = null,
    )
    val startAxis = VerticalAxis.rememberStart(
        line = null,
        tick = null,
        guideline = rememberAxisGuidelineComponent(
            fill = Fill(outline.copy(alpha = 0.5f)),
            thickness = 1.dp,
        ),
        label = rememberAxisLabelComponent(style = labelStyle),
        valueFormatter = remember {
            CartesianValueFormatter { _, value, _ -> "%.1f".format(value) }
        },
    )

    val markerFormatter = remember(sorted) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val index = targets.firstOrNull()?.x?.toInt() ?: 0
            val point = sorted.getOrNull(index)
            if (point == null) {
                ""
            } else {
                "第 ${formatEpisodeNumber(point.ep)} 集 · " +
                    "%.1f".format(point.score) +
                    (point.votes?.let { " · ${it} 票" } ?: "")
            }
        }
    }
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            style = MaterialTheme.typography.labelSmall.copy(color = onSurface),
            padding = Insets(horizontal = 8.dp, vertical = 4.dp),
            background = rememberShapeComponent(
                fill = Fill(surfaceVariant.copy(alpha = 0.95f)),
                shape = MarkerCornerBasedShape(RoundedCornerShape(6.dp)),
            ),
        ),
        valueFormatter = markerFormatter,
        labelPosition = DefaultCartesianMarker.LabelPosition.AroundPoint,
        guideline = rememberLineComponent(
            fill = Fill(onSurface.copy(alpha = 0.35f)),
            thickness = 1.dp,
        ),
    )

    val markerVisibilityListener = remember(sorted) {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                selectedIndex = targets.firstOrNull()?.x?.toInt()?.coerceIn(0, sorted.lastIndex)
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                selectedIndex = targets.firstOrNull()?.x?.toInt()?.coerceIn(0, sorted.lastIndex)
            }

            override fun onHidden(marker: CartesianMarker) {
                selectedIndex = null
            }
        }
    }

    val chart = rememberCartesianChart(
        lineLayer,
        startAxis = startAxis,
        bottomAxis = bottomAxis,
        marker = marker,
        markerVisibilityListener = markerVisibilityListener,
        markerController = CartesianMarkerController.rememberToggleOnTap(),
    )

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(sorted, showMa) {
        producer.runTransaction {
            lineModel {
                series(y = scores.map { it.toDouble() })
                if (showMa) {
                    series(y = stats.movingAverage.map { it.toDouble() })
                }
                series(y = List(sorted.size) { stats.average.toDouble() })
            }
        }
    }

    val minZoom = remember(sorted.size) {
        if (sorted.size > MAX_VISIBLE_EPISODES) {
            Zoom.x(MAX_VISIBLE_EPISODES.toDouble())
        } else {
            Zoom.Content
        }
    }
    val maxZoom = remember {
        Zoom.max(Zoom.x(1.0), Zoom.Content)
    }
    val scrollState = rememberVicoScrollState(scrollEnabled = true)
    val zoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = minZoom,
        minZoom = minZoom,
        maxZoom = maxZoom,
    )

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
            CartesianChartHost(
                chart = chart,
                modelProducer = producer,
                modifier = Modifier.fillMaxWidth().height(190.dp),
                scrollState = scrollState,
                zoomState = zoomState,
            )
        }
        EpisodeRatingChartFooter(
            sorted = sorted,
            stats = stats,
            selectedIndex = selectedIndex,
            color = color,
        )
    }
}

/** 手绘 Canvas 实现：保留为低端档 / 窄屏的降级路径（行为与升级前一致）。 */
@Composable
private fun EpisodeRatingChartCanvas(
    points: List<EpisodeRatingAnalyzer.RatingPoint>,
    modifier: Modifier,
    showMovingAverage: Boolean,
    color: Color,
    averageColor: Color,
    movingAverageColor: Color,
) {
    val sorted = remember(points) { points.sortedBy { it.ep } }
    val stats = remember(sorted) { EpisodeRatingAnalyzer.analyze(sorted) }
    var selectedIndex by remember(sorted) { mutableStateOf<Int?>(null) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
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
                    val pointCenter = Offset(xOf(index), yOf(point.score))
                    val isExtreme = point.epId == stats.max?.epId || point.epId == stats.min?.epId
                    val isSelected = selectedIndex == index
                    val r = if (isSelected) 9f else if (isExtreme) 7f else 4.5f
                    drawCircle(color = surfaceVariant, radius = r + 2f, center = pointCenter)
                    drawCircle(
                        color = if (isExtreme) averageColor else color,
                        radius = r,
                        center = pointCenter,
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

        EpisodeRatingChartFooter(
            sorted = sorted,
            stats = stats,
            selectedIndex = selectedIndex,
            color = color,
        )
    }
}

/** 折线图的数据点样式：最高 / 最低分用更大的圆点强调。 */
private class EpisodeRatingPointProvider(
    private val normalPoint: LineCartesianLayer.Point,
    private val extremePoint: LineCartesianLayer.Point,
    private val extremeIndices: Set<Int>,
) : LineCartesianLayer.PointProvider {
    override fun getPoint(
        entry: LineCartesianLayerModel.Entry,
        extraStore: ExtraStore,
    ): LineCartesianLayer.Point = if (entry.x.toInt() in extremeIndices) extremePoint else normalPoint

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = extremePoint
}

/** 选中集的标题行 + 平均/最高/最低/趋势/波动/集数结论条（两条实现共用）。 */
@Composable
private fun EpisodeRatingChartFooter(
    sorted: List<EpisodeRatingAnalyzer.RatingPoint>,
    stats: EpisodeRatingAnalyzer.TrendStats,
    selectedIndex: Int?,
    color: Color,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ChartStat("平均", "%.2f".format(stats.average))
        stats.max?.let { ChartStat("最高", "%.1f".format(it.score) + " (EP${formatEpisodeNumber(it.ep)})") }
        stats.min?.let { ChartStat("最低", "%.1f".format(it.score) + " (EP${formatEpisodeNumber(it.ep)})") }
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ChartStat("趋势", trendLabel(stats.direction) + " " + "%+.2f".format(stats.slope) + "/集")
        ChartStat(
            "波动",
            "%.2f".format(stats.volatility) +
                "（" + EpisodeRatingAnalyzer.volatilityLabel(stats.volatility) + "）",
        )
        ChartStat("集数", stats.count.toString())
    }
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
