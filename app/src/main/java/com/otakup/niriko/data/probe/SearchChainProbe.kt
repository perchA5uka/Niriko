package com.otakup.niriko.data.probe

import com.otakup.niriko.data.remote.BangumiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 搜索链路三层自检探针（第 6 轮 F9）。
 *
 * ## 为什么必须做这个
 *
 * 第 4 轮用「豆瓣三级链路自检」一次坐实了豆瓣的拦截层；本轮搜索「完全没有内容」同样
 * 只能靠一次真机测量定位，否则永远在猜。搜索链路的三层是：
 *
 * 1. GET /v0/subjects?type=2&sort=rank —— 浏览接口，历史排名用（GET，没有 POST body）；
 * 2. POST /v0/search/subjects —— 关键词检索，正式搜索用（最可能被反代吞 body / 缺 token）；
 * 3. GET /search/subject/关键词 —— 旧版兜底接口（免 token）。
 *
 * 三层各返回一条 [ProbeResult]：HTTP 状态码、耗时、解析出的条数、异常原文。
 * 一次点击即可判断「是某一层挂了」还是「网络整体不通」。
 *
 * ## 与主 client 的关系
 *
 * 探针**刻意不复用** BangumiClient 的 OkHttp（它挂着 baseUrl 重写 / token 注入 / 日志），
 * 用干净 client 测量，避免测量结果被拦截器污染；但 [run] 的 baseUrl 取自
 * [BangumiClient.baseUrl]，也就是用户当前真正在用的端点（官方/反代）。
 * token 则复用 [BangumiClient.authHeaderValue] 的域名白名单策略：官方域才带。
 */
object SearchChainProbe {

    /** 默认关键词。中文长词能同时检验 URL 编码与 POST body 编码。 */
    const val DEFAULT_KEYWORD = "巨人"

    /** 单层探测的超时（与 ProbeHttp 同量级：够看清楚，又不至于让用户等太久）。 */
    private const val TIMEOUT_SECONDS = 10L

    /** 请求体保留字节数（沿用 [ProbeHttp.PREVIEW_LIMIT]，便于原样看到反爬页/错误 JSON）。 */
    private const val PREVIEW_LIMIT = ProbeHttp.PREVIEW_LIMIT

    private const val USER_AGENT = "Niriko/1.0.0 (Android)"

    /** 一层探针端点（纯数据，构造过程可单测）。 */
    data class SearchChainStep(
        /** 展示名（含层号，UI 直接展示）。 */
        val name: String,
        val url: String,
        val method: String = "GET",
        /** POST 的 JSON body。 */
        val body: String? = null,
        /** 这一层的用途与判读提示。 */
        val note: String? = null,
    )

    /**
     * 构造三层端点（纯函数，可单测）。
     *
     * @param baseUrl 当前 API 基础地址（官方或反代），末尾斜杠可有可无
     */
    fun steps(baseUrl: String, keyword: String = DEFAULT_KEYWORD): List<SearchChainStep> {
        val base = baseUrl.trimEnd('/')
        val encoded = encodeKeyword(keyword)
        return listOf(
            SearchChainStep(
                name = "1) GET /v0/subjects 榜单",
                url = base + "/v0/subjects?type=2&sort=rank&limit=3",
                note = "浏览接口（历史排名走这条）；GET 没有 POST body，理论上最稳",
            ),
            SearchChainStep(
                name = "2) POST /v0/search/subjects 检索",
                url = base + "/v0/search/subjects?limit=3",
                method = "POST",
                body = searchPostBody(keyword),
                note = "正式搜索走这条；401=缺 token，0 条=接口被吞或关键词无命中；" +
                    "若仍 400，下一个候选项是 BangumiClient 的 Json explicitNulls=true " +
                    "（未设置的 filter 字段会被写成 null，删掉 series 后仍可能被拒）",
            ),
            SearchChainStep(
                name = "3) GET /search/subject 旧版兜底",
                url = base + "/search/subject/" + encoded + "?type=2&responseGroup=large&max_results=3",
                note = "免 token 的旧版接口；POST 层挂了时由它兜底",
            ),
        )
    }

