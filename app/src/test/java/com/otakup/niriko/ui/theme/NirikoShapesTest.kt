package com.otakup.niriko.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.min

/**
 * 形状令牌 + 平滑圆角（squircle）几何测试。
 *
 * squircle 参考值由文件头注明的公开 corner-smoothing 公式解析求出；
 * 除数值外还验证几何不变量（角占位长度恒等式、P1 落在圆弧上、s = 0 退化、
 * 有效半径钳制），保证 Shape 不会自交或越过半宽。
 */
class NirikoShapesTest {

    // ── 令牌档位 ─────────────────────────────────────────────

    @Test
    fun cornerTokensKeepLegacyValues() {
        // 与收敛前的散落字面量逐一相等：改这些值等于改全局观感
        assertEquals(20.dp, NirikoShapes.Card)
        assertEquals(16.dp, NirikoShapes.Section)
        assertEquals(999.dp, NirikoShapes.Pill)
        assertEquals(12.dp, NirikoShapes.Control)
        assertEquals(0.6f, NirikoShapes.DefaultSmoothing, 0f)
    }

    @Test
    fun tokenToShapeMappingIsLinearRounded() {
        assertTrue(NirikoShapes.rounded(NirikoShapes.Card) is RoundedCornerShape)
        assertTrue(NirikoShapes.CardShape is RoundedCornerShape)
        assertTrue(NirikoShapes.SectionShape is RoundedCornerShape)
        assertTrue(NirikoShapes.ControlShape is RoundedCornerShape)
        assertTrue(NirikoShapes.PillShape is RoundedCornerShape)
        // 按高度派生的胶囊（开关轨道）：等价既有 RoundedCornerShape(height / 2)
        assertTrue(NirikoShapes.capsule(30.dp) is RoundedCornerShape)
    }

    @Test
    fun squircleTokenReturnsSquircleShape() {
        val shape: Shape = NirikoShapes.squircle(NirikoShapes.Card)
        assertTrue(shape is SquircleCornerShape)
        assertTrue(NirikoShapes.squircle(NirikoShapes.Section, smoothing = 0f) is SquircleCornerShape)
    }

    // ── squircle 几何 ────────────────────────────────────────

    /** r = 16px、s = 0.6 的解析参考值。 */
    @Test
    fun squircleCornerMatchesAnalyticValues() {
        val g = squircleCorner(pixelRadius = 16f, smoothing = 0.6f)
        assertEquals(16f, g.effectiveRadius, 0f)
        assertEquals(25.6f, g.extent, 1e-4f)
        assertEquals(36f, g.arcMeasureDegrees, 1e-4f)
        assertEquals(27f, g.angleAlpha, 1e-4f)
        assertEquals(6.9924f, g.arcSectionLength, 1e-3f)
        assertEquals(3.4226f, g.c, 1e-3f)
        assertEquals(1.7439f, g.d, 1e-3f)
        assertEquals(4.4804f, g.b, 1e-3f)
        assertEquals(8.9607f, g.a, 1e-3f)
    }

    /** 恒等式：角占位长度 = 过渡段 + 圆弧弦 + 竖直过渡 + 切线过渡。 */
    @Test
    fun cornerSegmentsFillTheExtent() {
        for (s in listOf(0f, 0.2f, 0.6f, 1f)) {
            val g = squircleCorner(pixelRadius = 24f, smoothing = s)
            val sum = g.a + g.b + g.c + g.d + g.arcSectionLength
            assertEquals(g.extent, sum, 1e-3f)
        }
    }

    /** P1（圆弧起点）必须落在半径 r 的圆弧上，否则轮廓会出现折角。 */
    @Test
    fun arcStartLiesOnTheArcCircle() {
        for (s in listOf(0f, 0.3f, 0.6f, 1f)) {
            val g = squircleCorner(pixelRadius = 16f, smoothing = s)
            val p1x = g.extent - g.a - g.b - g.c
            val p1y = g.d
            assertEquals(g.effectiveRadius, hypot(p1x - g.effectiveRadius, p1y - g.effectiveRadius), 2e-3f)
        }
    }

    /** s = 0 ⇒ 精确退化为普通圆角（一个四分之一圆弧，过渡段长度为 0）。 */
    @Test
    fun zeroSmoothingDegeneratesToPlainRoundedCorner() {
        val g = squircleCorner(pixelRadius = 16f, smoothing = 0f)
        assertEquals(16f, g.extent, 1e-5f)
        assertEquals(90f, g.arcMeasureDegrees, 1e-5f)
        assertEquals(16f, g.arcSectionLength, 1e-4f)
        assertEquals(0f, g.a, 1e-4f)
        assertEquals(0f, g.b, 1e-4f)
        assertEquals(0f, g.c, 1e-4f)
        assertEquals(0f, g.d, 1e-4f)
    }

