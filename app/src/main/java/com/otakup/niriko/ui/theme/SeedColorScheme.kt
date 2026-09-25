package com.otakup.niriko.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette

/**
 * 任意种子色 → 完整 Material 3 配色方案。
 *
 * 采用 M3 默认 TONAL_SPOT 配方（Android 12 动态取色同款）：
 * - primaryPalette：种子色相，chroma 固定 36（保证低饱和种子也有品牌感）
 * - secondaryPalette：种子色相，chroma 16（衬托色，更安静）
 * - tertiaryPalette：种子色相 +60°，chroma 24（点缀色，邻近对比）
 * - 中性背景/表面：沿用项目固定中性色板（Neutral*），避免低质量种子色污染背景
 *   与玻璃卡层次（换主题色不动背景是本项目的既定视觉策略）
 *
 * 色阶映射遵循 M3 规范：浅色 primary=tone40 / container=tone90，
 * 深色 primary=tone80 / container=tone30，对比度由 HCT tone（L*）保证。
 */
object SeedColorScheme {

    /** 用户未设置自定义色时的默认种子（品牌绿）。 */
    val DefaultSeed: Color = GreenPrimaryLight

    /** 种子色是否为"未设置"哨兵值。 */
    const val UnsetSeed: Int = -1

    /** 由种子色生成浅色配色。 */
    fun light(seed: Color): ColorScheme {
        val palettes = palettesOf(seed)
        return lightColorScheme(
            primary = Color(palettes.primary.tone(40)),
            onPrimary = Color(palettes.primary.tone(100)),
            primaryContainer = Color(palettes.primary.tone(90)),
            onPrimaryContainer = Color(palettes.primary.tone(30)),
            secondary = Color(palettes.secondary.tone(40)),
            onSecondary = Color(palettes.secondary.tone(100)),
            secondaryContainer = Color(palettes.secondary.tone(90)),
            onSecondaryContainer = Color(palettes.secondary.tone(30)),
            tertiary = Color(palettes.tertiary.tone(40)),
            onTertiary = Color(palettes.tertiary.tone(100)),
            tertiaryContainer = Color(palettes.tertiary.tone(90)),
            onTertiaryContainer = Color(palettes.tertiary.tone(30)),
            // 中性背景/表面（与预置主题共用以维持玻璃层次）
            background = NeutralBackgroundLight,
            onBackground = NeutralOnBackgroundLight,
            surface = NeutralSurfaceLight,
            onSurface = NeutralOnSurfaceLight,
            surfaceVariant = NeutralSurfaceVariantLight,
            onSurfaceVariant = NeutralOnSurfaceVariantLight,
            outline = NeutralOutlineLight,
        )
    }

    /** 由种子色生成深色配色（[oledDark] 时背景/表面纯黑）。 */
    fun dark(seed: Color, oledDark: Boolean): ColorScheme {
        val palettes = palettesOf(seed)
        return darkColorScheme(
            primary = Color(palettes.primary.tone(80)),
            onPrimary = Color(palettes.primary.tone(20)),
            primaryContainer = Color(palettes.primary.tone(30)),
            onPrimaryContainer = Color(palettes.primary.tone(90)),
            secondary = Color(palettes.secondary.tone(80)),
            onSecondary = Color(palettes.secondary.tone(20)),
            secondaryContainer = Color(palettes.secondary.tone(30)),
            onSecondaryContainer = Color(palettes.secondary.tone(90)),
            tertiary = Color(palettes.tertiary.tone(80)),
            onTertiary = Color(palettes.tertiary.tone(20)),
            tertiaryContainer = Color(palettes.tertiary.tone(30)),
            onTertiaryContainer = Color(palettes.tertiary.tone(90)),
            background = if (oledDark) Color(0xFF000000) else NeutralBackgroundDark,
            onBackground = NeutralOnBackgroundDark,
            surface = if (oledDark) Color(0xFF000000) else NeutralSurfaceDark,
            onSurface = NeutralOnSurfaceDark,
            surfaceVariant = if (oledDark) Color(0xFF1C1F1A) else NeutralSurfaceVariantDark,
            onSurfaceVariant = NeutralOnSurfaceVariantDark,
            outline = NeutralOutlineDark,
        )
    }

    private data class SeedPalettes(
        val primary: TonalPalette,
        val secondary: TonalPalette,
        val tertiary: TonalPalette,
    )

    private fun palettesOf(seed: Color): SeedPalettes {
        val hct = Hct.fromInt(seed.toArgb())
        return SeedPalettes(
            primary = TonalPalette.fromHueAndChroma(hct.hue, 36.0),
            secondary = TonalPalette.fromHueAndChroma(hct.hue, 16.0),
            tertiary = TonalPalette.fromHueAndChroma((hct.hue + 60.0) % 360.0, 24.0),
        )
    }
}
