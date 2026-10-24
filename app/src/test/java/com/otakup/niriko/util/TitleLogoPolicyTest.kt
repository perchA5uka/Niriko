package com.otakup.niriko.util

import com.otakup.niriko.data.remote.tmdb.dto.TmdbImageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标题 logo 选图 / 尺寸框 / 回退的纯规则测试（C · 13.1）。
 *
 * TMDb 的 logos 集合天然脏：有 svg、有 0 宽高、有没语言代码的、票数也可能为 0。
 * 这些用例就是把「脏数据不许上屏」写成断言。
 */
class TitleLogoPolicyTest {

    private fun logo(
        path: String? = "/x.png",
        lang: String? = null,
        w: Int = 1000,
        h: Int = 300,
        aspect: Double = 3.0,
        vote: Double? = 5.0,
        count: Int? = 10,
    ) = TmdbImageDto(
        filePath = path, languageCode = lang, width = w, height = h,
        aspectRatio = aspect, voteAverage = vote, voteCount = count,
    )

    @Test
    fun `界面语言优先于原语言`() {
        val zh = logo(lang = "zh", vote = 3.0)
        val ja = logo(lang = "ja", vote = 9.0)
        assertEquals(zh, TitleLogoPolicy.pick(listOf(ja, zh), uiLanguage = "zh-CN", originalLanguage = "ja"))
    }

    @Test
    fun `中文缺失时只用日文_其他语言不要`() {
        val ja = logo(lang = "ja", vote = 1.0)
        val en = logo(lang = "en", vote = 9.0)
        assertEquals(ja, TitleLogoPolicy.pick(listOf(en, ja), uiLanguage = "zh"))
        assertNull(TitleLogoPolicy.pick(listOf(en), uiLanguage = "zh"))
    }

    @Test
    fun `同语言按票均再票数排序`() {
        val low = logo(lang = "ja", vote = 5.0, count = 999)
        val high = logo(lang = "ja", vote = 6.0, count = 1)
        assertEquals(high, TitleLogoPolicy.pick(listOf(low, high), uiLanguage = "ja"))
        val more = logo(lang = "ja", vote = 5.0, count = 1000, path = "/more.png")
        val fewer = logo(lang = "ja", vote = 5.0, count = 1, path = "/fewer.png")
        assertEquals(more, TitleLogoPolicy.pick(listOf(fewer, more), uiLanguage = "ja"))
    }

    @Test
    fun `脏数据全部跳过`() {
        val svg = logo(path = "/a.svg", lang = "ja")
        val zeroW = logo(path = "/b.png", lang = "ja", w = 0)
        val zeroH = logo(path = "/c.png", lang = "ja", h = 0)
        val badAspect = logo(path = "/d.png", lang = "ja", aspect = 0.0)
        val noPath = logo(path = null, lang = "ja")
        assertNull(TitleLogoPolicy.pick(listOf(svg, zeroW, zeroH, badAspect, noPath), uiLanguage = "ja"))
    }

    @Test
    fun `无语言与其他语言 logo 都不使用`() {
        val neutral = logo(lang = null, vote = 9.9, path = "/neutral.png")
        // 无语言不再作为回退：没有中/日 logo 就显示文字
        assertNull(TitleLogoPolicy.pick(listOf(neutral), uiLanguage = "zh"))
        // 有 ja 命中时仍然选 ja
        val ja = logo(lang = "ja", vote = 1.0, path = "/ja.png")
        assertEquals(ja, TitleLogoPolicy.pick(listOf(neutral, ja), uiLanguage = "ja"))
    }

    @Test
    fun `宽度主导_高度只做护栏`() {
        // 正常比例：宽度就是传入值（调用方传海报宽的 2/3），高度按比例
        val normal = TitleLogoPolicy.boxFor(widthDp = 200f, aspectRatio = 3.0)
        assertEquals(200f, normal.widthDp, 0.01f)
        assertEquals(200f / 3f, normal.heightDp, 0.01f)
        // 极高的 logo（aspect 0.5）：高度被行高护栏夹住，宽度随之收缩
        val tall = TitleLogoPolicy.boxFor(widthDp = 200f, aspectRatio = 0.5)
        assertTrue("高度必须有上限：${tall.heightDp}", tall.heightDp <= 34f * 2.5f + 0.01f)
        assertEquals(0.5f, tall.widthDp / tall.heightDp, 0.01f)
        // 极扁的 logo（aspect 8）：宽高比原样保留
        val flat = TitleLogoPolicy.boxFor(widthDp = 200f, aspectRatio = 8.0)
        assertEquals(200f, flat.widthDp, 0.01f)
        assertEquals(25f, flat.heightDp, 0.01f)
    }

