package com.otakup.niriko.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus

/**
 * 图表色板：从主题 primary（HCT）派生 6 色类比板（P0a）。
 *
 * 取代 StatsScreen 里游离的字面量色。换主题色 → 图表自动协调；
 * 深色模式 tone 整体上移（70–80），避免在深色壁纸/深色 surface 上发暗。
 */
@Composable
fun chartStatusColor(status: WatchStatus): Color = statusTone(status).accent

/** 作品类型 → 主题派生色（相邻近似色 + 一个补色）。 */
@Composable
fun chartTypeColor(type: SubjectType): Color {
    val palette = themeChartPalette()
    return when (type) {
        SubjectType.ANIME -> palette[0]
        SubjectType.MANGA -> palette[1]
        SubjectType.BOOK -> palette[2]
        SubjectType.GAME -> palette[3]
        SubjectType.MUSIC -> palette[4]
        SubjectType.REAL -> palette[5]
        SubjectType.PERSON -> palette[5]
        SubjectType.OTHER -> palette[5]
    }
}

/** 通用柱状图主色 / 强调色。 */
@Composable
fun chartBarDefaultColor(): Color = themeChartPalette()[0]

/** 强调色：主题绿 hue+60（青蓝），绕开黄绿泥区。 */
@Composable
fun chartBarAccentColor(): Color {
    val primary = MaterialTheme.colorScheme.primary
    val isDark = LocalDarkTheme.current
    return rememberChartHueColor(primary, isDark, 60f)
}

private val hueColorCache = java.util.concurrent.ConcurrentHashMap<Triple<Color, Boolean, Float>, Color>()

private fun rememberChartHueColor(primary: Color, dark: Boolean, delta: Float): Color {
    val key = Triple(primary, dark, delta)
    hueColorCache[key]?.let { return it }
    val hct = Hct.fromInt(primary.toArgb())
    val hue = ((hct.hue + delta) % 360f + 360f) % 360f
    val tone = if (dark) 76.0 else 46.0
    val chroma = if (dark) 56.0 else 50.0
    val color = Color(TonalPalette.fromHueAndChroma(hue, chroma).tone(tone.toInt()))
    hueColorCache[key] = color
    return color
}

/** 主题派生的 6 色板（hue −40/−20/0/+20/+40/+180）。 */
@Composable
fun themeChartPalette(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    val isDark = LocalDarkTheme.current
    return rememberChartPalette(primary, isDark)
}

private val chartCache = java.util.concurrent.ConcurrentHashMap<Pair<Color, Boolean>, List<Color>>()

private fun rememberChartPalette(primary: Color, dark: Boolean): List<Color> {
    val key = primary to dark
    chartCache[key]?.let { return it }
    val hct = Hct.fromInt(primary.toArgb())
    val hue = hct.hue
    val tones = if (dark) doubleArrayOf(78.0, 74.0, 80.0, 72.0, 76.0, 70.0) else
        doubleArrayOf(46.0, 44.0, 48.0, 42.0, 50.0, 38.0)
    val chroma = if (dark) 56.0 else 50.0
    // 绿(hue≈0) → 青(+40) → 蓝(+95) → 蓝紫(+150) → 紫(+210) → 红橙(+270)，
    // 完整绕开"橄榄泥"黄绿区（原 -40 落在 hue≈88 的黄绿）。
    val deltas = intArrayOf(0, 40, 95, 150, 210, 270)
    val colors = deltas.mapIndexed { i, delta ->
        val h = ((hue + delta) % 360f + 360f) % 360f
        Color(TonalPalette.fromHueAndChroma(h, chroma).tone(tones[i].toInt()))
    }
    chartCache[key] = colors
    return colors
}
