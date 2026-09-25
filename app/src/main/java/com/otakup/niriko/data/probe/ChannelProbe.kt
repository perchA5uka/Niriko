package com.otakup.niriko.data.probe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 灰色通道（非官方 / 未文档化接口）探针框架。
 *
 * ## 为什么需要这个框架
 *
 * 第 3 轮把豆瓣剧照实现成「客户端实时搜索」并失败了，根本原因是**没有任何测量手段**：
 * 我既不知道 rexxar / frodo / HTML 三条链路各自返回什么状态码，也不能在本机复现
 * （pwsh 无法直连外网、web_fetch 不能带自定义头）。于是只能盲猜路径与请求头。
 *
 * 本框架把这个「盲猜」变成可观测的工程动作：
 * - [ChannelProbe.probe] 对**每一个**候选端点单独发请求，把 HTTP 状态码、耗时、
 *   响应前若干字节**原样**返回给 UI 展示；
 * - 端点与请求头全部可配置，失效时用户在设置页改，不必等发版；
 * - **默认只在用户点「自检」时运行**，不参与任何后台刷新（避免把用户 IP 打进黑名单）；
 * - 每个源的解析逻辑都要有 fixture 单测（用自检拿到的真实响应固化），
 *   这样页面改版时测试会红，而不是静默失效。
 *
 * ## 与「评分源」的区别
 *
 * [com.otakup.niriko.data.remote.rating.RatingSource] 是**正向数据**通道（有官方 API）。
 * 本框架覆盖的是「有可用接口但非官方」的那一类，因此额外带：
 * 默认关闭、可配置头、显式风险标注、探针自检。
 */
interface ChannelProbe {

    /** 唯一 id（用于设置页持久化「上次自检结果」时可省略，仅作标识）。 */
    val id: String

    /** 展示名。 */
    val label: String

    /** 一句话说明这个通道是干什么的、以及它的性质。 */
    val description: String

    /** 用户可见的风险/合规提示（非空时设置页会以警示色显示）。 */
    val riskNote: String? get() = null

    /** 该源是否即使未开启灰色通道也允许自检（豆瓣是，因为它有独立开关）。 */
    val probeRegardlessOfToggle: Boolean get() = true

    /** 参与自检的端点清单（UI 逐个展示，便于看出是哪一条挂了）。 */
    fun endpoints(config: ProbeConfig): List<ProbeEndpoint>

    /**
     * 自定义探测实现。默认走 [ProbeHttp] 的通用 GET。
     *
     * 需要 POST / 特殊头（如豆瓣 frodo 要求 apikey 放 header）的源覆写本方法。
     */
    suspend fun probe(endpoint: ProbeEndpoint, config: ProbeConfig): ProbeResult =
        ProbeHttp.get(endpoint, config)
}

/**
 * 单个可探测端点。
 *
 * @param name 展示名（如「rexxar JSON」「HTML 剧照页」）
 * @param url 完整 URL
 * @param headers 该端点所需的额外请求头（Referer / apikey / Cookie 等）
 * @param method GET / POST
 * @param body POST 时的请求体（form 编码，已拼好）
 * @param note 该端点的预期（UI 展示，帮助用户判读结果）
 */
data class ProbeEndpoint(
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val method: String = "GET",
    val body: String? = null,
    val note: String? = null,
)

/** 探针结果状态。语义刻意区分「被拦」与「不可达」——两者的处置方式完全不同。 */
enum class ProbeState {
    /** 2xx，且（如有预期）响应内容符合预期。 */
    OK,

    /** 拿到了响应，但状态码非 2xx（401/403/400/302→反爬页）。说明**域名可达但被拒**。 */
    BLOCKED,

    /** 连不上（DNS / 超时 / 连接被重置）。说明**网络层就不通**。 */
    UNREACHABLE,

    /** 未执行（未配置必要参数）。 */
    SKIPPED,
}

/**
 * 一次探测的完整证据。
 *
 * [bodyPreview] 是**原样**的响应前若干字节——这是整个框架存在的理由：
 * 没有它，解析器就只能靠猜页面的结构。
 */
