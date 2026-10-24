package com.otakup.niriko.data.model.stats

import java.time.LocalDate

/**
 * 统计页「今晚看什么 / 接下来七天 / 漏看 / 健康度 / 口径」用到的纯数据模型。
 *
 * 放在 model 而不是 calculator：这些都直接进 [StatsUiState]，UI 只读；
 * 计算逻辑全在 data/calculator 下，保持「model 不含逻辑、calculator 不依赖 Android」的分层。
 */

/** 今晚这一行为什么出现：落后了，还是今天正好更新。 */
enum class TonightReason { BEHIND, AIRING_TODAY }

/** 「今晚看什么」的一行。 */
data class TonightItem(
    val subjectId: Long,
    val title: String,
    val coverUrl: String?,
    val reason: TonightReason,
    /** 本地已看集数。 */
    val watched: Int,
    /** 已播集数（[estimated] 为 true 时是按首播日推算的）。 */
    val aired: Int,
    /** 落后集数；AIRING_TODAY 时为 0。 */
    val behind: Int,
    /** 应该看的下一集（落后时 = 已播到的最新一集，否则 null）。 */
    val episodeNumber: Int?,
    val airMinutes: Int?,
    val airZone: String?,
    /** true = 已播集数由首播日推算（没有每集数据），UI 要显示"约"。 */
    val estimated: Boolean,
)

/** 「接下来七天」某一列里的一条放送。 */
data class UpcomingEntry(
    val subjectId: Long,
    val title: String,
    val coverUrl: String?,
    /** 是否已在收藏库：未收藏的只做「这季有什么」的提示，不参与在看统计。 */
    val inCollection: Boolean,
    val airMinutes: Int?,
    val airZone: String?,
    /** true = 该条没有真实集日期，日期由星期推算。 */
    val estimated: Boolean,
)

/** 「接下来七天」的一列。 */
data class UpcomingDay(
    val date: LocalDate,
    val entries: List<UpcomingEntry>,
) {
    val count: Int get() = entries.size
    val inCollectionCount: Int get() = entries.count { it.inCollection }
    /** 该列展示用的首封封面（第一条有封面的）。 */
    val firstCoverUrl: String? get() = entries.firstNotNullOfOrNull { it.coverUrl }
}

/** 已播集数的可信度。 */
enum class LagQuality { EXACT, ESTIMATED }

/** 一条「漏看」。 */
data class Lag(
    val subjectId: Long,
    val title: String,
    val aired: Int,
    val watched: Int,
    val behind: Int,
    val quality: LagQuality,
)

/** 收藏健康度的四类问题（口径写在 [com.otakup.niriko.data.calculator.HygieneCalculator] 的 KDoc 里）。 */
enum class HygieneKind { WATCHING_STALE, COMPLETED_UNRATED, UNTAGGED, PLAN_STALE }

data class HygieneIssue(val kind: HygieneKind, val count: Int)

/** 健康度报告；[issues] 固定四类（顺序即展示顺序），计数可能同时为 0。 */
data class HygieneReport(val issues: List<HygieneIssue> = emptyList()) {
    /** 有问题的条目"次"数合计（同一部作品可能同时命中多类，所以是粗汇总）。 */
    val totalCount: Int get() = issues.sumOf { it.count }
    /** 有几类真的非零，用于卡头"3 项待整理"。 */
    val actionableCount: Int get() = issues.count { it.count > 0 }
}

/** 完成率口径。三者的分母都写明在 [CompletionDetail]。 */
enum class CompletionCaliber {
    /** 全部分母：含想看（最容易把完成率拉低）。 */
    ALL,

    /** 剔除「想看」：默认口径，"我真正开始的里面看完了多少"。 */
    EXCLUDE_PLAN,

    /** 严格口径：只算已经进入过流程的（在看/看过/搁置/抛弃），不含想看。 */
    STRICT,
}

/** 完成率的分子分母快照，UI 的「?」说明卡直接读它，避免文案与算法分叉。 */
data class CompletionDetail(
    val completed: Int = 0,
    val total: Int = 0,
    val planToWatch: Int = 0,
    /** 严格口径的分母（在看 + 看过 + 搁置 + 抛弃）。 */
    val strictDenominator: Int = 0,
) {
    /** 剔除想看后的分母。 */
    val excludePlanDenominator: Int get() = (total - planToWatch).coerceAtLeast(0)

    fun denominator(caliber: CompletionCaliber): Int = when (caliber) {
        CompletionCaliber.ALL -> total
        CompletionCaliber.EXCLUDE_PLAN -> excludePlanDenominator
        CompletionCaliber.STRICT -> strictDenominator
    }

    fun rate(caliber: CompletionCaliber): Float {
        val denom = denominator(caliber)
        return if (denom <= 0) 0f else completed.toFloat() / denom
    }
}
