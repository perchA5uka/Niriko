package com.otakup.niriko.data.remote.douban

/**
 * 豆瓣剧照页 HTML 解析（纯函数，可单测）。
 *
 * 为什么需要它：豆瓣的剧照有三个入口，其中 rexxar / frodo 是 JSON（首选），
 * 但两者都可能因校验失败而返回 400；此时只剩 HTML 页——而 HTML 页会重定向到
 * sec.douban.com 反爬页。解析器必须能识别「拿到的是反爬页」并明确判失败，
 * 而不是解析出 0 张图然后让上层以为「这部作品没剧照」。
 *
 * 图片 URL 形态（观照 Bangumi-master 的实现）：
 * - 电影/剧集：https://img1.doubanio.com/view/photo/s_ratio_poster/public/p1234567.jpg
 * - 游戏：https://img1.doubanio.com/view/photo/thumb/public/p1234567.jpg
 * 大图把 /view/photo/{size}/ 换成 /view/photo/l/（游戏是 /photo/photo/）。
 */
object DoubanHtmlParser {

    /** 反爬跳转的标志：命中即判定为「被拦截」，与「确实没有剧照」区分开。 */
    private val BLOCKED_MARKERS = listOf(
        "sec.douban.com",
        "异常请求",
        "检测到有异常",
        "captcha",
    )

    /** 剧照列表项的正则：`.cover img` 的 src。 */
    private val COVER_IMG_REGEX = Regex(
        "class=\"cover\"[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"",
        RegexOption.IGNORE_CASE,
    )

    /** 兜底：任何 img 的 doubanio 图片链接（页面结构变化时不至于全空）。 */
    private val FALLBACK_IMG_REGEX = Regex(
        "src=\"(https?://img\\d?\\.doubanio\\.com/view/photo/[^\"]+)\"",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 总数（`共 N 张`）。
     *
     * 豆瓣在「共42张」与「共 42 张」两种形态间变动过（模板不同、以及 HTML 里
     * 数字两侧可能出现换行/空白），因此空白必须用 `\s*` 而不是写死。
     * 只认「共」与「张」紧贴数字的老写法，会在真实页面上直接拿不到总数。
     */
    private val COUNT_REGEX = Regex("""共\s*(\d+)\s*张""")

    /** 解析结果。 */
    data class Result(
        /** 缩略图 URL（去重、保序）。 */
        val thumbs: List<String>,
        /** 页面报告的剧照总数（拿不到为 null）。 */
        val totalCount: Int?,
        /** 是否被反爬拦下（此时 [thumbs] 必为空，且**不应该**被当成「没有剧照」缓存）。 */
        val blocked: Boolean,
    )

    fun parse(html: String?): Result {
        if (html.isNullOrBlank()) return Result(emptyList(), null, blocked = false)
        if (BLOCKED_MARKERS.any { html.contains(it, ignoreCase = true) }) {
            return Result(emptyList(), null, blocked = true)
        }
        val primary = COVER_IMG_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        val urls = if (primary.isNotEmpty()) {
            primary
        } else {
            FALLBACK_IMG_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        }
        val total = COUNT_REGEX.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return Result(
            thumbs = urls.mapNotNull { normalize(it) }.distinct(),
            totalCount = total,
            blocked = false,
        )
    }

    /** 缩略图 URL 规范化：补 https、去空白。 */
    fun normalize(raw: String?): String? {
        val url = raw?.trim()?.replace("&amp;", "&") ?: return null
        if (url.isEmpty()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
            url.startsWith("https://") -> url
            else -> null
        }
    }

    /**
     * 缩略图 → 大图。
     * 豆瓣的尺寸段是 `/view/photo/{size}/`，把 size 换成 `l` 即大图；
     * 游戏页给的是 `/view/photo/thumb/`，对应换 `/view/photo/photo/`。
     */
    fun toLargeUrl(thumb: String): String = when {
        thumb.contains("/view/photo/thumb/") -> thumb.replace("/view/photo/thumb/", "/view/photo/photo/")
        else -> thumb.replace(Regex("/view/photo/[^/]+/"), "/view/photo/l/")
    }
}
