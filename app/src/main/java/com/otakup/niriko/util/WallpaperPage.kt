package com.otakup.niriko.util

/** 顶层四页壁纸槽位（作品库/发现/统计/设置）。 */
enum class WallpaperPage(val key: String, val label: String) {
    LIBRARY("library", "作品库"),
    DISCOVER("discover", "发现"),
    STATS("stats", "统计"),
    SETTINGS("settings", "设置"),
}
