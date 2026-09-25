package com.otakup.niriko.data.match

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.InfoBoxEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一匹配服务的纯函数单测（第 4 轮 D）。
 *
 * 重点是**回归用户报告的那个具体缺陷**：
 * `VndbGameDataSource.search` 只用中文名搜 VNDB、且 VNDB 返回的 `titles[]` 没参与打分，
 * 于是《魔法少女的魔女审判》匹配不上，而它的同世界观未发售作反而命中。
 */
class ExternalMatchServiceTest {

    private fun subject(
        title: String = "魔法少女の魔女裁判",
        titleCN: String? = "魔法少女的魔女审判",
        airDate: String? = "2023-07-01",
        type: SubjectType = SubjectType.GAME,
    ) = SubjectEntity(
        subjectId = 1L,
        title = title,
        titleCN = titleCN,
        type = type,
        airDate = airDate,
    )

    // ==================== 打分：全标题集合 ====================

    @Test
    fun `候选的多语言标题全部参与打分_这是修复的核心`() {
        // 中文名与 VNDB 主标题（日文）没有公共子串，但 titles[] 里有中文标题
        val candidate = RawCandidate(
            externalId = "v12345",
            titles = listOf("魔法少女の魔女裁判", "Mahou Shoujo no Majo Saiban", "魔法少女的魔女审判"),
            year = 2023,
        )
        val score = MatchScorer.score(
            bangumiTitles = listOf("魔法少女的魔女审判"),
            candidate = candidate,
            bangumiYear = 2023,
        )
        // 完全一致 → 命中 0.95 下限并加上年份分
        assertTrue("应达到高置信度，实际 ${score.score}", score.score >= MatchScorer.HIGH_CONFIDENCE)
        assertTrue(score.reasons.any { it.contains("完全一致") })
    }

    @Test
    fun `只看主标题会失配_证明多标题集合是必要的`() {
        val onlyMainTitle = RawCandidate(
            externalId = "v12345",
            titles = listOf("魔法少女の魔女裁判"),
            year = 2023,
        )
        val score = MatchScorer.score(
            bangumiTitles = listOf("魔法少女的魔女审判"),
            candidate = onlyMainTitle,
            bangumiYear = 2023,
        )
        assertTrue("只给日文主标题时不该达到高置信度", score.score < MatchScorer.HIGH_CONFIDENCE)
    }

    @Test
    fun `同世界观作品不会被误判为高置信度`() {
        // 同世界观作的标题只共享「魔法少女」前缀
        val spinoff = RawCandidate(
            externalId = "v99999",
            titles = listOf("魔法少女の魔女裁判 -Another-"),
            year = 2025,
        )
        val score = MatchScorer.score(
            bangumiTitles = listOf("魔法少女的魔女审判"),
            candidate = spinoff,
            bangumiYear = 2023,
        )
        assertTrue("同世界观作不该达到自动绑定阈值", score.score < 0.92f)
    }

    @Test
    fun `年份冲突会被扣分`() {
        val sameTitleDifferentYear = RawCandidate(
            externalId = "v1",
            titles = listOf("魔法少女的魔女审判"),
            year = 2010,
        )
        val score = MatchScorer.score(
            bangumiTitles = listOf("魔法少女的魔女审判"),
            candidate = sameTitleDifferentYear,
            bangumiYear = 2023,
        )
        assertTrue(score.reasons.any { it.contains("年份冲突") })
    }

    @Test
    fun `标点与大小写归一化后仍能完全匹配`() {
        val candidate = RawCandidate(externalId = "v1", titles = listOf("Steins;Gate"))
        val score = MatchScorer.score(listOf("steins gate"), candidate)
        assertTrue(score.exactTitle)
        assertTrue(score.score >= 0.95f)
    }

    @Test
    fun `无标题重合返回0并给出理由`() {
        val score = MatchScorer.score(
            bangumiTitles = listOf("进击的巨人"),
            candidate = RawCandidate(externalId = "v1", titles = listOf("Clannad")),
        )
        assertEquals(0f, score.score, 0.0001f)
        assertTrue(score.reasons.isNotEmpty())
    }

