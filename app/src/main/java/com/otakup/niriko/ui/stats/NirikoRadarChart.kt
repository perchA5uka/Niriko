package com.otakup.niriko.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.AnimDurationLong
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.animation.motionEnabled
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 雷达图至少要 3 根轴（少于 3 根的「雷达」没有几何意义，直接不画）。 */
internal const val MIN_RADAR_AXES = 3

/** 雷达图最多展示的轴数（再多标签就挤在一起了）。 */
internal const val MAX_RADAR_AXES = 8

/** 网格圈数（含外圈）。 */
private const val GRID_RINGS = 4
private const val GRID_STROKE_DP = 1f
private const val DATA_STROKE_DP = 2f
private const val DOT_RADIUS_DP = 3.5f
private const val DEGREES_TO_RADIANS = PI.toFloat() / 180f

/** 雷达图的一根轴：标签、数值与轴色（轴色用于顶点圆点与图例小方块）。 */
data class RadarAxis(
    val label: String,
    val value: Float,
    val color: Color = Color.Unspecified,
)

/**
 * Niriko 雷达图（自绘 Canvas；参考文档 docs/ui-reference-plan-round3.md §四 R4）。
 *
 * 之所以自绘而不引入 io.github.ehsannarmani:compose-charts：后者已发布到 1.0.0 的源码里
 * **没有雷达图**（只有 ColumnChart / LineChart / PieChart / RowChart，全仓 grep "radar" = 0 命中），
 * 而柱状图 / 环形图本工程已由 Vico 3.2.0 承载。本项目只借鉴其「对称轴 + 入场动画」的设计。
 *
 * 视觉规则：
 * - 网格 = [GRID_RINGS] 圈同心多边形 + 每轴一条辐条，颜色取 onSurfaceVariant 低透明度，深浅色主题都可见；
 * - 数据多边形 = primary 半透明填充 + 同色描边，顶点用各轴自身的 [RadarAxis.color] 点出小圆点，与图例一一对应；
 * - 数值按本图最大值归一化（各轴同量纲即可，不需要 0..1 输入）；
 * - 入场动画 = 半径由 0 长到满格（[AnimDurationLong] + [AnimEasingDefault]），与统计页其它图表时长统一；
 *   开启「减弱动态效果」时瞬时到位。
 */
@Composable
fun NirikoRadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
) {
    if (axes.size < MIN_RADAR_AXES) return
    val displayed = if (axes.size > MAX_RADAR_AXES) axes.take(MAX_RADAR_AXES) else axes

    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val strokeColor = MaterialTheme.colorScheme.primary

    // 注意：motionEnabled() 是 @Composable，必须在 remember 之外读取（不能在 remember 的 lambda 里调用）。
    val animateGrowth = motionEnabled()
    val specs = NirikoMotionSpecs
    val growth = remember(animateGrowth) { Animatable(if (animateGrowth) 0f else 1f) }
    LaunchedEffect(displayed.map { it.value }) {
        if (growth.value < 1f) {
            growth.animateTo(1f, specs.spatialSlow())
        }
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = minOf(size.width, size.height) / 2f
        if (outerRadius <= 0f) return@Canvas

        val maxValue = displayed.maxOf { it.value }
        val ratios = displayed.map { RadarGeometry.normalize(it.value, maxValue) }
        val count = displayed.size

        repeat(GRID_RINGS) { ring ->
            val radius = outerRadius * (ring + 1) / GRID_RINGS
            drawPath(
                path = polygonPath(count = count, center = center, radius = radius, ratios = null),
                color = gridColor,
                style = Stroke(width = GRID_STROKE_DP.dp.toPx()),
            )
        }
        repeat(count) { index ->
            drawLine(
                color = gridColor,
                start = center,
                end = RadarGeometry.point(center, outerRadius, RadarGeometry.angleDegrees(index, count)),
                strokeWidth = GRID_STROKE_DP.dp.toPx(),
            )
        }

        val dataRadius = outerRadius * growth.value
        val dataPath = polygonPath(count = count, center = center, radius = dataRadius, ratios = ratios)
        drawPath(path = dataPath, color = fillColor)
        drawPath(path = dataPath, color = strokeColor, style = Stroke(width = DATA_STROKE_DP.dp.toPx()))
        displayed.forEachIndexed { index, axis ->
            val vertex = RadarGeometry.point(
                center = center,
                radius = dataRadius * ratios[index],
                angleDegrees = RadarGeometry.angleDegrees(index, count),
            )
            drawCircle(color = axis.color, radius = DOT_RADIUS_DP.dp.toPx(), center = vertex)
        }
    }
}

/** 按极坐标拼出一条闭合多边形；[ratios] 为 null 时所有顶点等高（网格圈），否则逐轴取 [ratios] 的比例。 */
private fun DrawScope.polygonPath(
    count: Int,
    center: Offset,
    radius: Float,
    ratios: List<Float>?,
): Path {
    val path = Path()
    repeat(count) { index ->
        val r = radius * (ratios?.getOrNull(index) ?: 1f)
        val point = RadarGeometry.point(center, r, RadarGeometry.angleDegrees(index, count))
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}

/**
 * 雷达图几何（纯函数，单测见 app/src/test/java/com/otakup/niriko/ui/stats/NirikoRadarChartTest.kt）。
 */
internal object RadarGeometry {

    /** 第 [index] 根轴的角度（度）：从正上方起顺时针均分。屏幕坐标系 y 轴向下，故正上方是 -90°。 */
    fun angleDegrees(index: Int, count: Int): Float {
        val safeCount = count.coerceAtLeast(1)
        return -90f + 360f * index / safeCount
    }

    /** 把 [value] 按 [max] 归一化并夹在 0..1；[max] 非正或非有限时返回 0（避免除零与 NaN 顶点）。 */
    fun normalize(value: Float, max: Float): Float {
        if (!max.isFinite() || max <= 0f) return 0f
        if (!value.isFinite()) return 0f
        return (value / max).coerceIn(0f, 1f)
    }

    /** 极坐标 → 屏幕坐标（角度制，0° = 正右，角度增大为屏幕上的顺时针方向）。 */
    fun point(center: Offset, radius: Float, angleDegrees: Float): Offset {
        val radians = angleDegrees * DEGREES_TO_RADIANS
        return Offset(
            x = center.x + radius * cos(radians),
            y = center.y + radius * sin(radians),
        )
    }
}
