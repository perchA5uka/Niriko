package com.otakup.niriko.data.sync.bangumi

import com.otakup.niriko.data.model.WatchStatus

/**
 * WatchStatus ↔ Bangumi 官方 CollectionType 双向映射。
 *
 * 关键陷阱(参照 Kazumi lib/modules/collect/collect_type_mapper.dart):
 * 本地枚举序号与官方数值**不等价**,绝不能按 ordinal 直转——
 * 本地顺序是 PLAN_TO_WATCH/WATCHING/COMPLETED/ON_HOLD/DROPPED,
 * 官方数值是 1=想看 2=看过 3=在看 4=搁置 5=抛弃。
 */
object BangumiStatusMapper {

    /** 官方 CollectionType:1=想看 2=看过 3=在看 4=搁置 5=抛弃。 */
    const val BANGUMI_PLAN_TO_WATCH = 1
    const val BANGUMI_COMPLETED = 2
    const val BANGUMI_WATCHING = 3
    const val BANGUMI_ON_HOLD = 4
    const val BANGUMI_DROPPED = 5

    /** 本地 → 官方。 */
    fun WatchStatus.toBangumiType(): Int = when (this) {
        WatchStatus.PLAN_TO_WATCH -> BANGUMI_PLAN_TO_WATCH
        WatchStatus.COMPLETED -> BANGUMI_COMPLETED
        WatchStatus.WATCHING -> BANGUMI_WATCHING
        WatchStatus.ON_HOLD -> BANGUMI_ON_HOLD
        WatchStatus.DROPPED -> BANGUMI_DROPPED
    }

    /** 官方 → 本地;未知值返回 null(调用方跳过该条目,不误改状态)。 */
    fun Int.toWatchStatus(): WatchStatus? = when (this) {
        BANGUMI_PLAN_TO_WATCH -> WatchStatus.PLAN_TO_WATCH
        BANGUMI_COMPLETED -> WatchStatus.COMPLETED
        BANGUMI_WATCHING -> WatchStatus.WATCHING
        BANGUMI_ON_HOLD -> WatchStatus.ON_HOLD
        BANGUMI_DROPPED -> WatchStatus.DROPPED
        else -> null
    }
}