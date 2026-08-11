package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 哔哩哔哩导入的原始数据快照（追番 + 用户评分/短评）。
 *
 * 与 [CollectionEntity]（用户收藏主表）解耦：导入数据先落此表，用户确认后
 * 才由导入器合并进 collections（status / rating / personalImpression / watchedEpisodes），
 * 避免未经确认就覆盖用户已有评分。
 */
@Entity(tableName = "bilibili_sync_items")
data class BilibiliSyncItemEntity(
    /** bilibili media_id（追番列表主键）。 */
    @PrimaryKey
    val mediaId: Long,
    /** follow/list 返回的 season_id，用于 bilibili_site_map 反查。 */
    val seasonId: Int? = null,
    /** 匹配到的 Bangumi 条目 ID。未匹配为 null。 */
    val bgmSubjectId: Long? = null,
    val title: String,
    val cover: String? = null,
    /** bili 追番状态：1 想看 / 2 在看 / 3 看过。 */
    val followStatus: Int,
    /** 看到第几话。 */
    val progress: Int? = null,
    val totalEpisodes: Int? = null,
    /** pgc/review/user 短评分数（0 表示未评分）。 */
    val biliScore: Float? = null,
    /** pgc/review/user 短评内容。 */
    val biliComment: String? = null,
    /** 已合并进 collections。 */
    val imported: Boolean = false,
    val importTime: Long = System.currentTimeMillis(),
)