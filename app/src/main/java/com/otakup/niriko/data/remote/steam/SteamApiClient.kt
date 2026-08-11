package com.otakup.niriko.data.remote.steam

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

    /**
     * 可选 Steam Web API key 读取器（由 Application 注入 SettingsDataStore 读取）。
     * 公开接口（storesearch / appdetails / 当前游玩人数）均无需 key；key 仅用于未来扩展。
     */
    @Volatile
    var apiKeyProvider: (() -> String?)? = null

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
}
