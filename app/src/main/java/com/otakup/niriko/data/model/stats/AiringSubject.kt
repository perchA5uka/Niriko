package com.otakup.niriko.data.model.stats

import com.otakup.niriko.data.local.entity.SubjectEntity
import java.time.LocalDate

/** 放送作品携带日期范围，用于精确映射到日历格子。 */
data class AiringSubject(
    val subject: SubjectEntity,
    val airDate: LocalDate,
    val estimatedEndDate: LocalDate?,  // null = 未知完结日
)
