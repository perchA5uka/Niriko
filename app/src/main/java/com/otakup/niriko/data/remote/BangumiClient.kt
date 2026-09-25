package com.otakup.niriko.data.remote

import com.otakup.niriko.data.remote.bangumi.BangumiApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object BangumiClient {

    /**
     * Bangumi API 基础地址。
     *
     * 默认官方地址；国内网络不可达时可改为自建反代/镜像（如 Kazumi 的 api.kazumi.fyi）。
     * 通过 [setBaseUrl] 可随时切换（依赖 [BaseUrlInterceptor] 在请求时动态重写，无需重建 Retrofit）。
     */
    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    /** 官方 API 地址。 */
    const val DEFAULT_BASE_URL = "https://api.bgm.tv/"

    /** 国内反代 API 地址（catcat.blog 公共反代，sub_filter 会把响应 JSON 图片域改写为反代图床）。 */
    const val PROXY_BASE_URL = "https://bgmapi.anibt.net/"

    /** 官方图片图床域（Bangumi 返回的封面 URL 前缀）。 */
    private const val OFFICIAL_IMAGE_HOST = "lain.bgm.tv"

    /** 官方 API 域：只有这个域才允许注入 Authorization（反代域绝不注入）。 */
    const val OFFICIAL_API_HOST = "api.bgm.tv"

    /** 反代图片图床域（对应 PROXY_BASE_URL 的图片反代）。 */
    private const val PROXY_IMAGE_HOST = "bgmimg.anibt.net"

    /** Bangumi 端点选择：官方站 / 国内反代。 */
    enum class BangumiEndpoint(val apiUrl: String, val imageHost: String) {
        OFFICIAL(DEFAULT_BASE_URL, OFFICIAL_IMAGE_HOST),
        PROXY(PROXY_BASE_URL, PROXY_IMAGE_HOST),
    }

    /** 当前端点（跟随 baseUrl）。 */
    val endpoint: BangumiEndpoint
        get() = if (baseUrl.startsWith(PROXY_BASE_URL)) BangumiEndpoint.PROXY else BangumiEndpoint.OFFICIAL

    /**
     * 切换端点（官方站/国内反代），即时生效（动态拦截器重写请求 URL）。
     * 同时影响 [rewriteImageUrl] 的图片域映射。
     */
    fun setEndpoint(e: BangumiEndpoint) {
        setBaseUrl(e.apiUrl)
    }
    /** 设置 API 基础地址（用于切换官方站/国内反代），可随时调用、即时生效。 */
    fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/') + "/"
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            baseUrl = normalized
        }
    }

    /**
     * 把 Bangumi 图片 URL 的图床域重写为当前端点对应域（官方 lain.bgm.tv / 反代 bgmimg.anibt.net）。
     * 仅替换已知图床域，其他 URL 原样返回。用于切换反代后旧缓存封面仍可加载。
     */
    fun rewriteImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val target = endpoint.imageHost
        return when {
            url.contains(OFFICIAL_IMAGE_HOST) -> url.replace(OFFICIAL_IMAGE_HOST, target)
            else -> url
        }
    }

    /** 重置为官方地址。 */
    fun resetToDefault() {
        baseUrl = DEFAULT_BASE_URL
    }

    private const val USER_AGENT = "Niriko/1.0.0 (Android)"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    /**
     * 当前用户的 Bangumi 凭据（token_type, access_token），由 Application 注入读取器。
     * token_type 按 Bangumi-master action.ts 约定存回传值（通常是 Bearer），为空时回退 Bearer。
     */
    @Volatile
    var authTokenProvider: (() -> AuthToken?)? = null

    /** 授权凭据（对应 Bangumi-master accessToken 的 token_type/access_token 双字段）。 */
    data class AuthToken(val tokenType: String = "Bearer", val accessToken: String)

    /**
     * 认证头注入策略（第 6 轮 F6，纯函数便于单测）。
     *
     * 只有**官方 API 域**（[OFFICIAL_API_HOST]）才可能拿到用户的 token：
     * 反代/镜像域不注入，避免把凭据交给第三方。无 token 或 token 为空 → 不注入。
     */
    internal fun authHeaderValue(host: String, token: AuthToken?): String? {
        if (host != OFFICIAL_API_HOST) return null
        if (token == null || token.accessToken.isBlank()) return null
        return if (token.tokenType.isBlank()) {
            "Bearer ${token.accessToken}"
        } else {
            "${token.tokenType} ${token.accessToken}"
        }
    }

    /**
     * 为官方域 v0/oauth 请求附加 Authorization
     *（Kazumi: Bearer 条件注入；Bangumi-master: token_type + access_token）。
     */
    internal val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val headerValue = authHeaderValue(request.url.host, authTokenProvider?.invoke())
        if (headerValue == null) {
            return@Interceptor chain.proceed(request)
        }
        chain.proceed(request.newBuilder().header("Authorization", headerValue).build())
    }

    private val userAgentInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .build()
        chain.proceed(request)
    }

    /**
     * 运行时动态 baseUrl：Retrofit 的 baseUrl 在构建时固定，无法感知切换。
     * 此拦截器在每次请求发出前读取当前 [baseUrl]，把请求 URL 的 scheme/host/port 重写为目标
     * 端点（路径与查询参数保持不变），从而实现官方站/反代的无缝切换。
     */
    private val baseUrlInterceptor = Interceptor { chain ->
        val current = baseUrl.trimEnd('/')
        val target = current.parseHttpUrl()
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

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            // 先重写域名（官方/反代切换），再按**重写后的域名**决定是否注入 token
            .addInterceptor(baseUrlInterceptor)
            // 第 6 轮 F6：主 client 也要带 token，否则 nsfw=true 的 v0 检索永远缺 Authorization。
            // 拦截器内部按域名白名单（只认官方 api.bgm.tv）注入，反代域拿不到用户凭据。
            .addInterceptor(authInterceptor)
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

        val apiService: BangumiApiService = retrofit.create(BangumiApiService::class.java)

    /** 专用 auth OkHttpClient：固定官方域（不挂 baseUrlInterceptor，避免 Token 打到反代）。 */
    private val authHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val authRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BASE_URL)
            .client(authHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /** v0 用户/收藏写接口，固定官方域（参考计划 2.5 风险条款：auth 一律走官方域）。 */
    val authApiService: com.otakup.niriko.data.remote.bangumi.BangumiAuthApi =
        authRetrofit.create(com.otakup.niriko.data.remote.bangumi.BangumiAuthApi::class.java)

    /**
     * 供 OAuth token 端点复用的 OkHttp 实例。
     *
     * `/oauth/access_token` 返回的是 **form/纯文本**（非 JSON），因此不能走 Retrofit 的
     * kotlinx-serialization 转换器；但又要复用「固定官方域 + User-Agent」这套配置，
     * 所以直接暴露 auth client 而不是另建一个。
     */
    val authOkHttpClient: OkHttpClient get() = authHttpClient

    /** 通用 OkHttp 实例（灰色通道探针复用其超时/日志配置）。 */
    val sharedOkHttpClient: OkHttpClient get() = okHttpClient

    /** OkHttp 的 HttpUrl 解析（用于合法性校验），不合法时返回 null。 */
    private fun String.parseHttpUrl(): okhttp3.HttpUrl? =
        runCatching { toHttpUrlOrNull() }.getOrNull()
}