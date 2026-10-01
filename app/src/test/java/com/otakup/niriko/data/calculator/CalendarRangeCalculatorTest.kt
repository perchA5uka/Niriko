package com.otakup.niriko.data.calculator

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日历「周 / 月」双模纯日期逻辑的单元测试。
 *
 * 覆盖计划书 §四 B3 点名的边界：跨月周、跨年周、1 月 1 日落在上一年周，
 * 以及「周 ↔ 月」切换时选中日期（锚点）保持不变。
 */
class CalendarRangeCalculatorTest {

    // ==================== 周起点 ====================

    @Test
    fun `一周以周日为第一天`() {
        assertEquals(DayOfWeek.SUNDAY, CalendarRangeCalculator.FirstDayOfWeek)
    }

    @Test
    fun `周日锚点所在周从自身开始`() {
        // 2026-01-04 是周日
        val sunday = LocalDate.of(2026, 1, 4)
        assertEquals(sunday, CalendarRangeCalculator.weekStart(sunday))
        assertEquals(LocalDate.of(2026, 1, 10), CalendarRangeCalculator.weekEnd(sunday))
    }

    @Test
    fun `周中每天映射回同一个周日`() {
        val sunday = LocalDate.of(2026, 1, 4)
        for (offset in 0L..6L) {
            assertEquals(
                "偏移 $offset 天的日期应归属 $sunday 那一周",
                sunday,
                CalendarRangeCalculator.weekStart(sunday.plusDays(offset)),
            )
        }
    }

    @Test
    fun `周六锚点所在周从六天前的周日开始`() {
        val saturday = LocalDate.of(2026, 1, 10)
        assertEquals(LocalDate.of(2026, 1, 4), CalendarRangeCalculator.weekStart(saturday))
        assertEquals(saturday, CalendarRangeCalculator.weekEnd(saturday))
    }

    // ==================== 边界：跨年周 ====================

    @Test
    fun `一月一日落在上一年的周内`() {
        // 2026-01-01 是周四，所在周从 2025-12-28（周日）开始
        val newYear = LocalDate.of(2026, 1, 1)
        val weekStart = CalendarRangeCalculator.weekStart(newYear)
        assertEquals(LocalDate.of(2025, 12, 28), weekStart)
        assertEquals(2025, weekStart.year)
        assertEquals(LocalDate.of(2026, 1, 3), CalendarRangeCalculator.weekEnd(newYear))
    }

    @Test
    fun `跨年周的可见范围跨越两个年份`() {
        val (start, end) = CalendarRangeCalculator.visibleRange(LocalDate.of(2026, 1, 1), expanded = false)
        assertEquals(2025, start.year)
        assertEquals(2026, end.year)
        assertEquals(7L, java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1)
    }

    @Test
    fun `跨年周覆盖到的月份是两个`() {
        val (start, end) = CalendarRangeCalculator.visibleRange(LocalDate.of(2026, 1, 1), expanded = false)
        assertEquals(
            listOf(YearMonth.of(2025, 12), YearMonth.of(2026, 1)),
            CalendarRangeCalculator.monthsCovering(start, end),
        )
    }

    // ==================== 边界：跨月周 ====================

    @Test
    fun `跨月周从上一个月的周日开始`() {
        // 2026-04-01 是周三，所在周从 2026-03-29（周日）到 2026-04-04（周六）
        val (start, end) = CalendarRangeCalculator.visibleRange(LocalDate.of(2026, 4, 1), expanded = false)
        assertEquals(LocalDate.of(2026, 3, 29), start)
        assertEquals(LocalDate.of(2026, 4, 4), end)
    }

