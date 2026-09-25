package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义主题色（HCT TONAL_SPOT 配方）正确性验证。
 * HCT 参考值取自 Google material-color-utilities 官方测试用例。
 */
class SeedColorSchemeTest {

    private fun toneOf(argb: Int): Double = Hct.fromInt(argb).tone

    @Test
    fun hctReferenceValuesMatchGoogleTestVectors() {
        // 官方参考：红 hue≈27.41 chroma≈113.36 tone≈53.24
        val red = Hct.fromInt(0xFFFF0000.toInt())
        assertEquals(27.41, red.hue, 0.5)
        assertEquals(113.36, red.chroma, 1.5)
        assertEquals(53.24, red.tone, 0.5)
        // 绿 hue≈142.14 chroma≈108.41 tone≈87.74
        val green = Hct.fromInt(0xFF00FF00.toInt())
        assertEquals(142.14, green.hue, 0.5)
        assertEquals(108.41, green.chroma, 1.5)
        assertEquals(87.74, green.tone, 0.5)
        // 蓝 hue≈282.79 chroma≈87.23 tone≈32.30
        val blue = Hct.fromInt(0xFF0000FF.toInt())
        assertEquals(282.79, blue.hue, 0.5)
        assertEquals(87.23, blue.chroma, 1.5)
        assertEquals(32.30, blue.tone, 0.5)
    }

    @Test
    fun lightSchemeUsesM3ToneMappingWithGuaranteedContrast() {
        val scheme = SeedColorScheme.light(Color(0xFF2E7D32))
        // primary=tone40 / onPrimary=tone100：tone 差 60 → 对比度 ≥ 4.5（HCT 保证）
        // 容差 0.3：色阶经 sRGB 量化后 tone 有 ≤0.11 的偏移（官方实现固有）
        assertEquals(40.0, toneOf(scheme.primary.toArgb()), 0.3)
        assertEquals(100.0, toneOf(scheme.onPrimary.toArgb()), 0.3)
        assertEquals(90.0, toneOf(scheme.primaryContainer.toArgb()), 0.3)
        assertEquals(30.0, toneOf(scheme.onPrimaryContainer.toArgb()), 0.3)
    }

    @Test
    fun darkSchemeUsesM3DarkToneMapping() {
        val scheme = SeedColorScheme.dark(Color(0xFF2E7D32), oledDark = false)
        assertEquals(80.0, toneOf(scheme.primary.toArgb()), 0.3)
        assertEquals(20.0, toneOf(scheme.onPrimary.toArgb()), 0.3)
        assertEquals(30.0, toneOf(scheme.primaryContainer.toArgb()), 0.3)
        assertEquals(90.0, toneOf(scheme.onPrimaryContainer.toArgb()), 0.3)
    }

    @Test
    fun oledDarkUsesPureBlackBackground() {
        val scheme = SeedColorScheme.dark(Color(0xFF2E7D32), oledDark = true)
        assertEquals(0xFF000000.toInt(), scheme.background.toArgb())
        assertEquals(0xFF000000.toInt(), scheme.surface.toArgb())
    }

    @Test
    fun differentSeedsProduceDifferentSchemes() {
        val green = SeedColorScheme.light(Color(0xFF2E7D32))
        val blue = SeedColorScheme.light(Color(0xFF1565C0))
        assertNotEquals(green.primary.toArgb(), blue.primary.toArgb())
        assertNotEquals(green.tertiary.toArgb(), blue.tertiary.toArgb())
    }

    @Test
    fun tertiaryHueRotates60DegreesFromSeed() {
        val seed = Color(0xFF2E7D32)
        val seedHue = Hct.fromInt(seed.toArgb()).hue
        val scheme = SeedColorScheme.light(seed)
        val tertiaryHue = Hct.fromInt(scheme.tertiary.toArgb()).hue
        // TONAL_SPOT：tertiary 色相 = 种子色相 + 60°（容差含 chroma 映射量化）
        val diff = (tertiaryHue - seedHue + 360.0) % 360.0
        assertTrue("tertiary hue should be seed+60 (got diff=" + diff + ")", kotlin.math.abs(diff - 60.0) < 6.0)
    }

    @Test
    fun neutralBackgroundsStayFixedRegardlessOfSeed() {
        val a = SeedColorScheme.light(Color(0xFF2E7D32))
        val b = SeedColorScheme.light(Color(0xFFE53935))
        assertEquals(a.background.toArgb(), b.background.toArgb())
        assertEquals(a.surface.toArgb(), b.surface.toArgb())
    }
}
