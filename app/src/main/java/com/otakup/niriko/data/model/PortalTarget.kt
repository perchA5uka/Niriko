package com.otakup.niriko.data.model

import com.otakup.niriko.data.local.entity.SubjectEntity

/**
 * 传送门：作品详情页跳转到其他平台 / App 的动作。
 * 统一三级降级由 [PortalLauncher] 走完（精确 scheme → 搜索 scheme → 网页兜底）。
 */
sealed interface PortalAction {
    /** 自定义 URL Scheme（如 bilibili:// / steam://）。能定位到具体条目或搜索。 */
    data class UriScheme(val uri: String) : PortalAction

    /** 仅拉起 App（如 Kazumi；无深链时降级用）。 */
    data class LaunchPackage(val packageName: String) : PortalAction

    /** 网页兜底（浏览器打开，或 App 注册了 App Link 时直接唤起 App）。 */
    data class WebUrl(val url: String) : PortalAction
}

/**
 * 详情页当前已持有的跨平台 ID（用于精确跳转）。
 * 由 UI 从 [com.otakup.niriko.viewmodel.SubjectDetailUiState] 组装，避免数据模型依赖 ViewModel。
 */
data class PortalIds(
    val biliSeasonId: Int? = null,
    val steamAppId: Int? = null,
    val vndbId: String? = null,
    val anilistId: Long? = null,
)

/**
 * 一个跳转目标。
 * [resolve] 按优先级返回候选动作（精确 → 搜索 → 网页），
 * 无可用动作时返回空列表（该目标对当前作品不显示）。
 */
data class PortalTarget(
    val id: String,
    val label: String,
    val subjectTypes: Set<SubjectType>,
    val resolve: (SubjectEntity, PortalIds) -> List<PortalAction>,
)

/**
 * Portal 注册表：按作品类型分组，返回可展示的跳转目标。
 * P0：Bilibili / Bangumi App / Steam / VNDB。Kazumi 深链适配暂缓（见 docs/portal-feature-plan.md）。
 */
