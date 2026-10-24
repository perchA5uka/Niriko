package com.otakup.niriko.data.onair

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-D2：放送时刻按设备时区换算。
 * 用例覆盖三类：跨日（次日/前一日）、同时区只显示一个时刻、以及 airTimeZone 缺失时回退 CN。
 * 时区一律作为入参传入，不读设备默认区，保证在任意 CI 时区下结论一致。
 */
class AirTimeTest {

    private val date = LocalDate.of(2026, 10, 17)
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun subject(minutes: Int?, zone: String? = null) = SubjectEntity(
        subjectId = 1, title = "Fixture", type = SubjectType.ANIME,
        airTimeMinutes = minutes, airTimeZone = zone,
    )

    @Test
    fun crosses_to_next_day_when_device_is_ahead() {
        // 23:30 中国时间 -> 次日 00:30 日本时间
        val airTime = AirTime(1410, "CN")
        assertEquals(LocalDateTime.of(2026, 10, 18, 0, 30), airTime.toLocal(date, tokyo))
        assertEquals(1, airTime.dayShift(date, tokyo))
        assertEquals("00:30（次日）", airTime.displayText(date, tokyo))
        assertEquals("北京时间 23:30", airTime.sourceHint(date, tokyo))
    }

    @Test
    fun stays_on_same_day_when_conversion_does_not_cross_midnight() {
        // 00:55 中国时间 -> 01:55 日本时间
        val airTime = AirTime(55, "CN")
        assertEquals(LocalDateTime.of(2026, 10, 17, 1, 55), airTime.toLocal(date, tokyo))
        assertEquals(0, airTime.dayShift(date, tokyo))
        assertEquals("01:55", airTime.displayText(date, tokyo))
    }

    @Test
    fun same_zone_shows_only_one_time() {
        val airTime = AirTime(1410, "CN")
        assertTrue(airTime.isSameOffset(date, shanghai))
        assertEquals("23:30", airTime.displayText(date, shanghai))
        assertEquals("23:30", airTime.formatLocal(date, shanghai))
        assertNull(airTime.sourceHint(date, shanghai))
    }

    @Test
    fun missing_zone_falls_back_to_china_time() {
        assertEquals(AirTime.DEFAULT_ZONE_ID, AirTime.of(subject(1410, zone = null))!!.zoneId)
        assertEquals("Asia/Shanghai", AirTime.DEFAULT_ZONE_ID)
        // 没有精确时刻 => 没有 AirTime，调用方回退到"集数/星期"展示
        assertNull(AirTime.of(subject(minutes = null, zone = "CN")))
    }

    @Test
    fun local_zone_uses_the_passed_device_zone() {
        val airTime = AirTime(1410, "LOCAL")
        assertEquals(LocalDateTime.of(2026, 10, 17, 23, 30), airTime.toLocal(date, tokyo))
        assertEquals("23:30", airTime.displayText(date, tokyo))
        assertEquals("设备时间", airTime.sourceLabel())
    }

    @Test
    fun crosses_to_previous_day_when_device_is_behind() {
        // 00:30 日本时间 -> 前一天 23:30 中国时间
        val airTime = AirTime(30, "JP")
        assertEquals(LocalDateTime.of(2026, 10, 16, 23, 30), airTime.toLocal(date, shanghai))
        assertEquals(-1, airTime.dayShift(date, shanghai))
        assertEquals("23:30（前一日）", airTime.displayText(date, shanghai))
        assertEquals("日本时间 00:30", airTime.sourceHint(date, shanghai))
    }

    @Test
    fun format_minutes_normalizes_out_of_range_values() {
        assertEquals("23:30", AirTime.format(1410))
        assertEquals("00:00", AirTime.format(0))
        assertEquals("00:10", AirTime.format(24 * 60 + 10))
        assertEquals(0, AirTime(1440, "CN").hour)
    }

    @Test
    fun of_carries_zone_from_subject() {
        val airTime = AirTime.of(subject(1410, zone = "JP"))!!
        assertEquals(1410, airTime.minutes)
        assertEquals("JP", airTime.zoneId)
        assertEquals("日本时间", airTime.sourceLabel())
    }
}
