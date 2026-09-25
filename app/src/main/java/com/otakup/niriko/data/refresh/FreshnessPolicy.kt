package com.otakup.niriko.data.refresh

import kotlinx.serialization.Serializable

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/**
 * 远端资源的新鲜度策略。
 *
 * 改造前每个仓储各写各的 TTL 常量（Steam 30 分钟、Anitabi 7 天、榜单 30 秒），
 * 而作品详情与剧集表**完全没有 TTL**——剧集一旦落库永不过期。
 * 这里集中定义，便于审查与单测（参考 AniShelf 把 lastAttempt / lastSuccess 显式建模的做法）。
 *
 * @property softTtlMs 软过期：超过即认为「可以复用缓存但值得后台校验」（stale-while-revalidate）
 * @property hardTtlMs 硬过期：超过则必须重新拉取，缓存不可直接信任
 */
enum class RefreshResource(
    val softTtlMs: Long,
    val hardTtlMs: Long,
) {
    /** 当季热门（发现页趋势区）。 */
    TRENDING(softTtlMs = 5 * MINUTE_MS, hardTtlMs = 30 * MINUTE_MS),

    /** 历史排名榜（GET /v0/subjects 榜单）。 */
    RANKING(softTtlMs = 5 * MINUTE_MS, hardTtlMs = 30 * MINUTE_MS),

    /** 放送日历（本周连载）。 */
    BROADCAST_CALENDAR(softTtlMs = 30 * MINUTE_MS, hardTtlMs = 6 * HOUR_MS),

    /** 季节 / 月度放送数据（跨月延续）。 */
    SEASONAL(softTtlMs = 6 * HOUR_MS, hardTtlMs = 7 * DAY_MS),

    /**
     * 作品详情。
     * soft = 0：保持「进详情页即出缓存 + 立刻后台刷新」的既有体感（不改变用户可感知行为）；
     * hard = 7 天：真正的下限，避免缓存被无限期当成最新数据。
     */
    SUBJECT_DETAIL(softTtlMs = 0L, hardTtlMs = 7 * DAY_MS),

    /** 剧集 / 章节表（修复「永不过期」）。 */
    EPISODES(softTtlMs = 6 * HOUR_MS, hardTtlMs = 7 * DAY_MS),

    /** Steam 扩展详情（价格/在线/开发商/截图）。 */
    STEAM_DETAIL(softTtlMs = 30 * MINUTE_MS, hardTtlMs = 24 * HOUR_MS),

    /** Steam 排行榜（store 热销榜 / 活跃榜）。 */
    STEAM_CHART(softTtlMs = 30 * MINUTE_MS, hardTtlMs = 24 * HOUR_MS),

    /** 圣地巡礼取景地标（Anitabi，D7 快照沿用）。 */
    ANITABI(softTtlMs = 7 * DAY_MS, hardTtlMs = 30 * DAY_MS),

    /**
     * 每集评分（TMDb 逐集 / IMDb 逐集）。
     *
     * 由 [com.otakup.niriko.data.repository.EpisodeRatingRepository.isEpisodeRatingsFresh] 消费，
     * UI 不再自己写 TTL（改造前 VM 里另有一个硬编码的 6 小时常量，与这里重复）。
     */
    EPISODE_RATINGS(softTtlMs = 6 * HOUR_MS, hardTtlMs = 7 * DAY_MS),

    /** 作品级权威评分（多源聚合，含低限流的 Steam 好评率等）。 */
    EXTERNAL_RATINGS(softTtlMs = 1 * DAY_MS, hardTtlMs = 7 * DAY_MS),

    /** 启动后台批量匹配 Steam 绑定。 */
    AUTO_BIND(softTtlMs = 6 * HOUR_MS, hardTtlMs = 6 * HOUR_MS),

    /** WebDAV 自动同步。 */
    AUTO_SYNC_WEBDAV(softTtlMs = 15 * MINUTE_MS, hardTtlMs = 24 * HOUR_MS),

    /** Bangumi 账号自动同步。 */
    AUTO_SYNC_BANGUMI(softTtlMs = 30 * MINUTE_MS, hardTtlMs = 24 * HOUR_MS),

    /** 搜索：不参与新鲜度缓存（每次都要打远端），但落库走 diff 写。 */
    SEARCH(softTtlMs = 0L, hardTtlMs = 0L);
}

/** 新鲜度判定结果。 */
enum class RefreshDecision {
    /** 命中软 TTL：直接用缓存，连后台校验都不必（调用方无需发请求）。 */
    FRESH,

    /** 软过期未硬过期：可先用缓存，但应发起一次后台校验（stale-while-revalidate）。 */
    REVALIDATE,

    /** 硬过期或从未成功过：必须重新拉取。 */
    EXPIRED,

    /** 处于失败退避窗口：本次跳过，除非显式 force。 */
    BACKOFF,
}

