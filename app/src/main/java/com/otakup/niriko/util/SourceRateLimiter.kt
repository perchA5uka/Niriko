package com.otakup.niriko.util

/**
 * 按源的请求闸门（计划 B4 · 4-12）。
 *
 * README「工程待办」列出的三条真实限额：IGDB 4 req/s、MusicBrainz 1 req/s、
 * VNDB 200 次 / 5 分钟。此前只靠「高置信提前停止查询」与批量查询降低请求量，
 * 没有硬闸门；超限会被源站限流甚至封禁。
 *
 * 纯逻辑：时间由调用方传入，等待由调用方执行，便于 JVM 单测。
 *
 * 语义：
 * - 窗口内的已有放行数未达上限 → 立即放行（允许一开始就用满配额）；
 * - 已达上限 → 按 `windowMs / maxRequests` 的间隔匀速排队。
 *   这一点很关键：若只按「等到队首过期」，同一批并发调用会在窗口边界同时放行，
 *   瞬时仍然超限（实测过：MusicBrainz 会在 1 秒边界一次放行两条）。
 */
class SourceRateLimiter(
    private val rules: List<RateRule> = DEFAULT_RULES,
) {

    /** 一条限额规则：[hostSuffix] 匹配该域名及其子域。 */
    data class RateRule(
        val hostSuffix: String,
        val maxRequests: Int,
        val windowMs: Long,
    )

    private val granted = HashMap<String, ArrayDeque<Long>>()

    /**
     * 申请一次放行。
     * @return 需要等待的毫秒数（0 = 立即放行）；未命中规则的 host 恒为 0。
     */
    @Synchronized
    fun acquire(host: String, nowMs: Long): Long {
        val rule = rules.firstOrNull {
            host.equals(it.hostSuffix, ignoreCase = true) ||
                host.endsWith("." + it.hostSuffix, ignoreCase = true)
        } ?: return 0L

        val queue = granted.getOrPut(host.lowercase()) { ArrayDeque() }
        // 窗口外的放行记录出队（窗口内重新变空 → 恢复「突发到上限」）
        while (queue.isNotEmpty() && queue.first() <= nowMs - rule.windowMs) queue.removeFirst()

        val slot = if (queue.size < rule.maxRequests) {
            nowMs
        } else {
            // 匀速排队：间隔 = 窗口 / 配额
            val interval = (rule.windowMs / rule.maxRequests).coerceAtLeast(1L)
            maxOf(nowMs, queue.last() + interval)
        }
        queue.addLast(slot)
        return (slot - nowMs).coerceAtLeast(0L)
    }

    companion object {
        /** 与 README 工程待办一致的三条限额。 */
        val DEFAULT_RULES: List<RateRule> = listOf(
            RateRule(hostSuffix = "api.igdb.com", maxRequests = 4, windowMs = 1_000L),
            RateRule(hostSuffix = "musicbrainz.org", maxRequests = 1, windowMs = 1_000L),
            RateRule(hostSuffix = "api.vndb.org", maxRequests = 200, windowMs = 5 * 60 * 1_000L),
        )
    }
}
