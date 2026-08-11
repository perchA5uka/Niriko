package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bangumi subject ↔ VNDB 条目绑定关系（表 vndb_bindings）。
 *
 * 与 steam_bindings 同构：bangumi 词条可同时绑定 Steam appid 与 VNDB id（绑定互通），
 * VNDB 作为补充信息源提供评分/开发者/时长/平台/语言/截图等。
 */
@Entity(tableName = "vndb_bindings")
data class VndbBindingEntity(
    /** Bangumi subjectId（主键）。 */
    @PrimaryKey
    val subjectId: Long,
    /** VNDB 条目 id（如 "v17"）。 */
    val vndbId: String,
    /** 匹配方式：AUTO（自动标题匹配）/ MANUAL（手动绑定）。 */
    val matchMethod: String = "AUTO",
    /** 自动匹配置信度（0-1），MANUAL 为 1。 */
    val confidence: Float = 0f,
    /** 绑定创建时间戳。 */
    val createTime: Long = 0L,
)
