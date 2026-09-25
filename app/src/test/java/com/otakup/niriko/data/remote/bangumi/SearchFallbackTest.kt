package com.otakup.niriko.data.remote.bangumi

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.bangumi.dto.LegacySearchResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.LegacySubjectDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchResponseDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

/**
 * 旧版 GET 搜索兜底的触发条件（第 6 轮 F5b + 复审收窄）。
 *
 * 改造前只有 nsfw=true 才走 GET /search/subject/{keywords}；随后放宽为「关键词非空且 POST 失败或为空」，
 * 但复审指出两个新问题，本测试把修正后的规则钉死：
 * 1. 带 tag / air_date / rank / nsfw 的请求**返回空**时不兜底（旧版没有这些语义，
 *    用旧版结果顶替等于悄悄丢掉用户筛选）——只有 POST 真失败才兜底；
 * 2. 「确实没有结果」时的重复兜底被负缓存抑制，且 key 必须带 type：同一关键词 + 同一类型的
 *    重复搜索在 TTL 内不再兜底，但不同关键词/类型互不影响（否则先返回空的类型会掐掉其它类型的
 *    兜底，导致同一关键词的结果随时序变化）。
 */
class SearchFallbackTest {

    /** 只实现 searchSubjects / legacySearchSubjects 的假 API（动态代理）。 */
    private class FakeApi {
        val postCalls = AtomicInteger(0)
        val legacyCalls = AtomicInteger(0)
        var postError: Exception? = null
        var postResponse = SearchResponseDto()
        var legacyResponse = LegacySearchResponseDto(
            results = 2,
            list = listOf(
                LegacySubjectDto(id = 1L, type = 2, name = "进击的巨人"),
                LegacySubjectDto(id = 2L, type = 2, name = "巨人 最终季"),
            ),
        )

