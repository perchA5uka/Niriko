package com.otakup.niriko.ui.components

import androidx.compose.ui.graphics.Color
import com.otakup.niriko.ui.theme.GlassChipTokens
import com.otakup.niriko.ui.theme.NeutralSurfaceDark
import com.otakup.niriko.ui.theme.NeutralSurfaceVariantDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2-3：滑块圆钮的对比度回归（深色主题下圆钮不能消失在轨道里）。
 *
 * 改造前圆钮与轨道同源（都是 surfaceContainerHighest），只剩 0.5dp / 40% 的描边能区分。
 * 这里用真实的中性色令牌把「圆钮 ∘ 轨道」的对比度算成一个确切数字，守住 ≥3:1（非文本图形元素）。
 */
class GlassSliderThumbContrastTest {

    @Test
    fun darkThumbIsPureWhite() {
        val lightValue = Color(0xFF123456)
        assertEquals("深色主题必须改用白圆钮", Color.White, glassSliderThumbColor(isDark = true, lightThumb = lightValue))
    }

    @Test
    fun lightThumbKeepsTheExistingSurfaceColor() {
        val lightValue = Color(0xFF123456)
        assertEquals("浅色主题保持原观感（surfaceContainerHighest）", lightValue, glassSliderThumbColor(isDark = false, lightThumb = lightValue))
    }

    @Test
    fun darkThumbClearsThreeToOneAgainstTheTrack() {
        val surface = NeutralSurfaceDark
        val trackRaw = NeutralSurfaceVariantDark.copy(alpha = 0.55f)
        val track = GlassChipTokens.compositeOver(trackRaw, surface)
        val thumb = glassSliderThumbColor(isDark = true, lightThumb = NeutralSurfaceVariantDark)
        val ratio = GlassChipTokens.contrastRatio(thumb, track)
        assertTrue("深色圆钮对轨道的对比度 $ratio 低于 3:1", ratio >= GLASS_SLIDER_THUMB_MIN_CONTRAST)
    }
}
