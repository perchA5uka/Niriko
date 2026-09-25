package com.otakup.niriko.data.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 每集评分对齐算法的单测（纯函数，无需 Android）。 */
class EpisodeAlignmentTest {

    private fun bgm(vararg sorts: Double) =
        sorts.mapIndexed { index, sort -> EpisodeAlignment.BangumiEp(index + 100L, sort) }

    private fun tmdb(count: Int, scores: List<Float?> = emptyList()) =
        (1..count).map { number ->
            EpisodeAlignment.TmdbEp(
                episodeNumber = number,
                score = scores.getOrNull(number - 1) ?: 7.0f,
                voteCount = 100,
                stillUrl = null,
            )
        }

    @Test
    fun `集号一致时按集号一一对齐`() {
        val result = EpisodeAlignment.align(bgm(1.0, 2.0, 3.0), tmdb(3))
        assertEquals(3, result.matchedCount)
        assertEquals(0, result.unmatchedBangumi)
        assertEquals(0, result.unmatchedTmdb)
        assertEquals(100L, result.matched.first().epId)
    }

    @Test
    fun `集号有缺口时按顺序补位`() {
        // Bangumi 集号 1,2,4（缺 3）；TMDb 只有 1..3 → 1/2 直接匹配，4↔3 顺位补
        val result = EpisodeAlignment.align(bgm(1.0, 2.0, 4.0), tmdb(3))
        assertEquals(3, result.matchedCount)
        assertEquals(0, result.unmatchedBangumi)
    }

    @Test
    fun `数量差距过大时不顺序补位_避免错配`() {
        // Bangumi 12 集（集号 1,2,3...），TMDb 只有 3 集且集号 101..103 → 只剩顺序补位，
        // 但差距 9 > max(3, 20% * 12 = 2) → 不做补位
        val bangumi = bgm(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0)
        val tmdbEps = (101..103).map { EpisodeAlignment.TmdbEp(it, 7.0f, 10, null) }
        val result = EpisodeAlignment.align(bangumi, tmdbEps)
        assertEquals(0, result.matchedCount)
        assertEquals(12, result.unmatchedBangumi)
        assertEquals(3, result.unmatchedTmdb)
    }

    @Test
    fun `小于容忍度时仍顺序补位`() {
        // 差距 2 <= max(3, 20% * 10 = 2) = 3 → 允许补位
        val bangumi = bgm(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)
        val tmdbEps = (1..8).map { EpisodeAlignment.TmdbEp(it, 7.0f, 10, null) }
        val result = EpisodeAlignment.align(bangumi, tmdbEps)
        assertEquals(8, result.matchedCount)
        assertEquals(2, result.unmatchedBangumi)
        assertEquals(0, result.unmatchedTmdb)
    }

    @Test
    fun `空输入返回全未匹配`() {
        val result = EpisodeAlignment.align(emptyList(), tmdb(3))
        assertEquals(0, result.matchedCount)
        assertEquals(3, result.unmatchedTmdb)
        assertTrue(!result.hasData)
    }

    @Test
    fun `单季作品直接选中该季`() {
        val seasons = listOf(EpisodeAlignment.SeasonRef(1, 2020, 12, "Season 1"))
        assertEquals(1, EpisodeAlignment.pickSeason(seasons, 2020, 12))
    }

    @Test
    fun `多季时按年份与集数最接近挑选`() {
        val seasons = listOf(
            EpisodeAlignment.SeasonRef(1, 2013, 25, "Season 1"),
            EpisodeAlignment.SeasonRef(2, 2017, 12, "Season 2"),
            EpisodeAlignment.SeasonRef(3, 2020, 25, "Season 3"),
        )
        assertEquals(2, EpisodeAlignment.pickSeason(seasons, 2017, 12))
        assertEquals(3, EpisodeAlignment.pickSeason(seasons, 2020, 25))
    }

    @Test
    fun `特别篇季号0被排除`() {
        val seasons = listOf(
            EpisodeAlignment.SeasonRef(0, 2015, 3, "Specials"),
            EpisodeAlignment.SeasonRef(1, 2016, 12, "Season 1"),
        )
        assertEquals(1, EpisodeAlignment.pickSeason(seasons, 2016, 12))
    }
}
