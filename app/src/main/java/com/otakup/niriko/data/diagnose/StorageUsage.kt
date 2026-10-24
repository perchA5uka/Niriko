package com.otakup.niriko.data.diagnose

import kotlin.math.abs

/**
 * 图片/缓存占用（C-10）：把磁盘条目归到 4 类，给出汇总与「单项超限」。
 *
 * 分类只看相对路径，不 stat 每个文件（大目录也能一次算完）；文件系统遍历在 UI 层做。
 */
enum class StorageBucket(val label: String) {
    COVER("封面缓存"),
    WALLPAPER("壁纸"),
    SNAPSHOT("本地快照"),
    OTHER("其他缓存"),
}

/** 磁盘上的一个条目（文件或目录）。 */
data class StorageItem(val bucket: StorageBucket, val name: String, val bytes: Long)

data class BucketUsage(val bucket: StorageBucket, val bytes: Long, val items: Int)

/** 单项超过该值就提醒（不是上限，只是「这一项特别大」）。 */
const val SINGLE_ITEM_WARN_BYTES = 20L * 1024 * 1024

/** 默认缓存上限（超过后一键清理）。 */
const val DEFAULT_CACHE_LIMIT_BYTES = 200L * 1024 * 1024

/**
 * 相对路径 → 分类。顺序重要：先匹配更具体的目录。
 *
 * 依据真实目录名：封面走 Coil 的 image_cache；壁纸是用户拷进私有目录的；
 * 快照是本轮新增的 snapshots/；其余（日志/导出/临时）归 OTHER。
 */
fun bucketOf(relativePath: String): StorageBucket {
    val p = relativePath.lowercase()
    return when {
        "snapshot" in p -> StorageBucket.SNAPSHOT
        "wallpaper" in p -> StorageBucket.WALLPAPER
        "image_cache" in p || "coil" in p || "cover" in p -> StorageBucket.COVER
        else -> StorageBucket.OTHER
    }
}

fun totalBytes(items: List<StorageItem>): Long = items.sumOf { it.bytes }

fun summarize(items: List<StorageItem>): List<BucketUsage> =
    StorageBucket.entries.map { bucket ->
        val own = items.filter { it.bucket == bucket }
        BucketUsage(bucket, own.sumOf { it.bytes }, own.size)
    }

/** 单项超限（用于「单项清理」提示）。按体积降序。 */
fun oversizedItems(items: List<StorageItem>, threshold: Long = SINGLE_ITEM_WARN_BYTES): List<StorageItem> =
    items.filter { it.bytes >= threshold }.sortedByDescending { it.bytes }

/** 是否该提示清理：总量超过上限或任一单项超限。 */
fun needsCleanup(
    items: List<StorageItem>,
    limit: Long = DEFAULT_CACHE_LIMIT_BYTES,
): Boolean = totalBytes(items) > limit || oversizedItems(items).isNotEmpty()

/** 人类可读体积（B/KB/MB/GB，保留 1 位）。 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    val rounded = kotlin.math.round(value * 10) / 10
    return "${if (abs(rounded - rounded.toLong()) < 0.05) rounded.toLong() else rounded} ${units[i]}"
}
