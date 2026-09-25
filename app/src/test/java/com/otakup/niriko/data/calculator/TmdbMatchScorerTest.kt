package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.remote.rating.sources.TmdbMatchScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbMatchScorerTest {

    @Test
    fun `完全相同标题得满分加上年份与集数加分`() {
        val score = TmdbMatchScorer.score(
            bangumiTitles = listOf("进击的巨人"),
            candidateTitles = listOf("进击的巨人"),
            bangumiYear = 2013,
            candidateYear = 2013,
            bangumiEpisodes = 25,
            candidateEpisodes = 25,
        )
        assertEquals(1f, score, 0.0001f)
    }

    @Test
    fun `完全无关标题得0分`() {
        val score = TmdbMatchScorer.score(
            bangumiTitles = listOf("进击的巨人"),
            candidateTitles = listOf("Clannad"),
        )
        assertEquals(0f, score, 0.0001f)
    }

    @Test
    fun `年份差1只加少量分_仍低于完全同年`() {
        val same = TmdbMatchScorer.score(listOf("Fate"), listOf("Fate"), 2014, 2014)
        val off = TmdbMatchScorer.score(listOf("Fate"), listOf("Fate"), 2014, 2015)
        assertTrue(same > off)
        assertEquals(0.86f, off, 0.001f)
    }

    @Test
    fun `大小写与标点被归一化`() {
        val score = TmdbMatchScorer.score(listOf("Steins;Gate"), listOf("steins gate"))
        assertEquals(0.8f, score, 0.001f)
    }

    @Test
    fun `空标题返回0`() {
        assertEquals(0f, TmdbMatchScorer.score(listOf(""), listOf("x")), 0.0001f)
        assertEquals(0f, TmdbMatchScorer.score(emptyList(), listOf("x")), 0.0001f)
    }

    @Test
    fun `多语言标题取最佳匹配`() {
        val score = TmdbMatchScorer.score(
            bangumiTitles = listOf("进击的巨人", "進撃の巨人"),
            candidateTitles = listOf("Attack on Titan", "進撃の巨人"),
        )
        assertEquals(0.8f, score, 0.001f)
    }
}
