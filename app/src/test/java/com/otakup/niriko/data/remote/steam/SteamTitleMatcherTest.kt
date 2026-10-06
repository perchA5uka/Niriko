package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.remote.steam.dto.SteamPriceDto
import com.otakup.niriko.data.remote.steam.dto.SteamStoreSearchItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SteamTitleMatcher 纯函数单元测试。
 */
class SteamTitleMatcherTest {

    // ==================== 归一化 ====================

    @Test
    fun normalizeTitle_stripsSymbolsAndNormalizesWidth() {
        assertEquals("black myth wukong", SteamTitleMatcher.normalizeTitle("Black™ Myth：Wukong®"))
        assertEquals("elden ring", SteamTitleMatcher.normalizeTitle("　ELDEN RING　"))
        assertEquals("hollow(knight)", SteamTitleMatcher.normalizeTitle("Hollow（Knight）"))
    }

    // ==================== 置信度 ====================

    @Test
    fun confidence_exactMatch_isOne() {
        assertEquals(1f, SteamTitleMatcher.confidence("艾尔登法环", "艾尔登法环"), 0.001f)
    }

    @Test
    fun confidence_caseInsensitive() {
        assertEquals(1f, SteamTitleMatcher.confidence("Elden Ring", "elden ring"), 0.001f)
    }

    @Test
    fun confidence_contains_isHigh() {
        val score = SteamTitleMatcher.confidence("艾尔登法环", "艾尔登法环 黄金树幽影")
        assertTrue("包含关系应 ≥0.85,实际 $score", score >= 0.85f)
    }

    @Test
    fun confidence_continuousSubstring_isLow() {
        // 连续子串（无空格边界）不应高置信：短标题撞长标题是误绑主因
        val score = SteamTitleMatcher.confidence("elden", "eldenring")
        assertTrue("连续子串应低于阈值 0.7,实际 $score", score < SteamTitleMatcher.MIN_CONFIDENCE)
        val score2 = SteamTitleMatcher.confidence("夏日", "夏日回忆录")
        assertTrue("连续子串(中文)应低于阈值,实际 $score2", score2 < SteamTitleMatcher.MIN_CONFIDENCE)
    }

    @Test
    fun confidence_wordBoundarySuffix_isHigh() {
        // 带空格副标题/版本后缀 → 词边界包含,保持高置信
        val score = SteamTitleMatcher.confidence("Hollow Knight", "Hollow Knight Silksong")
        assertTrue("词边界包含应 ≥0.85,实际 $score", score >= 0.85f)
    }

    @Test
    fun bestMatch_tiePrefersCloserLength() {
        // 同分(0.85 词边界包含)候选:选与查询标题长度更接近的(多语言/版本消歧)
        val result = SteamTitleMatcher.bestMatch(
            "黑神话悟空",
            listOf(
                candidate(1, "黑神话悟空 豪华版"),
                candidate(2, "黑神话悟空 终极典藏版"),
            ),
        )
        assertNotNull(result)
        assertEquals(1, result!!.appId)
    }

    @Test
    fun bestMatch_shortSubstringNotPickedOverExact() {
        // 短标题连续子串候选不得压过精确候选
        val result = SteamTitleMatcher.bestMatch(
            "Abyss",
            listOf(candidate(1, "Abyss"), candidate(2, "Abyssia of the Deep")),
        )
        assertNotNull(result)
        assertEquals(1, result!!.appId)
    }

    @Test
    fun confidence_unrelated_isLow() {
        val score = SteamTitleMatcher.confidence("孤独摇滚", "Dota 2")
        assertTrue("无关标题应 <0.5,实际 $score", score < 0.5f)
    }

    // ==================== 收紧：弱证据否决（用户反馈反例） ====================

    @Test
    fun confidence_cs2_vs_advanceWars2_isBelowThreshold() {
        // 用户反馈反例：仅共享一个 "2"（且词序无关）不得绑定
        val score = SteamTitleMatcher.confidence("Counter-Strike 2", "Advance Wars 2: Black Hole Rising")
        assertTrue("仅共享数字的弱证据应低于阈值,实际 $score", score < SteamTitleMatcher.MIN_CONFIDENCE)
    }

    @Test
    fun confidence_shortTitleContainment_isBelowThreshold() {
        // 短标题（<4 字符）仅靠包含关系不得高置信："2"、"cs" 单独出现不构成证据
        assertTrue("仅一个数字不构成证据", SteamTitleMatcher.confidence("2", "Counter-Strike 2") < SteamTitleMatcher.MIN_CONFIDENCE)
        assertTrue("短词包含应低于阈值", SteamTitleMatcher.confidence("cs", "Counter-Strike 2") < SteamTitleMatcher.MIN_CONFIDENCE)
    }

    @Test
    fun confidence_crossLanguage_fallbackLow_singleTitle() {
        // 单标题跨语言（中文 vs 英文无字符重叠）→ 低分；跨语言需靠 bestConfidence 多标题
        val score = SteamTitleMatcher.confidence("泰拉瑞亚", "Terraria")
        assertTrue("中英文单标题重叠应低于阈值,实际 $score", score < SteamTitleMatcher.MIN_CONFIDENCE)
        val score2 = SteamTitleMatcher.confidence("喵斯快跑", "Muse Dash")
        assertTrue("喵斯快跑 vs Muse Dash 单标题应低于阈值,实际 $score2", score2 < SteamTitleMatcher.MIN_CONFIDENCE)
    }

