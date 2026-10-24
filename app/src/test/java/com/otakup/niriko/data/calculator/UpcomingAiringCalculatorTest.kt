package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4.3 接下来七天：固定 7 列、空列保留、封面/在看标记、精确 vs 推算。 */
class UpcomingAiringCalculatorTest {

    private val today = LocalDate.of(2026, 10, 18)

    private fun subject(
        id: Long,
        airMinutes: Int? = 21 * 60 + 30,
        cover: String? = "https://example.invalid/" + id + ".jpg",
    ) = SubjectEntity(
        subjectId = id, title = "S" + id, type = SubjectType.ANIME,
        airDate = today.minusDays(7).toString(), totalEpisodes = 12,
        platform = "TV", airTimeMinutes = airMinutes, coverUrl = cover,
    )

    private fun cws(id: Long) = CollectionWithSubject(
        collection = CollectionEntity(subjectId = id, status = WatchStatus.WATCHING, watchedEpisodes = 1),
        subject = subject(id),
    )

    private fun events(vararg pairs: Pair<LocalDate, List<SubjectEntity>>): Map<LocalDate, CalendarDayEvents> =
        pairs.associate { (date, subjects) ->
            date to CalendarDayEvents(date = date, broadcastSubjects = subjects)
        }

    @Test
    fun always_returns_seven_columns_even_when_empty() {
        val days = UpcomingAiringCalculator.days(emptyMap(), emptyList(), today, 7)
        assertEquals(7, days.size)
        assertEquals(today, days.first().date)
        assertEquals(today.plusDays(6), days.last().date)
        assertTrue(days.all { it.count == 0 })
        assertNull(days.first().firstCoverUrl)
    }

    @Test
    fun entries_are_sorted_by_air_time() {
        val late = SubjectEntity(
            subjectId = 2, title = "晚", type = SubjectType.ANIME,
            airTimeMinutes = 23 * 60, airDate = today.minusDays(7).toString(), totalEpisodes = 12,
        )
        val early = SubjectEntity(
            subjectId = 1, title = "早", type = SubjectType.ANIME,
            airTimeMinutes = 21 * 60, airDate = today.minusDays(7).toString(), totalEpisodes = 12,
        )
        val days = UpcomingAiringCalculator.days(
            events(today to listOf(late, early)), listOf(cws(1)), today, 7,
        )
        assertEquals(listOf(1L, 2L), days.first().entries.map { it.subjectId })
        assertEquals(1, days.first().inCollectionCount)
        assertTrue(days.first().entries[0].inCollection)
        assertEquals(false, days.first().entries[1].inCollection)
    }

    @Test
    fun entry_without_episode_dates_is_marked_estimated() {
        val days = UpcomingAiringCalculator.days(
            events(today to listOf(subject(1))), emptyList(), today, 7,
        )
        assertTrue(days.first().entries.single().estimated)

        val withDates = mapOf(
            1L to listOf(
                EpisodeInfo(
                    id = 1, name = "ep", nameCn = null, desc = null, ep = 1.0, sort = 1.0,
                    airdate = today.toString(), duration = null, durationSeconds = 1440, type = 0,
                )
            )
        )
        val exact = UpcomingAiringCalculator.days(
            events(today to listOf(subject(1))), emptyList(), today, 7, withDates,
        )
        assertEquals(false, exact.first().entries.single().estimated)
    }

    @Test
    fun events_wrapper_uses_weekly_broadcast_map() {
        val weekly = subject(1, airMinutes = 22 * 60)
        val broadcast = mapOf(
            today.dayOfWeek to listOf(
                com.otakup.niriko.data.model.stats.AiringSubject(
                    subject = weekly,
                    airDate = today.minusDays(7),
                    estimatedEndDate = today.plusDays(60),
                )
            )
        )
        val result = UpcomingAiringCalculator.events(
            items = emptyList(), today = today, days = 7,
            broadcast = broadcast, seasonal = emptyMap(), episodesBySubject = emptyMap(),
        )
        assertEquals(1, result[today]?.broadcastSubjects?.size ?: 0)
    }
}