        val api: BangumiApiService = Proxy.newProxyInstance(
            BangumiApiService::class.java.classLoader,
            arrayOf(BangumiApiService::class.java),
            InvocationHandler { _, method, _ ->
                when (method.name) {
                    "searchSubjects" -> {
                        postCalls.incrementAndGet()
                        postError?.let { error -> throw error }
                        postResponse
                    }
                    "legacySearchSubjects" -> {
                        legacyCalls.incrementAndGet()
                        legacyResponse
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            },
        ) as BangumiApiService

        fun dataSource(): BangumiDataSource = BangumiDataSource(api)
    }

    /** 显式传满全部参数：不依赖接口参数默认值。 */
    private suspend fun BangumiDataSource.plainSearch(
        keyword: String,
        type: Int? = null,
        nsfw: Boolean? = null,
        tags: List<String>? = null,
        rank: List<String>? = null,
        airDate: List<String>? = null,
    ): List<SubjectEntity> = search(
        keyword = keyword,
        type = type,
        tags = tags,
        airDate = airDate,
        rank = rank,
        nsfw = nsfw,
        sort = null,
        limit = null,
        offset = null,
    )

    private suspend fun BangumiDataSource.plainSearchWithTotal(
        keyword: String,
        type: Int? = null,
        tags: List<String>? = null,
    ): Pair<List<SubjectEntity>, Int> = searchWithTotal(
        keyword = keyword,
        type = type,
        tags = tags,
        airDate = null,
        rank = null,
        nsfw = null,
        sort = null,
        limit = null,
        offset = null,
    )

    // ==================== 纯策略 ====================

    @Test
    fun policyTriggersOnPostFailureOrEmptyResult() {
        assertTrue(shouldUseLegacySearch("巨人", postFailed = true, postResultEmpty = false))
        assertTrue(shouldUseLegacySearch("巨人", postFailed = false, postResultEmpty = true))
        assertTrue(shouldUseLegacySearch("巨人", postFailed = true, postResultEmpty = true))
    }

    @Test
    fun policySkipsBlankKeywordOrHealthyPost() {
        assertFalse("空关键词不能兜底（旧版能力不等价）", shouldUseLegacySearch("", true, true))
        assertFalse("POST 正常且有结果时不该多打一次请求", shouldUseLegacySearch("巨人", false, false))
    }

    @Test
    fun policyWithFiltersOnlyFallsBackOnRealFailure() {
        assertFalse(
            "带筛选 + POST 空结果 = 该筛选下确实没有，不该用旧版顶替",
            shouldUseLegacySearch("巨人", postFailed = false, postResultEmpty = true, hasFilters = true),
        )
        assertTrue(
            "带筛选 + POST 真失败仍然兜底（网络通路问题，值得一试）",
            shouldUseLegacySearch("巨人", postFailed = true, postResultEmpty = true, hasFilters = true),
        )
        assertFalse(shouldUseLegacySearch("", false, true, hasFilters = true))
    }

    @Test
    fun hasFilterSemanticsDetectsEachDimension() {
        assertFalse(hasFilterSemantics(null, null, null, null))
        assertFalse(hasFilterSemantics(emptyList(), emptyList(), emptyList(), false))
        assertTrue(hasFilterSemantics(listOf("日本"), null, null, null))
        assertTrue(hasFilterSemantics(null, listOf(">=2020-01-01"), null, null))
        assertTrue(hasFilterSemantics(null, null, listOf(">0"), null))
        assertTrue(hasFilterSemantics(null, null, null, true))
    }

    // ==================== 负缓存（key = keyword + type） ====================

    @Test
    fun legacyEmptyCacheMarksAndSkipsPerKeywordAndType() {
        val cache = LegacyEmptyCache()
        assertFalse(cache.shouldSkip("不存在的作品", null))
        cache.markEmpty("不存在的作品", null)
        assertTrue(cache.shouldSkip("不存在的作品", null))
        assertFalse("all 与显式类型是两个 key", cache.shouldSkip("不存在的作品", 2))
        assertFalse("不同关键词互不影响", cache.shouldSkip("巨人", null))

        cache.markEmpty("巨人", 1)
        assertTrue(cache.shouldSkip("巨人", 1))
        assertFalse("同一关键词的其它类型仍可各自兜底（round 3：key 带 type）", cache.shouldSkip("巨人", 2))
    }

    @Test
    fun legacyEmptyCacheExpiresAfterTtl() {
        var now = 0L
        val cache = LegacyEmptyCache(ttlMs = 1_000L, clock = { now })
        cache.markEmpty("巨人", 2)
        assertTrue(cache.shouldSkip("巨人", 2))
        now += 1_500L
        assertFalse("超过 TTL 后应允许重试", cache.shouldSkip("巨人", 2))
    }

    // ==================== 行为 ====================

    @Test
    fun postFailureFallsBackToLegacyForPlainSearch() = runBlocking {
        val fake = FakeApi()
        fake.postError = IllegalStateException("HTTP 400")
        val dataSource = fake.dataSource()

        val results = dataSource.plainSearch(keyword = "巨人", type = 2)

        assertEquals(1, fake.postCalls.get())
        assertEquals(1, fake.legacyCalls.get())
        assertEquals(2, results.size)
        assertEquals(SubjectType.ANIME, results.first().type)
    }

    @Test
    fun postEmptyResultFallsBackToLegacyForPlainSearch() = runBlocking {
        val fake = FakeApi()
        fake.postResponse = SearchResponseDto(data = emptyList(), total = 0)
        val dataSource = fake.dataSource()

        val results = dataSource.plainSearch(keyword = "巨人")

        assertEquals(1, fake.legacyCalls.get())
        assertEquals(2, results.size)
    }

    @Test
    fun postEmptyWithTagsDoesNotFallBack() = runBlocking {
        val fake = FakeApi()
        fake.postResponse = SearchResponseDto(data = emptyList(), total = 0)
        val dataSource = fake.dataSource()

        // 带 tag 的 POST 空结果 = 这个筛选条件下确实没有结果；旧版没有 tag 能力，
        // 用它顶替等于悄悄丢掉筛选条件（比空结果更误导）
        val results = dataSource.plainSearch(keyword = "巨人", type = 2, tags = listOf("日本"))

        assertTrue(results.isEmpty())
        assertEquals("带筛选的 POST 空结果不应触发旧版兜底", 0, fake.legacyCalls.get())
    }

    @Test
    fun postEmptyWithRankOrAirDateDoesNotFallBack() = runBlocking {
        val rankFake = FakeApi()
        rankFake.postResponse = SearchResponseDto(data = emptyList(), total = 0)

        val rankResults = rankFake.dataSource()
            .plainSearch(keyword = "巨人", type = 2, rank = listOf(">0", "<=99999"))

        assertTrue(rankResults.isEmpty())
        assertEquals(0, rankFake.legacyCalls.get())

        val dateFake = FakeApi()
        dateFake.postResponse = SearchResponseDto(data = emptyList(), total = 0)

        val dateResults = dateFake.dataSource()
            .plainSearch(keyword = "巨人", type = 2, airDate = listOf(">=2024-01-01"))

        assertTrue(dateResults.isEmpty())
        assertEquals(0, dateFake.legacyCalls.get())
    }

    @Test
    fun postFailureWithFiltersStillFallsBack() = runBlocking {
        val fake = FakeApi()
        fake.postError = IllegalStateException("HTTP 400")
        val dataSource = fake.dataSource()

        val results = dataSource.plainSearch(keyword = "巨人", type = 2, tags = listOf("日本"))

        assertEquals(2, results.size)
        assertEquals(1, fake.legacyCalls.get())
    }

    @Test
    fun searchWithTotalWithTagsDoesNotFallBackOnEmptyPost() = runBlocking {
        val fake = FakeApi()
        val dataSource = fake.dataSource()

        val (results, total) = dataSource.plainSearchWithTotal(keyword = "巨人", type = 2, tags = listOf("TV"))

        assertTrue(results.isEmpty())
        assertEquals(0, total)
        assertEquals(0, fake.legacyCalls.get())
    }

    @Test
    fun sameKeywordAndTypeIsNotRetriedWithinTtl() = runBlocking {
        val fake = FakeApi()
        fake.legacyResponse = LegacySearchResponseDto(results = 0, list = emptyList())
        val dataSource = fake.dataSource()

        val first = dataSource.plainSearch(keyword = "不存在的作品", type = 2)
        val second = dataSource.plainSearch(keyword = "不存在的作品", type = 2)

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals("同一关键词 + 同一类型在 TTL 内只兜底一次", 1, fake.legacyCalls.get())
        assertEquals(2, fake.postCalls.get())
    }

    @Test
    fun differentTypesFallBackIndependently() = runBlocking {
        val fake = FakeApi()
        fake.legacyResponse = LegacySearchResponseDto(results = 0, list = emptyList())
        val dataSource = fake.dataSource()

        // round 3 复审裁决：key 必须带 type。若只按关键词，先返回空的那个类型会掐掉其它类型的
        // 兜底 —— 同一关键词的结果随时序变化（不确定性），60s 内重复搜索甚至直接变「未找到」。
        dataSource.plainSearch(keyword = "不存在的作品", type = 2)
        dataSource.plainSearch(keyword = "不存在的作品", type = 1)

        assertEquals("同一关键词的不同类型各自独立兜底（不误伤）", 2, fake.legacyCalls.get())
    }

    @Test
    fun blankKeywordNeverFallsBack() = runBlocking {
        val fake = FakeApi()
        val dataSource = fake.dataSource()

        val results = dataSource.plainSearch(keyword = "", type = 2)

        assertTrue(results.isEmpty())
        assertEquals(0, fake.legacyCalls.get())
    }

    @Test
    fun blankKeywordPostFailurePropagates() {
        val fake = FakeApi()
        fake.postError = IllegalStateException("HTTP 400")
        val dataSource = fake.dataSource()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { dataSource.plainSearch(keyword = "") }
        }
    }

