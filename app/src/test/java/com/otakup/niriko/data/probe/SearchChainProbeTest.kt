package com.otakup.niriko.data.probe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索链路自检探针的纯逻辑单测（第 6 轮 F9）。
 *
 * 不发任何真实请求：只验证三层端点、POST body、关键词编码与「返回条数」解析。
 * 真机连通性由搜索空态的「诊断搜索链路」按钮跑。
 */
class SearchChainProbeTest {

    @Test
    fun stepsCoverThreeLayersInOrder() {
        val steps = SearchChainProbe.steps("https://api.bgm.tv/")
        assertEquals(3, steps.size)
        assertTrue(steps[0].url.contains("/v0/subjects?"))
        assertTrue(steps[0].url.contains("sort=rank"))
        assertTrue(steps[1].url.contains("/v0/search/subjects"))
        assertEquals("POST", steps[1].method)
        assertTrue(steps[2].url.contains("/search/subject/"))
        assertTrue("三层都必须给出展示名", steps.all { it.name.isNotBlank() })
    }

    @Test
    fun baseUrlTrailingSlashIsNormalized() {
        val withSlash = SearchChainProbe.steps("https://api.bgm.tv/")
        val withoutSlash = SearchChainProbe.steps("https://api.bgm.tv")
        assertEquals(withoutSlash.map { it.url }, withSlash.map { it.url })
    }

    @Test
    fun proxyBaseUrlIsUsedAsGiven() {
        val steps = SearchChainProbe.steps("https://bgmapi.anibt.net/")
        assertTrue(steps.all { it.url.startsWith("https://bgmapi.anibt.net/") })
        assertTrue("反代下不应出现双斜杠", steps.none { it.url.contains("//v0") })
    }

    @Test
    fun keywordIsUrlEncodedInLegacyPath() {
        val steps = SearchChainProbe.steps("https://api.bgm.tv", keyword = "巨人")
        assertEquals("%E5%B7%A8%E4%BA%BA", SearchChainProbe.encodeKeyword("巨人"))
        assertTrue(steps[2].url.contains("/search/subject/%E5%B7%A8%E4%BA%BA"))
    }

    @Test
    fun postBodyIsJsonWithKeyword() {
        assertEquals(
            "{\"keyword\":\"巨人\",\"sort\":\"match\"}",
            SearchChainProbe.searchPostBody("巨人"),
        )
    }

    /** 转义必须让 body 仍然是合法 JSON，且关键词原样还原（用 JSON 解析验证，不靠手写转义字符串）。 */
    @Test
    fun postBodySurvivesQuotesAndBackslashes() {
        val keyword = "a\"b\\c"
        val body = SearchChainProbe.searchPostBody(keyword)
        val parsed = Json.parseToJsonElement(body).jsonObject
        assertEquals(keyword, parsed["keyword"]?.jsonPrimitive?.content)
        assertEquals("match", parsed["sort"]?.jsonPrimitive?.content)
    }

    @Test
    fun postBodyKeywordRoundTripsThroughJson() {
        val body = SearchChainProbe.searchPostBody("巨人")
        val parsed = Json.parseToJsonElement(body).jsonObject
        assertEquals("巨人", parsed["keyword"]?.jsonPrimitive?.content)
    }

    @Test
    fun countItemsUnderstandsV0AndLegacyShapes() {
        assertEquals(2, SearchChainProbe.countItems("{\"data\":[{\"id\":1},{\"id\":2}],\"total\":2}"))
        assertEquals(3, SearchChainProbe.countItems("{\"results\":3,\"list\":[{},{},{}]}"))
        assertEquals(1, SearchChainProbe.countItems("[{\"id\":1}]"))
        assertNull("解析不出列表结构就是 null", SearchChainProbe.countItems("{\"total\":5}"))
        assertNull("HTML 反爬页必须返回 null", SearchChainProbe.countItems("<html>blocked</html>"))
        assertNull(SearchChainProbe.countItems(""))
    }

    @Test
    fun summaryShowsItemCount() {
        val result = ProbeResult(
            endpointName = "2) POST /v0/search/subjects 检索",
            url = "https://api.bgm.tv/v0/search/subjects",
            state = ProbeState.OK,
            httpStatus = 200,
            elapsedMs = 320,
            itemCount = 25,
        )
        val summary = result.summary
        assertTrue(summary.contains("200"))
        assertTrue(summary.contains("320"))
        assertTrue(summary.contains("25 条"))
    }

    @Test
    fun blockedSummaryKeepsHttpAndError() {
        val result = ProbeResult(
            endpointName = "2) POST /v0/search/subjects 检索",
            url = "https://api.bgm.tv/v0/search/subjects",
            state = ProbeState.BLOCKED,
            httpStatus = 401,
            elapsedMs = 210,
            error = "HTTP 401 Unauthorized",
            itemCount = 0,
        )
        val summary = result.summary
        assertTrue(summary.contains("被拒绝"))
        assertTrue(summary.contains("401"))
        assertTrue(summary.contains("Unauthorized"))
        assertTrue(summary.contains("0 条"))
    }

    @Test
    fun defaultKeywordIsNonBlank() {
        assertNotNull(SearchChainProbe.DEFAULT_KEYWORD)
        assertTrue(SearchChainProbe.DEFAULT_KEYWORD.isNotBlank())
    }
}
