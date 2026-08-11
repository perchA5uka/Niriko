package com.otakup.niriko.data.remote.steam

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

/**
 * Steam 商店 / Web API 客户端。
 *
 * - baseUrl: https://store.steampowered.com/（商店搜索与详情）
 * - 当前游玩人数走 api.steampowered.com（@Url 传完整地址）
 * - 可选 API key 由 [apiKeyProvider] 动态注入（设置页配置，空则不携带）
 */
object SteamApiClient {

    /** 商店 API 基础地址。 */
    const val BASE_URL = "https://store.steampowered.com/"

    /** 当前游玩人数接口完整地址（api.steampowered.com）。 */
    const val CURRENT_PLAYERS_URL = "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/"

    /** 用户游戏库接口完整地址（api.steampowered.com，baseUrl 是 store 域，需完整地址）。 */
    const val OWNED_GAMES_URL = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/"

    /** 用户家庭组接口（api.steampowered.com，需用户 access token）。 */
    const val FAMILY_GROUP_URL = "https://api.steampowered.com/IFamilyGroupsService/GetFamilyGroup/v1/"

    /** 家庭组共享库应用列表接口（api.steampowered.com，需用户 access token）。 */
    const val SHARED_LIBRARY_APPS_URL = "https://api.steampowered.com/IFamilyGroupsService/GetSharedLibraryApps/v1/"

    /** 单游戏用户成就进度接口（api.steampowered.com）。 */
    const val PLAYER_ACHIEVEMENTS_URL = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v0001/"

    /** 单游戏成就定义接口（api.steampowered.com）。 */
    const val SCHEMA_FOR_GAME_URL = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v1/"

    /** 登录态下获取 webapi_token（用户 access token）的接口（store.steampowered.com）。 */
    const val WEB_API_TOKEN_URL = "https://store.steampowered.com/pointssummary/ajaxgetasyncconfig"

    /**
     * 可选 Steam Web API key 读取器（由 Application 注入 SettingsDataStore 读取）。
     * 公开接口（storesearch / appdetails / 当前游玩人数）均无需 key；key 仅用于未来扩展。
     */
    @Volatile
    var apiKeyProvider: (() -> String?)? = null

    /**
     * Steam 登录会话 Cookie 读取器（由登录页在 OpenID 流程后注入）。
     * 用于从 store.steampowered.com 登录态接口获取 webapi_token（用户 access token，
     * 供家庭库 IFamilyGroupsService 使用）。null 表示未注入（手动登录路径）。
     */
    @Volatile
    var webCookieProvider: (() -> String)? = null

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: SteamApiService = retrofit.create(SteamApiService::class.java)

    /** 当前注入的 API key（无配置返回 null）。 */
    fun currentApiKey(): String? = apiKeyProvider?.invoke()?.takeIf { it.isNotBlank() }

    /** 当前登录会话 Cookie（未注入返回 null）。 */
    fun currentWebCookie(): String? = webCookieProvider?.invoke()?.takeIf { it.isNotBlank() }

    /**
     * 从登录态获取 webapi_token（用户 access token，供家庭库接口使用）。
     *
     * 需要登录会话 Cookie（[currentWebCookie]）；token 约 1-2 天过期。
     * 直接 OkHttp 请求 store.steampowered.com 登录态接口（带 Cookie），
     * 解析响应中的 webapi_token 字段。失败（未登录/过期/网络）返回 null，不抛异常。
     */
    suspend fun fetchWebApiToken(): String? = runCatching {
        val cookie = currentWebCookie() ?: return null
        val request = okhttp3.Request.Builder()
            .url(WEB_API_TOKEN_URL)
            .header("Cookie", cookie)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36")
            .get()
            .build()
        val body = withContext(kotlinx.coroutines.Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } ?: return null
        json.decodeFromString<com.otakup.niriko.data.remote.steam.dto.SteamWebApiTokenDto>(body)
            .webApiToken
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** 当前是否具备家庭库拉取条件（有 Cookie 可拿 token）。 */
    fun canFetchFamilyLibrary(): Boolean = currentWebCookie() != null
}
