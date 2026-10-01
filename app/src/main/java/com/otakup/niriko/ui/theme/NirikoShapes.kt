package com.otakup.niriko.ui.theme

// 形状令牌（Shape Tokens）+ 平滑圆角（squircle）。
//
// ── 第三方来源声明（算法移植）─────────────────────────────────────────────
// 平滑圆角（continuous corner / squircle）算法移植自 **compose-miuix-ui/miuix**
//   * 上游仓库：https://github.com/compose-miuix-ui/miuix
//   * 上游版本：0.9.0（本工程实际依赖版本，见 app/build.gradle.kts 的
//     `top.yukonga.miuix.kmp:miuix-blur-android:0.9.0`；libs.versions.toml 另记 0.9.3 口径）
//   * 许可：Apache License 2.0（https://www.apache.org/licenses/LICENSE-2.0）
//
// 说明：本文件不是上游 Kotlin 源码的逐字拷贝，而是按公开的 corner-smoothing 几何
// 公式（cubic 过渡段 + 圆弧段拼接，与 figma-squircle 同族）独立实现，以便：
//   1) 不引入新的运行时依赖（本批次禁止升级 miuix / 新增坐标）；
//   2) 几何可在 JVM 单测中直接验证不变量（见 SquircleGeometryTest）。
// 实施期网络不可达，无法拉取上游源码逐字比对，因此这里只声明算法来源与许可；
// 许可与来源的完整记录见仓库根 THIRD-PARTY-NOTICES.md「内嵌的第三方源码」。
// ─────────────────────────────────────────────────────────────────────────

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * 全应用圆角令牌。
 *
 * 现状（收敛前的散落字面量）：统计区块 `RoundedCornerShape(20.dp)`、日历 `MaterialTheme.shapes.medium`、
 * 开关轨道 `RoundedCornerShape(trackHeight / 2)`、胶囊 `RoundedCornerShape(999.dp)`。
 * 收敛后只保留四档：[Card] / [Section] / [Pill] / [Control]，组件里禁止再写圆角魔数。
 *
 * 注意：令牌取值与收敛前的既有取值逐一相等（20 / 16 / 999 / 12 dp），
 * 因此把既有组件切到令牌**不会改变手机端观感**。
 */
object NirikoShapes {
    /** 卡片 / 统计区块容器圆角（20dp，既有 `RoundedCornerShape(20.dp)` 口径）。 */
    val Card: Dp = 20.dp

    /** 分区 / 次级卡片容器圆角（16dp，既有 `appleGlassCard` 默认值口径）。 */
    val Section: Dp = 16.dp

    /** 胶囊：半径远大于高度，被裁剪钳为完整胶囊（既有 `RoundedCornerShape(999.dp)` 口径）。 */
    val Pill: Dp = 999.dp

    /** 小控件（输入框、徽标、小按钮）圆角（12dp）。 */
    val Control: Dp = 12.dp

    /** 四档令牌 → [Shape] 的线性圆角映射（纯映射，无副作用）。 */
    fun rounded(radius: Dp): Shape = RoundedCornerShape(radius)

    /** 卡片档 Shape。 */
    val CardShape: Shape get() = rounded(Card)

    /** 分区档 Shape。 */
    val SectionShape: Shape get() = rounded(Section)

    /**
     * 胶囊档 Shape。
     *
     * 用百分比圆角而不是 999dp 字面量：对任意高度都精确等于「高 / 2」（999dp 依赖裁剪钳制，
     * 在极端尺寸下容易出现半像素缺口）。
     */
    val PillShape: Shape get() = RoundedCornerShape(percent = 50)

    /** 小控件档 Shape。 */
    val ControlShape: Shape get() = rounded(Control)

    /** 按高度派生的胶囊（开关轨道等）：等价既有 `RoundedCornerShape(height / 2)`。 */
    fun capsule(height: Dp): Shape = RoundedCornerShape(height / 2)

    /** 平滑圆角默认平滑度：0 = 普通圆角（与 [rounded] 完全等价），1 = 最大连续曲率。 */
    const val DefaultSmoothing: Float = 0.6f

    /**
     * 四档令牌 → 平滑圆角（squircle）Shape。
     *
     * 供后续批次按需选用（B2 竖向 dock / B6 截图矩阵）；默认平滑度为 [DefaultSmoothing]，
     * 传 `smoothing = 0f` 时与 [rounded] 逐点等价。
     */
    fun squircle(radius: Dp, smoothing: Float = DefaultSmoothing): Shape =
        SquircleCornerShape(radius = radius, smoothing = smoothing)
}

