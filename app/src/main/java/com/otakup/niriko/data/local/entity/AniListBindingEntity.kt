package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bangumi subject ↔ AniList 条目绑定关系（表 anilist_bindings）。
 *
 * 与 vndb_bindings/steam_bindings 同构：bangumi 词条可绑定 AniList 条目，
 * AniList 作为补充信息源提供英文名/原名/评分/状态/集数/标签/简介等。
 * 匹配**不限制类型**（动画/漫画/游戏等均可），区别于 VNDB 仅 GAME。
 */
@Entity(tableName = "anilist_bindings")
data class AniListBindingEntity(
    /** Bangumi subjectId（主键）。 */
    @PrimaryKey
    val subjectId: Long,
    /** AniList Media id（GraphQL Media.id）。 */
    val anilistId: Long,
    /** 匹配方式：AUTO（自动标题匹配）/ MANUAL（手动绑定）。 */
    val matchMethod: String = "AUTO",
    /** 自动匹配置信度（0-1），MANUAL 为 1。 */
    val confidence: Float = 0f,
    /** 绑定创建时间戳。 */
    val createTime: Long = 0L,
)
