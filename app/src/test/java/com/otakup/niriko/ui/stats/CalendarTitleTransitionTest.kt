package com.otakup.niriko.ui.stats

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.otakup.niriko.data.calculator.CalendarRangeCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-15：日历标题「切月 / 切视图」过渡的纯函数判据。
 *
 * 为什么先测纯函数而不是截图：标题过渡真机才能看出"方向感"，但**方向算错**是可以在
 * JVM 里穷举的——翻到下个月必须和翻到上个月方向相反、同月内换日不能出现横向滑动、
 * 周/月互切属于换粒度（没有前后）。这三条只要有一条反了，用户就会看到"往回翻却往右滑"。
 *
 * 另一条必须锁住的是 reduceMotion：打开「减少动态效果」时过渡必须是
 * [EnterTransition.None] + [ExitTransition.None]（瞬切），而不是短一点的滑动。
 */
class CalendarTitleTransitionTest {

    @Test
    fun 月视图向后翻页得到FORWARD() {
        val from = LocalDate.of(2026, 9, 15)
        val to = LocalDate.of(2026, 10, 2)
        assertEquals(
            CalendarTitleDirection.FORWARD,
            calendarTitleDirection(from, true, to, true),
        )
    }

    @Test
    fun 月视图向前翻页得到BACKWARD() {
        val from = LocalDate.of(2026, 10, 20)
        val to = LocalDate.of(2026, 9, 1)
        assertEquals(
            CalendarTitleDirection.BACKWARD,
            calendarTitleDirection(from, true, to, true),
        )
    }

    @Test
    fun 同月内换日不产生方向() {
        // 点日期条换的是"看哪一天"，标题（2026年10月）没变，不应该出现横向滑动
        val from = LocalDate.of(2026, 10, 2)
        val to = LocalDate.of(2026, 10, 28)
        assertEquals(
            CalendarTitleDirection.NONE,
            calendarTitleDirection(from, true, to, true),
        )
    }

    @Test
    fun 周视图按周起点比较方向() {
        val sunday = CalendarRangeCalculator.weekStart(LocalDate.of(2026, 10, 7))
        assertEquals(CalendarTitleDirection.FORWARD, calendarTitleDirection(sunday, false, sunday.plusDays(7), false))
        assertEquals(CalendarTitleDirection.BACKWARD, calendarTitleDirection(sunday, false, sunday.minusDays(7), false))
        // 同一周内的相邻日期：标题（10月4日 - 10月10日）没变，不做位移
        assertEquals(CalendarTitleDirection.NONE, calendarTitleDirection(sunday, false, sunday.plusDays(3), false))
    }

    @Test
    fun 周月互切没有前后方向() {
        val date = LocalDate.of(2026, 10, 4)
        assertEquals(
            CalendarTitleDirection.NONE,
            calendarTitleDirection(date, true, date, false),
        )
        assertEquals(
            CalendarTitleDirection.NONE,
            calendarTitleDirection(date, false, date, true),
        )
    }

    @Test
    fun reduceMotion时两侧都瞬切() {
        val transform = calendarTitleTransition(CalendarTitleDirection.FORWARD, reduceMotion = true)
        assertSame(EnterTransition.None, transform.targetContentEnter)
        assertSame(ExitTransition.None, transform.initialContentExit)
        assertNull("瞬切时也不应做尺寸动画", transform.sizeTransform)
    }

    @Test
    fun 正常模式下有位移但不是硬切() {
        val transform = calendarTitleTransition(CalendarTitleDirection.BACKWARD, reduceMotion = false)
        assertTrue("正常模式不应退化成瞬切", transform.targetContentEnter !== EnterTransition.None)
        assertTrue(transform.initialContentExit !== ExitTransition.None)
        // 标题宽度变化靠文本重排，不做尺寸动画（每帧重测不值得）
        assertNull(transform.sizeTransform)
    }

    @Test
    fun 无方向时仍是淡入淡出而不是瞬切() {
        // 换粒度是"换一块内容"，淡入淡出是对的；只有 reduceMotion 才瞬切
        val transform = calendarTitleTransition(CalendarTitleDirection.NONE, reduceMotion = false)
        assertTrue(transform.targetContentEnter !== EnterTransition.None)
    }
}
