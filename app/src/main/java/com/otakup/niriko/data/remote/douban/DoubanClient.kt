package com.otakup.niriko.data.remote.douban

import com.otakup.niriko.data.remote.rating.RatingHttp
import com.otakup.niriko.data.remote.rating.arr
import com.otakup.niriko.data.remote.rating.int
import com.otakup.niriko.data.remote.rating.obj
import com.otakup.niriko.data.remote.rating.objects
import com.otakup.niriko.data.remote.rating.ratingUrlEncode
import com.otakup.niriko.data.remote.rating.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 豆瓣剧照客户端（三级 fallback）。
 *
 * ## 现状考证（不是猜的）
 *
 * 1. Bangumi-master 的「加密域名」只是混淆：解密密钥就是仓库里的 APP_ID，
 *    解出来是 `https://movie.douban.com` / `https://m.douban.com`，图片 Referer 是 `douban.com`。
 *    它没有任何私有中转，就是直接打豆瓣。
 * 2. 实测：`movie.douban.com/subject/{id}/photos` 会 302 到 `sec.douban.com`（反爬）；
 *    `m.douban.com/rexxar/api/v2/...` 返回 400 invalid_request（校验 Referer）；
 *    `frodo.douban.com/api/v2/...` 返回 400 code 997（apikey 必须在 header）。
 *
 * 因此：
 * - 三级降级（rexxar JSON → frodo JSON → HTML 解析），任一级成功即停；
 * - **头配方来自设置**，失效时用户自己就能修，不必等发版；
 * - 全失败静默返回空，剧照区仍靠 TMDb / B站 / Anitabi 正常工作。
 *
 * ## 合规
 * 豆瓣没有授权第三方抓取。本客户端默认**关闭**该通道，由用户在设置里主动开启，
 * 且仅用于个人查看；若将来分发，建议移除。
 */
object DoubanClient {

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /** 头配方（来自设置；任一为空则用默认值）。 */
    data class Headers(
        val apiRefererPrefix: String = "https://m.douban.com",
        /**
         * 图片防盗链 Referer。
         *
         * 第 4 轮 F 改为 `https://douban.com`（**不带 path，不带尾斜杠**）——
         * 这是 Bangumi-master 的实测值：带 path 的 `movie.douban.com/` 会被图床拒。
         */
        val imageReferer: String = "https://douban.com",
        val userAgent: String = DESKTOP_UA,
        val frodoApiKey: String = DEFAULT_FRODO_KEY,
    )

    /** frodo 的公开 apikey（社区广泛使用的那个；豆瓣未官方发布，随时可能失效）。 */
    const val DEFAULT_FRODO_KEY = "0ac44ae016490db2204ce0a042db2916"

    private val SUBJECT_PATH = mapOf(
        DoubanCategory.MOVIE to "movie",
        DoubanCategory.TV to "tv",
        DoubanCategory.BOOK to "book",
        DoubanCategory.MUSIC to "music",
        DoubanCategory.GAME to "game",
    )

    /**
     * 取剧照（三级 fallback）。
     *
     * @param start 起点（防剧透时用「靠后」的位置）
     * @return 图片 URL 列表（大图）；被拦截/失败返回空列表
     */
    suspend fun photos(
        doubanId: String,
        category: DoubanCategory,
        headers: Headers,
        count: Int = 40,
        start: Int = 0,
    ): List<String> {
        if (doubanId.isBlank()) return emptyList()
        val path = SUBJECT_PATH[category] ?: "movie"

        rexxarPhotos(doubanId, path, headers, count, start)?.let { if (it.isNotEmpty()) return it }
        frodoPhotos(doubanId, path, headers, count, start)?.let { if (it.isNotEmpty()) return it }
        htmlPhotos(doubanId, category, headers, start)?.let { if (it.isNotEmpty()) return it }
        return emptyList()
    }

    private suspend fun rexxarPhotos(
        doubanId: String,
        path: String,
        headers: Headers,
        count: Int,
        start: Int,
    ): List<String>? = withContext(Dispatchers.IO) {
        val url = "https://m.douban.com/rexxar/api/v2/" + path + "/" + doubanId +
            "/photos?start=" + start + "&count=" + count + "&type=S"
        val json = runCatching {
            RatingHttp.getJson(
                url,
                mapOf(
                    // rexxar 会校验 Referer 必须与 subject 页面匹配
                    "Referer" to (headers.apiRefererPrefix.trimEnd('/') + "/" + path + "/subject/" + doubanId + "/"),
                    "User-Agent" to headers.userAgent,
                ),
            )
        }.getOrNull() ?: return@withContext null
        json.arr("photos").objects().mapNotNull { item ->
            val url = item.obj("photo")?.str("url") ?: item.obj("large")?.str("url")
            DoubanHtmlParser.normalize(url)
        }
    }

