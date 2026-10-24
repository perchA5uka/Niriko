package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import com.otakup.niriko.data.model.stats.Lag
import com.otakup.niriko.data.model.stats.LagQuality
import com.otakup.niriko.data.model.stats.TonightReason
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4.2 / 4.4 今晚看什么：漏看在前、同作品只保留一条、上限 8 条。 */
class StatsTonightCalculatorTest {

    private val today = LocalDate.of(2026, 10, 18)

    private fun subject(id: Long, minutes: Int? = 21 * 60) = SubjectEntity(
        subjectId = id, title = "S" + id, type = SubjectType.ANIME,
        airDate = today.minusDays(7).toString(), totalEpisodes = 12,
        platform = "TV", airTimeMinutes = minutes, ratingScore = 7f,
    )

    private fun cws(id: Long, status: WatchStatus = WatchStatus.WATCHING, watched: Int? = 2) =
        CollectionWithSubject(
            collection = CollectionEntity(subjectId = id, status = status, watchedEpisodes = watched),
            subject = subject(id),
        )

    private fun broadcast(vararg subjects: SubjectEntity): Map<LocalDate, CalendarDayEvents> =
        mapOf(today to CalendarDayEvents(date = today, broadcastSubjects = subjects.toList()))

    @Test
    fun lagging_rows_come_before_today_rows_and_dedupe_the_same_subject() {
        val items = listOf(cws(1), cws(2))
        val lags = listOf(Lag(1, "S1", aired = 8, watched = 2, behind = 6, quality = LagQuality.EXACT))
        // 1 号同时"今天更新"，必须只保留漏看那一条
        val tonight = StatsTonightCalculator.tonight(items, today, broadcast(subject(1), subject(2)), lags)

        assertEquals(2, tonight.size)
        assertEquals(TonightReason.BEHIND, tonight[0].reason)
        assertEquals(1L, tonight[0].subjectId)
        assertEquals(6, tonight[0].behind)
        assertEquals(8, tonight[0].episodeNumber)
        assertEquals(TonightReason.AIRING_TODAY, tonight[1].reason)
        assertEquals(2L, tonight[1].subjectId)
        assertEquals(1, tonight.count { it.subjectId == 1L })
    }

    @Test
    fun estimated_lag_marks_row_as_approximate() {
        val lags = listOf(Lag(1, "S1", aired = 5, watched = 1, behind = 4, quality = LagQuality.ESTIMATED))
        val tonight = StatsTonightCalculator.tonight(listOf(cws(1)), today, emptyMap(), lags)
        assertTrue(tonight.single().estimated)
    }

    @Test
    fun not_watching_items_never_appear() {
        val items = listOf(cws(1, status = WatchStatus.PLAN_TO_WATCH, watched = 0))
        val tonight = StatsTonightCalculator.tonight(items, today, broadcast(subject(1)), emptyList())
        assertTrue(tonight.isEmpty())
    }

    @Test
    fun rows_are_capped_at_max_rows() {
        val items = (1L..12L).map { cws(it) }
        val events = mapOf(
            today to CalendarDayEvents(date = today, broadcastSubjects = (1L..12L).map { subject(it) })
        )
        val tonight = StatsTonightCalculator.tonight(items, today, events, emptyList())
        assertEquals(StatsTonightCalculator.MAX_ROWS, tonight.size)
    }

    @Test
    fun tonight_rows_sorted_by_air_time() {
        val items = listOf(cws(1), cws(2))
        val late = subject(2, minutes = 23 * 60)
        val early = subject(1, minutes = 20 * 60)
        val events = mapOf(today to CalendarDayEvents(date = today, broadcastSubjects = listOf(late, early)))
        val tonight = StatsTonightCalculator.tonight(items, today, events, emptyList())
        // 21:30 的排在 23:00 之前：用户是按时间顺次看的
        assertEquals(listOf(1L, 2L), tonight.map { it.subjectId })
    }
}