    /**
     * POST /v0/search/subjects 的 JSON 请求体（纯函数，可单测）。
     *
     * 这里刻意**只发 keyword + sort**：本轮 §6.2 删掉的 filter.series 是未定义字段，
     * 发出去可能被服务端整条 400。
     *
     * 判读提示（诊断路径，来自 discover-eng）：BangumiClient 的 Json 默认 explicitNulls = true，
     * 会把「未设置」的 filter 字段序列化成 null。若 SERVER 对 null 字段严格校验，
     * 删掉 series 后 nsfw / 空关键词请求仍可能 400 —— 下一个候选项就是把 explicitNulls 设为 false。
     * 本探针自己拼 JSON 不受该设置影响，因此这一层的「200 但有/无条数」可以直接与正式搜索对照。
     */
    fun searchPostBody(keyword: String): String =
        "{\"keyword\":\"" + escapeJson(keyword) + "\",\"sort\":\"match\"}"

    /** JSON 字符串转义：只处理会破坏请求体的字符。 */
    internal fun escapeJson(text: String): String =
        text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    /** URL path 段编码（旧版接口把关键词放在 path 里）。 */
    internal fun encodeKeyword(keyword: String): String =
        URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")

    /**
     * 从响应体里数出条目数（纯函数，可单测）。
     *
     * 兼容三种形状：v0 分页对象（data）/ 旧版对象（list 或 results 数组）/ 裸数组。
     * 解析失败（例如返回的是 HTML 反爬页）返回 null。
     */
    fun countItems(body: String): Int? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return null
        if (root is JsonArray) return root.size
        val obj = root as? JsonObject ?: return null
        val array = (obj["data"] as? JsonArray)
            ?: (obj["list"] as? JsonArray)
            ?: (obj["results"] as? JsonArray)
        return array?.size
    }

    /** 探测专用干净 client（不挂任何拦截器，测量结果不被污染）。 */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 顺序执行三层探测（顺序而非并发：自检要的是逐层的耗时与状态，且数量很少）。
     *
     * @param baseUrl 被测基础地址，默认取当前端点
     * @param keyword 被测关键词
     * @param authTokenProvider 取凭据的回调，默认取应用当前登录态
     */
    suspend fun run(
        baseUrl: String = BangumiClient.baseUrl,
        keyword: String = DEFAULT_KEYWORD,
        authTokenProvider: (() -> BangumiClient.AuthToken?)? = BangumiClient.authTokenProvider,
    ): List<ProbeResult> = withContext(Dispatchers.IO) {
        steps(baseUrl, keyword).map { step -> execute(step, authTokenProvider) }
    }

    private suspend fun execute(
        step: SearchChainStep,
        authTokenProvider: (() -> BangumiClient.AuthToken?)?,
    ): ProbeResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val builder = Request.Builder()
            .url(step.url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
        // 与主 client 同一套域名白名单：只有官方域带 Authorization
        runCatching {
            val host = step.url.toHttpUrlOrNull()?.host
            if (host != null) {
                BangumiClient.authHeaderValue(host, authTokenProvider?.invoke())
            } else null
        }.getOrNull()?.let { builder.header("Authorization", it) }

        val request = if (step.method.equals("POST", ignoreCase = true)) {
            builder.post((step.body ?: "").toRequestBody("application/json".toMediaType())).build()
        } else {
            builder.get().build()
        }

        try {
            client.newCall(request).execute().use { response ->
                val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                val elapsed = System.currentTimeMillis() - started
                ProbeResult(
                    endpointName = step.name,
                    url = step.url,
                    state = if (response.isSuccessful) ProbeState.OK else ProbeState.BLOCKED,
                    httpStatus = response.code,
                    elapsedMs = elapsed,
                    bodyPreview = body.take(PREVIEW_LIMIT),
                    bodyLength = body.length,
                    finalUrl = response.request.url.toString().takeIf { it != step.url },
                    contentType = response.header("Content-Type"),
                    // 非 2xx 时把状态行原文带上，UI 一眼能看出是 401 还是被反爬页顶掉
                    error = if (response.isSuccessful) null else ("HTTP " + response.code + " " + response.message).trim(),
                    itemCount = countItems(body),
                )
            }
        } catch (e: Exception) {
            ProbeResult(
                endpointName = step.name,
                url = step.url,
                state = ProbeState.UNREACHABLE,
                elapsedMs = System.currentTimeMillis() - started,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }
}
