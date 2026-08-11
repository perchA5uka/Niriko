package com.otakup.niriko.data.model.stats

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import java.time.LocalDate

/** 某天的日历事件。 */
data class CalendarDayEvents(
    val date: LocalDate,
    val startedItems: List<CollectionWithSubject> = emptyList(),
    val completedItems: List<CollectionWithSubject> = emptyList(),
    val broadcastSubjects: List<SubjectEntity> = emptyList(),
    val releaseDateSubjects: List<SubjectEntity> = emptyList(),
    val coverCandidates: List<SubjectEntity> = emptyList(),
    /** 单格展示封面（一图一格，按同周几轮换选取）；null 表示该格不显示封面。 */
    val displayCover: SubjectEntity? = null,
    /** 该日是否有跨月延续的放送作品（airDate 早于当月 1 日）。由计算层预计算，避免 UI 组合期字符串解析。 */
    val hasContinuing: Boolean = false,
) {
    val hasPersonalEvents: Boolean get() = startedItems.isNotEmpty() || completedItems.isNotEmpty()
    val hasBroadcast: Boolean get() = broadcastSubjects.isNotEmpty()
    val hasRelease: Boolean get() = releaseDateSubjects.isNotEmpty()
    val hasEvents: Boolean get() = hasPersonalEvents || hasBroadcast || hasRelease
}