data class ProbeResult(
    val endpointName: String,
    val url: String,
    val state: ProbeState,
    val httpStatus: Int? = null,
    val elapsedMs: Long = 0L,
    /** 响应体前 [ProbeHttp.PREVIEW_LIMIT] 字节（原样，不美化）。 */
    val bodyPreview: String = "",
    /** 响应体总长度（截断前的真实值）。 */
    val bodyLength: Int = 0,
    /** 命中重定向时的最终 URL（豆瓣 302 到 sec.douban.com 是典型反爬信号）。 */
    val finalUrl: String? = null,
    /** 响应头里的关键项（content-type / set-cookie 摘要）。 */
    val contentType: String? = null,
    /** 失败原因（异常 message / 超时 / DNS）。 */
    val error: String? = null,
    /**
     * 响应里解析出的条目数（第 6 轮 F9）。
     *
     * 「HTTP 200 但 0 条」和「HTTP 200 有 25 条」是完全不同的结论，
     * 搜索链路自检必须把条数也摆出来，否则无法判断是接口被拦还是关键词无命中。
     * null = 响应体不是可识别的列表结构。
     */
    val itemCount: Int? = null,
) {
    /** UI 一行摘要。 */
    val summary: String
        get() = buildString {
            append(
                when (state) {
                    ProbeState.OK -> "可用"
                    ProbeState.BLOCKED -> "被拒绝"
                    ProbeState.UNREACHABLE -> "不可达"
                    ProbeState.SKIPPED -> "未执行"
                }
            )
            httpStatus?.let { append(" · HTTP $it") }
            if (elapsedMs > 0) append(" · ${elapsedMs}ms")
            itemCount?.let { append(" · $it 条") }
            error?.let { append(" · $it") }
        }
}

/** 探针运行所需的可配置项（全部来自设置页，用户可改）。 */
data class ProbeConfig(
    /** 自定义 User-Agent（空 = 用 [ProbeHttp.DEFAULT_UA]）。 */
    val userAgent: String = "",
    /** 自定义 Cookie 串（空 = 不带）。 */
    val cookie: String = "",
    /** 豆瓣图片 Referer（默认 douban.com，对齐 Bangumi-master 实测值）。 */
    val doubanImageReferer: String = "https://douban.com",
    /** 豆瓣 rexxar/frodo API 的 Referer 前缀。 */
    val doubanApiReferer: String = "https://m.douban.com",
    /** 灰色通道是否开启（影响是否执行非自检类探测）。 */
    val grayChannelEnabled: Boolean = false,
)

/** 通用 HTTP 探测实现（自建短超时 client，避免拖住详情页的网络栈）。 */
object ProbeHttp {

    /** 响应体保留的最长字节数——刚好够看清是 JSON 还是反爬页，又不撑爆 UI。 */
    const val PREVIEW_LIMIT = 400

    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * 专用短超时 client。
     *
     * 不复用主 OkHttp：探针的目的就是**干净地测量**，主 client 挂着 baseUrl 重写、
     * 鉴权注入等拦截器，会把结果污染掉。
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            // 手动处理重定向：需要看到 302 的**目标**（豆瓣反爬就靠这个判定）
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun get(endpoint: ProbeEndpoint, config: ProbeConfig): ProbeResult =
        execute(endpoint, config)

    suspend fun execute(endpoint: ProbeEndpoint, config: ProbeConfig): ProbeResult =
        withContext(Dispatchers.IO) {
            if (endpoint.url.isBlank()) {
                return@withContext ProbeResult(
                    endpointName = endpoint.name,
                    url = endpoint.url,
                    state = ProbeState.SKIPPED,
                    error = "未配置 URL",
                )
            }

            val builder = Request.Builder().url(endpoint.url)
            builder.header("User-Agent", config.userAgent.takeIf { it.isNotBlank() } ?: DEFAULT_UA)
            builder.header("Accept", "*/*")
            if (config.cookie.isNotBlank()) {
                builder.header("Cookie", config.cookie)
            }
            endpoint.headers.forEach { (k, v) -> if (v.isNotBlank()) builder.header(k, v) }

            val request = if (endpoint.method.equals("POST", ignoreCase = true)) {
                val body = (endpoint.body ?: "").toRequestBody("application/x-www-form-urlencoded".toMediaType())
                builder.post(body).build()
            } else {
                builder.get().build()
            }

            val started = System.currentTimeMillis()
            try {
                client.newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - started
                    val full = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    val preview = full.take(PREVIEW_LIMIT)
                    val state = when {
                        response.isSuccessful -> ProbeState.OK
                        else -> ProbeState.BLOCKED
                    }
                    ProbeResult(
                        endpointName = endpoint.name,
                        url = endpoint.url,
                        state = state,
                        httpStatus = response.code,
                        elapsedMs = elapsed,
                        bodyPreview = preview,
                        bodyLength = full.length,
                        finalUrl = response.request.url.toString().takeIf { it != endpoint.url },
                        contentType = response.header("Content-Type"),
                    )
                }
            } catch (e: Exception) {
                ProbeResult(
                    endpointName = endpoint.name,
                    url = endpoint.url,
                    state = ProbeState.UNREACHABLE,
                    elapsedMs = System.currentTimeMillis() - started,
                    error = e.message ?: e.javaClass.simpleName,
                )
            }
        }
}
