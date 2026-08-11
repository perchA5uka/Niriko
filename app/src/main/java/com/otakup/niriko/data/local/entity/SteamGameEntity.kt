package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Steam 扩展数据（表 steam_games）。
 *
 * 与核心作品模型解耦：subjectId 关联 [SubjectEntity]，
 * 仅存放 Steam 特有商业/运行数据，不污染 subjects 表。
 * 未来可同样方式扩展 VNDB / PSN / Xbox 等平台数据。
 */
@Entity(tableName = "steam_games")
data class SteamGameEntity(
    /** 关联的 Bangumi subjectId（主键，与 subjects.subjectId 对齐）。 */
    @PrimaryKey
    val subjectId: Long,
    /** Steam appid（商店应用 ID）。 */
    val appId: Int,
    /** Steam 商店标题（l=schinese 本地化）。 */
    val name: String,
    /** 商店短简介（short_description）。 */
    val shortDescription: String? = null,
    /** 开发商列表。 */
    val developers: List<String> = emptyList(),
    /** 发行商列表。 */
    val publishers: List<String> = emptyList(),
    /** 原始价格（分），币种见 [currency]；0 表示免费。 */
    val priceCents: Int? = null,
    /** 价格币种（如 CNY/USD）。 */
    val currency: String? = null,
    /** Metacritic 评分（0-100）。 */
    val metacriticScore: Int? = null,
    /** 当前游玩人数（30 分钟缓存后落库）。 */
    val currentPlayers: Int? = null,
    /** Steam 类型/标签（genres 描述，本地化）。 */
    val steamTags: List<String> = emptyList(),
    /** 商店截图全尺寸 URL 列表。 */
    val screenshots: List<String> = emptyList(),
    /** 商店 header 封面图 URL。 */
    val headerImage: String? = null,
    /** 发行日期（release_date.date 本地化字符串）。 */
    val releaseDate: String? = null,
    /** 最近一次更新（当前游玩人数刷新时间戳）。 */
    val lastUpdated: Long = 0L,
)
