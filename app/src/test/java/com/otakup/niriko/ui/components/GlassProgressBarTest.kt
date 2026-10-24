package com.otakup.niriko.ui.components

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6：开关位移 / 进度收敛两个纯函数 + 三处替换的源码契约。
 *
 * 纯函数部分钉住「圆钮落点」与「越界/NaN 收敛」；契约部分防止有人把 M3 控件又加回来
 * （本批次的核心可观测差异就是：设置行与两处进度条不再走 Material3）。
 */
class GlassProgressBarTest {

    private fun source(file: String) = File("src/main/java/com/otakup/niriko/" + file).readText()

    // ── 开关位移 ─────────────────────────────────────────────

    @Test
    fun thumbOffsetMovesExactlyTheInsetTrackSpan() {
        // 52 轨道 / 24 圆钮 / 两侧各 3 内缩 ⇒ 22dp
        assertEquals(22.dp, glassToggleThumbOffset(52.dp, 24.dp, 3.dp, checked = true))
        assertEquals(0.dp, glassToggleThumbOffset(52.dp, 24.dp, 3.dp, checked = false))
    }

    @Test
    fun thumbOffsetNeverGoesNegative() {
        // 轨道比圆钮还窄（异常尺寸）⇒ 夹到 0，而不是负偏移把圆钮推出轨道左侧
        assertEquals(0.dp, glassToggleThumbOffset(10.dp, 24.dp, 3.dp, checked = true))
        assertEquals(0.dp, glassToggleThumbOffset(24.dp, 24.dp, 0.dp, checked = true))
    }

    // ── 进度收敛 ─────────────────────────────────────────────

    @Test
    fun progressFractionClampsIntoUnitRange() {
        assertEquals(0f, progressFraction(-1f), 0f)
        assertEquals(0f, progressFraction(0f), 0f)
        assertEquals(0.25f, progressFraction(0.25f), 0f)
        assertEquals(1f, progressFraction(1f), 0f)
        assertEquals(1f, progressFraction(2f), 0f)
    }

    @Test
    fun progressFractionTreatsNaNAsZero() {
        // fillMaxWidth(NaN) 会抛 IllegalArgumentException，必须在入口收敛
        assertEquals(0f, progressFraction(Float.NaN), 0f)
    }

    // ── 源码契约：三处替换不能回潮 ────────────────────────────

    @Test
    fun settingsSwitchRowUsesGlassToggleInsteadOfMaterialSwitch() {
        val text = source("ui/settings/SettingsItem.kt")
        assertTrue(text.contains("GlassToggle("))
        assertFalse("设置行不得再直接用 M3 Switch", text.contains("Switch("))
    }

    @Test
    fun statsAndCardProgressUseGlassProgressBar() {
        val stats = source("ui/stats/StatsScreen.kt")
        assertTrue(stats.contains("GlassProgressBar("))
        assertFalse(stats.contains("LinearProgressIndicator("))

        val card = source("ui/subject/UniversalSubjectCard.kt")
        assertTrue(card.contains("GlassProgressBar("))
        assertFalse(card.contains("LinearProgressIndicator("))
    }
}
