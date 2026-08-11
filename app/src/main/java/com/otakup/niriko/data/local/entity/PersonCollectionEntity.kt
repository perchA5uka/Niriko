package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 人物收藏记录（声优/导演/作者等）。
 *
 * 与 [CollectionEntity]（作品收藏）分离：人物不参与观看状态/评分/进度，
 * 仅记录"已收藏"标记 + 展示所需元数据。主键为 Bangumi personId。
 */
@Entity(tableName = "person_collections")
data class PersonCollectionEntity(
    @PrimaryKey
    val personId: Long,
    val name: String,
    val nameCn: String? = null,
    val imageUrl: String? = null,
    val career: List<String> = emptyList(),
    val createTime: Long = System.currentTimeMillis(),
)
