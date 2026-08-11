package com.otakup.niriko.data.remote.game

/**
 * 游戏数据源能力声明。
 * 数据源可选择性实现部分能力；未声明的能力调用方不应调用。
 */
data class GameDataSourceCapabilities(
    /** 支持搜索。 */
    val supportsSearch: Boolean = false,
    /** 支持单游戏详情。 */
    val supportsDetail: Boolean = false,
    /** 支持截图。 */
    val supportsScreenshots: Boolean = false,
    /** 支持相似游戏。 */
    val supportsSimilar: Boolean = false,
    /** 支持热门/排行数据。 */
    val supportsCharts: Boolean = false,
)

/**
 * 通用游戏数据源接口。
 *
 * 实现类把各外部服务（Steam / RAWG / NeoDB 等）统一为 [GameItem] 模型，
 * 使"Bangumi 没有的词条、其他数据源有也能成为同等作品条目"。
 *
 * 所有方法异常保护由实现类自行保证（失败返回空/null，不抛给上层）。
 */
interface GameDataSource {
    /** 数据源唯一 id（如 "steam"、"rawg"、"neodb"），与 SubjectEntity.sourceId 对应。 */
    val id: String

    /** 数据源展示名（设置页/调试用）。 */
    val displayName: String

    /** 能力声明。 */
    val capabilities: GameDataSourceCapabilities

    /** 按标题搜索游戏条目。 */
    suspend fun search(query: String, limit: Int = 10): List<GameItem>

    /** 获取单游戏详情（sourceGameId 为该源内 id）。 */
    suspend fun getDetail(sourceGameId: String): GameItemDetail?

    /** 获取相似游戏。 */
    suspend fun getSimilarGames(sourceGameId: String, limit: Int = 10): List<GameItem>
}
