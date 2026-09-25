package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.local.entity.SteamBindingEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.steam.dto.SteamOwnedGameDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SteamLibraryMatcher 单元测试（依赖注入，不触网络/数据库）。
 */
class SteamLibraryMatcherTest {

    private fun game(
        appId: Int,
        name: String,
        playtime: Int = 0,
        icon: String? = null,
    ) = SteamOwnedGameDto(
        appid = appId,
        name = name,
        imgIconUrl = icon,
        playtimeForever = playtime,
    )

    private fun bgmSubject(id: Long, title: String, titleCN: String? = null) = SubjectEntity(
        subjectId = id,
        title = title,
        titleCN = titleCN,
        type = SubjectType.GAME,
    )

    // ==================== 绑定表优先 ====================

    @Test
    fun toPreview_boundFirst_usesBindingSubjectId() = kotlinx.coroutines.runBlocking {
        val matcher = SteamLibraryMatcher(
            steamDao = fakeSteamDao(binding = SteamBindingEntity(
                subjectId = 12345L,
                steamAppId = 570,
                matchMethod = "AUTO",
                confidence = 1f,
                createTime = 0L,
            )),
            inCollection = { it == 12345L },
            subjectTitle = { if (it == 12345L) "Dota 2" else null },
        )
        val preview = matcher.toPreview(game(570, "Dota 2", playtime = 120))
        assertTrue(preview.isMatched)
        assertEquals(12345L, preview.bgmSubjectId)
        assertFalse(preview.isPlaceholder)
        assertTrue(preview.alreadyInCollection)
        assertEquals("Dota 2", preview.localSubjectTitle)
        assertTrue(preview.selected)
        assertEquals(120, preview.playtimeForeverMinutes)
    }

    // ==================== 标题兜底 ====================

    @Test
    fun toPreview_titleFallback_matchesBangumi() = kotlinx.coroutines.runBlocking {
        val matcher = SteamLibraryMatcher(
            searchBangumiGame = { title ->
                if (title.contains("黑神话")) listOf(bgmSubject(9999, "黑神话：悟空", "黑神话：悟空"))
                else emptyList()
            },
        )
        val preview = matcher.toPreview(game(2358720, "黑神话：悟空"))
        assertTrue(preview.isMatched)
        assertEquals(9999L, preview.bgmSubjectId)
        assertFalse(preview.isPlaceholder)
    }

    // ==================== 占位判定 ====================

    @Test
    fun toPreview_unmatched_isPlaceholder() = kotlinx.coroutines.runBlocking {
        val matcher = SteamLibraryMatcher(
            searchBangumiGame = { emptyList() }, // Bangumi 无词条
        )
        val preview = matcher.toPreview(game(123456, "某个没有 Bangumi 词条的游戏"))
        assertNull(preview.bgmSubjectId)
        assertTrue(preview.isPlaceholder)
        assertFalse(preview.selected) // 占位默认不勾选
        // 占位 id 映射：sourceKey 体系下为正数（appId）
        assertEquals(123456L, preview.placeholderSubjectId)
    }

    @Test
    fun toPreview_bindingsHasPlaceholderSubjectId() = kotlinx.coroutines.runBlocking {
        // 已创建过占位条目（subjectId=appId, sourceKey="steam:{appId}"）再次拉库 → 识别为占位并标记已收藏
        val matcher = SteamLibraryMatcher(
            steamDao = fakeSteamDao(binding = SteamBindingEntity(
                subjectId = 123456L,
                steamAppId = 123456,
                matchMethod = "PLACEHOLDER",
                confidence = 1f,
                createTime = 0L,
            )),
            inCollection = { it == 123456L },
        )
        val preview = matcher.toPreview(game(123456, "某个游戏"))
        assertTrue(preview.isPlaceholder)
        assertEquals(123456L, preview.placeholderSubjectId)
        assertTrue(preview.alreadyInCollection)
        assertFalse(preview.selected) // 占位默认不勾选
    }

    // ==================== 封面 URL ====================

    @Test
    fun toPreview_coverUrl_buildsCdnPath() = kotlinx.coroutines.runBlocking {
        val matcher = SteamLibraryMatcher()
        val preview = matcher.toPreview(game(570, "Dota 2", icon = "abcdef123"))
        assertEquals(
            "https://media.steampowered.com/steamcommunity/public/images/apps/570/abcdef123.jpg",
            preview.coverUrl,
        )
    }

    // ==================== 批量 ====================

    @Test
    fun toPreviews_keepsOrderAndFiltersEmpty() = kotlinx.coroutines.runBlocking {
        val matcher = SteamLibraryMatcher(
            searchBangumiGame = { emptyList() },
        )
        val previews = matcher.toPreviews(
            listOf(
                game(1, "A 游戏"),
                game(2, "B 游戏"),
            ),
        )
        assertEquals(2, previews.size)
        assertEquals(1, previews[0].appId)
        assertEquals(2, previews[1].appId)
        assertTrue(previews.all { it.isPlaceholder })

        assertEquals(0, matcher.toPreviews(emptyList()).size)
    }

    private fun fakeSteamDao(binding: SteamBindingEntity) = object : com.otakup.niriko.data.local.dao.SteamDao {
        override suspend fun getBindingBySubjectId(subjectId: Long) = null
        override suspend fun getBindingsBySubjectIds(subjectIds: List<Long>) = emptyList<SteamBindingEntity>()
        override suspend fun getBindingByAppId(appId: Int) = binding
        override suspend fun getBindingsByAppIds(appIds: List<Int>) = listOf(binding).filter { it.steamAppId in appIds }
        override suspend fun insertBinding(binding: SteamBindingEntity) = 0L
        override suspend fun updateBinding(binding: SteamBindingEntity) = 0
        override suspend fun upsertBinding(binding: SteamBindingEntity) {}
        override suspend fun upsertBindings(bindings: List<SteamBindingEntity>) {}
        override suspend fun getGame(subjectId: Long) = null
        override suspend fun getGamesBySubjectIds(subjectIds: List<Long>) = emptyList<com.otakup.niriko.data.local.entity.SteamGameEntity>()
        override suspend fun insertGame(game: com.otakup.niriko.data.local.entity.SteamGameEntity) = 0L
        override suspend fun updateGame(game: com.otakup.niriko.data.local.entity.SteamGameEntity) = 0
        override suspend fun upsertGame(game: com.otakup.niriko.data.local.entity.SteamGameEntity) {}
        override suspend fun upsertGames(games: List<com.otakup.niriko.data.local.entity.SteamGameEntity>) {}
        override suspend fun migrateBindingSubjectId(oldSubjectId: Long, newSubjectId: Long) = 0
        override suspend fun deleteBindingBySubjectId(subjectId: Long) = 0
        override suspend fun migrateGameSubjectId(oldSubjectId: Long, newSubjectId: Long) = 0
        override suspend fun deleteGameBySubjectId(subjectId: Long) = 0
    }
}
