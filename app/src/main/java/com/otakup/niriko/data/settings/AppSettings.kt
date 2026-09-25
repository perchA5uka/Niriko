package com.otakup.niriko.data.settings

import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.remote.BangumiClient.BangumiEndpoint
import com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority

/**
 * 主题模式：跟随系统 / 浅色 / 深色
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 玻璃/特效强度：全效果（默认）/ 降低（关 AGSL 折射）/ 关闭（无背景捕获、无 lens）。
 * 用于低端机降级，减轻玻璃模糊与 backdrop 捕获开销。
 */
enum class GlassEffectLevel {
    FULL,
    REDUCED,
    OFF,
}

/**
 * 壁纸氛围：控制壁纸消化管线的色彩消化与 scrim 强度。
 * 浓郁（RICH）/ 均衡（BALANCED，默认）/ 素净（MINIMAL）。
 */
enum class WallpaperAtmosphere {
    RICH,
    BALANCED,
    MINIMAL,
}

/**
 * 卡片液态玻璃强度：全开 / 仅已收藏（收藏列表卡）/ 关闭（静态降级）。
 * 真玻璃每张卡一个 RenderEffect，中端机可用此档位降级。
 */
enum class CardGlassLevel {
    FULL,
    COLLECTION_ONLY,
    OFF,
}

/**
 * 应用设置数据类。
 * 所有可持久化的用户偏好集中于此。
 */
