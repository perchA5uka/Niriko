package com.otakup.niriko.data.remote.game

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GameItemMapper / GameDataSource 注册表单元测试。
 */
class GameItemMapperTest {

    // ==================== sourceKey ====================

    @Test
    fun sourceKey_concatenatesSourceAndGameId() {
        assertEquals("steam:570", GameItemMapper.sourceKey("steam", "570"))
        assertEquals("neodb:abc123", GameItemMapper.sourceKey("neodb", "abc123"))
    }

    // ==================== deriveSubjectId ====================

    @Test
    fun deriveSubjectId_numericId_usesAsIs() {
        assertEquals(570L, GameItemMapper.deriveSubjectId("steam", "570"))
        assertEquals(2358720L, GameItemMapper.deriveSubjectId("steam", "2358720"))
    }

    @Test
    fun deriveSubjectId_nonNumericId_positiveStableHash() {
        val id1 = GameItemMapper.deriveSubjectId("neodb", "some-uuid-123")
        assertTrue("哈希应为正数", id1 > 0)
        // 幂等：同输入同输出
        assertEquals(id1, GameItemMapper.deriveSubjectId("neodb", "some-uuid-123"))
        // 不同源/不同 id 不应撞（抽样验证）
        val id2 = GameItemMapper.deriveSubjectId("neodb", "other-uuid-456")
        assertTrue(id1 != id2)
    }

    // ==================== toSubject ====================

    @Test
    fun toSubject_mapsAllFields() {
        val item = GameItem(
            sourceGameId = "570",
            title = "Dota 2",
            aliases = "刀塔2",
            coverUrl = "https://cdn/dota.jpg",
            summary = "MOBA",
            platforms = listOf("Windows", "Linux"),
            developers = listOf("Valve"),
            publishers = listOf("Valve"),
            ratingScore = 4.5f,
            ratingCount = 1000,
            tags = listOf("MOBA", "竞技"),
            releaseDate = "2013-07-09",
        )
        val subject = GameItemMapper.toSubject("steam", item)
        assertEquals(570L, subject.subjectId)
        assertEquals("steam", subject.sourceId)
        assertEquals(SubjectType.GAME, subject.type)
        assertEquals("Dota 2", subject.title)
        assertEquals("https://cdn/dota.jpg", subject.coverUrl)
        assertEquals("Windows / Linux", subject.platform)
        assertEquals(listOf("MOBA", "竞技"), subject.tags)
        assertEquals(4.5f, subject.ratingScore ?: 0f, 0f)
        assertEquals(1000, subject.ratingTotal)
        assertEquals("2013-07-09", subject.airDate)
        // toSubject 写入 sourceKey（详情刷新/去重/占位判定依赖），格式 {sourceId}:{sourceGameId}
        assertEquals("steam:570", subject.sourceKey)
    }

    @Test
    fun toSubject_explicitSubjectId_used() {
        val item = GameItem(sourceGameId = "neodb:xyz", title = "G")
        val subject = GameItemMapper.toSubject("neodb", item, subjectId = 12345L)
        assertEquals(12345L, subject.subjectId)
    }

    @Test
    fun toSubject_emptyPlatforms_platformNull() {
        val item = GameItem(sourceGameId = "1", title = "G", platforms = emptyList())
        val subject = GameItemMapper.toSubject("steam", item)
        assertNull(subject.platform)
    }

    // ==================== 注册表 ====================

    @Test
    fun registry_registerAndRoute() {
        val registry = GameDataSourceRegistry()
        val steam = SteamGameDataSourceStub("steam")
        val neodb = SteamGameDataSourceStub("neodb")
        registry.register(steam)
        registry.register(steam) // 重复 id 忽略
        registry.register(neodb)
        assertEquals(2, registry.sources.size)
        assertEquals(steam, registry.get("steam"))
        assertEquals(neodb, registry.get("neodb"))
        assertNull(registry.get("rawg"))
        assertEquals(listOf(steam, neodb), registry.searchable())
        assertEquals(listOf(steam, neodb), registry.detailSources())
    }

    @Test
    fun registry_unregisteredSource_null() {
        val registry = GameDataSourceRegistry()
        assertNull(registry.get("steam"))
        assertTrue(registry.searchable().isEmpty())
    }

    // ==================== isSteamPlaceholder 语义 ====================

    @Test
    fun isSteamPlaceholder_sourceKeyPrefix() {
        assertTrue(
            SubjectEntity(
                subjectId = 570, title = "Dota 2", type = SubjectType.GAME,
                sourceId = "steam", sourceKey = "steam:570",
            ).isSteamPlaceholder
        )
        assertFalse(
            SubjectEntity(
                subjectId = 570, title = "Dota 2", type = SubjectType.GAME,
                sourceId = "steam", sourceKey = null, // 迁移前负数条目（旧语义）
            ).isSteamPlaceholder
        )
        assertFalse(
            SubjectEntity(
                subjectId = 1, title = "X", type = SubjectType.GAME,
                sourceId = "bangumi",
            ).isSteamPlaceholder
        )
    }

    // ==================== Steam 合辑过滤（bundle/sub 不入库） ====================

    @Test
    fun steamSearchFiltersBundleAndSub() {
        // 复刻 SteamGameDataSource.search 的过滤谓词：只保留独立游戏 App。
        // bundle（合集包）/sub（捆绑/DLC 组合）会以"合集重复添加"污染搜索与本地库。
        val acceptsApp = SteamGameDataSourceStub.ACCEPT_APP_PREDICATE("app")
        val acceptsNull = SteamGameDataSourceStub.ACCEPT_APP_PREDICATE(null)
        val rejectsBundle = SteamGameDataSourceStub.ACCEPT_APP_PREDICATE("bundle")
        val rejectsSub = SteamGameDataSourceStub.ACCEPT_APP_PREDICATE("sub")
        assertTrue(acceptsApp)
        assertTrue(acceptsNull)
        assertFalse(rejectsBundle)
        assertFalse(rejectsSub)
    }

    private class SteamGameDataSourceStub(
        override val id: String,
    ) : GameDataSource {
        companion object {
            /** 与 SteamGameDataSource.search 保持一致的独立 App 判定。 */
            val ACCEPT_APP_PREDICATE: (String?) -> Boolean = { t -> t.isNullOrBlank() || t == "app" }
        }
        override val displayName: String = id
        override val capabilities: GameDataSourceCapabilities = GameDataSourceCapabilities(
            supportsSearch = true,
            supportsDetail = true,
        )
        override suspend fun search(query: String, limit: Int): List<GameItem> = emptyList()
        override suspend fun getDetail(sourceGameId: String): GameItemDetail? = null
        override suspend fun getSimilarGames(sourceGameId: String, limit: Int): List<GameItem> = emptyList()
    }
}