/**
 * 单个圆角的平滑圆角几何量（像素单位；角点局部坐标系：原点在角点，+x / +y 指向形状内部）。
 *
 * 由 [squircleCorner] 计算，纯 Kotlin 计算、无 Android 依赖，可在 JVM 单测中直接验证。
 */
data class SquircleCorner(
    /** 实际使用的圆角半径 r（已按 [effectiveSquircleRadius] 钳制）。 */
    val effectiveRadius: Float,
    /** 角占位长度 p = (1 + s)·r：从角点沿两条边各占用的长度。 */
    val extent: Float,
    /** 圆弧段弦长。 */
    val arcSectionLength: Float,
    /** 圆弧段的角度（度）；s = 0 时为 90°。 */
    val arcMeasureDegrees: Float,
    /** 过渡段切角（度），= 45·s。 */
    val angleAlpha: Float,
    /** 同 [angleAlpha]（公式里两个角相等）。 */
    val angleBeta: Float,
    /** 第一段 cubic 的「长」控制量（a = 2b）。 */
    val a: Float,
    /** cubic 控制段长度。 */
    val b: Float,
    /** 圆弧到竖直切线的水平过渡距离。 */
    val c: Float,
    /** 圆弧端点到直线段的过渡距离。 */
    val d: Float,
)

private const val SQRT_2: Float = 1.41421356f

private fun sinDeg(deg: Float): Float = sin(deg * PI / 180.0).toFloat()

private fun cosDeg(deg: Float): Float = cos(deg * PI / 180.0).toFloat()

private fun tanDeg(deg: Float): Float = tan(deg * PI / 180.0).toFloat()

/**
 * 计算平滑圆角的几何量（纯函数）。
 *
 * @param pixelRadius 请求的圆角半径（像素，未钳制）
 * @param smoothing 平滑度，会被钳制到 0..1；0 ⇒ 退化为普通圆角
 */
fun squircleCorner(pixelRadius: Float, smoothing: Float): SquircleCorner {
    val s = smoothing.coerceIn(0f, 1f)
    val r = pixelRadius.coerceAtLeast(0f)
    val extent = (1f + s) * r
    val arcMeasure = 90f * (1f - s)
    val arcSectionLength = sinDeg(arcMeasure / 2f) * r * SQRT_2
    val angleAlpha = (90f - arcMeasure) / 2f
    val angleBeta = angleAlpha
    val p3ToP4Distance = r * tanDeg(angleAlpha / 2f)
    val c = p3ToP4Distance * cosDeg(angleBeta)
    val d = c * tanDeg(angleBeta)
    val b = (extent - arcSectionLength - c - d) / 3f
    val a = 2f * b
    return SquircleCorner(
        effectiveRadius = r,
        extent = extent,
        arcSectionLength = arcSectionLength,
        arcMeasureDegrees = arcMeasure,
        angleAlpha = angleAlpha,
        angleBeta = angleBeta,
        a = a,
        b = b,
        c = c,
        d = d,
    )
}

/**
 * 把请求半径钳制为「几何合法」的有效半径（纯函数）。
 *
 * 平滑圆角的角占位长度是 p = (1 + s)·r，所以不能像普通圆角那样把 r 直接钳到
 * `min(w, h) / 2`——那会让 p 超出半宽、圆弧圆心跑到形状外、几何自交。
 * 正确做法是先按 `maxRadius / (1 + s)` 缩半径，保证 p ≤ maxRadius。
 *
 * @return 有效半径（像素）；宽度 / 高度 / 半径任一为「非有限」或 ≤ 0 时返回 0（调用方应退化为矩形）
 */
fun effectiveSquircleRadius(
    pixelRadius: Float,
    widthPx: Float,
    heightPx: Float,
    smoothing: Float,
): Float {
    // 逐边判非法，而不是只看 min(w, h)：宽为 +Inf、高有限时 min() 会摘掉 Inf，
    // 于是退化尺寸被误判为合法尺寸。
    if (!widthPx.isFinite() || !heightPx.isFinite() || widthPx <= 0f || heightPx <= 0f) return 0f
    if (!pixelRadius.isFinite() || pixelRadius <= 0f) return 0f
    val s = if (smoothing.isFinite()) smoothing.coerceIn(0f, 1f) else 0f
    val maxRadius = min(widthPx, heightPx) / 2f
    return pixelRadius.coerceIn(0f, maxRadius / (1f + s))
}

