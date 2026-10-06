package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 半调网点（F18）的纯规则测试。
 *
 * §11.3 的三条约束在这里变成可断言的判据：**按类型**（只有书籍/漫画）、
 * **轻量**（强度有上限，浅色更淡）、**可独立关闭**（总开关关掉即完全不画）。
 * 「不遮挡正文 / 不拦点击」由画法保证（点阵画在内容之下），不是运行时判断。
 */
class HalftonePolicyTest {

    @Test
    fun `只有书籍与漫画自动启用`() {
        assertTrue(HalftonePolicy.shouldApply(SubjectType.BOOK, enabled = true))
        assertTrue(HalftonePolicy.shouldApply(SubjectType.MANGA, enabled = true))
        assertFalse(HalftonePolicy.shouldApply(SubjectType.ANIME, enabled = true))
        assertFalse(HalftonePolicy.shouldApply(SubjectType.GAME, enabled = true))
        assertFalse(HalftonePolicy.shouldApply(SubjectType.MUSIC, enabled = true))
        assertFalse(HalftonePolicy.shouldApply(SubjectType.REAL, enabled = true))
        assertFalse("类型未知不画", HalftonePolicy.shouldApply(null, enabled = true))
        assertEquals(setOf(SubjectType.BOOK, SubjectType.MANGA), HalftonePolicy.supportedTypes)
    }

    @Test
    fun `总开关关掉即完全不画`() {
        assertFalse(HalftonePolicy.shouldApply(SubjectType.MANGA, enabled = false))
        assertFalse(HalftonePolicy.shouldApply(SubjectType.BOOK, enabled = false))
    }

    @Test
    fun `强度有上限_且浅色比深色更淡`() {
        val light = HalftonePolicy.alphaFor(isDark = false)
        val dark = HalftonePolicy.alphaFor(isDark = true)
        assertTrue("浅色主题更淡：$light", light < dark)
        assertTrue("深色也不能超上限：$dark", dark <= HalftonePolicy.MAX_ALPHA)
        assertTrue("浅色必须有可见强度（> 0）", light > 0f)
        // 这条是 §11.3 的底线：强度再大就会影响正文可读性
        assertTrue(HalftonePolicy.MAX_ALPHA <= 0.12f)
    }

    @Test
    fun `点阵间距在可辨识区间内`() {
        assertTrue(HalftonePolicy.isSpacingSane())
        assertTrue(HalftonePolicy.isSpacingSane(8f))
        assertTrue(HalftonePolicy.isSpacingSane(20f))
        assertFalse("太密会变成灰幕", HalftonePolicy.isSpacingSane(3f))
        assertFalse("太疏看不出网点", HalftonePolicy.isSpacingSane(40f))
    }

    @Test
    fun `点数上界可控_1080p 手机不会画出十万个点`() {
        // 常见手机：约 393 × 830 dp 的可绘制区域
        val phone = HalftonePolicy.maxDotCount(393f, 830f)
        assertTrue("点数是 $phone", phone in 1..4000)
        // 平板：约 840 × 1200 dp
        val tablet = HalftonePolicy.maxDotCount(840f, 1200f)
        assertTrue("平板点数 $tablet", tablet in 1..10000)
        // 空尺寸不画
        assertEquals(0, HalftonePolicy.maxDotCount(0f, 800f))
        assertEquals(0, HalftonePolicy.maxDotCount(400f, 0f))
        // 非法间距不会除零
        assertTrue(HalftonePolicy.maxDotCount(400f, 800f, spacingDp = 0f) > 0)
    }
}
