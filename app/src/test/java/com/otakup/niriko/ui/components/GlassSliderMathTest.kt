package com.otakup.niriko.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 滑块取值数学（[offsetToValue]）回归测试。
 *
 * 该函数从 GlassControls.kt 抽到 GlassSliderMath.kt 就是为了让这条既有语义被单测钉住：
 * 0 档 / 满量程 / 中间档 / 范围反转 / 宽度为 0 / 0.5 步进全部覆盖。
 */
class GlassSliderMathTest {

    private val tenPoint = 0f..10f
    private val width = 300f

    /** 宽度为 0（尚未测量）⇒ 退回起点，而不是除零产出 NaN。 */
    @Test
    fun zeroWidthReturnsRangeStart() {
        assertEquals(0f, offsetToValue(xPx = 120f, widthPx = 0f, range = tenPoint, steps = 19), 0f)
        assertEquals(0f, offsetToValue(xPx = -50f, widthPx = -1f, range = tenPoint, steps = 19), 0f)
        // 反转范围同理退回其起点（= 10）
        assertEquals(10f, offsetToValue(xPx = 120f, widthPx = 0f, range = 10f..0f, steps = 19), 0f)
    }

    /** x = 0 ⇒ 范围起点。 */
    @Test
    fun leftEdgeReturnsRangeStart() {
        assertEquals(0f, offsetToValue(xPx = 0f, widthPx = width, range = tenPoint, steps = 19), 0f)
    }

    /** x = width ⇒ 满量程（含越界钳制）。 */
    @Test
    fun rightEdgeAndOvershootReturnRangeEnd() {
        assertEquals(10f, offsetToValue(xPx = width, widthPx = width, range = tenPoint, steps = 19), 0f)
        assertEquals(10f, offsetToValue(xPx = width * 3f, widthPx = width, range = tenPoint, steps = 19), 0f)
        assertEquals(0f, offsetToValue(xPx = -width, widthPx = width, range = tenPoint, steps = 19), 0f)
    }

    /** 中间档：中点为 5.0（0.5 步进的整数档）。 */
    @Test
    fun middleOfTrackReturnsHalfScale() {
        assertEquals(5f, offsetToValue(xPx = width / 2f, widthPx = width, range = tenPoint, steps = 19), 0f)
        // 略偏右：仍吸附到最近的 0.5 档
        assertEquals(5.5f, offsetToValue(xPx = width * 0.56f, widthPx = width, range = tenPoint, steps = 19), 0f)
        assertEquals(4.5f, offsetToValue(xPx = width * 0.44f, widthPx = width, range = tenPoint, steps = 19), 0f)
    }

    /** 范围反转（[10, 0]）时映射方向相反。 */
    @Test
    fun reversedRangeMapsInverted() {
        assertEquals(10f, offsetToValue(xPx = 0f, widthPx = width, range = 10f..0f, steps = 19), 0f)
        assertEquals(0f, offsetToValue(xPx = width, widthPx = width, range = 10f..0f, steps = 19), 0f)
        assertEquals(5f, offsetToValue(xPx = width / 2f, widthPx = width, range = 10f..0f, steps = 19), 0f)
    }

    /** steps = 19（0..10）⇒ 恰好 21 档、步长 0.5。 */
    @Test
    fun nineteenStepsProduceTwentyOneDiscreteValues() {
        val values = (0..20).map { i ->
            offsetToValue(xPx = width * i / 20f, widthPx = width, range = tenPoint, steps = 19)
        }
        assertEquals(21, values.size)
        assertEquals((0..20).map { it * 0.5f }, values)
    }

    /** steps <= 0 ⇒ 连续取值（不吸附）。 */
    @Test
    fun nonPositiveStepsStayContinuous() {
        assertEquals(3.3f, offsetToValue(xPx = width * 0.33f, widthPx = width, range = tenPoint, steps = 0), 1e-4f)
        assertEquals(3.3f, offsetToValue(xPx = width * 0.33f, widthPx = width, range = tenPoint, steps = -1), 1e-4f)
    }

    /** 其它档位：steps = 2 ⇒ 步长 1/3，四舍五入到最近档。 */
    @Test
    fun otherStepCountsSnapToTheirOwnGrid() {
        assertEquals(0f, offsetToValue(xPx = 0f, widthPx = width, range = 0f..1f, steps = 2), 1e-6f)
        assertEquals(2f / 3f, offsetToValue(xPx = width * 0.5f, widthPx = width, range = 0f..1f, steps = 2), 1e-6f)
        assertEquals(1f, offsetToValue(xPx = width, widthPx = width, range = 0f..1f, steps = 2), 1e-6f)
    }
}