/**
 * 平滑圆角（squircle）Shape：四个角用「cubic 过渡段 + 圆弧段」拼接，得到连续曲率的外观。
 *
 * `smoothing = 0f` 时逐点等价于 [RoundedCornerShape]；[`smoothing`] 越大越接近「方中带圆」的
 * 连续曲率轮廓。半径会被 [effectiveSquircleRadius] 钳制，形状绝不会自交。
 */
class SquircleCornerShape(
    private val radius: Dp,
    private val smoothing: Float = NirikoShapes.DefaultSmoothing,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        if (!size.width.isFinite() || !size.height.isFinite() || size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }
        val effective = effectiveSquircleRadius(
            pixelRadius = radius.value * density.density,
            widthPx = size.width,
            heightPx = size.height,
            smoothing = smoothing,
        )
        if (effective <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        return Outline.Generic(squirclePath(size, squircleCorner(effective, smoothing)))
    }

    override fun toString(): String = "SquircleCornerShape(radius=$radius, smoothing=$smoothing)"
}

/** 构造一个矩形尺寸的平滑圆角轮廓。 */
private fun squirclePath(size: Size, g: SquircleCorner): Path {
    val w = size.width
    val h = size.height
    val p = g.extent
    val path = Path()
    // 顺时针遍历，起点在顶边左端（左上角的 P0）：TL 逆向 → TR 正向 → BR 逆向 → BL 正向。
    path.moveTo(p, 0f)
    path.lineTo(w - p, 0f)
    path.appendCorner(g, forward = true, mirrored = true) { x, y -> Offset(w - x, y) }
    path.lineTo(w, h - p)
    path.appendCorner(g, forward = false, mirrored = false) { x, y -> Offset(w - x, h - y) }
    path.lineTo(p, h)
    path.appendCorner(g, forward = true, mirrored = true) { x, y -> Offset(x, h - y) }
    path.lineTo(0f, p)
    path.appendCorner(g, forward = false, mirrored = false) { x, y -> Offset(x, y) }
    path.close()
    return path
}

/**
 * 在当前轮廓末尾追加一个圆角。
 *
 * @param forward true = 沿局部坐标 P0 → P3 方向；false = 反向（P3 → P0）
 * @param mirrored 该角的变换是否镜像（水平/垂直翻转）——镜像会翻转圆弧的旋转方向
 * @param map 局部坐标 → 画布坐标
 */
private inline fun Path.appendCorner(
    g: SquircleCorner,
    forward: Boolean,
    mirrored: Boolean,
    map: (Float, Float) -> Offset,
) {
    val p = g.extent
    val a = g.a
    val b = g.b
    val c = g.c
    val d = g.d
    val r = g.effectiveRadius

    val p0 = map(p, 0f)
    val p1 = map(p - a - b - c, d)
    val p2 = map(d, p - a - b - c)
    val p3 = map(0f, p)
    val c1 = map(p - a, 0f)
    val c2 = map(p - a - b, 0f)
    val c3 = map(0f, p - a - b)
    val c4 = map(0f, p - a)
    val center = map(r, r)

    // 圆弧起点由映射后的点反解角度，天然兼容四种角变换（含镜像）。
    val arcStart = if (forward) p1 else p2
    val startAngle = Math.toDegrees(
        atan2((arcStart.y - center.y).toDouble(), (arcStart.x - center.x).toDouble()),
    ).toFloat()
    val direction = if (forward) -1f else 1f
    val sweep = g.arcMeasureDegrees * direction * (if (mirrored) -1f else 1f)
    val arcRect = Rect(center = center, radius = r)

    if (forward) {
        cubicSegment(c1, c2, p1)
        arcTo(arcRect, startAngle, sweep, false)
        cubicSegment(c3, c4, p3)
    } else {
        cubicSegment(c3, c4, p2)
        arcTo(arcRect, startAngle, sweep, false)
        cubicSegment(c2, c1, p0)
    }
}

/** Path.cubicTo 的 Offset 版（Compose Path 的公开重载只接受 6 个 Float 参数）。 */
private fun Path.cubicSegment(control1: Offset, control2: Offset, end: Offset) {
    cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
}
