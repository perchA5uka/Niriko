package com.otakup.niriko.data.remote.bilibili

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Bilibili 社区评分客户端。
 *
 * 能力: 给定 B 站 season_id → 拉取社区评分(score/count)与区域限制。
 * - 免登录、纯 UA 即可(实测, 无需 Cookie/SESSDATA)
 * - 任何失败(网络/404/字段缺失) → 返回 null, 不抛异常
 * - 独立 OkHttpClient, 不污染主 Bangumi 客户端
 */
object BilibiliRatingClient {

    /** Bilibili API 基础地址。 */
    const val DEFAULT_BASE_URL = "https://api.bilibili.com/"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** B 站对 API 有简单校验, 带 Referer 与网页 UA 更稳。 */
    private const val REFERER = "https://www.bilibili.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val service: BilibiliApiService by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BilibiliApiService::class.java)
    }

    /** 社区评分结果。 */
    data class Rating(
        val score: Double,
        val count: Int,
        /** 是否仅港澳台播放(area_limit != 0 / ban_area_show)。 */
        val areaLimited: Boolean,
    )

    /**
     * 抓取某 season 的社区评分。
     * @return 有评分返回 [Rating], 无评分/请求失败返回 null(不抛异常)
     */
    suspend fun fetchRating(seasonId: Long): Rating? {
        if (seasonId <= 0L) return null
        return try {
            val resp = service.getSeasonDetail(seasonId)
            if (resp.code != 0) return null
            val result = resp.result ?: return null
            val rating = result.rating ?: return null
            if (rating.count <= 0 && rating.score <= 0.0) return null
            Rating(
                score = rating.score,
                count = rating.count,
                areaLimited = result.areaLimited,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 便捷方法: 给定 bgmId, 查离线映射 → 抓评分。
     * 大陆 (MAINLAND) 优先; 大陆无映射时回退港澳台 (HK_MO_TW)。
     * @return 命中且成功返回 [Rating]; 否则 null
     */
    suspend fun fetchRatingByBgmId(
        context: Context,
        bgmId: Long,
        region: BilibiliSiteMap.Region = BilibiliSiteMap.Region.MAINLAND,
    ): Rating? {
        val seasonId = BilibiliSiteMap.seasonId(context, bgmId, region)
            ?: BilibiliSiteMap.seasonId(context, bgmId, BilibiliSiteMap.Region.HK_MO_TW)
            ?: return null
        return fetchRating(seasonId.toLong())
    }
}