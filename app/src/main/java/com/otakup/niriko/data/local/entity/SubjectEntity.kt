package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.util.TitleResolver

/**
 * 作品元数据（来自 Bangumi 或其他元数据源）。
 * 主键为 Bangumi Subject ID；本地其他数据源条目用正数 subjectId + sourceKey 标识。
 * sourceId 标识数据来源（如 "bangumi"、"anilist"、"steam"），用于插件路由。
 */
@Entity(
    tableName = "subjects",
    indices = [Index(value = ["sourceKey"], unique = true)],
)
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
    /** 精确放送时刻（0-1439 分钟，来自 onair 静态数据）。阶段 A。 */
    val airTimeMinutes: Int? = null,
    /** 放送时区标识（"CN" / "JP" / "LOCAL"），默认 CN。阶段 A。 */
    val airTimeZone: String? = null,
    /** 拼音搜索键（阶段 D：标题/中文名全拼音小写，SQL LIKE 命中用）。 */
    val pinyinKey: String? = null,
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
    /** 数据来源标识，如 "bangumi"、"anilist"、"steam"。插件/数据源路由依据。 */
    val sourceId: String = "bangumi",
    /**
     * 跨数据源稳定唯一键，形如 `{sourceId}:{sourceGameId}`（"steam:570"、"neodb:xxxx"）。
     * 取代负数占位 hack：任何数据源搜到的游戏都以正 subjectId + sourceKey 落库，
     * 与 Bangumi 词条同等展示/收藏。Bangumi 条目为 null（沿用 subjectId 语义）。
     */
    val sourceKey: String? = null,
) {
    /**
     * Steam 独占占位条目判定：sourceKey 以 "steam:" 为前缀（由 Steam 导入创建、
     * Bangumi 无词条的游戏）。详情页/卡片据此显示「Steam 独占」标记。
     * 迁移后为正值 subjectId（不再依赖负数 hack）。
     */
    val isSteamPlaceholder: Boolean
        get() = sourceKey?.startsWith("steam:") == true

    /** 展示名降级：中文名非空用中文名，否则用原名；原名也为空时用「未命名作品」。 */
    val displayTitle: String
        get() = TitleResolver.resolve(titleCN, title).primary.ifBlank { "未命名作品" }
}