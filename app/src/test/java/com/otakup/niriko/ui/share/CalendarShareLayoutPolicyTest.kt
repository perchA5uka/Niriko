package com.otakup.niriko.ui.share

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 日历分享卡版式单测。
 *
 * 覆盖的都是「真机上会摆错格子」的边界：1 号是周日（前置 6 格）、31 天的月份（6 行）、
 * 天号落在最后一列、以及点数上限收敛。
 */
class CalendarShareLayoutPolicyTest {

    @Test
    fun `前置空格按周一起始`() {
        assertEquals(0, CalendarShareLayoutPolicy.leadingBlanks(DayOfWeek.MONDAY))
        assertEquals(5, CalendarShareLayoutPolicy.leadingBlanks(DayOfWeek.SATURDAY))
        assertEquals(6, CalendarShareLayoutPolicy.leadingBlanks(DayOfWeek.SUNDAY))
    }

    @Test
    fun `31 天且 1 号是周日需要 6 行`() {
        val blanks = CalendarShareLayoutPolicy.leadingBlanks(DayOfWeek.SUNDAY)
        assertEquals(6, CalendarShareLayoutPolicy.rowCount(31, blanks))
    }

    @Test
    fun `28 天且 1 号是周一刚好 4 行`() {
        val blanks = CalendarShareLayoutPolicy.leadingBlanks(DayOfWeek.MONDAY)
        assertEquals(4, CalendarShareLayoutPolicy.rowCount(28, blanks))
    }

    @Test
    fun `空月份不占行`() {
        assertEquals(0, CalendarShareLayoutPolicy.rowCount(0, 0))
    }

    @Test
    fun `天号定位不越界`() {
        val blanks = CalendarShareLayoutPolicy.leadingBlanks(DayOfWeek.SUNDAY)
        val first = CalendarShareLayoutPolicy.cellOf(1, blanks)
        assertEquals(0 to 6, first)
        val second = CalendarShareLayoutPolicy.cellOf(2, blanks)
        assertEquals(1 to 0, second)
        val last = CalendarShareLayoutPolicy.cellOf(31, blanks)
        assertEquals(5 to 1, last)
    }

    @Test
    fun `点数收敛到 3`() {
        assertEquals(0, CalendarShareLayoutPolicy.dotCount(0))
        assertEquals(2, CalendarShareLayoutPolicy.dotCount(2))
        assertEquals(3, CalendarShareLayoutPolicy.dotCount(9))
    }

    @Test
    fun `标题与副文案`() {
        assertEquals("2026 年 11 月", CalendarShareLayoutPolicy.monthTitle(2026, 11))
        assertEquals("本月放送 12 部 · 更新 9 天", CalendarShareLayoutPolicy.headerMeta(12, 9))
    }

    @Test
    fun `拼卡数据 天数与前置空格正确`() {
        val month = java.time.YearMonth.of(2026, 11)
        val counts = mapOf(month.atDay(3) to 2, month.atDay(13) to 4)
        val data = buildCalendarShareData(month, counts, monthSubjectCount = 9)
        assertEquals(30, data.days.size)
        assertEquals("2026 年 11 月", data.monthLabel)
        assertEquals("本月放送 9 部 · 更新 2 天", data.headerMeta)
        assertEquals(2, data.days.first { it.dayOfMonth == 3 }.subjectCount)
        assertEquals(0, data.days.first { it.dayOfMonth == 4 }.subjectCount)
        assertEquals(listOf("本月放送" to "9", "更新天数" to "2", "最忙一天" to "4"), data.stats)
        assertEquals(CalendarShareLayoutPolicy.leadingBlanks(month.atDay(1).dayOfWeek), data.leadingBlanks)
    }

    @Test
    fun `拼卡数据 空月份不崩`() {
        val month = java.time.YearMonth.of(2026, 2)
        val data = buildCalendarShareData(month, emptyMap(), monthSubjectCount = 0)
        assertEquals(28, data.days.size)
        assertEquals(0, data.days.sumOf { it.subjectCount })
    }

    @Test
    fun `月末余位不属于本月`() {
        assertEquals(true, CalendarShareLayoutPolicy.isInMonth(30, 30))
        assertEquals(false, CalendarShareLayoutPolicy.isInMonth(31, 30))
        assertEquals(false, CalendarShareLayoutPolicy.isInMonth(0, 30))
        assertEquals(true, CalendarShareLayoutPolicy.isInMonth(28, 28))
    }

    @Test
    fun `封面地址进格子`() {
        val month = java.time.YearMonth.of(2026, 11)
        val covers = mapOf(month.atDay(3) to "https://example.com/a.jpg")
        val data = buildCalendarShareData(month, mapOf(month.atDay(3) to 2), 1, covers)
        assertEquals("https://example.com/a.jpg", data.days.first { it.dayOfMonth == 3 }.coverUrl)
        assertEquals(null, data.days.first { it.dayOfMonth == 4 }.coverUrl)
    }
}
