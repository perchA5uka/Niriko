package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.HygieneIssue
import com.otakup.niriko.data.model.stats.HygieneKind
import com.otakup.niriko.data.model.stats.HygieneReport
import java.util.concurrent.TimeUnit

/**
 * 「收藏健康度」：找出现在就值得处理的四条口径。
 *
 * 为什么用 updateTime 而不是"最后观看时间"：收藏表没有 lastProgressAt 列
 * （批次 A 明确不动表），而 A-D6 之后 updateTime 在每次状态/进度变更时都会 bump，
 * 所以「在看 + 90 天没动」用它判定是可靠的；代价是"只改评分"也算动过 —— 口径写进单测。
 *
 * 四类（顺序即展示顺序）：
 * 1. [HygieneKind.WATCHING_STALE] 在看且 90 天没有更新过进度（"可能其实已经弃了"）；
 * 2. [HygieneKind.COMPLETED_UNRATED] 看过但没评分（统计的均分基数被拉低）；
 * 3. [HygieneKind.UNTAGGED] 没有个人标签（词云/筛选都用不上）；
 * 4. [HygieneKind.PLAN_STALE] 想看且超过一年没动（"想看"正在变成"不想看"）。
 *
 * 原计划的第四类是「空分区」，但分区数据在 LibraryFolder 仓储里，统计 ViewModel
 * 拿不到（加依赖要动 NirikoApplication，超出本卡授权），先用「想看停滞」替代并在卡里注明。
 */
object HygieneCalculator {

    /** 在看多久没动算停滞。 */
    const val STALE_DAYS = 90L

    /** 想看多久没动算"其实不想看了"。 */
    const val PLAN_STALE_DAYS = 365L

    fun report(
        items: List<CollectionWithSubject>,
        nowMillis: Long,
        staleDays: Long = STALE_DAYS,
        planStaleDays: Long = PLAN_STALE_DAYS,
    ): HygieneReport {
        if (items.isEmpty()) return HygieneReport(emptyList())
        val staleMs = TimeUnit.DAYS.toMillis(staleDays)
        val planStaleMs = TimeUnit.DAYS.toMillis(planStaleDays)

        var stale = 0
        var unrated = 0
        var untagged = 0
        var planStale = 0
        items.forEach { item ->
            val collection = item.collection
            val idleMs = nowMillis - collection.updateTime
            when (collection.status) {
                WatchStatus.WATCHING -> if (idleMs > staleMs) stale++
                WatchStatus.COMPLETED -> if (collection.rating == null) unrated++
                WatchStatus.PLAN_TO_WATCH -> if (idleMs > planStaleMs) planStale++
                else -> Unit
            }
            if (collection.personalTags.isEmpty()) untagged++
        }

        return HygieneReport(
            issues = listOf(
                HygieneIssue(HygieneKind.WATCHING_STALE, stale),
                HygieneIssue(HygieneKind.COMPLETED_UNRATED, unrated),
                HygieneIssue(HygieneKind.UNTAGGED, untagged),
                HygieneIssue(HygieneKind.PLAN_STALE, planStale),
            )
        )
    }
}
