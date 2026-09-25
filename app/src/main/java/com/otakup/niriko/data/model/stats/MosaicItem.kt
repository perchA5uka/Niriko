package com.otakup.niriko.data.model.stats

import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import java.time.LocalDate

/** 照片墙（马赛克）瓷砖（阶段 E）。 */
data class MosaicItem(
    val subjectId: Long,
    val title: String,
    val coverUrl: String?,
    val type: SubjectType,
    val status: WatchStatus,
    /** 收藏时间（用于排序/复古时间线）。 */
    val createDate: LocalDate,
)