    @Test
    fun `平台重叠会加分`() {
        val withPlatform = RawCandidate("v1", listOf("Test Game"), platforms = listOf("win", "ps4"))
        val without = RawCandidate("v1", listOf("Test Game"), platforms = emptyList())
        val a = MatchScorer.score(listOf("Test Game"), withPlatform, bangumiPlatforms = listOf("PC"))
        val b = MatchScorer.score(listOf("Test Game"), without, bangumiPlatforms = listOf("PC"))
        // "win" 与 "PC" 不重叠，因此两者应当相同（证明逻辑没有瞎加分）
        assertEquals(b.score, a.score, 0.0001f)

        val overlap = MatchScorer.score(
            listOf("Test Game"),
            RawCandidate("v1", listOf("Test Game"), platforms = listOf("pc")),
            bangumiPlatforms = listOf("PC"),
        )
        assertTrue(overlap.score >= a.score)
    }

    // ==================== 查询串构造 ====================

    @Test
    fun `查询串包含中文名_原名_infobox别名`() {
        val queries = MatchQueryBuilder.build(
            subject = subject(),
            infobox = listOf(InfoBoxEntry("别名", "魔女裁判 / Witch Trial")),
        )
        assertTrue(queries.contains("魔法少女的魔女审判"))
        assertTrue(queries.contains("魔法少女の魔女裁判"))
        assertTrue(queries.contains("魔女裁判"))
        assertTrue(queries.contains("Witch Trial"))
        // 保序去重
        assertEquals(queries.distinct(), queries)
    }

    @Test
    fun `infobox 里的长文本不会被当成标题`() {
        val long = "x".repeat(200)
        val queries = MatchQueryBuilder.build(
            subject = subject(),
            infobox = listOf(InfoBoxEntry("别名", long)),
        )
        assertTrue(queries.none { it == long })
    }

    @Test
    fun `拼音跳过日文_只保留纯 ASCII`() {
        val hints = MatchQueryBuilder.romanizationHints(subject())
        // 中文/日文标题不是 ASCII，因此不应被当作罗马音
        assertTrue(hints.isEmpty())

        val ascii = MatchQueryBuilder.romanizationHints(
            SubjectEntity(subjectId = 2, title = "Steins;Gate", type = SubjectType.ANIME),
        )
        assertTrue(ascii.contains("Steins;Gate"))
    }

    // ==================== infobox 明确 ID ====================

    @Test
    fun `从 infobox 提取 vndb id`() {
        val id = MatchQueryBuilder.extractInfoboxId(
            infobox = listOf(InfoBoxEntry("vndb", "https://vndb.org/v12345")),
            keys = listOf("vndb"),
            pattern = Regex("""v(\d{1,6})""", RegexOption.IGNORE_CASE),
        )
        assertEquals("12345", id)
    }

    @Test
    fun `infobox 没有该键时不猜`() {
        val id = MatchQueryBuilder.extractInfoboxId(
            infobox = listOf(InfoBoxEntry("官方网站", "https://example.com")),
            keys = listOf("vndb"),
            pattern = Regex("""v(\d{1,6})"""),
        )
        assertNull(id)
    }

    // ==================== VNDB id 规范化 ====================

    @Test
    fun `vndb id 规范化覆盖链接与裸数字`() {
        assertEquals("v17", com.otakup.niriko.data.match.VndbProviderMatcher.normalizeId("v17"))
        assertEquals("v17", com.otakup.niriko.data.match.VndbProviderMatcher.normalizeId("V17"))
        assertEquals("v17", com.otakup.niriko.data.match.VndbProviderMatcher.normalizeId("17"))
        assertEquals("v17", com.otakup.niriko.data.match.VndbProviderMatcher.normalizeId("https://vndb.org/v17"))
        assertNull(com.otakup.niriko.data.match.VndbProviderMatcher.normalizeId("not an id"))
        assertNull(com.otakup.niriko.data.match.VndbProviderMatcher.normalizeId(""))
    }