    @Test
    fun `三档回退矩阵`() {
        // OFF 永远回退
        assertTrue(TitleLogoPolicy.shouldFallbackToText(TitleLogoMode.OFF, hasLogo = true, contrastOk = true))
        // ALWAYS 有图就不回退（对比度不参与）
        assertFalse(TitleLogoPolicy.shouldFallbackToText(TitleLogoMode.ALWAYS, hasLogo = true, contrastOk = false))
        assertTrue(TitleLogoPolicy.shouldFallbackToText(TitleLogoMode.ALWAYS, hasLogo = false, contrastOk = true))
        // 第三档：看不清就回退
        assertTrue(TitleLogoPolicy.shouldFallbackToText(TitleLogoMode.TEXT_WHEN_UNCLEAR, hasLogo = true, contrastOk = false))
        assertFalse(TitleLogoPolicy.shouldFallbackToText(TitleLogoMode.TEXT_WHEN_UNCLEAR, hasLogo = true, contrastOk = true))
    }

    @Test
    fun `对比度与开关解析`() {
        assertEquals(1f, TitleLogoPolicy.contrastRatio(0.5f, 0.5f), 0.001f)
        assertTrue(TitleLogoPolicy.isContrastOk(1f, 0f))
        assertFalse(TitleLogoPolicy.isContrastOk(0.5f, 0.55f))
        assertEquals(TitleLogoMode.OFF, TitleLogoMode.fromKey(null))
        assertEquals(TitleLogoMode.OFF, TitleLogoMode.fromKey("garbage"))
        assertEquals(TitleLogoMode.TEXT_WHEN_UNCLEAR, TitleLogoMode.fromKey("text_when_unclear"))
        assertEquals(TitleLogoMode.ALWAYS, TitleLogoMode.fromKey(" ALWAYS "))
    }

    @Test
    fun `第三档在对比度边界上回退`() {
        // 边界两侧：6.5:1 视为可读（不回退），1.09:1 视为看不清（回退文字）。
        assertTrue(TitleLogoPolicy.isContrastOk(0.05f, 0.6f))
        assertFalse(TitleLogoPolicy.isContrastOk(0.5f, 0.55f))
        assertFalse(
            TitleLogoPolicy.shouldFallbackToText(
                TitleLogoMode.TEXT_WHEN_UNCLEAR,
                hasLogo = true,
                contrastOk = TitleLogoPolicy.isContrastOk(0.05f, 0.6f),
            ),
        )
        assertTrue(
            TitleLogoPolicy.shouldFallbackToText(
                TitleLogoMode.TEXT_WHEN_UNCLEAR,
                hasLogo = true,
                contrastOk = TitleLogoPolicy.isContrastOk(0.5f, 0.55f),
            ),
        )
        // 阈值附近用连续函数断言，避免浮点抖动导致「有时回退有时不回退」。
        assertTrue(TitleLogoPolicy.contrastRatio(0.05f, 0.45f) > TitleLogoPolicy.MIN_CONTRAST)
        assertTrue(TitleLogoPolicy.contrastRatio(0.5f, 0.55f) < TitleLogoPolicy.MIN_CONTRAST)
    }

    @Test
    fun `字号与宽高比边界下尺寸框仍合法`() {
        for (line in listOf(12f, 20f, 34f, 48f, 72f)) {
            for (aspect in listOf(0.5, 1.0, 2.19, 3.0, 6.0, 30.0)) {
                val box = TitleLogoPolicy.boxFor(widthDp = 240f, aspectRatio = aspect, lineHeightDp = line)
                assertTrue("宽必须为正", box.widthDp > 0f)
                assertTrue("高不低于最小高度", box.heightDp >= TitleLogoPolicy.MIN_HEIGHT_DP - 0.01f)
                assertTrue("高不超过两倍半行高", box.heightDp <= line * 2.5f + 0.01f)
                assertTrue("宽不超过目标宽度", box.widthDp <= 240f + 0.01f)
            }
        }
    }
}
