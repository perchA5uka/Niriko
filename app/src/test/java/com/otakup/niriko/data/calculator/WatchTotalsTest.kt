package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A-D1：观看量分流。集数 / 视频时长 / 游戏分钟 / 阅读单位 / 曲目数各自独立，
 * 不再是"一个 sumOf 什么类型都加"。
 */
class WatchTotalsTest {

    private fun subject(id: Long, type: SubjectType, totalEpisodes: Int? = null) =
        SubjectEntity(subjectId = id, title = "S$id", type = type, totalEpisodes = totalEpisodes)

    private fun collection(subjectId: Long, episodes: Int? = null, volumes: Int? = null) =
        CollectionEntity(
            subjectId = subjectId,
            status = WatchStatus.WATCHING,
            watchedEpisodes = episodes,
            watchedVolumes = volumes,
        )

    private fun cws(subject: SubjectEntity, collection: CollectionEntity) =
        CollectionWithSubject(collection = collection, subject = subject)

    private fun episode(durationSeconds: Int, type: Int = 0) = EpisodeInfo(
        id = 1, name = "ep", nameCn = null, desc = null, ep = 1.0, sort = 1.0,
        airdate = null, duration = null, durationSeconds = durationSeconds, type = type,
    )

    @Test
    fun anime_and_game_split_into_different_buckets() {
        val items = listOf(
            cws(subject(1, SubjectType.ANIME), collection(1, episodes = 12)),
            cws(subject(2, SubjectType.GAME), collection(2, episodes = 600)),
        )
        val totals = computeWatchTotals(items)
        assertEquals(12, totals.videoEpisodes)
        assertEquals(600, totals.gameMinutes)
        // 动画没有集数据 -> 按类型默认 24 分钟估算
        assertEquals(12 * 24, totals.minutes)
        assertEquals(1, totals.estimatedMinutesSubjects)
    }

    @Test
    fun exact_duration_from_episode_data() {
        val items = listOf(cws(subject(1, SubjectType.ANIME), collection(1, episodes = 12)))
        val totals = computeWatchTotals(items, mapOf(1L to listOf(episode(1500))))
        assertEquals(12 * 25, totals.minutes)
        assertEquals(0, totals.estimatedMinutesSubjects)
    }

    @Test
    fun book_uses_volumes_not_episodes() {
        val items = listOf(cws(subject(1, SubjectType.BOOK), collection(1, episodes = 999, volumes = 3)))
        val totals = computeWatchTotals(items)
        assertEquals(3, totals.readUnits)
        assertEquals(0, totals.videoEpisodes)
        assertEquals(0, totals.minutes)
    }

    @Test
    fun music_tracks_never_count_as_episodes() {
        val items = listOf(cws(subject(1, SubjectType.MUSIC), collection(1, episodes = 9)))
        val totals = computeWatchTotals(items)
        assertEquals(9, totals.trackCount)
        assertEquals(0, totals.videoEpisodes)
        assertEquals(0, totals.minutes)
    }

    @Test
    fun empty_library_is_all_zero() {
        val totals = computeWatchTotals(emptyList())
        assertEquals(WatchTotals(), totals)
    }

    @Test
    fun anime_without_total_episodes_still_uses_real_duration() {
        val items = listOf(cws(subject(1, SubjectType.ANIME, totalEpisodes = null), collection(1, episodes = 3)))
        val totals = computeWatchTotals(items, mapOf(1L to listOf(episode(1440))))
        assertEquals(3, totals.videoEpisodes)
        assertEquals(3 * 24, totals.minutes)
        assertEquals(0, totals.estimatedMinutesSubjects)
    }

    @Test
    fun minutes_per_episode_falls_back_to_type_default() {
        assertEquals(24, minutesPerEpisode(subject(1, SubjectType.ANIME), emptyList()).minutes)
        assertEquals(MinutesSource.TYPE_DEFAULT, minutesPerEpisode(subject(1, SubjectType.ANIME), emptyList()).source)
        assertEquals(45, minutesPerEpisode(subject(1, SubjectType.REAL), emptyList()).minutes)
        assertEquals(0, minutesPerEpisode(subject(1, SubjectType.BOOK), emptyList()).minutes)
    }

    @Test
    fun stats_does_not_mix_game_minutes_into_episode_count() {
        val items = listOf(
            cws(subject(1, SubjectType.ANIME), collection(1, episodes = 12)),
            cws(subject(2, SubjectType.GAME), collection(2, episodes = 3000)),
        )
        val state = StatsCalculator.computeStats(items)
        assertEquals(12, state.totalWatchedEpisodes)
        assertEquals(3000, state.gameMinutes)
        assertEquals(12 * 24, state.watchMinutes)
        // 年表同样不能把 3000 分钟算成 3000 集
        assertEquals(12, state.yearlyStats.sumOf { it.totalEpisodes })
    }
}
