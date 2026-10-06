package com.otakup.niriko.data.wallpaper

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 壁纸库中的一条壁纸（R3，参考 Peristyle 的壁纸库形态）。
 *
 * - [id] 稳定标识，用于列表 key 与删除；
 * - [uri] 本地文件 URI（`file://…/files/wallpapers/<id>.<ext>`），也允许外部 `content://`；
 * - [tags] 用户自定义标签；[favorite] 供「仅收藏轮换」使用。
 */
@Serializable
data class WallpaperLibraryEntry(
    val id: String,
    val uri: String,
    val name: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val addedAt: Long = 0L,
)

/**
 * 壁纸库 JSON 编解码：DataStore 只能存字符串，所以整表序列化成一行 JSON。
 * 未知字段忽略（旧版本写入的多余键不会让整表读不出来），损坏时返回空表。
 */
object WallpaperLibraryCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val serializer = ListSerializer(WallpaperLibraryEntry.serializer())

    fun encode(entries: List<WallpaperLibraryEntry>): String =
        runCatching { json.encodeToString(serializer, entries) }.getOrDefault("[]")

    fun decode(raw: String?): List<WallpaperLibraryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
