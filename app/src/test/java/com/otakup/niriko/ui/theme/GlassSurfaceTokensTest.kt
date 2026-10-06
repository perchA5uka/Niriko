package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索展开表面（F03）的材质判据。
 *
 * 这一条以前只能靠「展开看一眼、收起看一眼、弹键盘再看一眼」判断，现在把规则写成了函数：
 * 收起更透 / 展开更实 / 玻璃关闭时接近实心 / 浅色是白系垫层。
 */
class GlassSurfaceTokensTest {

    @Test
    fun `收起比展开更透明_展开要压住滚动内容`() {
        val collapsed = GlassSurfaceTokens.searchBackingAlpha(expanded = false, glassEnabled = true)
        val expanded = GlassSurfaceTokens.searchBackingAlpha(expanded = true, glassEnabled = true)
        assertTrue("展开态的垫层必须比收起态更实：$expanded vs $collapsed", expanded > collapsed)
    }

    @Test
    fun `展开态必须达到可读性下限`() {
        assertTrue(GlassSurfaceTokens.expandedBackingIsLegible(glassEnabled = true))
        assertTrue(GlassSurfaceTokens.expandedBackingIsLegible(glassEnabled = false))
        val expanded = GlassSurfaceTokens.searchBackingAlpha(expanded = true, glassEnabled = true)
        assertTrue(
            "展开态低于下限会让滚动内容透上来：$expanded",
            expanded >= GlassSurfaceTokens.MIN_EXPANDED_BACKING_ALPHA,
        )
    }

    @Test
    fun `关闭玻璃档位时用接近实心的底`() {
        val offExpanded = GlassSurfaceTokens.searchBackingAlpha(expanded = true, glassEnabled = false)
        val offCollapsed = GlassSurfaceTokens.searchBackingAlpha(expanded = false, glassEnabled = false)
        assertTrue("没有折射可用时必须接近实心：$offExpanded", offExpanded >= 0.95f)
        assertTrue("收起态同样要比玻璃档位更实：$offCollapsed", offCollapsed >= 0.88f)
        assertTrue(GlassSurfaceTokens.backingIsOpaqueFallback(glassEnabled = false))
        assertFalse(GlassSurfaceTokens.backingIsOpaqueFallback(glassEnabled = true))
        // 关闭档位时两种状态都必须比开启时更实
        assertTrue(offExpanded > GlassSurfaceTokens.searchBackingAlpha(true, true))
        assertTrue(offCollapsed > GlassSurfaceTokens.searchBackingAlpha(false, true))
    }

    @Test
    fun `浅色是白系垫层_深色是黑系垫层`() {
        val light = GlassSurfaceTokens.searchBackingColor(isDark = false, alpha = 0.72f)
        assertTrue("浅色垫层必须是白系：$light", light.red > 0.95f && light.green > 0.95f && light.blue > 0.95f)
        assertEquals(0.72f, light.alpha, 1f / 255f)

        val dark = GlassSurfaceTokens.searchBackingColor(isDark = true, alpha = 0.72f)
        assertTrue("深色垫层必须是黑系：$dark", dark.red < 0.05f && dark.green < 0.05f && dark.blue < 0.05f)
        assertEquals(0.72f, dark.alpha, 1f / 255f)
    }

    @Test
    fun `垫层不透明度始终在可用区间内`() {
        for (expanded in listOf(true, false)) {
            for (glass in listOf(true, false)) {
                val alpha = GlassSurfaceTokens.searchBackingAlpha(expanded, glass)
                assertTrue("alpha=$alpha 超出 (0,1)", alpha > 0f && alpha <= 1f)
            }
        }
    }

    /** 文字在垫层上必须仍然达标（垫层是内容层的实际背景）。 */
    @Test
    fun `垫层上的正文与次要文字都达标`() {
        val onSurfaceLight = Color(0xFF1D1B20)
        val onSurfaceDark = Color(0xFFE6E1E5)
        val onSurfaceVariantLight = Color(0xFF49454F)
        val onSurfaceVariantDark = Color(0xFFCAC4D0)
        for (isDark in listOf(false, true)) {
            val alpha = GlassSurfaceTokens.searchBackingAlpha(expanded = true, glassEnabled = true)
            val backing = GlassSurfaceTokens.searchBackingColor(isDark, alpha)
            // 垫层是不透明的最终背景：把页面底色也合进来（垫层本身可能仍带 alpha）
            val page = if (isDark) Color(0xFF141218) else Color(0xFFFDFBFF)
            val effective = GlassChipTokens.compositeOver(backing, page)
            val primary = if (isDark) onSurfaceDark else onSurfaceLight
            val secondary = if (isDark) onSurfaceVariantDark else onSurfaceVariantLight
            assertTrue(
                "正文对比度不足（isDark=$isDark）：${GlassChipTokens.contrastRatio(primary, effective)}",
                GlassChipTokens.contrastRatio(primary, effective) >= GlassChipTokens.MIN_CONTRAST,
            )
            assertTrue(
                "次要文字对比度不足（isDark=$isDark）：${GlassChipTokens.contrastRatio(secondary, effective)}",
                GlassChipTokens.contrastRatio(secondary, effective) >= GlassChipTokens.MIN_CONTRAST,
            )
        }
    }
}
