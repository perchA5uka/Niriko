package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.util.AiringStatus
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StatsCalculator.computeCalendarEventsForRange] 的范围裁剪与跨月合并测试。
 *
 * 周视图的 7 天可能横跨两个月（例如 2026-03-29 ~ 2026-04-04），
 * 单月的 computeCalendarEvents 覆盖不到，需要按范围逐月合并再裁剪。
 */
class StatsCalculatorCalendarRangeTest {

    private fun item(
        subjectId: Long,
        startDate: LocalDate? = null,
        finishDate: LocalDate? = null,
    ) = CollectionWithSubject(
        collection = CollectionEntity(
            id = subjectId,
            subjectId = subjectId,
            status = WatchStatus.WATCHING,
            startDate = startDate,
            finishDate = finishDate,
        ),
        subject = SubjectEntity(
            subjectId = subjectId,
            title = "作品$subjectId",
            type = SubjectType.ANIME,
            totalEpisodes = 12,
        ),
    )

    private val historicalSubject = SubjectEntity(
        subjectId = 414461,
        title = "Zom 100",
        type = SubjectType.ANIME,
        platform = "TV",
        airDate = "2023-07-09",
        totalEpisodes = 14,
    )
    private val historicalAiring = AiringSubject(
        historicalSubject, LocalDate.parse("2023-07-09"),
        AiringStatus.getEstimatedEndDate(LocalDate.parse("2023-07-09"), 14),
    )

    // Public Bangumi subject 414461: three delayed main episodes aired together on December 25.
    private fun historicalEpisodes(): List<EpisodeInfo> {
        val dates = listOf(
            "2023-07-09", "2023-07-16", "2023-07-23", "2023-07-30",
            "2023-08-13", "2023-08-27", "2023-09-03", "2023-09-17",
            "2023-09-24", "2023-12-25", "2023-12-25", "2023-12-25",
            "2023-08-06", "2023-09-10",
        )
        return dates.mapIndexed { index, date ->
            EpisodeInfo(
                id = index.toLong(), name = "Episode", nameCn = null, desc = null,
                ep = (index + 1).toDouble(), sort = (index + 1).toDouble(), airdate = date,
                duration = null, type = if (index < 12) 0 else 1,
            )
        }
    }

    private fun historicalEvents(start: String, end: String, episodes: List<EpisodeInfo>) =
        StatsCalculator.computeCalendarEventsForRange(
            items = emptyList(), rangeStart = LocalDate.parse(start), rangeEnd = LocalDate.parse(end),
            mode = CalendarMode.BROADCAST,
            broadcast = mapOf(DayOfWeek.SUNDAY to listOf(historicalAiring)),
            seasonal = mapOf("2023-08" to listOf(historicalAiring), "2023-12" to listOf(historicalAiring)),
            episodesBySubject = mapOf(historicalSubject.subjectId to episodes),
        )

    @Test
    fun actualHistoricalDatesSurviveEstimatedEndAndChangedWeekday() {
        val events = historicalEvents("2023-12-01", "2023-12-31", historicalEpisodes())
        assertEquals(setOf(LocalDate.parse("2023-12-25")), events.keys)
        assertEquals(listOf(414461L), events.getValue(LocalDate.parse("2023-12-25")).broadcastSubjects.map { it.subjectId })
    }

    @Test
    fun actualDatesSuppressHiatusWeeksAndExcludeSpecials() {
        val events = historicalEvents("2023-08-01", "2023-08-31", historicalEpisodes())
        assertEquals(setOf(LocalDate.parse("2023-08-13"), LocalDate.parse("2023-08-27")), events.keys)
    }