    private suspend fun frodoPhotos(
        doubanId: String,
        path: String,
        headers: Headers,
        count: Int,
        start: Int,
    ): List<String>? = withContext(Dispatchers.IO) {
        val url = "https://frodo.douban.com/api/v2/" + path + "/" + doubanId +
            "/photos?start=" + start + "&count=" + count + "&type=S"
        val json = runCatching {
            RatingHttp.getJson(
                url,
                mapOf(
                    // 关键：apikey 必须在 header（放 query 会被拒，实测 code 997）
                    "apikey" to headers.frodoApiKey,
                    "User-Agent" to headers.userAgent,
                ),
            )
        }.getOrNull() ?: return@withContext null
        json.arr("photos").objects().mapNotNull { item ->
            DoubanHtmlParser.normalize(item.obj("photo")?.str("url"))
        }
    }

    private suspend fun htmlPhotos(
        doubanId: String,
        category: DoubanCategory,
        headers: Headers,
        start: Int,
    ): List<String>? = withContext(Dispatchers.IO) {
        val base = if (category == DoubanCategory.GAME) {
            "https://www.douban.com/game/" + doubanId + "/photos/?type=1&sortby=hot"
        } else {
            "https://movie.douban.com/subject/" + doubanId +
                "/photos?type=S&start=" + start + "&sortby=time&size=a&subtype=o"
        }
        val html = runCatching { getText(base, headers) }.getOrNull()
        val parsed = DoubanHtmlParser.parse(html)
        if (parsed.blocked) return@withContext null
        parsed.thumbs.map { DoubanHtmlParser.toLargeUrl(it) }
    }

    /** 原样获取 HTML（OkHttp，带 UA/Referer）。 */
    private fun getText(url: String, headers: Headers): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", headers.userAgent)
            .header("Referer", headers.imageReferer)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }.getOrNull()

    /**
     * 搜索豆瓣条目（用于 Bangumi 词条 → 豆瓣 ID 的候选解析）。
     * 返回 (id, title, year, 原始名) 四元组；失败返回空。
     */
    suspend fun search(keyword: String, headers: Headers): List<DoubanSearchItem> =
        withContext(Dispatchers.IO) {
            if (keyword.isBlank()) return@withContext emptyList()
            val url = "https://www.douban.com/search?cat=1002&q=" + ratingUrlEncode(keyword)
            val json = runCatching {
                RatingHttp.getJson(
                    "https://m.douban.com/rexxar/api/v2/search?q=" + ratingUrlEncode(keyword) +
                        "&type=movie&count=10",
                    mapOf(
                        "Referer" to "https://m.douban.com/search/?query=" + ratingUrlEncode(keyword),
                        "User-Agent" to headers.userAgent,
                    ),
                )
            }.getOrNull()
            // rexxar 搜索可用时优先（JSON，稳定）；否则退回 HTML 搜索页
            val fromJson = json?.arr("items").objects().mapNotNull { item ->
                val target = item.obj("target") ?: item
                val id = target.str("id") ?: return@mapNotNull null
                DoubanSearchItem(
                    id = id,
                    title = target.str("title") ?: return@mapNotNull null,
                    year = target.str("year"),
                    originalTitle = target.str("original_title"),
                )
            }.orEmpty()
            if (fromJson.isNotEmpty()) return@withContext fromJson

            val html = runCatching { getText(url, headers) }.getOrNull() ?: return@withContext emptyList()
            parseSearchHtml(html)
        }

    /** 搜索结果项。 */
    data class DoubanSearchItem(
        val id: String,
        val title: String,
        val year: String?,
        val originalTitle: String?,
    )

    /** 从搜索页 HTML 抠结果（`onclick="moreurl(this, {sid: 1234567})"` + h3 标题）。 */
    internal fun parseSearchHtml(html: String?): List<DoubanSearchItem> {
        if (html.isNullOrBlank()) return emptyList()
        if (html.contains("sec.douban.com")) return emptyList()
        // 真实的豆瓣搜索页结构是：<h3><a href=... onclick="moreurl(this, {sid: 1234567, ...})" >标题</a></h3>
        // 因此 sid 在 <h3> 之后、标题之前。
        val regex = Regex(
            "<h3>[\\s\\S]{0,200}?sid:\\s*(\\d+)[\\s\\S]{0,300}?>([^<]+)</a>",
            RegexOption.IGNORE_CASE,
        )
        return regex.findAll(html).mapNotNull { match ->
            val id = match.groupValues.getOrNull(1)?.trim() ?: return@mapNotNull null
            val title = match.groupValues.getOrNull(2)?.trim()?.replace("&amp;", "&")
                ?: return@mapNotNull null
            if (id.isEmpty() || title.isEmpty()) null else DoubanSearchItem(id, title, null, null)
        }.toList().distinctBy { it.id }
    }
}

/** 豆瓣条目类别（决定 API 路径）。 */
enum class DoubanCategory(val label: String) {
    MOVIE("电影"),
    TV("剧集"),
    BOOK("图书"),
    MUSIC("音乐"),
    GAME("游戏"),
}
