package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.otakup.niriko.data.model.SubjectType

/**
 * 作品元数据（来自 Bangumi 或其他元数据源）。
 * 主键为 Bangumi Subject ID；本地手动添加的作品可用负数占位。
 * sourceId 标识数据来源（如 "bangumi"、"anilist"），用于插件路由。
 */
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey
    val subjectId: Long,
    val title: String,
    val titleCN: String? = null,
    val type: SubjectType,
    val summary: String? = null,
    val coverUrl: String? = null,
    val totalEpisodes: Int? = null,
    val platform: String? = null,
    val volumes: Int? = null,
    val airDate: String? = null,
    val airWeekday: Int? = null,
    val ratingScore: Float? = null,
    val ratingTotal: Int? = null,
    /** Bangumi 排名（rating.rank），1 为最高。历史排名/榜单用。 */
    val rank: Int? = null,
    /** Bilibili 社区评分（result.rating.score）。无则 null。 */
    val biliScore: Float? = null,
    /** Bilibili 评分人数（result.rating.count）。无则 null。 */
    val biliRatingTotal: Int? = null,
    /** 命中 bilibili_site_map 的 season_id（b / bhmt）。无映射则 null。 */
    val biliSeasonId: Int? = null,
    val series: Boolean? = null,
    val tags: List<String> = emptyList(),
    val lastSyncTime: Long = 0L,
    /** 数据来源标识，如 "bangumi"、"anilist"。插件路由依据。 */
    val sourceId: String = "bangumi",
) {
    /**
     * Steam 独占占位条目判定：Steam 有词条但 Bangumi 无，以负数 subjectId（-appId）占位展示。
     * 详情页/卡片据此显示「Steam 独占」标记；可经升级迁移转为正式 Bangumi 词条。
     */
    val isSteamPlaceholder: Boolean
        get() = sourceId == "steam" && subjectId < 0
}