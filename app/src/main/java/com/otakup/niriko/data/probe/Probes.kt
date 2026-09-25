package com.otakup.niriko.data.probe

import com.otakup.niriko.data.remote.douban.DoubanClient

/**
 * 豆瓣剧照三级链路探针。
 *
 * 三条链路分别对应 [com.otakup.niriko.data.remote.douban.DoubanClient] 的三级 fallback，
 * 拆成三个端点是为了让用户在自检结果里直接看出**是哪一层被拦**，
 * 而不是只拿到一句笼统的「豆瓣不可用」。
 *
 * 豆瓣没有独立于灰色通道总开关的开关，因此 [probeRegardlessOfToggle] 为 true。
 */
object DoubanProbe : ChannelProbe {

    override val id: String = "douban"

    override val label: String = "豆瓣"

    override val description: String =
        "剧照三级降级链路（rexxar JSON → frodo JSON → HTML 页）。逐层探测，便于定位被拦的那一层。"

    override val riskNote: String =
        "豆瓣没有授权第三方抓取。该通道默认关闭，仅供个人查看；若将来分发建议移除。"

    override val probeRegardlessOfToggle: Boolean = true

    override fun endpoints(config: ProbeConfig): List<ProbeEndpoint> = listOf(
        ProbeEndpoint(
            name = "rexxar JSON",
            url = "https://m.douban.com/rexxar/api/v2/movie/1292052/photos?start=0&count=20&type=S",
            headers = mapOf(
                "Referer" to (
                    config.doubanApiReferer.trimEnd('/') + "/movie/subject/1292052/"
                    ),
            ),
            note = "JSON 剧照接口；实测返回 400 invalid_request 表示 Referer 校验失败",
        ),
        ProbeEndpoint(
            name = "frodo JSON",
            url = "https://frodo.douban.com/api/v2/movie/1292052/photos?start=0&count=20&type=S",
            headers = mapOf(
                "apikey" to DoubanClient.DEFAULT_FRODO_KEY,
                "Referer" to "https://movie.douban.com/",
            ),
            note = "apikey 必须在 header；实测 400 code 997 表示鉴权失败",
        ),
        ProbeEndpoint(
            name = "HTML 剧照页",
            url = "https://movie.douban.com/subject/1292052/photos?type=S&start=0&sortby=time&size=a&subtype=o",
            headers = mapOf(
                "Referer" to config.doubanImageReferer,
            ),
            note = "302 到 sec.douban.com = 反爬拦截（机房 IP 基本必挂，住宅/移动网络成功率更高）",
        ),
    )
}

/**
 * AniList GraphQL 探针。
 *
 * GraphQL 只接受 POST + application/json，而 [ProbeHttp] 固定把 POST body 标成
 * form-urlencoded，无法在这里伪造头；因此本探针改用 **GET** 打同一个 URL：
 * 非 2xx（400/405）只说明它不接受 GET，**域名可达性仍然可判定**——
 * 对「灰色通道能不能用」这个问题来说，这已经够了。
 */
object AniListProbe : ChannelProbe {

    override val id: String = "anilist"

    override val label: String = "AniList"

    override val description: String =
        "AniList 官方 GraphQL 端点（动画元数据/标题映射的补充来源）。"

    override val riskNote: String? = null

    override val probeRegardlessOfToggle: Boolean = false

    override fun endpoints(config: ProbeConfig): List<ProbeEndpoint> = listOf(
        ProbeEndpoint(
            name = "GraphQL 端点",
            url = "https://graphql.anilist.co",
            note = "GraphQL 端点；非 2xx 只说明不接受 GET，域名可达性仍可判定",
        ),
    )
}

/**
 * Jikan（MyAnimeList 的非官方只读镜像）探针。
 */
object JikanProbe : ChannelProbe {

    override val id: String = "jikan"

    override val label: String = "Jikan (MAL)"

    override val description: String =
        "MyAnimeList 的非官方只读 REST 镜像，用于补全动画元数据。"

    override val riskNote: String? = null

    override val probeRegardlessOfToggle: Boolean = false

    override fun endpoints(config: ProbeConfig): List<ProbeEndpoint> = listOf(
        ProbeEndpoint(
            name = "anime/1",
            url = "https://api.jikan.moe/v4/anime/1",
            note = "MyAnimeList 的非官方只读镜像；偶发 504（限流），重试即可",
        ),
    )
}

/**
 * AniDB 标题映射表探针。
 *
 * 用的是公开的 anime-titles.xml.gz（不需要 key），但 AniDB 并未提供官方 HTTP API，
 * 因此风险提示非空、且限制请求频率。
 */
object AniDbTitleProbe : ChannelProbe {

    override val id: String = "anidb"

    override val label: String = "AniDB 标题映射"

    override val description: String =
        "AniDB 的标题映射表（日文原名 / 罗马音 / 英文别名），用于标题补全与匹配。"

    override val riskNote: String =
        "AniDB 未提供官方 HTTP API；该端点用于标题映射，请勿高频请求。"

    override val probeRegardlessOfToggle: Boolean = false

    override fun endpoints(config: ProbeConfig): List<ProbeEndpoint> = listOf(
        ProbeEndpoint(
            name = "anime-titles.xml.gz",
            url = "https://anidb.net/api/anime-titles.xml.gz",
            note = "标题映射表（免 key）；用于日文原名/罗马音补全",
        ),
    )
}

/**
 * Bangumi 旧版搜索接口探针。
 *
 * 新版搜索在 nsfw=true 时要求 token；旧版 search/subject 免 token 且含 NSFW 结果，
 * 是缺 token 时的兜底通道。因此这里显式带一个自有 UA 以表明身份。
 */
object BangumiLegacySearchProbe : ChannelProbe {

    private const val BGM_UA = "Niriko/1.0.0 (Android)"

    override val id: String = "bangumi_legacy"

    override val label: String = "Bangumi 旧版搜索"

    override val description: String =
        "免 token 的旧版条目搜索接口，含 NSFW 结果。"

    override val riskNote: String? = null

    override val probeRegardlessOfToggle: Boolean = false

    override fun endpoints(config: ProbeConfig): List<ProbeEndpoint> = listOf(
        ProbeEndpoint(
            name = "search/subject",
            url = "https://api.bgm.tv/search/subject/Steins%3BGate?type=2&responseGroup=large&max_results=3",
            headers = mapOf("User-Agent" to BGM_UA),
            note = "免 token、含 NSFW；是 nsfw=true 缺 token 时的兜底通道",
        ),
    )
}

/**
 * 灰色通道注册表（自检页与设置页的唯一入口）。
 *
 * 顺序 = UI 展示顺序；新增探针时在这里登记即可，不必改自检流程。
 */
object GrayChannelRegistry {

    /** 全部已登记的灰色通道探针。 */
    val all: List<ChannelProbe> = listOf(
        DoubanProbe,
        AniListProbe,
        JikanProbe,
        AniDbTitleProbe,
        BangumiLegacySearchProbe,
    )

    /** 按 id 查探针；未知 id 返回 null（调用方自行决定是报错还是忽略）。 */
    fun byId(id: String): ChannelProbe? = all.firstOrNull { it.id == id }
}
