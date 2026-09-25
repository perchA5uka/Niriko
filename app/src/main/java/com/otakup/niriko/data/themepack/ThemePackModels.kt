package com.otakup.niriko.data.themepack

import kotlinx.serialization.Serializable

/**
 * .nirikotheme 主题包清单（theme.json）。
 * 兼容规则：未知字段忽略（新包装进旧 App），缺失字段回落默认（旧包装进新 App）。
 * formatVersion 仅作提示，不做硬门禁（保持单向兼容）。
 */
@Serializable
data class ThemePackManifest(
    val formatVersion: Int = 1,
    val id: String,
    val name: String,
    val author: String = "",
    val version: String = "1.0.0",
    val minAppVersion: String = "1.0.0",
    val colors: ThemePackColors = ThemePackColors(),
    val wallpaper: ThemePackWallpaper? = null,
    val pages: ThemePackPages = ThemePackPages(),
    val splash: ThemePackSplash = ThemePackSplash(),
)

@Serializable
data class ThemePackColors(
    val light: ThemePackColorSeed = ThemePackColorSeed(),
    val dark: ThemePackColorSeed = ThemePackColorSeed(),
)

/** 种子色（任意 6 位十六进制，如 "#2E7D32"；secondary/tertiary 可省略，生成器会派生）。 */
@Serializable
data class ThemePackColorSeed(
    val primary: String = "#2E7D32",
    val secondary: String = "",
    val tertiary: String = "",
)

/** 全局壁纸（包内文件，如 "wallpaper.webp" / "wallpaper.mp4"）。 */
@Serializable
data class ThemePackWallpaper(
    val type: String = "image",
    val file: String = "wallpaper.webp",
    val blurRadiusDp: Int = 0,
)

/** 每页壁纸覆盖（file 为空 = 跟随全局）。 */
@Serializable
data class ThemePackPages(
    val library: ThemePackPage = ThemePackPage(),
    val discover: ThemePackPage = ThemePackPage(),
    val stats: ThemePackPage = ThemePackPage(),
    val settings: ThemePackPage = ThemePackPage(),
)

@Serializable
data class ThemePackPage(
    val file: String = "",
    val blurRadiusDp: Int = 0,
)

@Serializable
data class ThemePackSplash(
    val enabled: Boolean = true,
)

/** 已安装主题包元信息（扫描 filesDir/themes/<id>/theme.json 得到）。 */
data class ThemePackMeta(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val wallpaperType: String, // "none" | "image" | "video"
    val manifest: ThemePackManifest,
)

/** 应用主题包时的落库载荷（由 UI 经 SettingsViewModel 写入）。 */
data class ThemePackApply(
    val seedColorArgb: Int,
    val wallpaperUri: String?,
    val wallpaperBlurDp: Int,
    val splashEnabled: Boolean,
)

sealed interface ThemePackImportResult {
    data class Success(val meta: ThemePackMeta) : ThemePackImportResult
    data class Failure(val reason: String) : ThemePackImportResult
}

/** 单例：外部以"打开方式"唤起时暂存的主题包 URI（MainActivity → 外观页消费）。 */
object ThemePackImportRequest {
    var uri: String? = null
}
