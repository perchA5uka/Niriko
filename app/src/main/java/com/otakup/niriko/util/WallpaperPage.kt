package com.otakup.niriko.util

/** 顶层四页壁纸槽位（作品库/发现/统计/设置）。 */
enum class WallpaperPage(val key: String, val label: String) {
    LIBRARY("library", "作品库"),
    DISCOVER("discover", "发现"),
    STATS("stats", "统计"),
    SETTINGS("settings", "设置"),
}

/**
 * 某页最终生效的壁纸 URI：页面覆盖非空时优先，否则继承全局。
 * 与 WallpaperHost 里的取值规则保持一致（两处必须同源，否则卡片 tint 会与壁纸不一致）。
 */
fun effectiveWallpaperUri(globalUri: String, perPageUri: String?): String {
    val override = perPageUri?.trim().orEmpty()
    return if (override.isNotEmpty()) override else globalUri.trim()
}
