package com.otakup.niriko.data.remote.game

import com.otakup.niriko.data.model.SubjectType

/**
 * 游戏数据源统一条目模型（数据源无关）。
 *
 * 各数据源（Steam / RAWG / NeoDB 等）的搜索结果先映射为该模型，
 * 再经 [GameItemMapper] 转为本地 SubjectEntity，使"任何源搜到的游戏
 * 都能成为与 Bangumi 作品同等的条目"。
 *
 * 字段取各源交集，缺失项为 null；映射时按需填充。
 */
data class GameItem(
    /** 数据源内唯一 id（如 Steam appid、RAWG id、NeoDB uuid 的字符串形式）。 */
    val sourceGameId: String,
    /** 主标题。 */
    val title: String,
    /** 别名（逗号分隔或空格分隔的字符串，可为 null）。 */
    val aliases: String? = null,
    /** 封面 URL。 */
    val coverUrl: String? = null,
    /** 简介（可为 null）。 */
    val summary: String? = null,
    /** 平台列表（如 ["PC", "PlayStation 5"]，可为空）。 */
    val platforms: List<String> = emptyList(),
    /** 开发商列表（可为空）。 */
    val developers: List<String> = emptyList(),
    /** 发行商列表（可为空）。 */
    val publishers: List<String> = emptyList(),
    /** 评分（源内评分，量纲随源；RAWG 0-5、NeoDB 0-10、Steam Metacritic 0-100）。 */
    val ratingScore: Float? = null,
    /** 评分人数。 */
    val ratingCount: Int? = null,
    /** 标签（如 ["角色扮演", "冒险"]）。 */
    val tags: List<String> = emptyList(),
    /** 发行日期（ISO-8601，如 "2023-08-03"）。 */
    val releaseDate: String? = null,
    /**
     * 落库类型。默认 GAME（Steam/VNDB 等纯游戏源）；AniList 等覆盖动画/漫画的源
     * 按条目实际类型填充（ANIME/MANGA），避免被硬编码成 GAME 导致类型筛选/详情行为错位。
     */
    val type: SubjectType = SubjectType.GAME,
)

/**
 * 游戏数据源统一详情模型（数据源无关，详情页增强用）。
 * 在 [GameItem] 基础上补充截图与相似游戏。
 */
data class GameItemDetail(
    val item: GameItem,
    /** 截图 URL 列表。 */
    val screenshots: List<String> = emptyList(),
    /** 相似游戏（精简条目，可为空）。 */
    val similarGames: List<GameItem> = emptyList(),
)