    // ==================== 编排器（L1/L4） ====================

    /** 假 provider：固定返回两条候选，用于验证编排器的去重/排序/手动路径。 */
    private class FakeMatcher(
        override val provider: String = "fake",
        override val label: String = "Fake",
        private val responses: Map<String, List<RawCandidate>> = emptyMap(),
        private val byIdResult: RawCandidate? = null,
    ) : ProviderMatcher {
        override val infoboxKeys = listOf("fake")
        override val infoboxIdPattern = Regex("""fake:(\d+)""")

        var queriedFor: MutableList<String> = mutableListOf()

        override suspend fun query(text: String, subject: SubjectEntity): List<RawCandidate> {
            queriedFor += text
            return responses[text].orEmpty()
        }

        override suspend fun byId(externalId: String, subject: SubjectEntity): RawCandidate? = byIdResult
    }

    @Test
    fun `编排器优先用 infobox 明确 id 并给满分置信度`() = kotlinx.coroutines.runBlocking {
        val matcher = FakeMatcher(
            byIdResult = RawCandidate(externalId = "42", titles = listOf("正确条目")),
        )
        val service = ExternalMatchService(listOf(matcher))
        val results = service.match(
            provider = "fake",
            subject = subject(),
            infobox = listOf(InfoBoxEntry("fake", "fake:42")),
        )
        assertEquals(1, results.size)
        assertEquals("42", results[0].externalId)
        assertEquals(1f, results[0].confidence, 0.0001f)
        assertEquals(MatchCandidate.SOURCE_INFOBOX, results[0].source)
        // infobox 命中后**不应**再发查询（省额度）
        assertTrue(matcher.queriedFor.isEmpty())
    }

    @Test
    fun `编排器对多查询串结果按最高分去重合并`() = kotlinx.coroutines.runBlocking {
        val matcher = FakeMatcher(
            responses = mapOf(
                "魔法少女的魔女审判" to listOf(
                    RawCandidate("v1", listOf("无关条目")),
                    RawCandidate("v2", listOf("魔法少女的魔女审判"), year = 2023),
                ),
                "魔法少女の魔女裁判" to listOf(
                    RawCandidate("v2", listOf("魔法少女の魔女裁判", "魔法少女的魔女审判"), year = 2023),
                ),
            ),
        )
        val service = ExternalMatchService(listOf(matcher))
        val results = service.match(provider = "fake", subject = subject(), infobox = emptyList())

        // v2 被两个查询串命中，但只应出现一次，且取最高分那次
        assertEquals(2, results.size)
        assertEquals("v2", results[0].externalId)
        assertTrue(results[0].confidence > results[1].confidence)
        assertNotNull(results[0].matchedQuery)
    }

    @Test
    fun `手动搜关键词不过滤低分候选`() = kotlinx.coroutines.runBlocking {
        val matcher = FakeMatcher(
            responses = mapOf(
                "用户自己搜的词" to listOf(
                    RawCandidate("v9", listOf("完全不相关的标题")),
                ),
            ),
        )
        val service = ExternalMatchService(listOf(matcher))
        val results = service.searchByQuery("fake", "用户自己搜的词", subject())
        // 用户主动搜出来的东西必须展示，即使分数为 0
        assertEquals(1, results.size)
        assertEquals(MatchCandidate.SOURCE_MANUAL, results[0].source)
    }

    @Test
    fun `未知 provider 返回空而不是崩`() = kotlinx.coroutines.runBlocking {
        val service = ExternalMatchService(listOf(FakeMatcher()))
        assertTrue(service.match("does-not-exist", subject()).isEmpty())
        assertTrue(service.searchByQuery("does-not-exist", "x", subject()).isEmpty())
        assertNull(service.byId("does-not-exist", "1", subject()))
    }
}
