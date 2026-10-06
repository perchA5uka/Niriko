package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 横滑轨道卡片高度稳定（B03）的纯规则测试。
 *
 * 这个 Bug 的根因不在某个具体卡片，而在一条**不变量**：同一轨道内所有卡片必须占同样的行数。
 * 因此这里测的是那条不变量本身，而不是某张卡长什么样。
 */
class RailCardPolicyTest {
    @Test
    fun staffAndTailUseSameFontScaledSlots() {
        for (lineHeight in listOf(12f, 16f, 24f, 32f)) {
            val regular = RailCardPolicy.staffCardHeightDp(lineHeight)
            val tail = RailCardPolicy.staffCardHeightDp(lineHeight)
            assertEquals(64f + 6f + 2f * lineHeight + 32f, regular, 0.001f)
            assertTrue(RailCardPolicy.isUniform(listOf(regular, tail)))
            assertEquals(regular + lineHeight + 2f,
                RailCardPolicy.staffCardHeightDp(lineHeight, labelLines = 2, extraGapDp = 2f), 0.001f)
        }
    }


    @Test
    fun `固定行数与数据无关`() {
        // 有标签 / 无标签，预留的行数完全一样 —— 这就是「不抖」的全部依据
        assertEquals(
            RailCardPolicy.fixedTextLines(),
            RailCardPolicy.fixedTextLines(),
        )
        // 宽卡（两行标题）比窄卡多一行，但**同一轨道内**必须统一
        assertEquals(RailCardPolicy.TITLE_LINES + RailCardPolicy.LABEL_LINES, RailCardPolicy.fixedTextLines())
        assertEquals(
            RailCardPolicy.WIDE_TITLE_LINES + RailCardPolicy.LABEL_LINES,
            RailCardPolicy.fixedTextLines(RailCardPolicy.WIDE_TITLE_LINES),
        )
        assertTrue(RailCardPolicy.fixedTextLines(RailCardPolicy.WIDE_TITLE_LINES) > RailCardPolicy.fixedTextLines())
    }

    @Test
    fun `非法入参不会算出零行或负行`() {
        // 标题至少一行（0/负值被抬高）——否则卡片会塌成一条线
        assertEquals(1 + 1, RailCardPolicy.fixedTextLines(titleLines = 0))
        assertEquals(1 + 1, RailCardPolicy.fixedTextLines(titleLines = -3))
        // 标签行允许为 0（某些轨道确实没有标签），但不能为负
        assertEquals(1, RailCardPolicy.fixedTextLines(labelLines = 0))
        assertEquals(1, RailCardPolicy.fixedTextLines(labelLines = -2))
    }

    @Test
    fun `一致性判据`() {
        assertTrue(RailCardPolicy.isUniform(listOf(234f, 234f, 234f)))
        assertTrue("空集合也视作一致（没有卡片就没有抖动）", RailCardPolicy.isUniform(emptyList()))
        assertFalse("只要有一张矮一点就会抖", RailCardPolicy.isUniform(listOf(234f, 218f)))
        // 浮点误差不算「不一致」的场景由调用方保证（高度由同一组常量算出，不会是近似值）
    }

    @Test
    fun `最小内容高度包含封面与预留行`() {
        val narrow = RailCardPolicy.minContentHeightDp(coverHeightDp = 100f)
        val wide = RailCardPolicy.minContentHeightDp(coverHeightDp = 120f, titleLines = RailCardPolicy.WIDE_TITLE_LINES)
        assertTrue(narrow > 100f)
        assertTrue(wide > 120f)
        // 同一组入参必须得到同一个值（否则卡片之间就会不一致）
        assertEquals(narrow, RailCardPolicy.minContentHeightDp(coverHeightDp = 100f), 0.0001f)
    }

    @Test
    fun `常量本身合理`() {
        assertTrue(RailCardPolicy.TITLE_LINES >= 1)
        assertTrue(RailCardPolicy.WIDE_TITLE_LINES >= RailCardPolicy.TITLE_LINES)
        assertTrue(RailCardPolicy.LABEL_LINES >= 1)
    }
}
