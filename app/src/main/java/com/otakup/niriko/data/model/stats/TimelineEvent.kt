package com.otakup.niriko.data.model.stats

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import java.time.LocalDate

/** 时间线条目。 */
data class TimelineEvent(
    val date: LocalDate,
    val action: TimelineAction,
    val collectionWithSubject: CollectionWithSubject,
    val actionLabel: String, // 如 "收藏了"、"开始看"、"看过了"
)
