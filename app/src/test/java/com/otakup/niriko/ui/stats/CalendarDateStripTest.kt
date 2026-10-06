package com.otakup.niriko.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 日期条（R9 DateSelector）几何与窗口中心的纯函数测试。 */
class CalendarDateStripTest {

    @Test
    fun dateStripDatesReturnsAscendingWindowAroundCenter() {
        val center = LocalDate.of(2026, 3, 15)
        val dates = dateStripDates(center)
        assertEquals(2 * DATE_STRIP_RADIUS + 1, dates.size)
        assertTrue(dates.contains(center))
        assertEquals(center.minusDays(DATE_STRIP_RADIUS.toLong()), dates.first())
        assertEquals(center.plusDays(DATE_STRIP_RADIUS.toLong()), dates.last())
        assertEquals(dates.sorted(), dates)
        dates.zipWithNext().forEach { (a, b) -> assertEquals(1L, ChronoUnit.DAYS.between(a, b)) }
    }

    @Test
    fun dateStripDatesHonoursCustomRadius() {
        val center = LocalDate.of(2026, 1, 1)
        assertEquals(
            listOf(
                LocalDate.of(2025, 12, 30),
                LocalDate.of(2025, 12, 31),
                center,
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3),
            ),
            dateStripDates(center, radius = 2),
        )
    }

    @Test
    fun dateStripDatesCrossesMonthAndYearBoundaries() {
        val dates = dateStripDates(LocalDate.of(2026, 1, 1), radius = 3)
        assertEquals(LocalDate.of(2025, 12, 29), dates.first())
        assertEquals(LocalDate.of(2026, 1, 4), dates.last())
    }

    @Test
    fun centerStaysOnTodayWhileAnchorIsInsideWindow() {
        val today = LocalDate.of(2026, 5, 10)
        assertEquals(today, dateStripCenter(today, today))
        assertEquals(today, dateStripCenter(today, today.plusDays(7)))
        // 窗口边界（±RADIUS）仍算「窗口内」，整条不移动
        assertEquals(today, dateStripCenter(today, today.minusDays(DATE_STRIP_RADIUS.toLong())))
        assertEquals(today, dateStripCenter(today, today.plusDays(DATE_STRIP_RADIUS.toLong())))
    }

    @Test
    fun centerReanchorsWhenAnchorLeavesWindow() {
        val today = LocalDate.of(2026, 5, 10)
        val after = today.plusDays(DATE_STRIP_RADIUS + 1L)
        val before = today.minusDays(DATE_STRIP_RADIUS + 1L)
        assertEquals(after, dateStripCenter(today, after))
        assertEquals(before, dateStripCenter(today, before))
    }

    @Test
    fun weekdayIndexIsSundayZero() {
        assertEquals(0, weekdayIndexSunday0(LocalDate.of(2026, 1, 4)))
        assertEquals(1, weekdayIndexSunday0(LocalDate.of(2026, 1, 5)))
        assertEquals(6, weekdayIndexSunday0(LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun weekdayIndexAdvancesOnePerDayWithinSevenDayCycle() {
        val start = LocalDate.of(2026, 1, 1)
        var expected = weekdayIndexSunday0(start)
        repeat(60) { offset ->
            val date = start.plusDays(offset.toLong())
            assertEquals(expected, weekdayIndexSunday0(date))
            assertTrue(weekdayIndexSunday0(date) in 0..6)
            expected = (expected + 1) % 7
        }
    }
}
