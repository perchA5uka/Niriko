package com.otakup.niriko.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置搜索（10.17）：纯过滤 + 高亮区间。
 *
 * 用一张小索引覆盖三类命中（标题 / 关键词 / 分组）与排序优先级，
 * 避免「搜不到设置项」这类回归只能靠手点。
 */
class SettingsSearchIndexTest {

    private val index = listOf(
        SettingsSearchEntry(
            id = "appearance.theme",
            pageTitle = "外观",
            group = "主题",
            title = "主题色",
            keywords = listOf("种子色", "seed", "颜色"),
            route = "settings/appearance",
        ),
        SettingsSearchEntry(
            id = "appearance.wallpaper",
            pageTitle = "外观",
            group = "壁纸",
            title = "壁纸柔化",
            keywords = listOf("模糊", "blur"),
            route = "settings/appearance",
        ),
        SettingsSearchEntry(
            id = "sync.webdav",
            pageTitle = "同步与备份",
            group = "WebDAV 同步",
            title = "服务器地址",
            keywords = listOf("webdav", "url"),
            route = "settings/sync",
        ),
    )

    @Test
    fun emptyOrBlankQueryReturnsNothing() {
        assertTrue(searchSettings(index, "").isEmpty())
        assertTrue(searchSettings(index, "   ").isEmpty())
    }

    @Test
    fun titleHitComesBeforeKeywordAndGroupHits() {
        // "壁纸" 命中：t1 标题（wallpaper.title）、t2 关键词、"壁纸" 也命中 appearance.wallpaper.title
        val hits = searchSettings(index, "壁纸")
        assertEquals("appearance.wallpaper", hits.first().entry.id)
        assertEquals(SearchField.TITLE, hits.first().matchedField)

        // "webdav" 命中 sync.webdav 的关键词；分组也含 WebDAV，但仍应作为关键词命中
        val keywordHit = searchSettings(index, "webdav").first()
        assertEquals(SearchField.KEYWORDS, keywordHit.matchedField)

        // "同步" 只出现在 sync 条目的 group（"WebDAV 同步"）里 → 分组命中
        val groupHit = searchSettings(index, "同步").first()
        assertEquals(SearchField.GROUP, groupHit.matchedField)
        assertEquals("sync.webdav", groupHit.entry.id)
    }

    @Test
    fun searchIsCaseInsensitiveAndTrimmed() {
        assertEquals(1, searchSettings(index, "  SEED  ").size)
        assertEquals(1, searchSettings(index, "Blur").size)
    }

    @Test
    fun titleHitsCarryHighlightRanges() {
        val hit = searchSettings(index, "柔化").first()
        assertEquals(SearchField.TITLE, hit.matchedField)
        assertEquals(listOf(2 until 4), hit.ranges)   // "壁纸柔化" 的 2..3
    }

    @Test
    fun matchRangesFindsEveryOccurrence() {
        assertEquals(listOf(0 until 2, 3 until 5), matchRanges("ab ab", "ab"))
        assertTrue(matchRanges("abc", "").isEmpty())
        assertTrue(matchRanges("", "abc").isEmpty())
    }

    @Test
    fun unknownQueryHitsNothing() {
        assertTrue(searchSettings(index, "不存在的东西").isEmpty())
    }

    @Test
    fun defaultIndexCoversEverySettingsCategoryAndCommonSynonyms() {
        val entries = defaultSettingsIndex()
        assertEquals(SettingsCategory.entries.size, entries.size)
        // 每个分类都能用自己的标题搜到（新加分类忘了进索引会被这条抓住）
        SettingsCategory.entries.forEach { category ->
            assertTrue(
                "搜不到分类 ${category.title}",
                searchSettings(entries, category.title).any { it.entry.id == category.name },
            )
        }
        // 同义词：用户搜「壁纸/玻璃」应命中「外观」
        assertTrue(searchSettings(entries, "壁纸").any { it.entry.id == SettingsCategory.APPEARANCE.name })
        assertTrue(searchSettings(entries, "玻璃").any { it.entry.id == SettingsCategory.APPEARANCE.name })
        // 数据类同义词
        assertTrue(searchSettings(entries, "webdav").any { it.entry.id == SettingsCategory.SYNC.name })
    }
}