    @Test
    fun partialOrMissingDatesRetainEstimatesButDoNotLoseKnownDelayedDates() {
        val partial = historicalEpisodes().take(12)
        val august = historicalEvents("2023-08-01", "2023-08-31", partial)
        assertTrue(august.containsKey(LocalDate.parse("2023-08-06")))
        val december = historicalEvents("2023-12-24", "2023-12-30", partial)
        assertEquals(listOf(414461L), december.getValue(LocalDate.parse("2023-12-25")).broadcastSubjects.map { it.subjectId })
        val missing = historicalEpisodes().mapIndexed { index, ep -> if (index == 1) ep.copy(airdate = null) else ep }
        assertTrue(historicalEvents("2023-08-01", "2023-08-31", missing).containsKey(LocalDate.parse("2023-08-06")))
        val monthOnly = historicalEpisodes().mapIndexed { index, ep -> if (index == 0) ep.copy(airdate = "2023-08") else ep }
        assertFalse(historicalEvents("2023-08-01", "2023-08-31", monthOnly).containsKey(LocalDate.parse("2023-08-01")))
        assertTrue(historicalEvents("2023-12-01", "2023-12-31", emptyList()).isEmpty())
    }

    private fun rangeEvents(
        start: String,
        end: String,
        items: List<CollectionWithSubject>,
    ) = StatsCalculator.computeCalendarEventsForRange(
        items = items,
        rangeStart = LocalDate.parse(start),
        rangeEnd = LocalDate.parse(end),
        mode = CalendarMode.PERSONAL,
        broadcast = emptyMap(),
        seasonal = emptyMap(),
    )

    @Test
    fun `整月范围与单月计算结果完全一致`() {
        val items = listOf(
            item(subjectId = 1, startDate = LocalDate.parse("2026-03-05")),
            item(subjectId = 2, finishDate = LocalDate.parse("2026-03-31")),
            item(subjectId = 3, startDate = LocalDate.parse("2026-02-10")),
        )

        val singleMonth = StatsCalculator.computeCalendarEvents(
            items = items,
            year = 2026,
            month = 3,
            mode = CalendarMode.PERSONAL,
            broadcast = emptyMap(),
            seasonal = emptyMap(),
        )

        assertEquals(singleMonth, rangeEvents("2026-03-01", "2026-03-31", items))
    }

    @Test
    fun `跨月周合并早月与晚月的事件`() {
        val items = listOf(
            item(subjectId = 1, startDate = LocalDate.parse("2026-03-31")),
            item(subjectId = 2, finishDate = LocalDate.parse("2026-04-02")),
        )

        val events = rangeEvents("2026-03-29", "2026-04-04", items)

        assertEquals(
            setOf(LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-02")),
            events.keys,
        )
        assertTrue(events.getValue(LocalDate.parse("2026-03-31")).hasEvents)
        assertTrue(events.getValue(LocalDate.parse("2026-04-02")).hasEvents)
    }

    @Test
    fun `范围外的日期被裁掉`() {
        val items = listOf(
            item(subjectId = 1, startDate = LocalDate.parse("2026-03-05")),
            item(subjectId = 2, startDate = LocalDate.parse("2026-03-31")),
            item(subjectId = 3, startDate = LocalDate.parse("2026-04-20")),
        )

        val events = rangeEvents("2026-03-29", "2026-04-04", items)

        assertEquals(setOf(LocalDate.parse("2026-03-31")), events.keys)
        assertFalse(events.containsKey(LocalDate.parse("2026-03-05")))
        assertFalse(events.containsKey(LocalDate.parse("2026-04-20")))
    }

    @Test
    fun `跨年周合并两个年份的事件`() {
        val items = listOf(
            item(subjectId = 1, startDate = LocalDate.parse("2025-12-30")),
            item(subjectId = 2, startDate = LocalDate.parse("2026-01-02")),
        )

        val events = rangeEvents("2025-12-28", "2026-01-03", items)

        assertEquals(
            setOf(LocalDate.parse("2025-12-30"), LocalDate.parse("2026-01-02")),
            events.keys,
        )
    }

    @Test
    fun `空收藏返回空映射`() {
        assertEquals(emptyMap<LocalDate, CalendarDayEvents>(), rangeEvents("2026-03-01", "2026-03-31", emptyList()))
    }

    @Test
    fun `起始日晚于结束日返回空映射`() {
        val items = listOf(item(subjectId = 1, startDate = LocalDate.parse("2026-03-05")))

        assertEquals(emptyMap<LocalDate, CalendarDayEvents>(), rangeEvents("2026-03-31", "2026-03-01", items))
    }
}