data class AppSettings(
    // ===== 外观 =====
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val oledDark: Boolean = false,
    /** 主题色下标（0=绿色默认；见 ThemePalettes）。旧备份兼容字段，预置色板已移除。 */
    val themeColorIndex: Int = 0,
    /** 自定义主题色种子（ARGB；-1=未设置=默认品牌绿）。优先级：动态取色 > 自定义种子 > 默认绿。 */
    val customSeedColor: Int = -1,
    /** 壁纸总开关。 */
    val wallpaperEnabled: Boolean = false,
    /** 全局壁纸（SAF content:// URI，图片或视频；空=未设置）。 */
    val wallpaperUri: String = "",
    /** 各顶层页壁纸覆盖（空=继承全局）。 */
    val wallpaperLibraryUri: String = "",
    val wallpaperDiscoverUri: String = "",
    val wallpaperStatsUri: String = "",
    val wallpaperSettingsUri: String = "",
    /** 壁纸高斯柔化半径（dp，0=不柔化）。 */
    val wallpaperBlurDp: Int = 0,
    /** 壁纸氛围：浓郁 / 均衡 / 素净（控制壁纸消化管线强度）。 */
    val wallpaperAtmosphere: WallpaperAtmosphere = WallpaperAtmosphere.BALANCED,
    /** 卡片液态玻璃档位：全开 / 仅已收藏 / 关闭（性能保护，默认全开）。 */
    val cardGlassLevel: CardGlassLevel = CardGlassLevel.FULL,
    /** 开屏动画（系统 SplashScreen + Compose 衔接动画）。 */
    val splashEnabled: Boolean = true,
    /** 当前应用的主题包 id（空=未使用主题包）。 */
    val activeThemePackId: String = "",
    /** 减少动态效果：跳过入场类动画（系统 animator 关闭时同样生效）。 */
    val reduceMotion: Boolean = false,
    /** 玻璃/特效强度：默认 FULL（全效果），可降级为 REDUCED/OFF（阶段 P 性能优化）。 */
    val glassEffect: GlassEffectLevel = GlassEffectLevel.FULL,
    /** 自定义桌面图标模式："none"=默认 / "theme"=主题色生成 / "image"=用户上传图。 */
    val customIconMode: String = "none",
    /** 分享卡包含项（阶段 I）。 */
    val shareIncludeRating: Boolean = true,
    val shareIncludeProgress: Boolean = true,
    val shareIncludeTags: Boolean = true,

    // ===== 收藏 =====
    val defaultSortOrder: SortOrder = SortOrder.UPDATE_TIME,
    val showStatusTags: Boolean = true,
    val showProgressBar: Boolean = true,

    // ===== 搜索 =====
    val nsfwEnabled: Boolean = false,
    val showSearchSuggestions: Boolean = true,
    /**
     * 发现页默认布局（第 5 轮 D28）："CARD" = 卡片列表，"GRID" = 宫格首页。
     *
     * 存字符串而不是枚举，避免 settings 包反向依赖 UI 模型；解析用
     * DiscoveryLayout.fromKey（无法识别一律回退 CARD）。
     */
    val discoveryLayout: String = "CARD",

    // ===== 放送提醒 =====
    /** 在看条目新集放送通知开关（阶段 J）。 */
    val airingReminderEnabled: Boolean = false,

    // ===== 数据源 =====
    val activeDataSourceId: String = "bangumi",
    /** Bangumi 接口端点：官方站 / 国内反代。 */
    val bangumiEndpoint: BangumiEndpoint = BangumiEndpoint.OFFICIAL,
    /** Steam Web API key（可选，公开接口无需 key，仅未来扩展用）。 */
    val steamApiKey: String = "",
    /** Steam 登录用户 SteamID64（OpenID 登录或手动输入，空表示未登录）。 */
    val steamId64: String = "",
    /** Steam 用户 access token（webapi_token，家庭库接口用；登录会话抓取，约 1-2 天过期）。 */
    val steamWebApiToken: String = "",

    // ===== 权威评分数据源与密钥 =====
    /**
     * 权威评分总开关。关闭后详情页不出现任何外部评分区块（Steam/VNDB/AniList 补充区块不受影响）。
     * 默认开启；所有源都可缺省降级，未配置 key 的源自动隐藏。
     */
    val externalRatingsEnabled: Boolean = true,
    /**
     * TMDb v3 API Key（用户自备，**绝不硬编码**——对齐 AniShelf：key 存 Keychain、用
     * /3/configuration 校验）。留空则不出现任何 TMDb UI。
     */
    val tmdbApiKey: String = "",
    /**
     * TMDb API 基础地址（留空 = 官方 https://api.themoviedb.org/）。
     * **国区必需**：api.themoviedb.org 与 image.tmdb.org 均不可直连，用户可填自建镜像。
     */
    val tmdbApiUrl: String = "",
    /** TMDb 图片基础地址（留空 = 官方 https://image.tmdb.org/t/p/）。需与上面同时切换。 */
    val tmdbImageUrl: String = "",
    /** OMDb API Key（免费 1000 次/天）。用于叠加 IMDb 逐集评分。 */
    val omdbApiKey: String = "",
    /**
     * 是否启用每集 IMDb 评分。默认关闭：IMDb 逐集需要 N×2 次请求（TMDb external_ids + OMDb），
     * 且 OMDb 免费额度有限；由用户在剧集列表显式点击触发。
     */
    val imdbEpisodeRatingsEnabled: Boolean = false,
    /** IGDB（Twitch client-credentials）—— 游戏媒体均分 aggregated_rating。 */
    val igdbClientId: String = "",
    val igdbClientSecret: String = "",
    /** RAWG API Key（可选，Metacritic 单值）。 */
    val rawgApiKey: String = "",
    /** Discogs personal access token（音乐社区评分）。 */
    val discogsToken: String = "",
    /** OpenCritic API Key（官方渠道需申请，可选；未配置则整源隐藏）。 */
    val openCriticApiKey: String = "",

    // ===== 豆瓣剧照（灰色通道，默认关） =====
    /**
     * 豆瓣剧照总开关。**默认关闭**：豆瓣没有授权第三方抓取，官方站还会 302 到
     * sec.douban.com 反爬页（机房 IP 基本必挂、住宅 IP 通常可过）。
     * 关闭时剧照区仍由 TMDb / B站 / Anitabi / Steam 正常提供。
     */
    val doubanPhotosEnabled: Boolean = false,
    /**
     * 加载豆瓣图片时的 Referer（防盗链必需；失效时可自行修改，不必等发版）。
     * 默认 https://douban.com —— 这是 Bangumi-master 的实测值：带 path 的
     * movie.douban.com/ 反而会被图床拒。注意本字符串**不带尾部斜杠**。
     */
    val doubanImageReferer: String = "https://douban.com",
    /** 豆瓣 rexxar API 的 Referer 前缀（会拼成 {prefix}/{type}/subject/{id}/）。 */
    val doubanApiReferer: String = "https://m.douban.com",

    // ===== 灰色通道（非官方 / 未文档化接口）通用配置 =====
    /**
     * 灰色通道总开关。**默认关闭**：这些源都属「有可用接口但非官方」。
     * 自检工具不受本开关限制（必须能在没开启通道时先自检），
     * 但 AniList / Jikan / AniDB 标题映射 / Bangumi 旧版搜索不参与评分聚合。
     */
    val grayChannelEnabled: Boolean = false,
    /** 灰色通道自定义 User-Agent（留空 = 探针自带默认值）。 */
    val probeUserAgent: String = "",
    /** 灰色通道自定义 Cookie（留空 = 不带；豆瓣某些端点需要登录态）。 */
    val probeCookie: String = "",

    // ===== TMDb 覆盖范围 =====
    /**
     * 是否允许 TMDb 覆盖 GAME / BOOK / MUSIC（走 TMDb movie）。
     * **默认关**：TMDb 对这三类覆盖差，开启会制造噪声候选。
     */
    val tmdbIncludeNonTvTypes: Boolean = false,

    // ===== Bangumi OAuth =====
    /**
     * Bangumi OAuth 应用 client id。**由用户自行在 bgm.tv 创建应用获得，不内置**
     * （内置会违反 Bangumi 应用条款且容易被封）。
     */
    val bangumiClientId: String = "",
    /** Bangumi OAuth 应用 client secret（仅本机保存）。 */
    val bangumiClientSecret: String = "",
    /**
     * OAuth 回跳地址，必须与用户在 Bangumi 应用后台登记的一致。
     * 默认用 niriko://oauth/bangumi（自定义 scheme，WebView 可直接拦截，无需起本地服务）。
     */
    val bangumiRedirectUri: String = "niriko://oauth/bangumi",

    // ===== WebDAV 同步 =====
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val webDavAutoSync: Boolean = false,

    // ===== Bangumi 账号同步 =====
    /** Bangumi Access Token（手动粘贴或 OAuth 写入）。 */
    val bangumiAccessToken: String = "",
    /** 令牌类型（OAuth 回传，通常是 Bearer；空串回退 Bearer）。 */
    val bangumiTokenType: String = "",
    /** 最近一次 /v0/me 返回的用户名（缓存展示）。 */
    val bangumiUsername: String = "",
    /** 同步总开关（Kazumi bangumiSyncEnable）。 */
    val bangumiSyncEnabled: Boolean = false,
    /** 冲突优先级：本地优先 / Bangumi 优先。 */
    val bangumiSyncPriority: BangumiSyncPriority = BangumiSyncPriority.LOCAL_FIRST,
    /** 自动同步（预留，手动触发为主）。 */
    val bangumiAutoSync: Boolean = false,

    // ===== 统计与启动 =====
    val showAnnuallySummary: Boolean = true,
    val startPage: String = "library",
)