    /** s = 1 ⇒ 圆弧段消失，全部由过渡段构成。 */
    @Test
    fun maxSmoothingHasNoArcSegment() {
        val g = squircleCorner(pixelRadius = 16f, smoothing = 1f)
        assertEquals(32f, g.extent, 1e-4f)
        assertEquals(0f, g.arcMeasureDegrees, 1e-5f)
        assertEquals(0f, g.arcSectionLength, 1e-5f)
        assertTrue(g.b > 0f)
    }

    /** 平滑度越界被钳制（-1 ≡ 0，2 ≡ 1）。 */
    @Test
    fun smoothingIsClampedToOneUnit() {
        val low = squircleCorner(pixelRadius = 16f, smoothing = -1f)
        val zero = squircleCorner(pixelRadius = 16f, smoothing = 0f)
        assertEquals(zero.extent, low.extent, 0f)
        assertEquals(zero.arcMeasureDegrees, low.arcMeasureDegrees, 0f)
        assertEquals(zero.a, low.a, 0f)
        val high = squircleCorner(pixelRadius = 16f, smoothing = 2f)
        val one = squircleCorner(pixelRadius = 16f, smoothing = 1f)
        assertEquals(one.extent, high.extent, 0f)
        assertEquals(one.a, high.a, 0f)
    }

    /** 圆弧 + 过渡段总和随平滑度单调递减且始终小于半径 ⇒ b 恒为正、几何不自交。 */
    @Test
    fun cornerFootprintShrinksMonotonicallyWithSmoothing() {
        var previous = Float.MAX_VALUE
        for (i in 0..10) {
            val s = i / 10f
            val g = squircleCorner(pixelRadius = 16f, smoothing = s)
            val footprint = g.arcSectionLength + g.c + g.d
            assertTrue("footprint must stay inside the radius at s=$s", footprint <= 16f + 1e-4f)
            assertTrue("footprint must shrink at s=$s", footprint < previous)
            assertTrue("b must stay positive at s=$s", g.b > 0f)
            previous = footprint
        }
    }

    /** 负半径按 0 处理。 */
    @Test
    fun negativeRadiusCollapsesToZero() {
        val g = squircleCorner(pixelRadius = -8f, smoothing = 0.6f)
        assertEquals(0f, g.effectiveRadius, 0f)
        assertEquals(0f, g.extent, 0f)
        assertEquals(0f, g.arcSectionLength, 0f)
    }

    // ── 有效半径钳制 ─────────────────────────────────────────

    /** 大半径被 `maxRadius / (1 + s)` 钳住（不是直接钳到半宽）。 */
    @Test
    fun effectiveRadiusKeepsExtentInsideHalfSize() {
        val r = effectiveSquircleRadius(pixelRadius = 100f, widthPx = 50f, heightPx = 50f, smoothing = 0.6f)
        assertEquals(15.625f, r, 1e-4f)
        val g = squircleCorner(r, 0.6f)
        assertEquals(25f, g.extent, 1e-3f)
        assertTrue(g.extent <= min(50f, 50f) / 2f + 1e-4f)
    }

    /** 半径本身较小时不被放大。 */
    @Test
    fun smallRadiusPassesThrough() {
        assertEquals(10f, effectiveSquircleRadius(10f, 50f, 50f, 0.6f), 1e-5f)
        assertEquals(10f, effectiveSquircleRadius(10f, 50f, 50f, 0f), 1e-5f)
    }

    /** 退化尺寸 / 非法尺寸 ⇒ 0（调用方退化为矩形）。 */
    @Test
    fun degenerateSizesCollapseToZero() {
        assertEquals(0f, effectiveSquircleRadius(10f, 0f, 50f, 0.6f), 0f)
        assertEquals(0f, effectiveSquircleRadius(10f, 50f, 0f, 0.6f), 0f)
        assertEquals(0f, effectiveSquircleRadius(10f, Float.NaN, 50f, 0.6f), 0f)
        assertEquals(0f, effectiveSquircleRadius(10f, Float.POSITIVE_INFINITY, 50f, 0.6f), 0f)
        assertEquals(0f, effectiveSquircleRadius(-4f, 50f, 50f, 0.6f), 0f)
    }

    /** 任意尺寸 / 平滑度下，钳制后的角占位长度都不超过半宽半高。 */
    @Test
    fun clampHoldsForAllSmoothingLevels() {
        for (s in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (side in listOf(12f, 40f, 200f)) {
                val r = effectiveSquircleRadius(pixelRadius = 999f, widthPx = side, heightPx = side, smoothing = s)
                val g = squircleCorner(r, s)
                assertTrue(g.extent <= side / 2f + 1e-3f)
            }
        }
    }
}
