package com.otakup.niriko.data.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeTitleBackfillTest {

    private fun aligned(epId: Long, number: Int, name: String?) = EpisodeAlignment.Aligned(
        epId = epId,
        tmdb = EpisodeAlignment.TmdbEp(
            episodeNumber = number,
            score = null,
            voteCount = null,
            stillUrl = null,
            name = name,
        ),
    )

    @Test
    fun `只补空缺，不覆盖已有标题`() {
        val plan = EpisodeTitleBackfill.plan(
            matched = listOf(aligned(1L, 1, "TMDb 第一集"), aligned(2L, 2, "TMDb 第二集")),
            currentTitles = mapOf(1L to "Bangumi 原始标题", 2L to ""),
        )
        assertEquals(1, plan.size)
        assertEquals(EpisodeTitleBackfill.Fill(2L, "TMDb 第二集"), plan.first())
    }

    @Test
    fun `空白标题（空格）视为缺失`() {
        val plan = EpisodeTitleBackfill.plan(
            matched = listOf(aligned(7L, 1, "补上")),
            currentTitles = mapOf(7L to "   "),
        )
        assertEquals(listOf(EpisodeTitleBackfill.Fill(7L, "补上")), plan)
    }

    @Test
    fun `TMDb 无标题时不产出计划`() {
        val plan = EpisodeTitleBackfill.plan(
            matched = listOf(aligned(1L, 1, null), aligned(2L, 2, "  ")),
            currentTitles = emptyMap(),
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `库里没有记录也算缺失`() {
        val plan = EpisodeTitleBackfill.plan(
            matched = listOf(aligned(9L, 3, "第三集")),
            currentTitles = mapOf(1L to "别的集"),
        )
        assertEquals(listOf(EpisodeTitleBackfill.Fill(9L, "第三集")), plan)
    }

    @Test
    fun `同一 epId 重复出现时只取第一个`() {
        val plan = EpisodeTitleBackfill.plan(
            matched = listOf(aligned(5L, 1, "先到的"), aligned(5L, 1, "后到的")),
            currentTitles = emptyMap(),
        )
        assertEquals(listOf(EpisodeTitleBackfill.Fill(5L, "先到的")), plan)
    }

    @Test
    fun `标题两端空白会被裁掉`() {
        val plan = EpisodeTitleBackfill.plan(
            matched = listOf(aligned(1L, 1, "  带空格  ")),
            currentTitles = emptyMap(),
        )
        assertEquals(listOf(EpisodeTitleBackfill.Fill(1L, "带空格")), plan)
    }
}
