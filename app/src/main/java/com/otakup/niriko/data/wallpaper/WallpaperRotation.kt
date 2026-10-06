package com.otakup.niriko.data.wallpaper

/**
 * 壁纸轮换的选择逻辑。刻意写成纯函数（无 Android 依赖），便于 JVM 单测。
 */
object WallpaperRotation {

    /** 轮换候选池：开启「仅收藏」时只取收藏项（没有收藏则视为空池）。 */
    fun candidates(entries: List<WallpaperLibraryEntry>, favoritesOnly: Boolean): List<WallpaperLibraryEntry> {
        if (entries.isEmpty()) return emptyList()
        return if (favoritesOnly) entries.filter { it.favorite } else entries
    }

    /**
     * 下一张：从当前全局壁纸在候选池中的位置顺延一位，到末尾回到第一张。
     * 当前壁纸不在池中（或为空）时从第 0 项开始；池为空返回 null（调用方保持现状）。
     */
    fun next(entries: List<WallpaperLibraryEntry>, currentUri: String?, favoritesOnly: Boolean): WallpaperLibraryEntry? {
        val pool = candidates(entries, favoritesOnly)
        if (pool.isEmpty()) return null
        val index = pool.indexOfFirst { it.uri == currentUri }
        val nextIndex = if (index < 0) 0 else (index + 1) % pool.size
        return pool[nextIndex]
    }
}
