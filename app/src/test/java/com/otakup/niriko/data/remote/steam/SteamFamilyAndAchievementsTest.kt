package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.remote.steam.dto.SteamFamilyGroupResponseDto
import com.otakup.niriko.data.remote.steam.dto.SteamOwnedGameDto
import com.otakup.niriko.data.remote.steam.dto.SteamPlayerAchievementsResponseDto
import com.otakup.niriko.data.remote.steam.dto.SteamSchemaForGameResponseDto
import com.otakup.niriko.data.remote.steam.dto.SteamSharedLibraryAppsResponseDto
import com.otakup.niriko.data.remote.steam.dto.SteamWebApiTokenDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Steam 家庭库 / 成就相关单元测试（DTO 序列化 + 匹配器 shared 标记 + 纯逻辑）。
 */
class SteamFamilyAndAchievementsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== webapi_token 解析 ====================

    @Test
    fun webApiToken_parsesTokenField() {
        val body = """{"webapi_token":"abc123token","something_else":42}"""
        val dto = json.decodeFromString<SteamWebApiTokenDto>(body)
        assertEquals("abc123token", dto.webApiToken)
    }

    @Test
    fun webApiToken_missingField_null() {
        val body = """{"other":1}"""
        val dto = json.decodeFromString<SteamWebApiTokenDto>(body)
        assertNull(dto.webApiToken)
    }

    // ==================== 家庭组 / 共享库 DTO ====================

    @Test
    fun familyGroup_parsesGroupId() {
        val body = """{"response":{"family_groupid":"1234567890123456789"}}"""
        val dto = json.decodeFromString<SteamFamilyGroupResponseDto>(body)
        assertEquals("1234567890123456789", dto.response?.familyGroupId)
    }

    @Test
    fun sharedLibrary_parsesApps() {
        val body = """
            {"response":{"apps":[
                {"appid":570,"name":"Dota 2","img_icon_url":"abc"},
                {"appid":2358720,"name":"黑神话：悟空","img_icon_url":"def"}
            ]}}
        """.trimIndent()
        val dto = json.decodeFromString<SteamSharedLibraryAppsResponseDto>(body)
        val apps = dto.response?.apps.orEmpty()
        assertEquals(2, apps.size)
        assertEquals(570, apps[0].appid)
        assertEquals("Dota 2", apps[0].name)
        assertEquals("abc", apps[0].imgIconUrl)
    }

    // ==================== 成就 DTO ====================

    @Test
    fun playerAchievements_parsesProgress() {
        val body = """
            {"playerstats":{
                "steamID":"76561198012345678",
                "gameName":"Dota 2",
                "success":true,
                "achievements":[
                    {"apiname":"win_1","achieved":true,"unlocktime":1700000000},
                    {"apiname":"win_2","achieved":false,"unlocktime":0}
                ]
            }}
        """.trimIndent()
        val dto = json.decodeFromString<SteamPlayerAchievementsResponseDto>(body)
        val stats = dto.playerstats
        assertTrue(stats?.success == true)
        val list = stats?.achievements.orEmpty()
        assertEquals(2, list.size)
        assertTrue(list[0].achieved)
        assertEquals(1700000000L, list[0].unlocktime)
        assertFalse(list[1].achieved)
    }

    @Test
    fun schemaForGame_parsesDefinitions() {
        val body = """
            {"game":{
                "gameName":"Dota 2",
                "availableGameStats":{
                    "achievements":[
                        {"name":"win_1","displayName":"第一胜","description":"赢下第一局","hidden":false,"icon":"https://cdn/win1.jpg"}
                    ]
                }
            }}
        """.trimIndent()
        val dto = json.decodeFromString<SteamSchemaForGameResponseDto>(body)
        val defs = dto.game?.availableGameStats?.achievements.orEmpty()
        assertEquals(1, defs.size)
        assertEquals("第一胜", defs[0].displayName)
        assertFalse(defs[0].hidden)
    }

    // ==================== 家庭库 shared 标记 ====================

    @Test
    fun toPreview_sharedGame_markedShared() = kotlinx.coroutines.runBlocking {
        val matcher = SteamLibraryMatcher()
        val preview = matcher.toPreview(
            SteamOwnedGameDto(appid = 999, name = "共享游戏", playtimeForever = 0),
            shared = true,
        )
        assertTrue(preview.shared)
        assertTrue(preview.isPlaceholder) // 未匹配 → 占位
    }

    @Test
    fun toPreviews_sharedAppIds_markCorrectRows() = kotlinx.coroutines.runBlocking {
        val matcher = SteamLibraryMatcher(
            searchBangumiGame = { emptyList() },
        )
        val previews = matcher.toPreviews(
            games = listOf(
                SteamOwnedGameDto(appid = 1, name = "A"),
                SteamOwnedGameDto(appid = 2, name = "B"),
                SteamOwnedGameDto(appid = 3, name = "C"),
            ),
            sharedAppIds = setOf(2, 3),
        )
        assertEquals(3, previews.size)
        assertFalse(previews[0].shared)
        assertTrue(previews[1].shared)
        assertTrue(previews[2].shared)
    }

    // ==================== SteamAchievements 模型 ====================

    @Test
    fun achievements_percentCalculated() {
        val items = listOf(
            SteamAchievementItem(apiName = "a", achieved = true),
            SteamAchievementItem(apiName = "b", achieved = true),
            SteamAchievementItem(apiName = "c", achieved = false),
        )
        val achievements = SteamAchievements(unlocked = 2, total = 3, items = items)
        assertEquals(2, achievements.unlocked)
        assertEquals(3, achievements.total)
        assertEquals(2f / 3f * 100f, achievements.percent, 0.001f)
    }

    @Test
    fun achievements_zeroTotal_percentZero() {
        val achievements = SteamAchievements()
        assertEquals(0f, achievements.percent, 0f)
    }
}
