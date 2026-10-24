package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.CompletionCaliber
import com.otakup.niriko.data.model.stats.CompletionDetail

/**
 * 完成率口径。
 *
 * 改造前只有一个除法 completed / total，分母含「想看」——收藏里躺 200 部想看时，
 * 完成率会被压到 30% 以下，用户只会觉得统计坏了。现在口径显式化：
 * 默认 [CompletionCaliber.EXCLUDE_PLAN]（"我真正开始的里面看完了多少"），
 * UI 的"?"说明卡再把三种分母都摊开。
 */
object CompletionCaliberCalculator {

    /** 默认口径。改这里等于改全站完成率，必须同步改单测与说明卡文案。 */
    val DEFAULT_CALIBER: CompletionCaliber = CompletionCaliber.EXCLUDE_PLAN

    fun detail(items: List<CollectionWithSubject>): CompletionDetail {
        if (items.isEmpty()) return CompletionDetail()
        var completed = 0
        var planToWatch = 0
        var strict = 0
        items.forEach { item ->
            when (item.collection.status) {
                WatchStatus.COMPLETED -> {
                    completed++
                    strict++
                }
                WatchStatus.PLAN_TO_WATCH -> planToWatch++
                WatchStatus.WATCHING, WatchStatus.ON_HOLD, WatchStatus.DROPPED -> strict++
                else -> Unit
            }
        }
        return CompletionDetail(
            completed = completed,
            total = items.size,
            planToWatch = planToWatch,
            strictDenominator = strict,
        )
    }

    fun rate(items: List<CollectionWithSubject>, caliber: CompletionCaliber = DEFAULT_CALIBER): Float =
        detail(items).rate(caliber)
}
