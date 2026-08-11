package com.otakup.niriko.data.remote.game

import com.otakup.niriko.data.remote.steam.SteamApiClient
import com.otakup.niriko.data.remote.steam.SteamApiService
import com.otakup.niriko.data.remote.steam.dto.SteamStoreSearchItemDto

/**
 * Steam 作为通用游戏数据源（[GameDataSource] 的第一个实现）。
 *
 * 复用现有 SteamApiService（storesearch / appdetails）：
 * - search：商店搜索（l=schinese, cc=CN）；
 * - getDetail：appdetails 拉取详情（开发商/发行商/截图/标签等）；
 * - 能力：search + detail（截图含在 detail 内）。
 *
 * 全部方法异常保护，失败返回空/null（补充数据源，不影响主流程）。
 */
class SteamGameDataSource(
    private val apiService: SteamApiService = SteamApiClient.apiService,
) : GameDataSource {

    override val id: String = "steam"

    override val displayName: String = "Steam"

    override val capabilities: GameDataSourceCapabilities = GameDataSourceCapabilities(
        supportsSearch = true,
        supportsDetail = true,
        supportsScreenshots = true,
    )

    override suspend fun search(query: String, limit: Int): List<GameItem> {
        return runCatching {
            apiService.searchApps(term = query, count = limit.coerceIn(1, 50))
                .items
                .map { it.toGameItem() }
        }.getOrDefault(emptyList())
    }

    override suspend fun getDetail(sourceGameId: String): GameItemDetail? {
        val appId = sourceGameId.toIntOrNull() ?: return null
        return runCatching {
            val wrapper = apiService.appDetails(appIds = appId.toString())[appId.toString()] ?: return null
            val data = wrapper.data ?: return null
            GameItemDetail(
                item = GameItem(
                    sourceGameId = sourceGameId,
                    title = data.name ?: "App $appId",
                    summary = data.shortDescription,
                    platforms = data.platforms?.let { p ->
                        listOfNotNull(
                            p.windows?.takeIf { it }?.let { "Windows" },
                            p.mac?.takeIf { it }?.let { "macOS" },
                            p.linux?.takeIf { it }?.let { "Linux" },
                        )
                    } ?: emptyList(),
                    developers = data.developers,
                    publishers = data.publishers,
                    ratingScore = data.metacritic?.score?.toFloat(),
                    tags = data.genres.mapNotNull { it.description },
                    coverUrl = data.headerImage,
                    releaseDate = data.releaseDate?.date,
                ),
                screenshots = data.screenshots.mapNotNull { it.pathFull },
            )
        }.getOrNull()
    }

    override suspend fun getSimilarGames(sourceGameId: String, limit: Int): List<GameItem> =
        emptyList() // Steam 无相似游戏接口，留空

    /** storesearch 条目 → GameItem（注意 storesearch 没有开发商/简介，仅标题/封面/发行日期）。 */
    private fun SteamStoreSearchItemDto.toGameItem(): GameItem = GameItem(
        sourceGameId = id.toString(),
        title = name,
        aliases = null,
        coverUrl = tinyImage,
        summary = null,
        releaseDate = null,
    )
}
