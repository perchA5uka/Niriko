package com.otakup.niriko.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 参与作品卡（F01）几何常量的纯函数测试。
 *
 * 这张卡的意义就在于**两个页面共用同一组数字**：角色页的「出演作品」与人物页的「参与作品」。
 * 因此这里锁的不是「卡片长得好看」（那只能真机看），而是三条能被机器判定的约定：
 * 1. 封面比例是 2:3，且封面高度**由宽度与比例推出来**（改宽度不会留下错位的高度）；
 * 2. 卡片内容有一个最小高度下限，横滑时不会因为标题长短跳高矮；
 * 3. 类型标签对每个 Bangumi type 都有确定的中文结果 —— 两个页面不会给出不同文字。
 */
class CreditSubjectCardTest {

    /** 封面高度 = 宽度 ÷ 比例（宽/高 = 2/3 ⇒ 高 = 宽 / (2/3) = 宽 × 1.5）。 */
    @Test
    fun 封面高度由宽度与比例推出() {
        val expected = CreditSubjectCardMetrics.width / CreditSubjectCardMetrics.posterAspectRatio
        val actual = CreditSubjectCardMetrics.posterHeight()
        assertTrue("expected=$expected actual=$actual", kotlin.math.abs((expected - actual).value) < 0.01f)
        // 2:3 → 高度必须大于宽度（竖向海报），否则比例写反了
        assertTrue(CreditSubjectCardMetrics.posterHeight() > CreditSubjectCardMetrics.width)
        assertEquals(1.5f, CreditSubjectCardMetrics.posterHeight().value / CreditSubjectCardMetrics.width.value, 0.01f)
    }

    @Test
    fun 卡片内容最小高度包含封面() {
        val min = CreditSubjectCardMetrics.minContentHeight()
        assertTrue(
            "内容下限必须覆盖封面 + 文字区：min=$min poster=${CreditSubjectCardMetrics.posterHeight()}",
            min > CreditSubjectCardMetrics.posterHeight(),
        )
        // 文字区至少一个标签行的高度，不能是 0（否则标题会被压到卡片外）
        assertTrue(CreditSubjectCardMetrics.minTextHeight.value >= 16f)
    }

    @Test
    fun 几何常量取值合理() {
        assertTrue(CreditSubjectCardMetrics.width.value >= 96f)
        assertTrue(CreditSubjectCardMetrics.railSpacing.value > 0f)
        assertTrue(CreditSubjectCardMetrics.cornerRadius.value > 0f)
        assertTrue(CreditSubjectCardMetrics.contentPadding.value > 0f)
        // 比例是「宽/高」= 2/3，必须小于 1
        assertTrue(CreditSubjectCardMetrics.posterAspectRatio < 1f)
        assertNotEquals(0f, CreditSubjectCardMetrics.posterAspectRatio)
    }

    /** 类型标签：两个页面共用同一份映射，未知类型回退到「其他」而不是空串。 */
    @Test
    fun 类型标签覆盖已知类型且未知类型有回退() {
        assertEquals("动画", subjectTypeLabel(2))
        assertEquals("书籍", subjectTypeLabel(1))
        assertEquals("音乐", subjectTypeLabel(3))
        assertEquals("游戏", subjectTypeLabel(4))
        assertEquals("三次元", subjectTypeLabel(6))
        assertEquals("其他", subjectTypeLabel(0))
        assertEquals("其他", subjectTypeLabel(-1))
        assertEquals("其他", subjectTypeLabel(999))
        // 永远不返回空串：徽标位置不能塌成一行空白
        for (type in -5..20) {
            assertTrue("type=$type", subjectTypeLabel(type).isNotBlank())
        }
    }
}
