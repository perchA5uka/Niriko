package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bangumi subject ↔ Steam appid 绑定关系（表 steam_bindings）。
 *
 * 保存匹配结果避免每次搜索重复匹配。
 * subjectId 为 Bangumi 作品主键，steamAppId 为 Steam 商店应用 ID。
 */
@Entity(tableName = "steam_bindings")
data class SteamBindingEntity(
    /** Bangumi subjectId（主键）。 */
    @PrimaryKey
    val subjectId: Long,
    /** Steam appid。 */
    val steamAppId: Int,
    /** 匹配方式：AUTO（自动标题匹配）/ MANUAL（手动绑定）。 */
    val matchMethod: String = "AUTO",
    /** 自动匹配置信度（0-1），MANUAL 为 1。 */
    val confidence: Float = 0f,
    /** 绑定创建时间戳。 */
    val createTime: Long = 0L,
)
