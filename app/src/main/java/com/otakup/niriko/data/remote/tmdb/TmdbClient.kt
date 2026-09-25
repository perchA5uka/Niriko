package com.otakup.niriko.data.remote.tmdb

import com.otakup.niriko.data.remote.tmdb.TmdbApiService
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * TMDb 客户端。
 *
 * 设计对齐项目已有的 [com.otakup.niriko.data.remote.BangumiClient]：
 *
 * - **baseUrl 运行时可切换**：Retrofit 的 baseUrl 构建时固定，用 [baseUrlInterceptor] 在每次请求前
 *   把 scheme/host/port 重写到当前端点。这是**国区可用性的关键**——`api.themoviedb.org` 与
 *   `image.tmdb.org` 在国区不可直连，用户可在设置页填写自建镜像。
 * - **api_key 由用户自备**（对齐 AniShelf：Keychain 存 key、`/3/configuration` 校验、绝不硬编码）。
 *   拦截器统一把 key 附加为 query 参数。
 * - **429 退避**：最多重试 2 次，优先尊重 `Retry-After`（对齐 AniShelf 的 RedirectingHTTPClient）。
 */
object TmdbClient {

    const val DEFAULT_BASE_URL = "https://api.themoviedb.org/"
    const val DEFAULT_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    /** 图片基地址（随端点一起切换）。 */
    @Volatile
    var imageBaseUrl: String = DEFAULT_IMAGE_BASE_URL
        private set

    @Volatile
    private var apiKey: String = ""

    /** 由 Application 注入：从设置读取 api key（避免网络层依赖 DataStore）。 */
    @Volatile
    var apiKeyProvider: (() -> String?)? = null

    /**
     * TMDb 端点。
     * 注意：官方域在国区不可直连，[CUSTOM] 允许用户填自建反代 / 镜像（公共镜像有稳定性与法律风险，
     * 因此不硬编码任何第三方地址）。
     */
    enum class TmdbEndpoint(val label: String, val apiUrl: String, val imageUrl: String) {
        OFFICIAL("官方（需自备网络环境）", DEFAULT_BASE_URL, DEFAULT_IMAGE_BASE_URL),
        CUSTOM("自定义镜像", DEFAULT_BASE_URL, DEFAULT_IMAGE_BASE_URL),
        ;
    }

    fun setApiKey(key: String?) {
        apiKey = key?.trim().orEmpty()
    }

    /** 当前有效 key（优先注入的 provider，回退到 setApiKey 写入的值）。 */
    fun currentApiKey(): String = (apiKeyProvider?.invoke() ?: apiKey).trim()

    fun hasApiKey(): Boolean = currentApiKey().isNotEmpty()

    /**
     * 切换端点。customApiUrl / customImageUrl 为空时回退官方。
     * api 与 image 两个域必须同时切换，否则封面仍会打到不可达的 image.tmdb.org。
     */
    fun setEndpoint(apiUrl: String?, imageUrl: String?) {
        val api = normalize(apiUrl) ?: DEFAULT_BASE_URL
        val img = normalize(imageUrl) ?: DEFAULT_IMAGE_BASE_URL
        baseUrl = api
        imageBaseUrl = img
        TmdbImageUrl.imageBaseUrl = img
    }

    fun resetToDefault() {
        setEndpoint(null, null)
    }

    private fun normalize(url: String?): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) return null
        return raw.trimEnd('/') + "/"
    }

    private const val USER_AGENT = "Niriko/1.0.0 (Android)"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val userAgentInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder().header("User-Agent", USER_AGENT).build()
        )
    }

    /** 运行时动态 baseUrl（与 BangumiClient 的 baseUrlInterceptor 同款写法）。 */
    private val baseUrlInterceptor = Interceptor { chain ->
        val target = baseUrl.trimEnd('/').toHttpUrlOrNull()
        val original = chain.request()
        if (target == null) {
            chain.proceed(original)
        } else {
            val rewritten = original.url.newBuilder()
                .scheme(target.scheme)
                .host(target.host)
                .port(target.port)
                .encodedPath(target.encodedPath + original.url.encodedPath.trimStart('/'))
                .build()
            chain.proceed(original.newBuilder().url(rewritten).build())
        }
    }

    /** 统一附加 api_key（v3 认证方式；缺 key 时不附加，由调用方保证不会发出请求）。 */
    private val apiKeyInterceptor = Interceptor { chain ->
        val key = currentApiKey()
        val original = chain.request()
        if (key.isEmpty()) {
            chain.proceed(original)
        } else {
            val url = original.url.newBuilder().addQueryParameter("api_key", key).build()
            chain.proceed(original.newBuilder().url(url).build())
        }
    }

    /** 429 退避重试（最多 2 次，优先 Retry-After）。 */
    private val rateLimitInterceptor = Interceptor { chain ->
        var attempt = 0
        var response = chain.proceed(chain.request())
        while (response.code == 429 && attempt < MAX_RATE_LIMIT_RETRIES) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
            val delayMs = retryAfterSeconds?.let { it * 1000L }
                ?: (BASE_RETRY_DELAY_MS shl attempt)
            response.close()
            Thread.sleep(delayMs.coerceAtMost(MAX_RETRY_DELAY_MS))
            attempt++
            response = chain.proceed(chain.request())
        }
        response
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val apiService: TmdbApiService by lazy { retrofit.create(TmdbApiService::class.java) }

    private const val MAX_RATE_LIMIT_RETRIES = 2
    private const val BASE_RETRY_DELAY_MS = 500L
    private const val MAX_RETRY_DELAY_MS = 10_000L
}
