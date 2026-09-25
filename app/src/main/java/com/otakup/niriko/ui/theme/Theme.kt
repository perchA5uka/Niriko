package com.otakup.niriko.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 当前主题是否深色（由 NirikoTheme 的设置驱动 darkTheme 提供）。
 *
 * 组件不得直接读 [isSystemInDarkTheme]——那返回系统值,与 App 内手动选浅色/深色
 * (ThemeMode) 不一致会导致主题色与玻璃组件明暗错位(如浅色主题 + 深灰玻璃卡)。
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** 单套主题色的完整定义（亮/暗两套 + OLED 背景色）。 */
data class ThemePalette(
    val label: String,
    val light: ColorSchemeSeed,
    val dark: ColorSchemeSeed,
)

/** 一组色种（primary/secondary/tertiary 及容器色）。 */
data class ColorSchemeSeed(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

/** 预置主题色（下标即设置中的 themeColorIndex；0=绿色默认）。 */
val ThemePalettes: List<ThemePalette> = listOf(
    ThemePalette(
        label = "绿",
        light = ColorSchemeSeed(
            primary = GreenPrimaryLight, onPrimary = GreenOnPrimaryLight,
            primaryContainer = GreenPrimaryContainerLight, onPrimaryContainer = GreenOnPrimaryContainerLight,
            secondary = GreenSecondaryLight, onSecondary = GreenOnSecondaryLight,
            secondaryContainer = GreenSecondaryContainerLight, onSecondaryContainer = GreenOnSecondaryContainerLight,
            tertiary = GreenTertiaryLight, onTertiary = GreenOnTertiaryLight,
            tertiaryContainer = GreenTertiaryContainerLight, onTertiaryContainer = GreenOnTertiaryContainerLight,
        ),
        dark = ColorSchemeSeed(
            primary = GreenPrimaryDark, onPrimary = GreenOnPrimaryDark,
            primaryContainer = GreenPrimaryContainerDark, onPrimaryContainer = GreenOnPrimaryContainerDark,
            secondary = GreenSecondaryDark, onSecondary = GreenOnSecondaryDark,
            secondaryContainer = GreenSecondaryContainerDark, onSecondaryContainer = GreenOnSecondaryContainerDark,
            tertiary = GreenTertiaryDark, onTertiary = GreenOnTertiaryDark,
            tertiaryContainer = GreenTertiaryContainerDark, onTertiaryContainer = GreenOnTertiaryContainerDark,
        ),
    ),
    ThemePalette(
        label = "蓝",
        light = ColorSchemeSeed(
            primary = BluePrimaryLight, onPrimary = BlueOnPrimaryLight,
            primaryContainer = BluePrimaryContainerLight, onPrimaryContainer = BlueOnPrimaryContainerLight,
            secondary = BlueSecondaryLight, onSecondary = BlueOnSecondaryLight,
            secondaryContainer = BlueSecondaryContainerLight, onSecondaryContainer = BlueOnSecondaryContainerLight,
            tertiary = BlueTertiaryLight, onTertiary = BlueOnTertiaryLight,
            tertiaryContainer = BlueTertiaryContainerLight, onTertiaryContainer = BlueOnTertiaryContainerLight,
        ),
        dark = ColorSchemeSeed(
            primary = BluePrimaryDark, onPrimary = BlueOnPrimaryDark,
            primaryContainer = BluePrimaryContainerDark, onPrimaryContainer = BlueOnPrimaryContainerDark,
            secondary = BlueSecondaryDark, onSecondary = BlueOnSecondaryDark,
            secondaryContainer = BlueSecondaryContainerDark, onSecondaryContainer = BlueOnSecondaryContainerDark,
            tertiary = BlueTertiaryDark, onTertiary = BlueOnTertiaryDark,
            tertiaryContainer = BlueTertiaryContainerDark, onTertiaryContainer = BlueOnTertiaryContainerDark,
        ),
    ),
    ThemePalette(
        label = "紫",
        light = ColorSchemeSeed(
            primary = PurplePrimaryLight, onPrimary = PurpleOnPrimaryLight,
            primaryContainer = PurplePrimaryContainerLight, onPrimaryContainer = PurpleOnPrimaryContainerLight,
            secondary = PurpleSecondaryLight, onSecondary = PurpleOnSecondaryLight,
            secondaryContainer = PurpleSecondaryContainerLight, onSecondaryContainer = PurpleOnSecondaryContainerLight,
            tertiary = PurpleTertiaryLight, onTertiary = PurpleOnTertiaryLight,
            tertiaryContainer = PurpleTertiaryContainerLight, onTertiaryContainer = PurpleOnTertiaryContainerLight,
        ),
        dark = ColorSchemeSeed(
            primary = PurplePrimaryDark, onPrimary = PurpleOnPrimaryDark,
            primaryContainer = PurplePrimaryContainerDark, onPrimaryContainer = PurpleOnPrimaryContainerDark,
            secondary = PurpleSecondaryDark, onSecondary = PurpleOnSecondaryDark,
            secondaryContainer = PurpleSecondaryContainerDark, onSecondaryContainer = PurpleOnSecondaryContainerDark,
            tertiary = PurpleTertiaryDark, onTertiary = PurpleOnTertiaryDark,
            tertiaryContainer = PurpleTertiaryContainerDark, onTertiaryContainer = PurpleOnTertiaryContainerDark,
        ),
    ),
    ThemePalette(
        label = "橙",
        light = ColorSchemeSeed(
            primary = OrangePrimaryLight, onPrimary = OrangeOnPrimaryLight,
            primaryContainer = OrangePrimaryContainerLight, onPrimaryContainer = OrangeOnPrimaryContainerLight,
            secondary = OrangeSecondaryLight, onSecondary = OrangeOnSecondaryLight,
            secondaryContainer = OrangeSecondaryContainerLight, onSecondaryContainer = OrangeOnSecondaryContainerLight,
            tertiary = OrangeTertiaryLight, onTertiary = OrangeOnTertiaryLight,
            tertiaryContainer = OrangeTertiaryContainerLight, onTertiaryContainer = OrangeOnTertiaryContainerLight,
        ),
        dark = ColorSchemeSeed(
            primary = OrangePrimaryDark, onPrimary = OrangeOnPrimaryDark,
            primaryContainer = OrangePrimaryContainerDark, onPrimaryContainer = OrangeOnPrimaryContainerDark,
            secondary = OrangeSecondaryDark, onSecondary = OrangeOnSecondaryDark,
            secondaryContainer = OrangeSecondaryContainerDark, onSecondaryContainer = OrangeOnSecondaryContainerDark,
            tertiary = OrangeTertiaryDark, onTertiary = OrangeOnTertiaryDark,
            tertiaryContainer = OrangeTertiaryContainerDark, onTertiaryContainer = OrangeOnTertiaryContainerDark,
        ),
    ),
    ThemePalette(
        label = "红",
        light = ColorSchemeSeed(
            primary = RedPrimaryLight, onPrimary = RedOnPrimaryLight,
            primaryContainer = RedPrimaryContainerLight, onPrimaryContainer = RedOnPrimaryContainerLight,
            secondary = RedSecondaryLight, onSecondary = RedOnSecondaryLight,
            secondaryContainer = RedSecondaryContainerLight, onSecondaryContainer = RedOnSecondaryContainerLight,
            tertiary = RedTertiaryLight, onTertiary = RedOnTertiaryLight,
            tertiaryContainer = RedTertiaryContainerLight, onTertiaryContainer = RedOnTertiaryContainerLight,
        ),
        dark = ColorSchemeSeed(
            primary = RedPrimaryDark, onPrimary = RedOnPrimaryDark,
            primaryContainer = RedPrimaryContainerDark, onPrimaryContainer = RedOnPrimaryContainerDark,
            secondary = RedSecondaryDark, onSecondary = RedOnSecondaryDark,
            secondaryContainer = RedSecondaryContainerDark, onSecondaryContainer = RedOnSecondaryContainerDark,
            tertiary = RedTertiaryDark, onTertiary = RedOnTertiaryDark,
            tertiaryContainer = RedTertiaryContainerDark, onTertiaryContainer = RedOnTertiaryContainerDark,
        ),
    ),
    ThemePalette(
        label = "青",
        light = ColorSchemeSeed(
            primary = TealPrimaryLight, onPrimary = TealOnPrimaryLight,
            primaryContainer = TealPrimaryContainerLight, onPrimaryContainer = TealOnPrimaryContainerLight,
            secondary = TealSecondaryLight, onSecondary = TealOnSecondaryLight,
            secondaryContainer = TealSecondaryContainerLight, onSecondaryContainer = TealOnSecondaryContainerLight,
            tertiary = TealTertiaryLight, onTertiary = TealOnTertiaryLight,
            tertiaryContainer = TealTertiaryContainerLight, onTertiaryContainer = TealOnTertiaryContainerLight,
        ),
        dark = ColorSchemeSeed(
            primary = TealPrimaryDark, onPrimary = TealOnPrimaryDark,
            primaryContainer = TealPrimaryContainerDark, onPrimaryContainer = TealOnPrimaryContainerDark,
            secondary = TealSecondaryDark, onSecondary = TealOnSecondaryDark,
            secondaryContainer = TealSecondaryContainerDark, onSecondaryContainer = TealOnSecondaryContainerDark,
            tertiary = TealTertiaryDark, onTertiary = TealOnTertiaryDark,
            tertiaryContainer = TealTertiaryContainerDark, onTertiaryContainer = TealOnTertiaryContainerDark,
        ),
    ),
)

/** 取主题色组（越界回退绿色默认）。 */
fun themePaletteAt(index: Int): ThemePalette =
    ThemePalettes.getOrElse(index.coerceAtLeast(0)) { ThemePalettes.first() }

private fun buildLightScheme(seed: ColorSchemeSeed) = lightColorScheme(
    primary = seed.primary,
    onPrimary = seed.onPrimary,
    primaryContainer = seed.primaryContainer,
    onPrimaryContainer = seed.onPrimaryContainer,
    secondary = seed.secondary,
    onSecondary = seed.onSecondary,
    secondaryContainer = seed.secondaryContainer,
    onSecondaryContainer = seed.onSecondaryContainer,
    tertiary = seed.tertiary,
    onTertiary = seed.onTertiary,
    tertiaryContainer = seed.tertiaryContainer,
    onTertiaryContainer = seed.onTertiaryContainer,
    // 中性背景/表面（各主题共用，避免换色后玻璃卡/背景失衡）
    background = NeutralBackgroundLight,
    onBackground = NeutralOnBackgroundLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceVariant = NeutralSurfaceVariantLight,
    onSurfaceVariant = NeutralOnSurfaceVariantLight,
    outline = NeutralOutlineLight,
)

private fun buildDarkScheme(seed: ColorSchemeSeed, oledDark: Boolean) = darkColorScheme(
    primary = seed.primary,
    onPrimary = seed.onPrimary,
    primaryContainer = seed.primaryContainer,
    onPrimaryContainer = seed.onPrimaryContainer,
    secondary = seed.secondary,
    onSecondary = seed.onSecondary,
    secondaryContainer = seed.secondaryContainer,
    onSecondaryContainer = seed.onSecondaryContainer,
    tertiary = seed.tertiary,
    onTertiary = seed.onTertiary,
    tertiaryContainer = seed.tertiaryContainer,
    onTertiaryContainer = seed.onTertiaryContainer,
    background = if (oledDark) Color(0xFF000000) else NeutralBackgroundDark,
    onBackground = NeutralOnBackgroundDark,
    surface = if (oledDark) Color(0xFF000000) else NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceVariant = if (oledDark) Color(0xFF1C1F1A) else NeutralSurfaceVariantDark,
    onSurfaceVariant = NeutralOnSurfaceVariantDark,
    outline = NeutralOutlineDark,
)

/**
 * Niriko 主题：由设置驱动的主题系统。
 *
 * @param darkTheme 是否为深色模式（由设置中的 ThemeMode 决定）
 * @param dynamicColor 是否启用 Material You 动态色
 * @param oledDark 是否启用 OLED 纯黑优化（仅深色模式下生效）
 * @param themeColorIndex 主题色下标（0=绿色默认；见 [ThemePalettes]；自定义种子未设置时生效）
 * @param customSeedColor 自定义主题色种子（ARGB；-1=未设置）。优先级：动态取色 > 自定义种子 > 预置
 * @param reduceMotion 减少动态效果（跳过入场类动画；系统 animator 关闭时也置 true）
 * @param content 内容
 */
@Composable
fun NirikoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    oledDark: Boolean = false,
    themeColorIndex: Int = 0,
    customSeedColor: Int = SeedColorScheme.UnsetSeed,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val seed = themePaletteAt(themeColorIndex)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // 任意种子色（HCT TONAL_SPOT 配方）；未设置时回退预置色板（默认绿）
        customSeedColor != SeedColorScheme.UnsetSeed -> {
            val seedColor = Color(customSeedColor)
            if (darkTheme) SeedColorScheme.dark(seedColor, oledDark) else SeedColorScheme.light(seedColor)
        }
        darkTheme -> buildDarkScheme(seed.dark, oledDark)
        else -> buildLightScheme(seed.light)
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        com.otakup.niriko.ui.animation.LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NirikoTypography,
            content = content,
        )
    }
}
