package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class BadgeSemanticColorTest {
    @Test fun redAndAmberRemainChromaticOnStableDarkBadgeBacking() {
        val backdrop = GlassChipTokens.worstCaseBackdrop(true, Color.Black.copy(alpha = 0.80f))
        val red = GlassChipTokens.ensureReadable(Color(0xFFDC2626), backdrop, 3f)
        val amber = GlassChipTokens.ensureReadable(Color(0xFFF59E0B), backdrop)
        assertTrue(red.red - red.green > 0.35f)
        assertTrue(amber.red - amber.blue > 0.35f)
        assertTrue(GlassChipTokens.contrastRatio(red, backdrop) >= 3f)
        assertTrue(GlassChipTokens.contrastRatio(amber, backdrop) >= 4.5f)
    }
    @Test fun moderateBackdropChoosesTheActuallyMoreReadablePolarity() {
        val background = Color(0.62f, 0.62f, 0.62f)
        val color = GlassChipTokens.ensureReadable(Color(0xFFF59E0B), background)
        assertTrue(GlassChipTokens.contrastRatio(color, background) >= 4.5f)
        assertTrue(color.red > color.blue)
    }
}
