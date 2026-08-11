package com.otakup.niriko.data.remote.vndb

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType

/**
 * VNDB REST API v2 客户端。
 *
 * - baseUrl: https://api.vndb.org/kana/（查询端点为 vn）
 * - 无需 API key；限流约 200 次/5 分钟
 * - 带 User-Agent（VNDB 官方要求，方便识别客户端）
 */
object VndbApiClient {

    const val BASE_URL = "https://api.vndb.org/kana/"

    /** VNDB 官方要求的 User-Agent（格式：{名称}/{版本}）。 */
    private const val USER_AGENT = "Niriko/1.0 (Android; https://github.com/)"

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
            // VNDB 要求 User-Agent；OkHttp 默认自带 UA，这里显式覆盖为可识别格式
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
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

    val apiService: VndbApiService = retrofit.create(VndbApiService::class.java)
}
