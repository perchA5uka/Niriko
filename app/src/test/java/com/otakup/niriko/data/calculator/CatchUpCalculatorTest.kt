package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.LagQuality
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 4.5 漏看判定：门槛（在看 / 未完结 / 仍在放送）与「精确 vs 推算」两条路。 */
class CatchUpCalculatorTest {

    private val today = LocalDate.of(2026, 10, 18)

    private fun subject(
        id: Long = 1,
        airDate: String? = today.minusDays(20).toString(),
        totalEpisodes: Int? = 12,
    ) = SubjectEntity(
        subjectId = id, title = "S" + id, type = SubjectType.ANIME,
        airDate = airDate, totalEpisodes = totalEpisodes, platform = "TV",
    )

    private fun cws(
        subject: SubjectEntity,
        status: WatchStatus = WatchStatus.WATCHING,
        watched: Int? = 1,
        finishDate: LocalDate? = null,
    ) = CollectionWithSubject(
        collection = CollectionEntity(
            subjectId = subject.subjectId, status = status,
            watchedEpisodes = watched, finishDate = finishDate,
        ),
        subject = subject,
    )

    private fun ep(sort: Double, airdate: String?) = EpisodeInfo(
        id = sort.toLong(), name = "ep", nameCn = null, desc = null,
        ep = sort, sort = sort, airdate = airdate, duration = null,
        durationSeconds = 1440, type = 0,
    )

    @Test
    fun exact_episode_dates_produce_exact_lag() {
        val episodes = listOf(
            ep(1.0, today.minusDays(21).toString()),
            ep(2.0, today.minusDays(14).toString()),
            ep(3.0, today.minusDays(7).toString()),
            ep(4.0, today.toString()),
        )
        val lag = CatchUpCalculator.lagOf(cws(subject(), watched = 1), episodes, today)!!
        assertEquals(4, lag.aired)
        assertEquals(1, lag.watched)
        assertEquals(3, lag.behind)
        assertEquals(LagQuality.EXACT, lag.quality)
    }

    @Test
    fun no_episode_data_falls_back_to_first_airing_estimates() {
        // 开播 14 天 => 第 1 周 + 第 3 周 = 3 集
        val lag = CatchUpCalculator.lagOf(cws(subject(), watched = 0), emptyList(), today)!!
        assertEquals(3, lag.aired)
        assertEquals(3, lag.behind)
        assertEquals(LagQuality.ESTIMATED, lag.quality)
    }

    @Test
    fun estimated_airing_count_never_exceeds_declared_total() {
        val old = subject(airDate = today.minusDays(300).toString(), totalEpisodes = 12)
        val aired = CatchUpCalculator.estimatedAired(old, today)!!
        assertEquals(12, aired)
        assertTrue(aired <= 12)
    }

    @Test
    fun not_watching_is_ignored() {
        val item = cws(subject(), status = WatchStatus.PLAN_TO_WATCH, watched = 0)
        assertNull(CatchUpCalculator.lagOf(item, emptyList(), today))
    }

    @Test
    fun finished_collection_is_ignored() {
        val item = cws(subject(), watched = 0, finishDate = today.minusDays(1))
        assertNull(CatchUpCalculator.lagOf(item, emptyList(), today))
    }

    @Test
    fun already_completed_subject_is_not_a_lagging_airing_show() {
        // 两年前开播、12 集早就播完 => 补番不是漏看
        val item = cws(subject(airDate = today.minusYears(2).toString()), watched = 0)
        assertNull(CatchUpCalculator.lagOf(item, emptyList(), today))
    }

    @Test
    fun caught_up_item_is_ignored() {
        val episodes = listOf(ep(1.0, today.minusDays(7).toString()), ep(2.0, today.toString()))
        val item = cws(subject(), watched = 2)
        assertNull(CatchUpCalculator.lagOf(item, episodes, today))
    }

    @Test
    fun min_behind_threshold_filters_small_gaps() {
        val episodes = listOf(ep(1.0, today.minusDays(7).toString()), ep(2.0, today.toString()))
        val item = cws(subject(), watched = 1)
        // 落后 1 集：minBehind = 2 时不提示
        assertNull(CatchUpCalculator.lagOf(item, episodes, today, minBehind = 2))
        assertEquals(1, CatchUpCalculator.lagOf(item, episodes, today)!!.behind)
    }

    @Test
    fun lagging_sorts_by_behind_descending() {
        val items = listOf(
            cws(subject(1, airDate = today.minusDays(7).toString()), watched = 0),
            cws(subject(2, airDate = today.minusDays(35).toString()), watched = 0),
        )
        val lags = CatchUpCalculator.lagging(items, emptyMap(), today)
        assertEquals(2, lags.size)
        assertEquals(2L, lags[0].subjectId)
        assertTrue(lags[0].behind > lags[1].behind)
    }

    @Test
    fun severe_lags_only_keep_three_or_more_behind() {
        val lags = listOf(
            com.otakup.niriko.data.model.stats.Lag(1, "A", aired = 5, watched = 4, behind = 1, quality = LagQuality.EXACT),
            com.otakup.niriko.data.model.stats.Lag(2, "B", aired = 6, watched = 3, behind = 3, quality = LagQuality.EXACT),
            com.otakup.niriko.data.model.stats.Lag(3, "C", aired = 9, watched = 2, behind = 7, quality = LagQuality.ESTIMATED),
        )
        assertEquals(listOf(2L, 3L), CatchUpCalculator.severeLags(lags).map { it.subjectId })
        assertEquals(3, CatchUpCalculator.severeLags(lags, threshold = 1).size)
    }
}
