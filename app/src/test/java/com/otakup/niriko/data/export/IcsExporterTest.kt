package com.otakup.niriko.data.export

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4.8 放送日历导出的纯函数测试：UID 幂等键、RRULE 只用 COUNT、75 octets 折行、转义、DTSTART/SEQUENCE。
 */
class IcsExporterTest {

    private fun subject(
        id: Long = 100,
        total: Int? = 12,
        airDate: String? = "2026-10-02",
        minutes: Int? = 23 * 60 + 30,
    ) = SubjectEntity(
        subjectId = id,
        title = "测试作品",
        titleCN = "测试作品",
        type = SubjectType.ANIME,
        totalEpisodes = total,
        airDate = airDate,
        airTimeMinutes = minutes,
    )

    @Test
    fun uidIsStableAndDistinguishesEpisodeFromSeason() {
        assertEquals("niriko-100-ep3@niriko.local", IcsExporter.uid(100, 3))
        assertEquals("niriko-100@niriko.local", IcsExporter.uid(100, null))
        // 同一输入永远同一 UID：重复导入时日历端更新而不是新增
        assertEquals(IcsExporter.uid(100, 3), IcsExporter.uid(100, 3))
    }

    @Test
    fun estimatedRruleUsesCountOnlyAndNeverUntil() {
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=1", IcsExporter.weeklyCountRrule(0))
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=12", IcsExporter.weeklyCountRrule(12))
        assertEquals("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=200", IcsExporter.weeklyCountRrule(300))
        assertFalse(IcsExporter.weeklyCountRrule(12).contains("UNTIL"))
    }

    @Test
    fun textEscapingFollowsRfc5545() {
        assertEquals("a\\\\b", IcsExporter.escapeText("a\\b"))
        assertEquals("a\\;b", IcsExporter.escapeText("a;b"))
        assertEquals("a\\,b", IcsExporter.escapeText("a,b"))
        assertEquals("a\\nb", IcsExporter.escapeText("a\nb"))
    }

    @Test
    fun longLinesFoldAt75OctetsWithoutSplittingCharacters() {
        val title = "《这是一个非常非常长的中文标题用来验证折行不会把多字节字符劈开》".repeat(3)
        val folded = IcsExporter.foldLine("SUMMARY:$title")
        assertTrue("应发生折行", folded.contains("\r\n "))
        folded.split("\r\n").forEach { line ->
            assertTrue("单行超过 75 octets: $line", line.toByteArray(Charsets.UTF_8).size <= 75)
        }
        // 去掉折行标记后应与原文一致（没有丢字符、没有劈开代理对）
        assertEquals("SUMMARY:$title", folded.replace("\r\n ", ""))
    }

