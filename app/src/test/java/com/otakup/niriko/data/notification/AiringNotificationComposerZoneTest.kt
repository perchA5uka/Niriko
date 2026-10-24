package com.otakup.niriko.data.notification

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A-D2：通知文案里的放送时刻按设备时区显示。
 *
 * 与 [AiringNotificationComposerTest] 分开是刻意的：那边锁定"合并/降级"等既有行为，
 * 这里只锁"有时区信息时的换算"，两边都不必为对方的改动改断言。
 */
class AiringNotificationComposerZoneTest {

    private val date = LocalDate.of(2026, 10, 17)
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun item(id: Long, title: String, minutes: Int?, zone: String? = null) =
        AiringNotificationItem(subjectId = id, title = title, airTimeMinutes = minutes, airTimeZone = zone)

    @Test
    fun crossDayConversion_dropsTheTodayWording() {
        // 23:30 中国时间 -> 次日 00:30 日本时间
        val content = AiringNotificationComposer.compose(listOf(item(1, "作品A", 1410, "CN")), date, tokyo)!!
        assertEquals("《作品A》今日更新", content.title)
        assertEquals("放送时间 00:30（次日），记得观看", content.text)
        assertFalse(content.merged)
    }

    @Test
    fun sameDayConversion_keepsTodayWording() {
        // 00:55 中国时间 -> 01:55 日本时间
        val content = AiringNotificationComposer.compose(listOf(item(1, "作品A", 55, "CN")), date, tokyo)!!
        assertEquals("今日 01:55 放送，记得观看", content.text)
    }

    @Test
    fun sameZone_keepsSourceText() {
        val content = AiringNotificationComposer.compose(listOf(item(1, "作品A", 1410, "CN")), date, shanghai)!!
        assertEquals("今日 23:30 放送，记得观看", content.text)
    }

    @Test
    fun missingZone_keepsLegacyText() {
        // 历史数据 / 没有 onair 时刻来源时，行为必须与改造前一致
        val content = AiringNotificationComposer.compose(listOf(item(1, "作品A", 1410, null)), date, tokyo)!!
        assertEquals("今日 23:30 放送，记得观看", content.text)
    }

    @Test
    fun mergedNotification_stillUsesGenericText() {
        val content = AiringNotificationComposer.compose(
            listOf(item(1, "A", 1410, "CN"), item(2, "B", 600, "CN")), date, tokyo,
        )!!
        assertEquals("《A》《B》今日更新", content.title)
        assertEquals("今日放送，记得观看", content.text)
    }
}
