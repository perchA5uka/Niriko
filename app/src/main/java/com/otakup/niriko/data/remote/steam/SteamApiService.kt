package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.remote.steam.dto.SteamAppDetailsWrapperDto
import com.otakup.niriko.data.remote.steam.dto.SteamCurrentPlayersResponseDto
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
}