object PortalRegistry {
    val all: List<PortalTarget> = listOf(
        PortalTarget(
            id = "bilibili",
            label = "Bilibili",
            subjectTypes = setOf(SubjectType.ANIME, SubjectType.REAL),
        ) { s, ids ->
            val title = s.displayTitle
            if (ids.biliSeasonId != null) {
                listOf(
                    PortalAction.UriScheme("bilibili://bangumi/season/${ids.biliSeasonId}"),
                    PortalAction.WebUrl("https://www.bilibili.com/bangumi/play/ss${ids.biliSeasonId}"),
                )
            } else {
                listOf(
                    PortalAction.UriScheme("bilibili://search?keyword=${title.urlEncoded()}"),
                    PortalAction.WebUrl("https://search.bilibili.com/all?keyword=${title.urlEncoded()}"),
                )
            }
        },
        PortalTarget(
            id = "bangumi",
            label = "Bangumi App",
            subjectTypes = setOf(SubjectType.ANIME, SubjectType.MANGA, SubjectType.BOOK, SubjectType.GAME, SubjectType.MUSIC, SubjectType.REAL),
        ) { s, _ ->
            // 优先使用通用链接：App 注册了 bgm.tv App Link 则直接唤起，否则浏览器打开。
            listOf(PortalAction.WebUrl("https://bgm.tv/subject/${s.subjectId}"))
        },
        PortalTarget(
            id = "steam",
            label = "Steam",
            subjectTypes = setOf(SubjectType.GAME),
        ) { _, ids ->
            val appId = ids.steamAppId
            if (appId != null) {
                listOf(
                    PortalAction.UriScheme("steam://store/$appId"),
                    PortalAction.WebUrl("https://store.steampowered.com/app/$appId"),
                )
            } else emptyList()
        },
        PortalTarget(
            id = "vndb",
            label = "VNDB",
            subjectTypes = setOf(SubjectType.GAME),
        ) { _, ids ->
            val v = ids.vndbId
            if (v != null) listOf(PortalAction.WebUrl("https://vndb.org/v$v")) else emptyList()
        },
        PortalTarget(
            id = "anilist",
            label = "AniList",
            subjectTypes = setOf(SubjectType.ANIME, SubjectType.MANGA),
        ) { s, ids ->
            val id = ids.anilistId
            if (id != null) {
                val kind = if (s.type == SubjectType.MANGA) "manga" else "anime"
                listOf(PortalAction.WebUrl("https://anilist.co/$kind/$id"))
            } else emptyList()
        },
        PortalTarget(
            id = "netease",
            label = "网易云音乐",
            subjectTypes = setOf(SubjectType.MUSIC),
        ) { s, _ ->
            val title = s.displayTitle
            listOf(
                PortalAction.UriScheme("orpheus://search?keyword=${title.urlEncoded()}"),
                PortalAction.WebUrl("https://music.163.com/#/search/m/?s=${title.urlEncoded()}"),
            )
        },
        PortalTarget(
            id = "qqmusic",
            label = "QQ音乐",
            subjectTypes = setOf(SubjectType.MUSIC),
        ) { s, _ ->
            val title = s.displayTitle
            listOf(
                PortalAction.UriScheme("qqmusic://search?keyword=${title.urlEncoded()}"),
                PortalAction.WebUrl("https://y.qq.com/n/ryqq/search?w=${title.urlEncoded()}"),
            )
        },
        PortalTarget(
            id = "mihon",
            label = "Mihon",
            subjectTypes = setOf(SubjectType.MANGA),
        ) { _, _ ->
            // 无统一跨平台 ID，仅拉起 App；未安装时菜单项自动隐藏（见 PortalLauncher.anyLaunchable）。
            listOf(PortalAction.LaunchPackage("eu.kanade.tachiyomi"))
        },
        // ===== 阶段 7：权威机构 / 数据库外链（无跨平台 ID，统一走标题搜索） =====
        // 说明：这些站点要么没有可接入的 API，要么条款禁止抓取 —— 只做「带标题跳过去」，
        // 不复制/缓存其内容。
        PortalTarget(
            id = "imdb",
            label = "IMDb",
            subjectTypes = setOf(SubjectType.ANIME, SubjectType.REAL),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://www.imdb.com/find/?q=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "tmdb",
            label = "TMDb",
            subjectTypes = setOf(SubjectType.ANIME, SubjectType.REAL),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://www.themoviedb.org/search?query=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "mal",
            label = "MyAnimeList",
            subjectTypes = setOf(SubjectType.ANIME, SubjectType.MANGA),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://myanimelist.net/anime.php?q=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "anidb",
            label = "AniDB",
            subjectTypes = setOf(SubjectType.ANIME),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://anidb.net/search/anime/?adb.search=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "douban",
            label = "豆瓣",
            subjectTypes = setOf(SubjectType.ANIME, SubjectType.REAL, SubjectType.BOOK, SubjectType.MUSIC, SubjectType.GAME),
        ) { s, _ ->
            val path = when (s.type) {
                SubjectType.BOOK -> "book"
                SubjectType.MUSIC -> "music"
                SubjectType.GAME -> "game"
                else -> "movie"
            }
            listOf(
                PortalAction.UriScheme("douban://douban.com/search?q=${s.displayTitle.urlEncoded()}"),
                PortalAction.WebUrl("https://search.douban.com/$path/subject_search?search_text=${s.displayTitle.urlEncoded()}"),
            )
        },
        PortalTarget(
            id = "famitsu",
            label = "Fami通",
            subjectTypes = setOf(SubjectType.GAME),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://www.famitsu.com/search/?q=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "metacritic",
            label = "Metacritic",
            subjectTypes = setOf(SubjectType.GAME, SubjectType.REAL),
        ) { s, _ ->
            val path = if (s.type == SubjectType.GAME) "game" else "movie"
            listOf(PortalAction.WebUrl("https://www.metacritic.com/search/$path/${s.displayTitle.urlEncoded()}/"))
        },
        PortalTarget(
            id = "opencritic",
            label = "OpenCritic",
            subjectTypes = setOf(SubjectType.GAME),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://opencritic.com/search?criteria=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "billboard",
            label = "Billboard",
            subjectTypes = setOf(SubjectType.MUSIC),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://www.billboard.com/?s=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "oricon",
            label = "Oricon",
            subjectTypes = setOf(SubjectType.MUSIC),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://www.oricon.co.jp/search/result.php?search_str=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "musicbrainz",
            label = "MusicBrainz",
            subjectTypes = setOf(SubjectType.MUSIC),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://musicbrainz.org/search?type=release_group&query=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "discogs",
            label = "Discogs",
            subjectTypes = setOf(SubjectType.MUSIC),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://www.discogs.com/search/?q=${s.displayTitle.urlEncoded()}&type=release"))
        },
        PortalTarget(
            id = "rym",
            label = "RateYourMusic",
            subjectTypes = setOf(SubjectType.MUSIC),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://rateyourmusic.com/search?searchterm=${s.displayTitle.urlEncoded()}&searchtype=l"))
        },
        PortalTarget(
            id = "goodreads",
            label = "Goodreads",
            subjectTypes = setOf(SubjectType.BOOK),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://www.goodreads.com/search?q=${s.displayTitle.urlEncoded()}"))
        },
        PortalTarget(
            id = "erogamescape",
            label = "批评空间",
            subjectTypes = setOf(SubjectType.GAME),
        ) { s, _ ->
            listOf(PortalAction.WebUrl("https://erogamescape.dyndns.org/~ap2/ero/toukei_kaiseki/search.php?word=${s.displayTitle.urlEncoded()}"))
        },
    )

    /** 对某作品类型返回按注册序的跳转目标。 */
    fun targetsFor(type: SubjectType): List<PortalTarget> = all.filter { type in it.subjectTypes }
}

/** 标题 URL 编码（查询参数用）。 */
private fun String.urlEncoded(): String = java.net.URLEncoder.encode(this, "UTF-8")