    @Test
    fun buildWritesCalendarWithInlineTimezoneAndCrlf() {
        val body = IcsExporter.build(
            events = listOf(
                IcsAirEvent(
                    subjectId = 100,
                    title = "测试作品",
                    date = LocalDate.of(2026, 10, 2),
                    minutesOfDay = 23 * 60 + 30,
                    episode = 1,
                    sequence = 1_760_000_000,
                ),
            ),
            stamp = Instant.parse("2026-10-18T01:00:00Z"),
        )
        assertTrue(body.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(body.endsWith("END:VCALENDAR\r\n"))
        assertTrue(body.contains("VERSION:2.0"))
        assertTrue(body.contains("PRODID:-//Niriko//Airing Calendar//CN"))
        // 内联 VTIMEZONE（Outlook 必需）
        assertTrue(body.contains("BEGIN:VTIMEZONE"))
        assertTrue(body.contains("TZID:Asia/Shanghai"))
        assertTrue(body.contains("TZOFFSETFROM:+0800"))
        assertTrue(body.contains("END:VTIMEZONE"))
        // DTSTART：中国时区的放送时刻
        assertTrue(body.contains("DTSTART;TZID=Asia/Shanghai:20261002T233000"))
        assertTrue(body.contains("DURATION:PT24M"))
        assertTrue(body.contains("TRANSP:TRANSPARENT"))
        assertTrue(body.contains("UID:niriko-100-ep1@niriko.local"))
        assertTrue(body.contains("SEQUENCE:1760000000"))
        assertTrue(body.contains("URL:https://bgm.tv/subject/100"))
        assertTrue(body.contains("DTSTAMP:20261018T010000Z"))
        assertFalse("逐集事件不应带 RRULE", body.contains("RRULE"))
        // 全部换行都是 CRLF（没有裸 LF）
        assertFalse(body.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun estimatedEventCarriesRruleAndSuffix() {
        val body = IcsExporter.build(
            events = listOf(
                IcsAirEvent(
                    subjectId = 7,
                    title = "估算作品",
                    date = LocalDate.of(2026, 10, 9),
                    minutesOfDay = 22 * 60,
                    episode = null,
                    episodeCount = 3,
                    estimated = true,
                ),
            ),
            stamp = Instant.parse("2026-10-18T01:00:00Z"),
        )
        assertTrue(body.contains("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=3"))
        assertTrue(body.contains("UID:niriko-7@niriko.local"))
        assertTrue(body.contains("（预估）"))
    }

    @Test
    fun completeEpisodesProducePerEpisodeEvents() {
        val first = LocalDate.of(2026, 10, 2)
        val dates = (0L..2L).map { first.plusWeeks(it) }
        val events = buildIcsSubjectEvents(
            subject = subject(total = 3),
            episodeDates = dates,
            from = LocalDate.of(2026, 10, 1),
            to = LocalDate.of(2026, 10, 31),
            sequence = 42,
        )
        assertEquals(3, events.size)
        assertEquals(listOf(1, 2, 3), events.map { it.episode })
        assertTrue(events.none { it.estimated })
        assertTrue(events.none { it.delayed })
        assertEquals(42, events.first().sequence)
        assertTrue(events.all { it.minutesOfDay == 23 * 60 + 30 })
    }

    @Test
    fun delayedEpisodeIsFlaggedAgainstWeeklyCadence() {
        val first = LocalDate.of(2026, 10, 2)
        // 第 2 集原定 10/09，实际 10/16（晚一周）
        val dates = listOf(first, LocalDate.of(2026, 10, 16))
        val events = buildIcsSubjectEvents(
            subject = subject(total = 2),
            episodeDates = dates,
            from = LocalDate.of(2026, 10, 1),
            to = LocalDate.of(2026, 10, 31),
            sequence = 0,
        )
        assertEquals(2, events.size)
        assertFalse(events[0].delayed)
        assertTrue(events[1].delayed)
    }

    @Test
    fun incompleteEpisodesFallBackToSingleEstimatedEvent() {
        val first = LocalDate.of(2026, 10, 2)
        val events = buildIcsSubjectEvents(
            subject = subject(total = 12),
            episodeDates = listOf(first),
            from = LocalDate.of(2026, 10, 1),
            to = LocalDate.of(2026, 10, 31),
            sequence = 0,
        )
        assertEquals(1, events.size)
        val event = events.single()
        assertTrue(event.estimated)
        assertNull(event.episode)
        assertEquals(12, event.episodeCount)
        assertEquals(first, event.date)
    }

    @Test
    fun missingAirTimeOrAirDateExportsNothing() {
        assertTrue(
            buildIcsSubjectEvents(subject(minutes = null), emptyList(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0).isEmpty(),
        )
        assertTrue(
            buildIcsSubjectEvents(subject(airDate = null), emptyList(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 0).isEmpty(),
        )
    }

    @Test
    fun seasonWindowOnlyAcceptsDatesInsideTheRequestedRange() {
        val dates = listOf(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 2))
        val events = buildIcsSubjectEvents(
            subject = subject(total = 2),
            episodeDates = dates,
            from = LocalDate.of(2026, 10, 1),
            to = LocalDate.of(2026, 10, 31),
            sequence = 0,
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 2)), events.map { it.date })
    }
}
