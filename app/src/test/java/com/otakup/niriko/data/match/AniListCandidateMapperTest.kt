package com.otakup.niriko.data.match

import com.otakup.niriko.data.remote.game.GameItem
import com.otakup.niriko.data.remote.steam.SteamTitleMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AniListCandidateMapper 单测。
 *
 * 回归的是用户反馈的「AniList 单条目 UI 无论匹配度多少都展示 0%」：
 * 匹配度必须在映射时算出来，而不是落回 MatchCandidate.confidence 的默认 0f。
 */
class AniListCandidateMapperTest {

    private fun item(
        id: String = "media-21",
        title: String = "ONE PIECE",
        aliases: String? = "航海王 / 海贼王 / One Piece",
        rating: Float? = 85f,
    ) = GameItem(
        sourceGameId = id,
        title = title,
        aliases = aliases,
        coverUrl = "https://img.example/cover.jpg",
        ratingScore = rating,
    )

    @Test
    fun `中文标题命中时候选带出真实匹配度而不是 0`() {
        val candidate = AniListCandidateMapper.map(item(), listOf("航海王", "One Piece"))
        assertTrue(
            "匹配度应达到可展示/可比对水平，实际 " + candidate.confidence,
            candidate.confidence >= SteamTitleMatcher.MIN_CONFIDENCE,
        )
        assertTrue("应给出匹配理由", candidate.reasons.isNotEmpty())
    }

    @Test
    fun `完全无关的标题匹配度低于自动绑定阈值且不算命中`() {
        val candidate = AniListCandidateMapper.map(
            item(id = "media-999", title = "Steakhouse Prologue", aliases = null, rating = null),
            listOf("灌篮高手"),
        )
        assertTrue(
            "无关标题不应达到阈值，实际 " + candidate.confidence,
            candidate.confidence < SteamTitleMatcher.MIN_CONFIDENCE,
        )
        assertTrue("无关标题不应声称「标题完全一致」", candidate.reasons.none { it.contains("完全一致") })
    }

    @Test
    fun `指定 id 时置信度按用户意图置 1`() {
        val candidate = AniListCandidateMapper.map(
            item = item(title = "有些出入的标题"),
            queryTitles = listOf("航海王"),
            query = "https://anilist.co/anime/21",
            confidenceOverride = 1f,
        )
        assertEquals(1f, candidate.confidence, 0.0001f)
        assertTrue("应记录搜索词", candidate.reasons.any { it.contains("anime/21") })
    }

    @Test
    fun `展示字段沿用候选自身的 id 标题封面与副标题`() {
        val candidate = AniListCandidateMapper.map(item(), listOf("航海王"))
        assertEquals(AniListCandidateMapper.PROVIDER, candidate.provider)
        assertEquals("media-21", candidate.externalId)
        assertEquals("ONE PIECE", candidate.title)
        assertEquals("https://img.example/cover.jpg", candidate.imageUrl)
        assertEquals("航海王 / 海贼王 / One Piece", candidate.subtitle)
    }

    @Test
    fun `无别名时副标题回落到评分`() {
        val candidate = AniListCandidateMapper.map(item(aliases = null), listOf("ONE PIECE"))
        assertEquals("85.0 分", candidate.subtitle)
    }

    @Test
    fun `查询标题去空去重且保留主标题在前`() {
        assertEquals(listOf("航海王", "One Piece"), AniListCandidateMapper.queryTitles(" 航海王 ", "One Piece"))
        assertEquals(listOf("航海王"), AniListCandidateMapper.queryTitles("航海王", "航海王"))
        assertEquals(listOf("航海王"), AniListCandidateMapper.queryTitles("航海王", null))
        assertEquals(emptyList<String>(), AniListCandidateMapper.queryTitles(null, "   "))
    }

    @Test
    fun `mapAll 保持输入顺序并逐个计算`() {
        val list = AniListCandidateMapper.mapAll(
            items = listOf(item(id = "media-1", title = "航海王", aliases = null), item(id = "media-2")),
            queryTitles = listOf("航海王"),
        )
        assertEquals(listOf("media-1", "media-2"), list.map { it.externalId })
        assertTrue(list.first().confidence >= SteamTitleMatcher.MIN_CONFIDENCE)
        assertFalse(list.any { it.provider != AniListCandidateMapper.PROVIDER })
    }
}