    @Test
    fun nsfwKeywordSearchStillPrefersLegacyWithoutPosting() = runBlocking {
        val fake = FakeApi()
        val dataSource = fake.dataSource()

        val results = dataSource.plainSearch(keyword = "巨人", type = 2, nsfw = true)

        assertEquals(2, results.size)
        assertEquals("nsfw 关键词搜索应直接走免 token 的旧版接口", 0, fake.postCalls.get())
        assertEquals(1, fake.legacyCalls.get())
    }

    @Test
    fun searchWithTotalReportsLegacySize() = runBlocking {
        val fake = FakeApi()
        fake.postError = IllegalStateException("HTTP 401")
        val dataSource = fake.dataSource()

        val (results, total) = dataSource.plainSearchWithTotal(keyword = "巨人", type = 2)

        assertEquals(2, results.size)
        assertEquals(2, total)
    }

    @Test
    fun legacyFailureOnEmptyPostStillReturnsEmptyInsteadOfThrowing() = runBlocking {
        val fake = FakeApi()
        fake.legacyResponse = LegacySearchResponseDto(results = 0, list = emptyList())
        val dataSource = fake.dataSource()

        // POST 正常但为空 + 旧版也为空 → 是「确实没有结果」，不是网络失败
        val results = dataSource.plainSearch(keyword = "不存在的作品名")

        assertTrue(results.isEmpty())
        assertEquals(1, fake.legacyCalls.get())
    }
}
