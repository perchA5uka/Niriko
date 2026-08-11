package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.otakup.niriko.data.model.WatchStatus
import java.time.LocalDate

/**
 * 用户个人观看/阅读收藏记录。
 * 通过 [subjectId] 关联作品元数据，每个 subjectId 唯一对应一条记录。
 */
@Entity(
    tableName = "collections",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["subjectId"], unique = true)],
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val status: WatchStatus,
    val watchedEpisodes: Int? = null,
    val rating: Float? = null,
    val startDate: LocalDate? = null,
    val finishDate: LocalDate? = null,
    val personalTags: List<String> = emptyList(),
    val personalImpression: String? = null,
    val remark: String? = null,
    /** 已听曲目 id 集合（音乐类型逐首勾选）。其他类型为空。 */
    val watchedTrackIds: List<Long> = emptyList(),
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis(),
)