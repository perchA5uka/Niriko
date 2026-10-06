package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.ui.components.watchStatusColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 海报小件与标签胶囊材质（F05 + F02）的**机械**判据。
 *
 * §8.2 的验收是「共用材质且对比度达标」。对比度这条以前只能靠真机截图肉眼判断，
 * 现在它变成了可以在这里算出来的数：WCAG 2.x 比值。
 *
 * 判据分两类：
 * 1. 海报小件压在**图片**上 —— 图片亮度不可控，所以要求「压在纯黑图」与「压在纯白图」
 *    两个极端的合成结果上都达标（[GlassChipTokens.worstCaseBackdrop] 取的是文字那一侧的最坏值）。
 * 2. 页面标签压在**页面背景**上 —— 背景是已知色，直接按实际合成结果判定。
 */
class GlassChipTokensTest {

    /** 文字色 = 品牌色经 ensureReadable 调整后的颜色。 */
    private fun readableBadgeText(accent: Color, isDark: Boolean): Pair<Color, Color> {
        val base = GlassChipTokens.onImageBackground(isDark, imageLuminance = null, glassEnabled = true)
        val worst = GlassChipTokens.worstCaseBackdrop(isDark, base)
        return base to GlassChipTokens.ensureReadable(accent, worst)
    }

    @Test
    fun `浅色模式的小件是浅白底而不是黑纱`() {
        val light = GlassChipTokens.onImageBackground(isDark = false)
        // 白底：三个通道都接近 1
        assertTrue("浅色底必须是白系：$light", light.red > 0.95f && light.green > 0.95f && light.blue > 0.95f)
        assertTrue("浅色底必须仍然半透明（保留玻璃观感）：${light.alpha}", light.alpha in 0.6f..0.9f)

        val dark = GlassChipTokens.onImageBackground(isDark = true)
        assertTrue("深色底必须是黑系：$dark", dark.red < 0.05f && dark.green < 0.05f && dark.blue < 0.05f)
        assertTrue("深色底也必须半透明：${dark.alpha}", dark.alpha in 0.5f..0.8f)
    }

    @Test
    fun `关闭玻璃档位时用更实的底_可读性优先`() {
        val translucent = GlassChipTokens.onImageBackground(isDark = false, glassEnabled = true)
        val opaque = GlassChipTokens.onImageBackground(isDark = false, glassEnabled = false)
        assertTrue(
            "关闭玻璃必须更不透明：${opaque.alpha} vs ${translucent.alpha}",
            opaque.alpha > translucent.alpha,
        )
        assertFalse(GlassChipTokens.glassEnabled(GlassEffectLevel.OFF, CardGlassLevel.FULL))
        assertFalse(GlassChipTokens.glassEnabled(GlassEffectLevel.FULL, CardGlassLevel.OFF))
        assertTrue(GlassChipTokens.glassEnabled(GlassEffectLevel.FULL, CardGlassLevel.FULL))
    }

    @Test
    fun `底色随图片亮度自适应但幅度很小`() {
        val onDarkImage = GlassChipTokens.onImageBackground(isDark = false, imageLuminance = 0f)
        val onBrightImage = GlassChipTokens.onImageBackground(isDark = false, imageLuminance = 1f)
        // 浅底压在亮图上要更实一点
        assertTrue("亮图上的浅底应该更不透明", onBrightImage.alpha > onDarkImage.alpha)
        // 但幅度受控：全量程摆幅就是 LUMINANCE_ADAPT_RANGE（小件是装饰层）。
        // 容差 1/255：底色由 Color.White/Color.Black 派生，它们是 8 位打包表示，
        // 因此 alpha 会被量化到 1/255（0.86 → 219/255）。这是 Compose 的真实行为，不是误差需要掩盖。
        assertEquals(
            GlassChipTokens.LUMINANCE_ADAPT_RANGE,
            onBrightImage.alpha - onDarkImage.alpha,
            1f / 255f,
        )
        // 极端值被夹住，不会出现「完全透明」或「完全不透明」
        assertTrue(onDarkImage.alpha >= 0.55f && onBrightImage.alpha <= 0.95f)
    }