/**
 * 持久化的新鲜度快照（每个「单例资源」一条）。
 *
 * 注意适用范围：只用于**具名单例资源**（趋势榜、榜单、放送日历、Steam 排行榜、自动同步等，key 数量有限）。
 * 逐条目的新鲜度**不放在这里**——作品详情用 \`SubjectEntity.lastSyncTime\`、
 * 剧集用 \`EpisodeEntity.lastSyncTime\`（数据库里已有逐行时间戳，避免把上千个 id 灌进 DataStore）。
 */
@Serializable
data class FreshnessSnapshot(
    val lastSuccessAt: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val failureCount: Int = 0,
    val backoffUntil: Long = 0L,
    val lastError: String? = null,
) {
    val hasSucceeded: Boolean get() = lastSuccessAt > 0L

    /** 距上次成功的毫秒数；从未成功过返回 [Long.MAX_VALUE]。 */
    fun ageAt(now: Long): Long = if (hasSucceeded) now - lastSuccessAt else Long.MAX_VALUE

    /** 成功后：清退避与失败计数。 */
    fun afterSuccess(now: Long): FreshnessSnapshot = copy(
        lastSuccessAt = now,
        lastAttemptAt = now,
        failureCount = 0,
        backoffUntil = 0L,
        lastError = null,
    )

    /** 失败后：累计失败次数并按退避梯设置下一次可尝试时间。 */
    fun afterFailure(now: Long, error: String?): FreshnessSnapshot {
        val count = failureCount + 1
        return copy(
            lastAttemptAt = now,
            failureCount = count,
            backoffUntil = now + RetryLadder.backoffMs(count),
            lastError = error,
        )
    }
}

/**
 * 失败退避梯（参考 AniShelf `LibrarySyncScheduler.failureRetryIntervals`）：
 * 30s → 60s → 120s → 300s，之后固定 300s。
 */
object RetryLadder {
    private val INTERVALS_MS = longArrayOf(30_000L, 60_000L, 120_000L, 300_000L)

    /** 第 [failureCount] 次失败后应退避多久（failureCount 从 1 开始）。 */
    fun backoffMs(failureCount: Int): Long {
        if (failureCount <= 0) return 0L
        val idx = (failureCount - 1).coerceAtMost(INTERVALS_MS.lastIndex)
        return INTERVALS_MS[idx]
    }
}

/** 纯函数判定，便于单测。 */
object FreshnessDecider {

    /**
     * @param force 用户显式的强制刷新（下拉刷新 / 手动重试）——绕过软硬 TTL **与退避**，
     *              因为用户主动要求时「什么都不做」是最差的反馈。
     */
    fun decide(
        policy: RefreshResource,
        snapshot: FreshnessSnapshot?,
        now: Long,
        force: Boolean = false,
    ): RefreshDecision {
        if (force) return RefreshDecision.EXPIRED
        // 搜索类资源从不缓存
        if (policy.softTtlMs <= 0L && policy.hardTtlMs <= 0L) return RefreshDecision.EXPIRED
        val snap = snapshot ?: return RefreshDecision.EXPIRED
        // 退避必须**先于**「从未成功」判定：一个从未成功过的资源（例如接口一直不可达）
        // 恰恰是最需要退避的场景；若先判 hasSucceeded 就直接 EXPIRED，退避永远不生效。
        if (snap.backoffUntil > now) return RefreshDecision.BACKOFF
        if (!snap.hasSucceeded) return RefreshDecision.EXPIRED
        val age = snap.ageAt(now)
        return when {
            age < policy.softTtlMs -> RefreshDecision.FRESH
            age < policy.hardTtlMs -> RefreshDecision.REVALIDATE
            else -> RefreshDecision.EXPIRED
        }
    }

    /**
     * 按「逐行时间戳」判定（作品详情 / 剧集的场景）。
     *
     * 这两类资源的数据行本身带时间戳（\`lastSyncTime\`），无需再维护一份 key→时间 的映射表。
     * 无退避概念（单行失败即回退缓存），因此不会返回 [RefreshDecision.BACKOFF]。
     */
    fun decideByTimestamp(
        policy: RefreshResource,
        lastSuccessAt: Long,
        now: Long,
        force: Boolean = false,
    ): RefreshDecision {
        if (force) return RefreshDecision.EXPIRED
        if (policy.softTtlMs <= 0L && policy.hardTtlMs <= 0L) return RefreshDecision.EXPIRED
        if (lastSuccessAt <= 0L) return RefreshDecision.EXPIRED
        val age = now - lastSuccessAt
        return when {
            age < policy.softTtlMs -> RefreshDecision.FRESH
            age < policy.hardTtlMs -> RefreshDecision.REVALIDATE
            else -> RefreshDecision.EXPIRED
        }
    }
}
