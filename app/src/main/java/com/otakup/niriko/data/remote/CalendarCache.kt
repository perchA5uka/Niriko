package com.otakup.niriko.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * /calendar（每日放送）响应的共享内存缓存（第 5 轮 D26）。
 *
 * ## 为什么需要它
 *
 * 同一个 `/calendar` 响应有两个消费者：追番日历（BroadcastFetcher）与发现页「当季热门」。
 * 改造前各自直连一次接口 —— 打开统计页再打开发现页就是两次完全相同的请求。
 * 这里收敛成一处，并顺带提供**失败时回退旧数据**的能力（降级链第 2 级）。
 *
 * 进程内缓存，不落库：/calendar 是纯服务端公开数据，重启后重取即可。
 */
class CalendarCache(
    /** 新鲜期。默认 6 小时，与 FreshnessPolicy.SEASONAL 的软 TTL 对齐。 */
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {

    /** 一次读取的结果。 */
    data class Snapshot(
        val entries: List<CalendarDaySchedule>,
        /** true = 取接口失败，退回的是过期数据。 */
        val stale: Boolean,
    )

    private val mutex = Mutex()
    private var entries: List<CalendarDaySchedule>? = null
    private var fetchedAt: Long = 0L

    /**
     * 取日历：新鲜则直接返回；否则调 [fetch]。
     *
     * 失败时**不抛出**，退回上一次的结果并标记 [Snapshot.stale]；
     * 从未成功过则返回 null（调用方走降级链第 3 级）。
     */
    suspend fun load(fetch: suspend () -> List<CalendarDaySchedule>): Snapshot? = mutex.withLock {
        val now = System.currentTimeMillis()
        val current = entries
        if (!current.isNullOrEmpty() && now - fetchedAt < ttlMs) {
            return@withLock Snapshot(current, stale = false)
        }
        val fresh = runCatching { fetch() }.getOrNull()
        if (!fresh.isNullOrEmpty()) {
            entries = fresh
            fetchedAt = now
            return@withLock Snapshot(fresh, stale = false)
        }
        val fallback = current?.takeIf { it.isNotEmpty() }
        if (fallback != null) Snapshot(fallback, stale = true) else null
    }

    /** 只看缓存（不触发请求），用于诊断与「日历不可用」的降级判断。 */
    suspend fun peek(): List<CalendarDaySchedule>? = mutex.withLock { entries }

    /** 退出登录 / 切换数据源时清空。 */
    suspend fun clear() = mutex.withLock {
        entries = null
        fetchedAt = 0L
    }

    companion object {
        const val DEFAULT_TTL_MS = 6L * 60 * 60 * 1000
    }
}
