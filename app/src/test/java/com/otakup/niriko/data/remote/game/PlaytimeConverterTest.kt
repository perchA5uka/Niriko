package com.otakup.niriko.data.remote.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PlaytimeConverter 时长换算单元测试（存储分钟、UI 小时）。
 */
class PlaytimeConverterTest {

    @Test
    fun minutesToHours_floorDivide() {
        assertEquals(0, PlaytimeConverter.minutesToHours(30))
        assertEquals(1, PlaytimeConverter.minutesToHours(60))
        assertEquals(2, PlaytimeConverter.minutesToHours(150))
        assertEquals(10, PlaytimeConverter.minutesToHours(600))
    }

    @Test
    fun hoursToMinutes_multiplies() {
        assertEquals(60, PlaytimeConverter.hoursToMinutes(1))
        assertEquals(300, PlaytimeConverter.hoursToMinutes(5))
        assertEquals(0, PlaytimeConverter.hoursToMinutes(0))
    }

    @Test
    fun hoursToMinutes_nullOrNegative_null() {
        assertNull(PlaytimeConverter.hoursToMinutes(null))
        assertNull(PlaytimeConverter.hoursToMinutes(-1))
    }

    @Test
    fun format_readable() {
        assertEquals("45m", PlaytimeConverter.format(45))
        assertEquals("2h", PlaytimeConverter.format(120))
        assertEquals("2h30m", PlaytimeConverter.format(150))
        assertEquals("0m", PlaytimeConverter.format(0))
    }
}
