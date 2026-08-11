package com.otakup.niriko.data.remote.bilibili

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bilibili 评分 DTO 解码 + 区域限制判定测试。
 * 用接近实测响应的 JSON 验证契约, 不依赖网络。
 */
class BilibiliSeasonDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `season 响应解码评分与人数`() {
        val dto = json.decodeFromString<BilibiliSeasonResponseDto>(
            """
            {
              "code": 0,
              "message": "success",
              "result": {
                "title": "冰菓",
                "season_id": 3398,
                "media_id": 3398,
                "rating": { "count": 84312, "score": 9.8 }
              }
            }
            """.trimIndent(),
        )
        assertEquals(0, dto.code)
        val result = dto.result!!
        assertEquals(9.8, result.rating?.score ?: 0.0, 0.001)
        assertEquals(84312, result.rating?.count)
        assertEquals("冰菓", result.title)
    }

    @Test
    fun `season 响应缺 rating 时默认 0 而非崩溃`() {
        val dto = json.decodeFromString<BilibiliSeasonResponseDto>(
            """{"code": 0, "result": {"season_id": 3398}}""",
        )
        val score = dto.result?.rating?.score ?: 0.0
        val count = dto.result?.rating?.count ?: 0
        assertEquals(0.0, score, 0.001)
        assertEquals(0, count)
    }

    @Test
    fun `未知字段被忽略`() {
        val dto = json.decodeFromString<BilibiliSeasonResponseDto>(
            """{"code":0, "hhh": {"x":1}, "result": {"rating":{"score":1.0,"count":2,"extra":9}}}""",
        )
        assertEquals(1.0, dto.result?.rating?.score ?: 0.0, 0.001)
        assertEquals(2, dto.result?.rating?.count)
    }

    @Test
    fun `港澳台区域限制可识别`() {
        val dto = json.decodeFromString<BilibiliSeasonResponseDto>(
            """
            {
              "code": 0,
              "result": {
                "rights": { "area_limit": 328, "ban_area_show": 1 }
              }
            }
            """.trimIndent(),
        )
        assertTrue(dto.result?.areaLimited ?: false)
    }

    @Test
    fun `无区域限制时为 false`() {
        val dto = json.decodeFromString<BilibiliSeasonResponseDto>(
            """{"code":0, "result": {"rights": {}}}""",
        )
        assertFalse(dto.result?.areaLimited ?: true)
    }

    @Test
    fun `code 非 0 时客户端返回 null`() {
        val resp = json.decodeFromString<BilibiliSeasonResponseDto>("""{"code":-404,"message":"啥都木有"}""")
        assertEquals(-404, resp.code)
        assertNull(resp.result)
    }
}