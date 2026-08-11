package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.remote.steam.dto.SteamAppDetailsWrapperDto
import com.otakup.niriko.data.remote.steam.dto.SteamCurrentPlayersResponseDto
import com.otakup.niriko.data.remote.steam.dto.SteamOwnedGamesResponseDto
import com.otakup.niriko.data.remote.steam.dto.SteamStoreSearchResponseDto
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Steam 商店 / Web API。
 *
 * 全部为公开接口（无需 API key）；key 仅用于未来扩展（可选，通过 [SteamApiClient] 注入）。
 * 语言/地区固定中文：l=schinese + cc=CN（价格币种 CNY、标题与描述中文）。
 */
interface SteamApiService {

    /**
     * 商店搜索（store.steampowered.com/api/storesearch）。
     * 仅用于 Steam 匹配，不是主搜索源。
     */
    @GET("api/storesearch")
    suspend fun searchApps(
        @Query("term") term: String,
        @Query("l") lang: String = "schinese",
        @Query("cc") countryCode: String = "CN",
        @Query("count") count: Int = 10,
    ): SteamStoreSearchResponseDto

    /**
     * 商店应用详情（store.steampowered.com/api/appdetails）。
     * 响应 Map<appid, AppDetailsWrapper>。
     */
    @GET("api/appdetails")
    suspend fun appDetails(
        @Query("appids") appIds: String,
        @Query("l") lang: String = "schinese",
        @Query("cc") countryCode: String = "CN",
    ): Map<String, SteamAppDetailsWrapperDto>

    /** 当前游玩人数（api.steampowered.com）。用 @Url 指向完整地址。 */
    @GET
    suspend fun currentPlayers(
        @Url url: String,
        @Query("appid") appId: Int,
        @Query("key") key: String? = null,
    ): SteamCurrentPlayersResponseDto

    /**
     * 用户游戏库（api.steampowered.com/IPlayerService/GetOwnedGames）。
     * 需 key + steamid64（用户自己的 key 查自己的库，隐私私密也可见）。
     * 用 @Url 传完整地址：baseUrl 是 store.steampowered.com（商店搜索/详情），
     * 相对路径会解析到 store 域 → 返回 HTML 错误页而非 JSON。
     */
    @GET
    suspend fun ownedGames(
        @Url url: String,
        @Query("key") key: String,
        @Query("steamid") steamId: String,
        @Query("include_appinfo") includeAppInfo: Boolean = true,
        @Query("include_played_free_games") includePlayedFreeGames: Boolean = true,
        @Query("include_free_sub") includeFreeSub: Boolean = true,
        @Query("format") format: String = "json",
    ): SteamOwnedGamesResponseDto
}