    @Test
    fun bestConfidence_crossLanguage_picksMaxPair() {
        // 双标题集：其中一对精确命中 → 高分（Muse Dash 场景）
        val score = SteamTitleMatcher.bestConfidence(
            queryTitles = listOf("喵斯快跑", "Muse Dash"),
            candidateTitles = listOf("Muse Dash", "喵斯快跑"),
        )
        assertEquals(1f, score, 0.001f)
        val terraria = SteamTitleMatcher.bestConfidence(
            queryTitles = listOf("泰拉瑞亚", "Terraria"),
            candidateTitles = listOf("Terraria", "Terraria 官方中文版"),
        )
        assertTrue("Terraria 精确命中应 ≥0.85,实际 $terraria", terraria >= 0.85f)
    }

    @Test
    fun bestMatch_longSubtitledCandidate_isPenalized() {
        // 长副标题包短名：即使词边界包含 0.85，长度差 >50% 惩罚后应低于阈值（歧义拒绝）
        val result = SteamTitleMatcher.bestMatch(
            "Advance Wars 2",
            listOf(candidate(1, "Advance Wars 2: Black Hole Rising")),
        )
        assertNull("歧义长副标题候选应被拒绝", result)
    }

    @Test
    fun bestMatch_typeNullCandidate_isRejected() {
        // type=null（旧接口缺省）不得参与绑定
        val result = SteamTitleMatcher.bestMatch(
            "黑神话：悟空",
            listOf(candidate(2358720, "黑神话：悟空", type = null)),
        )
        assertNull("type 为 null 的候选应拒绝", result)
    }

    // ==================== 最佳匹配 ====================

    private fun candidate(id: Int, name: String, type: String? = "app"): SteamStoreSearchItemDto =
        SteamStoreSearchItemDto(
            type = type,
            name = name,
            id = id,
            price = SteamPriceDto(currency = "CNY", initial = 29800, final = 26800),
        )

    @Test
    fun bestMatch_picksExactOverFuzzy() {
        val result = SteamTitleMatcher.bestMatch(
            "艾尔登法环",
            listOf(candidate(1245620, "艾尔登法环"), candidate(1203620, "艾尔登法环 黄金树幽影")),
        )
        assertNotNull(result)
        assertEquals(1245620, result!!.appId)
        assertEquals(1f, result.confidence, 0.001f)
    }

    @Test
    fun bestMatch_ignoresNonAppTypes() {
        val result = SteamTitleMatcher.bestMatch(
            "黑神话：悟空",
            listOf(candidate(2358720, "黑神话：悟空", type = "app"), candidate(999, "黑神话：悟空 DLC", type = "sub")),
        )
        assertNotNull(result)
        assertEquals(2358720, result!!.appId)
    }

    @Test
    fun bestMatch_carriesPriceAndImage() {
        val result = SteamTitleMatcher.bestMatch(
            "艾尔登法环",
            listOf(candidate(1245620, "艾尔登法环")),
        )
        assertNotNull(result)
        assertEquals(26800, result!!.priceCents)
        assertEquals("CNY", result.currency)
    }

    @Test
    fun bestMatch_noCandidate_belowThreshold_returnsNull() {
        assertNull(SteamTitleMatcher.bestMatch("随机作品名", emptyList()))
        assertNull(SteamTitleMatcher.bestMatch("随机作品名", listOf(candidate(1, "完全无关的名字"))))
    }

    // ============ CJK 空格差异（B08：《女神异闻录》系列绑不上 Steam） ============

    @Test
    fun confidence_cjkTitleDifferingOnlyByASpace_isSameWork() {
        // 真实数据：
        // Bangumi subject 278949 name_cn =「女神异闻录5 皇家版」（带空格）
        // Steam app 1687950（l=schinese&cc=CN）name =「女神异闻录5皇家版」（不带空格）
        assertEquals(
            1f,
            SteamTitleMatcher.confidence("女神异闻录5 皇家版", "女神异闻录5皇家版"),
            0.001f,
        )
    }

    @Test
    fun bestMatchMulti_personaBindsThroughChineseTitle() {
        // Bangumi name「ペルソナ5 ザ・ロイヤル」在 Steam 商店搜不到（实测 total=0），
        // 能搜到的是中文名 —— 必须靠 name_cn 这条路绑上，且只差一个空格也要算命中。
        val result = SteamTitleMatcher.bestMatchMulti(
            listOf("ペルソナ5 ザ・ロイヤル", "女神异闻录5 皇家版"),
            listOf(candidate(1687950, "女神异闻录5皇家版")),
        )
        assertNotNull("中文名只差一个空格也必须绑上", result)
        assertEquals(1687950, result!!.appId)
        assertEquals(1f, result.confidence, 0.001f)
    }

    @Test
    fun confidence_cjkSequelsWithoutSpaces_stayBelowThreshold() {
        // 去空格捷径只处理「排版差异」，绝不能把同系列不同续作放进来（数字才是区分依据）
        assertTrue(
            "女神异闻录3 携带版 vs 女神异闻录4 携带版 必须低于阈值",
            SteamTitleMatcher.confidence("女神异闻录3 携带版", "女神异闻录4 携带版") <
                SteamTitleMatcher.MIN_CONFIDENCE,
        )
        assertTrue(
            "女神异闻录5 vs 女神异闻录4 必须低于阈值",
            SteamTitleMatcher.confidence("女神异闻录5", "女神异闻录4") <
                SteamTitleMatcher.MIN_CONFIDENCE,
        )
    }
}
