package com.otakup.niriko.data.probe

import com.otakup.niriko.data.diagnose.ProbeCell
import com.otakup.niriko.data.diagnose.ProbeStatus
import com.otakup.niriko.data.diagnose.ProbeSummary
import com.otakup.niriko.data.diagnose.SourceCapability
import com.otakup.niriko.data.diagnose.planProbes
import com.otakup.niriko.plugin.DataSourcePlugin
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 数据源自检执行器（2.5）。
 *
 * 本轮只做两件**只读**的事：
 * 1. 把插件声明的能力摊成矩阵（不联网）——「哪些能力根本没实现」立刻可见；
 * 2. 对每个源发一次 2s 超时的 HEAD/GET 小请求，只记录状态码与耗时（不解析正文）。
 *
 * 不做 POST、不登录、不并发（沿用 GrayChannelRunner「不并发打同一站」的原则），
 * 因此不会被当成爬虫，也不会因为一个源超时拖垮整页。
 */
object SourceProbeRunner {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    /** 插件 → 矩阵行（能力名取自 PROBE_CAPABILITIES，与 UI 表头同源）。 */
    fun capabilitiesOf(plugins: List<DataSourcePlugin>): List<SourceCapability> = plugins.map { plugin ->
        val caps = plugin.capabilities
        SourceCapability(
            sourceId = plugin.id,
            label = plugin.name,
            capabilities = mapOf(
                "搜索" to caps.supportsSearch,
                "详情" to caps.supportsDetail,
                "剧集" to caps.supportsEpisodes,
                "角色" to caps.supportsCharacters,
                "制作人员" to caps.supportsStaff,
                "日历" to caps.supportsCalendar,
                "评分分布" to caps.supportsRatingDistribution,
            ),
        )
    }

    /** 静态计划（不联网）：声明支持 = 待实测，未声明 = 不支持。 */
    fun plan(plugins: List<DataSourcePlugin>): List<ProbeCell> = planProbes(capabilitiesOf(plugins))

    /**
     * 对一组 URL 串行探测（只读）。
     * @return (url, 状态码或 null, 耗时毫秒)
     */
    suspend fun probeUrls(urls: List<String>): List<UrlProbe> = withContext(Dispatchers.IO) {
        urls.map { url -> probeOne(url) }
    }

    private fun probeOne(url: String): UrlProbe {
        val started = System.currentTimeMillis()
        val request = runCatching { Request.Builder().url(url).head().build() }.getOrNull()
            ?: return UrlProbe(url, null, 0, "URL 非法")
        return runCatching {
            client.newCall(request).execute().use { response ->
                UrlProbe(url, response.code, System.currentTimeMillis() - started, null)
            }
        }.getOrElse { error ->
            UrlProbe(url, null, System.currentTimeMillis() - started, error.message ?: "请求失败")
        }
    }

    fun summarize(urlProbes: List<UrlProbe>): ProbeSummary {
        val failed = urlProbes.count { it.statusCode == null || it.statusCode >= 400 }
        return ProbeSummary(
            probed = urlProbes.size,
            failed = failed,
            slowestMs = urlProbes.maxOfOrNull { it.elapsedMs } ?: 0,
        )
    }
}

/** 单次 URL 探测结果。 */
data class UrlProbe(
    val url: String,
    val statusCode: Int?,
    val elapsedMs: Long,
    val error: String?,
)
