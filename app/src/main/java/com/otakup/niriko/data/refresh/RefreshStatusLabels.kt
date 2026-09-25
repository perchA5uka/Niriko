package com.otakup.niriko.data.refresh

/**
 * 刷新状态的**展示层**纯函数：把 key / 快照翻译成人能读的文案。
 *
 * 放在 data/refresh 而不是 UI 里，是为了可单测 —— 这些字符串会出现在诊断页与
 * 「上次更新 X 分钟前」的标签上，算错会直接误导用户。
 */
object RefreshStatusLabels {

    private val KNOWN: Map<String, String> = mapOf(
        RefreshKeys.STEAM_CHART to "Steam 排行榜",
        RefreshKeys.AUTO_BIND to "Steam 自动匹配",
        RefreshKeys.AUTO_SYNC_WEBDAV to "WebDAV 自动同步",
        RefreshKeys.AUTO_SYNC_BANGUMI to "Bangumi 自动同步",
        RefreshKeys.BROADCAST_CALENDAR to "放送日历",
    )

    /** 资源 key → 友好名称（未知 key 原样返回）。 */
    fun label(key: String): String = KNOWN[key] ?: when {
        key.startsWith(TRENDING_PREFIX) -> trendingLabel(key)
        else -> key
    }

    private fun trendingLabel(key: String): String {
        val parts = key.removePrefix(TRENDING_PREFIX).split(':')
        val mode = parts.getOrNull(0).orEmpty()
        val type = parts.getOrNull(1)
        return buildString {
            append("发现页趋势")
            append(" · ").append(trendingModeLabel(mode))
            if (!type.isNullOrBlank() && type != "all") append(" · 类型 ").append(type)
        }
    }

    private fun trendingModeLabel(mode: String): String = when (mode) {
        "SEASONAL" -> "当季热门"
        "ALL_TIME" -> "历史排名"
        "STEAM" -> "Steam 榜单"
        else -> mode
    }

    /** 「X 分钟前」样式（at 在过去）。 */
    fun formatAgo(at: Long, now: Long): String {
        if (at <= 0L) return "从未"
        val minutes = ((now - at).coerceAtLeast(0L)) / 60_000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes} 分钟前"
            minutes < 24 * 60 -> "${minutes / 60} 小时前"
            else -> "${minutes / (24 * 60)} 天前"
        }
    }

    /**
     * 「X 分钟后」样式（at 在未来）。
     *
     * 不足一分钟单独成档：退避梯最短 30 秒，若向上取整成「1 分钟后」会让人以为还要等一分钟。
     */
    fun formatUntil(at: Long, now: Long): String {
        val diff = at - now
        if (diff <= 0L) return "马上"
        if (diff < 60_000L) return "不到 1 分钟"
        val minutes = (diff + 59_999) / 60_000
        return if (minutes < 60) "${minutes} 分钟后" else "${minutes / 60} 小时后"
    }

    /** 诊断页每行的一行摘要。 */
    fun describe(snapshot: FreshnessSnapshot?, now: Long): String {
        if (snapshot == null || !snapshot.hasSucceeded) {
            return if (snapshot?.failureCount ?: 0 > 0) {
                "尚未成功 · 已失败 ${snapshot!!.failureCount} 次"
            } else {
                "尚未刷新"
            }
        }
        val head = "上次 ${formatAgo(snapshot.lastSuccessAt, now)}"
        return when {
            snapshot.backoffUntil > now ->
                "$head · 上次失败，${formatUntil(snapshot.backoffUntil, now)}重试"
            snapshot.failureCount > 0 ->
                "$head · 累计失败 ${snapshot.failureCount} 次"
            else -> head
        }
    }

    /** 诊断页排序：有问题的排前面（退避中 > 有失败 > 其余按 key）。 */
    fun diagnoseOrder(a: Pair<String, FreshnessSnapshot>, b: Pair<String, FreshnessSnapshot>, now: Long): Int {
        val rankA = severity(a.second, now)
        val rankB = severity(b.second, now)
        return if (rankA != rankB) rankA - rankB else a.first.compareTo(b.first)
    }

    private fun severity(snapshot: FreshnessSnapshot, now: Long): Int = when {
        snapshot.backoffUntil > now -> 0
        snapshot.failureCount > 0 -> 1
        !snapshot.hasSucceeded -> 2
        else -> 3
    }

    private const val TRENDING_PREFIX = "trending:"
}
