package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AniListIdParser 单测。
 *
 * 回归的是用户反馈的「按下右侧绑定无反应」：候选 externalId 形如 `media-123`，
 * 而绑定入口此前用 `toLongOrNull()` 直接解析 → 恒为 null → 点击没有任何反馈。
 */
class AniListIdParserTest {

    @Test
    fun `media 前缀可解析出 id`() {
        assertEquals(123L, AniListIdParser.parse("media-123"))
        assertEquals(21L, AniListIdParser.parse("media-21"))
    }

    @Test
    fun `纯数字与路径链接可解析`() {
        assertEquals(12345L, AniListIdParser.parse("12345"))
        assertEquals(12345L, AniListIdParser.parse("anime/12345"))
        assertEquals(12345L, AniListIdParser.parse("https://anilist.co/anime/12345"))
        assertEquals(12345L, AniListIdParser.parse("  https://anilist.co/anime/12345/  "))
    }

    @Test
    fun `空值与非正数返回 null`() {
        assertNull(AniListIdParser.parse(null))
        assertNull(AniListIdParser.parse(""))
        assertNull(AniListIdParser.parse("   "))
        assertNull(AniListIdParser.parse("0"))
        assertNull(AniListIdParser.parse("media-0"))
        assertNull(AniListIdParser.parse("没有任何数字"))
    }

    @Test
    fun `超长数字不会被截成错误的 id`() {
        // 1–8 位：9 位以上拒绝，避免把任意长数字当成合法 id
        assertNull(AniListIdParser.parse("123456789"))
    }
}
