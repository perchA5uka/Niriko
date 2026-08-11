package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.remote.steam.dto.SteamMostPlayedGamesResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SteamMostPlayedGames DTO 解析 + 排行缓存模型测试。
 * 解析样本 = 本设备实测响应（key=0 时的真实返回）。
 */
class SteamMostPlayedGamesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val realSample = """
        {"response":{"rollup_date":1778457600,"ranks":[
            {"rank":1,"appid":730,"last_week_rank":1,"peak_in_game":1275982},
            {"rank":2,"appid":578080,"last_week_rank":2,"peak_in_game":732248},
            {"rank":3,"appid":570,"last_week_rank":3,"peak_in_game":635321}
        ]}}
    """.trimIndent()

    @Test
    fun mostPlayedGames_parsesRealSample() {
        val dto = json.decodeFromString<SteamMostPlayedGamesResponseDto>(realSample)
        val resp = dto.response
        assertEquals(1778457600L, resp?.rollupDate ?: 0L)
        val ranks = resp?.ranks.orEmpty()
        assertEquals(3, ranks.size)
        assertEquals(1, ranks[0].rank)
        assertEquals(730, ranks[0].appid)
        assertEquals(1, ranks[0].lastWeekRank)
        assertEquals(1275982, ranks[0].peakInGame)
        assertEquals(570, ranks[2].appid)
    }

    @Test
    fun mostPlayedGames_emptyResponse_nullSafe() {
        val dto = json.decodeFromString<SteamMostPlayedGamesResponseDto>("{}")
        assertNull(dto.response)
        val ranks = dto.response?.ranks.orEmpty()
        assertTrue(ranks.isEmpty())
    }

    @Test
    fun mostPlayedGames_unknownFieldsIgnored() {
        // 兼容未来字段（如 concurrent_in_game 若 Valve 补上）
        val body = """{"response":{"rollup_date":1,"ranks":[
            {"rank":1,"appid":730,"last_week_rank":1,"peak_in_game":100,"concurrent_in_game":50}
        ]}}"""
        val dto = json.decodeFromString<SteamMostPlayedGamesResponseDto>(body)
        assertEquals(100, dto.response?.ranks?.first()?.peakInGame)
    }

    // ==================== 排行模型 ====================

    @Test
    fun chartEntry_fields() {
        val entry = SteamChartEntry(rank = 3, lastWeekRank = 5, peakInGame = 635321)
        assertEquals(3, entry.rank)
        assertEquals(5, entry.lastWeekRank)
        assertEquals(635321, entry.peakInGame)
    }
}
