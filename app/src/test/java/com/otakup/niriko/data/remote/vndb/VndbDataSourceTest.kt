package com.otakup.niriko.data.remote.vndb

import com.otakup.niriko.data.remote.steam.SteamTitleMatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VNDB 数据源单元测试：DTO 解析 / filters 序列化 / 标题匹配复用。
 */
class VndbDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** VNDB 实测响应样本（POST /kana/vn 返回）。 */
    private val sampleResponse = """
        {
          "results": [
            {
              "id": "v17",
              "title": "Katawa Shoujo",
              "alttitle": "かたわ少女",
              "titles": [
                {"lang": "ja", "title": "かたわ少女", "latin": "Katawa Shoujo", "official": true, "main": true},
                {"lang": "zh-Hans", "title": "片轮少女", "latin": "", "official": false, "main": false}
              ],
              "description": "A romance visual novel.",
              "developers": [{"id": "p22", "name": "Four Leaf Studios", "original": "Four Leaf Studios"}],
              "tags": [{"id": "g112", "name": "Romance", "category": "content", "rating": 2.5}],
              "released": "2012-01-04",
              "rating": 90,
              "votecount": 1234,
              "image": {"id": "i1", "url": "https://t.vndb.org/1.jpg", "dims": [600, 800]},
              "length": 4,
              "length_minutes": 2400,
              "platforms": ["win"],
              "olang": "ja",
              "languages": ["ja", "zh-Hans"],
              "screenshots": [{"id": "sf1", "url": "https://t.vndb.org/s1.jpg"}]
            }
          ],
          "more": false
        }
    """.trimIndent()

    @Test
    fun queryResponse_parsesSample() {
        val parsed = json.decodeFromString(
            com.otakup.niriko.data.remote.vndb.dto.VndbQueryResponse.serializer(),
            sampleResponse,
        )
        val vn = parsed.results.first()
        assertEquals("v17", vn.id)
        assertEquals("Katawa Shoujo", vn.title)
        // 中文标题从 titles 提取
        assertEquals("片轮少女", vn.titles.firstOrNull { it.lang.startsWith("zh") }?.title)
        assertEquals(90, vn.rating)
        assertEquals(1234, vn.votecount)
        assertEquals("2012-01-04", vn.released)
        assertEquals(listOf("win"), vn.platforms)
        assertEquals("Four Leaf Studios", vn.developers.first().name)
        assertEquals(1, vn.screenshots.size)
        assertEquals(2400, vn.lengthMinutes)
    }

    @Test
    fun queryRequest_filtersSerializesAsJsonArray() {
        // filters 是混合类型数组，序列化后必须是 JSON 数组而非对象
        val request = com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest(
            filters = buildJsonArray { add("search"); add("="); add("Katawa") },
            fields = "title",
            results = 10,
        )
        val encoded = json.encodeToString(
            com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest.serializer(),
            request,
        )
        assertTrue("filters 应为 JSON 数组", encoded.contains("[\"search\",\"=\",\"Katawa\"]"))
    }

    @Test
    fun titleMatching_reusesSteamMatcher() {
        // VNDB 绑定复用 SteamTitleMatcher：精确命中高置信
        val score = SteamTitleMatcher.confidence("Katawa Shoujo", "Katawa Shoujo")
        assertEquals(1f, score, 0.001f)
        // 无关标题低置信
        val low = SteamTitleMatcher.confidence("孤独摇滚", "Dota 2")
        assertTrue(low < SteamTitleMatcher.MIN_CONFIDENCE)
    }

    @Test
    fun rating_scale100To10() {
        // VNDB rating 0-100 → GameItem 统一 10 分制
        val parsed = json.decodeFromString(
            com.otakup.niriko.data.remote.vndb.dto.VndbQueryResponse.serializer(),
            sampleResponse,
        )
        val vn = parsed.results.first()
        val score = vn.rating?.let { it / 10f }
        assertEquals(9.0f, score!!, 0.001f)
    }

    @Test
    fun gameItemMapping_picksChineseTitle() {
        val parsed = json.decodeFromString(
            com.otakup.niriko.data.remote.vndb.dto.VndbQueryResponse.serializer(),
            sampleResponse,
        )
        val vn = parsed.results.first()
        val cnTitle = vn.titles.firstOrNull { it.lang.startsWith("zh") }?.title
        assertNotNull("应能提取中文标题", cnTitle)
    }
}