    @Test
    fun `跨月周覆盖到的月份是两个且升序`() {
        val (start, end) = CalendarRangeCalculator.visibleRange(LocalDate.of(2026, 4, 1), expanded = false)
        assertEquals(
            listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 4)),
            CalendarRangeCalculator.monthsCovering(start, end),
        )
    }

    // ==================== 月视图可见范围 ====================

    @Test
    fun `月视图可见范围是整月`() {
        val (start, end) = CalendarRangeCalculator.visibleRange(LocalDate.of(2026, 1, 17), expanded = true)
        assertEquals(LocalDate.of(2026, 1, 1), start)
        assertEquals(LocalDate.of(2026, 1, 31), end)
    }

    @Test
    fun `月视图闰年二月有二十九天`() {
        val (start, end) = CalendarRangeCalculator.visibleRange(LocalDate.of(2024, 2, 10), expanded = true)
        assertEquals(LocalDate.of(2024, 2, 1), start)
        assertEquals(LocalDate.of(2024, 2, 29), end)
    }

    @Test
    fun `月视图平年二月有二十八天`() {
        val (_, end) = CalendarRangeCalculator.visibleRange(LocalDate.of(2025, 2, 10), expanded = true)
        assertEquals(LocalDate.of(2025, 2, 28), end)
    }

    @Test
    fun `月视图只覆盖一个月份`() {
        val (start, end) = CalendarRangeCalculator.visibleRange(LocalDate.of(2026, 7, 20), expanded = true)
        assertEquals(listOf(YearMonth.of(2026, 7)), CalendarRangeCalculator.monthsCovering(start, end))
    }

    // ==================== 周 / 月切换保持锚点 ====================

    @Test
    fun `任意锚点在两种粒度下都落在可见范围内`() {
        val anchors = listOf(
            LocalDate.of(2026, 1, 1),   // 跨年周
            LocalDate.of(2026, 4, 1),   // 跨月周
            LocalDate.of(2024, 2, 29),  // 闰日
            LocalDate.of(2026, 12, 31), // 年末
            LocalDate.of(2026, 1, 4),   // 周日
            LocalDate.of(2026, 1, 10),  // 周六
        )
        for (anchor in anchors) {
            val (monthStart, monthEnd) = CalendarRangeCalculator.visibleRange(anchor, expanded = true)
            val (weekStart, weekEnd) = CalendarRangeCalculator.visibleRange(anchor, expanded = false)
            assertTrue("$anchor 应落在月视图范围内", !anchor.isBefore(monthStart) && !anchor.isAfter(monthEnd))
            assertTrue("$anchor 应落在周视图范围内", !anchor.isBefore(weekStart) && !anchor.isAfter(weekEnd))
            // 切粒度的锚点位移为 0：选中日期不跳变
            assertEquals(anchor, CalendarRangeCalculator.shiftAnchor(anchor, expanded = true, delta = 0))
            assertEquals(anchor, CalendarRangeCalculator.shiftAnchor(anchor, expanded = false, delta = 0))
        }
    }

    @Test
    fun `周视图一周恒为七天`() {
        val anchors = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 12, 31),
        )
        for (anchor in anchors) {
            val (start, end) = CalendarRangeCalculator.visibleRange(anchor, expanded = false)
            assertEquals(
                "$anchor 所在周应恰好 7 天",
                7L,
                java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1,
            )
            assertEquals(DayOfWeek.SUNDAY, start.dayOfWeek)
            assertEquals(DayOfWeek.SATURDAY, end.dayOfWeek)
        }
    }

    // ==================== 翻页位移 ====================

    @Test
    fun `月视图翻页按整月位移`() {
        val anchor = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 2, 15), CalendarRangeCalculator.shiftAnchor(anchor, true, 1))
        assertEquals(LocalDate.of(2025, 12, 15), CalendarRangeCalculator.shiftAnchor(anchor, true, -1))
    }

    @Test
    fun `周视图翻页按整周位移`() {
        val anchor = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 1, 22), CalendarRangeCalculator.shiftAnchor(anchor, false, 1))
        assertEquals(LocalDate.of(2026, 1, 8), CalendarRangeCalculator.shiftAnchor(anchor, false, -1))
    }

    @Test
    fun `月末翻月按月截断日`() {
        // LocalDate.plusMonths 对不存在的日期取当月最后一天
        assertEquals(
            LocalDate.of(2026, 2, 28),
            CalendarRangeCalculator.shiftAnchor(LocalDate.of(2026, 1, 31), true, 1),
        )
    }

    @Test
    fun `周视图翻页后仍在同一新周基准上`() {
        val anchor = LocalDate.of(2026, 1, 15)
        val next = CalendarRangeCalculator.shiftAnchor(anchor, false, 1)
        assertFalse(CalendarRangeCalculator.sameWeek(anchor, next))
        assertEquals(
            CalendarRangeCalculator.weekStart(anchor).plusDays(7),
            CalendarRangeCalculator.weekStart(next),
        )
    }

    // ==================== 标题 ====================

    @Test
    fun `月视图标题与改造前一致`() {
        assertEquals("2026年1月", CalendarRangeCalculator.title(LocalDate.of(2026, 1, 17), expanded = true))
        assertEquals("2026年12月", CalendarRangeCalculator.title(LocalDate.of(2026, 12, 1), expanded = true))
    }

    @Test
    fun `周视图标题同月内省略重复月份`() {
        assertEquals("1月4日 – 10日", CalendarRangeCalculator.title(LocalDate.of(2026, 1, 4), expanded = false))
    }

    @Test
    fun `周视图标题跨月时写全两侧月份`() {
        assertEquals("3月29日 – 4月4日", CalendarRangeCalculator.title(LocalDate.of(2026, 4, 1), expanded = false))
    }

    @Test
    fun `周视图标题跨年时写全两侧月份`() {
        assertEquals("12月28日 – 1月3日", CalendarRangeCalculator.title(LocalDate.of(2025, 12, 31), expanded = false))
    }

    // ==================== 月份枚举 ====================

    @Test
    fun `跨年周的月份枚举升序且含边界`() {
        assertEquals(
            listOf(YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1)),
            CalendarRangeCalculator.monthsCovering(LocalDate.of(2025, 11, 30), LocalDate.of(2026, 1, 1)),
        )
    }

    @Test
    fun `起点晚于终点时月份枚举为空`() {
        assertEquals(
            emptyList<YearMonth>(),
            CalendarRangeCalculator.monthsCovering(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)),
        )
    }

    // ==================== 同周判定 ====================

    @Test
    fun `同一周内任意两天判定为同周`() {
        assertTrue(CalendarRangeCalculator.sameWeek(LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 10)))
        assertFalse(CalendarRangeCalculator.sameWeek(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11)))
    }

    @Test
    fun `跨年周的周六与次年周日不同周`() {
        assertTrue(CalendarRangeCalculator.sameWeek(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 3)))
        assertFalse(CalendarRangeCalculator.sameWeek(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4)))
    }
}
