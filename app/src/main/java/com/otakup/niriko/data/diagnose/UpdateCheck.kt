package com.otakup.niriko.data.diagnose

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 检查更新（13.4）：**只做版本比较 + 给浏览器地址**，不做 APK 下载/安装。
 *
 * 纯函数部分（解析/比较/URL）可单测；网络请求由 UI 用现成 OkHttp 发一次，
 * 失败就退化成「直接打开 Releases 页」，用户永远能自己看最新版。
 */
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int, val preRelease: String? = null)

/** 解析 "v1.2.1" / "1.2.1-beta01" / "v1.2"；无法解析返回 null。 */
fun parseVersion(raw: String?): SemanticVersion? {
    val s = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
    if (s.isEmpty()) return null
    val core = s.substringBefore('-')
    val pre = s.substringAfter('-', "").ifBlank { null }
    val parts = core.split('.')
    if (parts.isEmpty() || parts.size > 3) return null
    val nums = parts.map { it.toIntOrNull() ?: return null }
    return SemanticVersion(
        major = nums.getOrElse(0) { 0 },
        minor = nums.getOrElse(1) { 0 },
        patch = nums.getOrElse(2) { 0 },
        preRelease = pre,
    )
}

/**
 * latest 是否比 current 新。
 *
 * 规则：数字段逐个比；数字相同而 latest 是正式版、current 是预发布时，latest 更新
 * （避免 beta 用户永远收不到正式版提示）。无法解析任一侧 → false（宁可不提示）。
 */
fun isNewerVersion(current: String, latest: String): Boolean {
    val c = parseVersion(current) ?: return false
    val l = parseVersion(latest) ?: return false
    if (l.major != c.major) return l.major > c.major
    if (l.minor != c.minor) return l.minor > c.minor
    if (l.patch != c.patch) return l.patch > c.patch
    return c.preRelease != null && l.preRelease == null
}

/**
 * GitHub Releases 页面（只跳浏览器，不申请安装权限）。
 *
 * owner/repo 取自 README 顶部「仓库：https://github.com/perchA5uka/Niriko」；
 * 换成别的镜像时只改这两个默认值即可（UI 不再拼 URL）。
 */
fun releasesUrl(owner: String = "perchA5uka", repo: String = "Niriko"): String =
    "https://github.com/$owner/$repo/releases"

data class UpdateDecision(
    val current: String,
    val latest: String?,
    val hasUpdate: Boolean,
    val url: String,
)

/** 组合决策：拿不到 latest 也返回可点的 URL（UI 可直接打开）。 */
fun decideUpdate(current: String, latestTag: String?): UpdateDecision = UpdateDecision(
    current = current,
    latest = latestTag,
    hasUpdate = latestTag != null && isNewerVersion(current, latestTag),
    url = releasesUrl(),
)

/** GitHub `releases/latest` 的按需字段（只取展示与跳转要用的三个）。 */
data class ReleaseInfo(
    val tag: String,
    val htmlUrl: String,
    val publishedAt: String? = null,
)

/** 「最新 release」接口（公开仓库，无需 token；GitHub 要求带 User-Agent）。 */
const val LATEST_RELEASE_API: String =
    "https://api.github.com/repos/perchA5uka/Niriko/releases/latest"

private val releaseJson = Json { ignoreUnknownKeys = true }

/**
 * 解析 `releases/latest` 的响应体。
 *
 * 抽成纯函数是为了用固定字符串单测：GitHub 的字段很多，我们只认 `tag_name`（必需）、
 * `html_url`（缺失时回退发布页）、`published_at`（可选）。结构不符返回 null，由调用方降级。
 */
fun parseLatestRelease(body: String): ReleaseInfo? = runCatching {
    val root = releaseJson.parseToJsonElement(body).jsonObject
    val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: return@runCatching null
    ReleaseInfo(
        tag = tag,
        htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: releasesUrl(),
        publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull,
    )
}.getOrNull()

/** 3 秒超时：这是设置页的一次轻量查询，慢于 3 秒就不值得让用户等。 */
private val releaseClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()
}

/**
 * 真查一次最新版本（13.4）。**只查版本，不下载、不安装，不申请任何安装权限。**
 *
 * 任何失败（无网 / 超时 / 403 / 响应不可解析）都收敛成 [Result.failure]，
 * 由 UI 退化成「打开发布页」——用户永远有手动路径。
 */
suspend fun fetchLatestRelease(url: String = LATEST_RELEASE_API): Result<ReleaseInfo> =
    withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                // GitHub API 对无 UA 的请求直接 403；这里不是伪装浏览器，只标明客户端身份。
                .header("User-Agent", "Niriko-Android")
                .get()
                .build()
            val info = releaseClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                parseLatestRelease(response.body?.string().orEmpty()) ?: error("响应缺少 tag_name")
            }
            Result.success(info)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