    /** 四个小件用到的每个品牌色，在两种主题的最坏合成底上都必须 ≥ 4.5:1。 */
    @Test
    fun `每个状态色的文字在两种主题下都达到 AA`() {
        val accents = WatchStatus.entries.map { it to watchStatusColor(it) }.toMap()
        for ((status, accent) in accents) {
            for (isDark in listOf(false, true)) {
                val (base, text) = readableBadgeText(accent, isDark)
                val background = GlassChipTokens.worstCaseBackdrop(isDark, base)
                val ratio = GlassChipTokens.contrastRatio(text, background)
                assertTrue(
                    "状态 $status（isDark=$isDark）对比度 $ratio 不足 4.5",
                    ratio >= GlassChipTokens.MIN_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `评分与收藏心颜色同样达标`() {
        val amber = Color(0xFFF59E0B)
        val red = Color(0xFFDC2626)
        val blue = Color(0xFF3B82F6)
        for (accent in listOf(amber, red, blue)) {
            for (isDark in listOf(false, true)) {
                val (base, text) = readableBadgeText(accent, isDark)
                val background = GlassChipTokens.worstCaseBackdrop(isDark, base)
                assertTrue(
                    "accent=$accent isDark=$isDark 对比度 ${GlassChipTokens.contrastRatio(text, background)} 不足",
                    GlassChipTokens.contrastRatio(text, background) >= GlassChipTokens.MIN_CONTRAST,
                )
            }
        }
    }

    /** 品牌色在需要时会**被压暗/提亮**，且色相方向保持（只调明度，不做去饱和）。 */
    @Test
    fun `ensureReadable 只调明度而且真的提高了对比度`() {
        val amber = Color(0xFFF59E0B)
        val lightBase = GlassChipTokens.onImageBackground(isDark = false)
        val lightWorst = GlassChipTokens.worstCaseBackdrop(false, lightBase)
        // 琥珀在浅底上本来不达标 —— 这正是旧实现（浅色黑纱）绕开的那个组合
        assertTrue(
            "前提：琥珀在浅白底上不达标",
            GlassChipTokens.contrastRatio(amber, lightWorst) < GlassChipTokens.MIN_CONTRAST,
        )
        val fixed = GlassChipTokens.ensureReadable(amber, lightWorst)
        assertTrue(GlassChipTokens.contrastRatio(fixed, lightWorst) >= GlassChipTokens.MIN_CONTRAST)
        // 只调明度：仍明显偏暖（红 > 蓝），没有变成灰
        assertTrue("调完必须还是暖色：$fixed", fixed.red > fixed.blue)
        // 已经达标的颜色应原样返回（不做无意义的改动）
        assertEquals(amber, GlassChipTokens.ensureReadable(amber, Color.Black))
    }

    @Test
    fun `页面标签在两种页面背景上都达标`() {
        // 浅色页面：近白背景；深色页面：近黑背景
        val lightPage = Color(0xFFFDFBFF)
        val darkPage = Color(0xFF141218)
        for (isDark in listOf(false, true)) {
            val page = if (isDark) darkPage else lightPage
            for (emphatic in listOf(false, true)) {
                val base = GlassChipTokens.surfaceChipBackground(isDark, emphatic, glassEnabled = true)
                val effective = GlassChipTokens.compositeOver(base, page)
                val text = GlassChipTokens.ensureReadable(if (isDark) Color(0xFFE6E1E5) else Color(0xFF1D1B20), effective)
                val ratio = GlassChipTokens.contrastRatio(text, effective)
                assertTrue(
                    "标签文字在页面背景上不达标（isDark=$isDark emphatic=$emphatic）：$ratio",
                    ratio >= GlassChipTokens.MIN_CONTRAST,
                )
            }
        }
        // 强调态的底必须比普通态更实（可分辨），否则「强调」是空话
        val plain = GlassChipTokens.surfaceChipBackground(true, false, true)
        val strong = GlassChipTokens.surfaceChipBackground(true, true, true)
        assertTrue(strong.alpha > plain.alpha)
    }

    @Test
    fun `对比度计算符合 WCAG 基准值`() {
        // 黑白 = 21:1，同色 = 1:1 —— 这两个基准错了说明公式整体不对
        assertEquals(21f, GlassChipTokens.contrastRatio(Color.Black, Color.White), 0.05f)
        assertEquals(1f, GlassChipTokens.contrastRatio(Color.Red, Color.Red), 0.001f)
        // 对称
        assertEquals(
            GlassChipTokens.contrastRatio(Color(0xFF3B82F6), Color.White),
            GlassChipTokens.contrastRatio(Color.White, Color(0xFF3B82F6)),
            0.001f,
        )
        // sRGB 合成：白 0.5 压纯黑 = 中灰
        val mid = GlassChipTokens.compositeOver(Color.White.copy(alpha = 0.5f), Color.Black)
        assertEquals(0.5f, mid.red, 0.01f)
        assertEquals(1f, mid.alpha, 0.001f)
    }
}
