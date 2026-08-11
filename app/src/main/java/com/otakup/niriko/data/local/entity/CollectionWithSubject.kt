package com.otakup.niriko.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 收藏记录 + 关联作品元数据（通过 Room @Relation JOIN 查询）。
 */
data class CollectionWithSubject(
    @Embedded
    val collection: CollectionEntity,

    @Relation(
        parentColumn = "subjectId",
        entityColumn = "subjectId",
    )
    val subject: SubjectEntity,
)
