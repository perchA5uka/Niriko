package com.otakup.niriko.data.snapshot

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地快照的命名与保留策略（C-3）。
 *
 * 纯函数、不碰文件系统：保留规则是「最近 5 个 + 最近 7 天每天 1 个」，
 * 这条规则直接决定用户「能不能回滚到昨天」，所以必须可穷举测试，
 * 而不是散在 IO 代码里靠手测。
 */
data class SnapshotFile(
    val name: String,
    /** 创建时间（epoch millis）。 */
    val createdAt: Long,
    val bytes: Long,
)

/** 无条件保留的最近快照数。 */
const val RECENT_KEEP = 5

/** 按天保留的天数（每天保留最新一个）。 */
const val DAILY_KEEP_DAYS = 7

/** 一天毫秒数。 */
const val DAY_MS = 24L * 60 * 60 * 1000

private val stampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

/** 文件名：snapshot_20261018_104500_<tag>.json.gz（tag 已净化，避免路径穿越）。 */
fun snapshotFileName(createdAt: Long, tag: String): String {
    val safeTag = tag.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "manual" }
    return "snapshot_${stampFormat.format(Date(createdAt))}_$safeTag.json.gz"
}

/** 哪一天（本地时区，只用于分组）。 */
fun dayKey(createdAt: Long): Long = createdAt / DAY_MS

/**
 * 计算应保留的快照（返回「保留集合」的 name）。输入无序也可以。
 *
 * 规则：
 * 1. 最新的 [RECENT_KEEP] 个无条件保留；
 * 2. 最近 [DAILY_KEEP_DAYS] 天内，每个自然日保留当天**最新**的一个；
 * 3. 其余全部可删。
 */
fun snapshotsToKeep(files: List<SnapshotFile>): Set<String> {
    if (files.isEmpty()) return emptySet()
    val sorted = files.sortedByDescending { it.createdAt }
    val keep = LinkedHashSet<String>()
    sorted.take(RECENT_KEEP).forEach { keep += it.name }

    val newestDay = dayKey(sorted.first().createdAt)
    val oldestDay = newestDay - (DAILY_KEEP_DAYS - 1)
    val seenDays = mutableSetOf<Long>()
    for (file in sorted) {
        val day = dayKey(file.createdAt)
        if (day < oldestDay) continue
        if (seenDays.add(day)) keep += file.name   // 每个自然日的第一个（= 当天最新）保留
    }
    return keep
}

/** 应删除的快照（= 全量 − 保留）。 */
fun snapshotsToDelete(files: List<SnapshotFile>): List<SnapshotFile> =
    files.filter { it.name !in snapshotsToKeep(files) }
