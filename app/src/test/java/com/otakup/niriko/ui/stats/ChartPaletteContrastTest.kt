package com.otakup.niriko.ui.stats

import androidx.compose.ui.graphics.Color
import com.otakup.niriko.ui.theme.GlassChipTokens
import com.otakup.niriko.ui.theme.NeutralBackgroundDark
import com.otakup.niriko.ui.theme.NeutralSurfaceLight
import com.otakup.niriko.ui.theme.chartHueColorFor
import com.otakup.niriko.ui.theme.chartPaletteFor
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图表线色对比度护栏（V-19 配套；task-2 第 6 项）。
 *
 * 为什么用纯函数而不是截图：图表线色是从 [androidx.compose.material3.MaterialTheme.colorScheme.primary]
 * 经 HCT 派生的，截图只能验证"当前这一个种子色"，换主题色（用户可任选种子）就可能失守。
 * 这里直接调生产用的派生本体 [chartPaletteFor] / [chartHueColorFor]（Lead 已把私有实现
 * 暴露为 internal 纯函数，测试不复制派生逻辑，因此不会变成"测自己写的那份"的假护栏）。
 *
 * 判据取 WCAG 大字号/图形元素的 3:1（非正文 4.5:1）：折线、柱条是图形元素，
 * 3:1 是"看得见一条线"的下限；低于 3:1 时深色壁纸/浅色壁纸下会与背景糊在一起。
 * 背景取主题的中性 surface 常量（NeutralSurfaceLight / NeutralBackgroundDark）——
 * 图表卡是 appleGlassCard 的瓷面，最坏情况下（玻璃关闭、壁纸纯色）就落在该中性底上。
 */
class ChartPaletteContrastTest {

    /** 代表性种子：主题预设色板 + 高饱和纯色（最坏情况是色相极端、色度很高）。 */
    private val seeds = listOf(
        Color(0xFF2E7D32),
        Color(0xFF1565C0),
        Color(0xFF6A1B9A),
        Color(0xFFEF6C00),
        Color(0xFFC62828),
        Color(0xFF00838F),
        Color(0xFF00FF00),
        Color(0xFFFF00FF),
    )

    private fun assertLineColorsReadable(primary: Color, dark: Boolean) {
        val background = if (dark) NeutralBackgroundDark else NeutralSurfaceLight
        // 柱图/折线默认色 = 主题色板第 0 位；强调线 = 色相 +60（chartBarAccentColor 的纯函数本体）。
        val lineColors = listOf(
            "palette[0]" to chartPaletteFor(primary, dark)[0],
            "hue+60" to chartHueColorFor(primary, dark, 60f),
        )
        lineColors.forEach { (name, color) ->
            val ratio = GlassChipTokens.contrastRatio(color, background)
            assertTrue(
                "图表线色 $name 对背景对比度 $ratio 低于 3:1（dark=$dark, seed=$primary）；" +
                    "换主题种子后折线会与卡面糊在一起",
                ratio >= 3f,
            )
        }
    }

    @Test
    fun `浅色主题两条线色对比度至少3比1`() {
        seeds.forEach { assertLineColorsReadable(it, dark = false) }
    }

    @Test
    fun `深色主题两条线色对比度至少3比1`() {
        seeds.forEach { assertLineColorsReadable(it, dark = true) }
    }

    @Test
    fun `色板派生是纯函数且与缓存无关`() {
        // 同一输入第二次调用必须返回同一结果（内部有 ConcurrentHashMap 缓存，
        // 若哪天缓存 key 写错，换主题色后图表会拿到上一个种子的颜色）。
        val first = chartPaletteFor(Color(0xFF2E7D32), dark = false)
        val second = chartPaletteFor(Color(0xFF2E7D32), dark = false)
        assertTrue(first == second)
        // 不同种子必须得到不同色板（缓存不能把单一种子"粘"在全局）。
        val other = chartPaletteFor(Color(0xFFC62828), dark = false)
        assertTrue(first != other)
    }
}
