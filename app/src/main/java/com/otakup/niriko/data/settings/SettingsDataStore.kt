package com.otakup.niriko.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.remote.BangumiClient.BangumiEndpoint
import com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "niriko_settings")

/**
 * DataStore Preferences 封装。
 * 所有设置项键、默认值、Flow 读取和 suspend 写入集中于此。
 */
class SettingsDataStore(private val context: Context) {

    // ==================== 键定义 ====================

    private object Keys {
        // 外观
        val THEME_MODE = intPreferencesKey("theme_mode")           // 0=SYSTEM, 1=LIGHT, 2=DARK
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val OLED_DARK = booleanPreferencesKey("oled_dark")
        val THEME_COLOR_INDEX = intPreferencesKey("theme_color_index")
        val CUSTOM_SEED_COLOR = intPreferencesKey("custom_seed_color")
        val WALLPAPER_ENABLED = booleanPreferencesKey("wallpaper_enabled")
        val WALLPAPER_URI = stringPreferencesKey("wallpaper_uri")
        val WALLPAPER_LIBRARY_URI = stringPreferencesKey("wallpaper_library_uri")
        val WALLPAPER_DISCOVER_URI = stringPreferencesKey("wallpaper_discover_uri")
        val WALLPAPER_STATS_URI = stringPreferencesKey("wallpaper_stats_uri")
        val WALLPAPER_SETTINGS_URI = stringPreferencesKey("wallpaper_settings_uri")
        val WALLPAPER_BLUR_DP = intPreferencesKey("wallpaper_blur_dp")
        val WALLPAPER_ATMOSPHERE = stringPreferencesKey("wallpaper_atmosphere")  // WallpaperAtmosphere.name
        val CARD_GLASS_LEVEL = stringPreferencesKey("card_glass_level")  // CardGlassLevel.name
        val SPLASH_ENABLED = booleanPreferencesKey("splash_enabled")
        val ACTIVE_THEME_PACK_ID = stringPreferencesKey("active_theme_pack_id")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val GLASS_EFFECT = stringPreferencesKey("glass_effect")  // GlassEffectLevel.name
        val CUSTOM_ICON_MODE = stringPreferencesKey("custom_icon_mode")
        val SHARE_INCLUDE_RATING = booleanPreferencesKey("share_include_rating")
        val SHARE_INCLUDE_PROGRESS = booleanPreferencesKey("share_include_progress")
        val SHARE_INCLUDE_TAGS = booleanPreferencesKey("share_include_tags")

        // 收藏
        val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")  // SortOrder.name
        val SHOW_STATUS_TAGS = booleanPreferencesKey("show_status_tags")
        val SHOW_PROGRESS_BAR = booleanPreferencesKey("show_progress_bar")

        // 搜索
        val NSFW_ENABLED = booleanPreferencesKey("nsfw_enabled")
        val SHOW_SEARCH_SUGGESTIONS = booleanPreferencesKey("show_search_suggestions")
        val DISCOVERY_LAYOUT = stringPreferencesKey("discovery_layout")

        // 放送提醒
        val AIRING_REMINDER_ENABLED = booleanPreferencesKey("airing_reminder_enabled")

        // 统计与启动
        val SHOW_ANNUALLY_SUMMARY = booleanPreferencesKey("show_annually_summary")
        val START_PAGE = stringPreferencesKey("start_page")

        // 数据源
        val ACTIVE_DATA_SOURCE_ID = stringPreferencesKey("active_data_source_id")
        val BANGUMI_ENDPOINT = stringPreferencesKey("bangumi_endpoint")
        val STEAM_API_KEY = stringPreferencesKey("steam_api_key")
        val STEAM_ID64 = stringPreferencesKey("steam_id64")
        val STEAM_WEB_API_TOKEN = stringPreferencesKey("steam_web_api_token")

        // 权威评分数据源与密钥
        val EXTERNAL_RATINGS_ENABLED = booleanPreferencesKey("external_ratings_enabled")
        val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        val TMDB_API_URL = stringPreferencesKey("tmdb_api_url")
        val TMDB_IMAGE_URL = stringPreferencesKey("tmdb_image_url")
        val OMDB_API_KEY = stringPreferencesKey("omdb_api_key")
        val IMDB_EPISODE_RATINGS_ENABLED = booleanPreferencesKey("imdb_episode_ratings_enabled")
        val IGDB_CLIENT_ID = stringPreferencesKey("igdb_client_id")
        val IGDB_CLIENT_SECRET = stringPreferencesKey("igdb_client_secret")
        val RAWG_API_KEY = stringPreferencesKey("rawg_api_key")
        val DISCOGS_TOKEN = stringPreferencesKey("discogs_token")
        val OPENCRITIC_API_KEY = stringPreferencesKey("opencritic_api_key")
        val DOUBAN_PHOTOS_ENABLED = booleanPreferencesKey("douban_photos_enabled")
        val DOUBAN_IMAGE_REFERER = stringPreferencesKey("douban_image_referer")
        val DOUBAN_API_REFERER = stringPreferencesKey("douban_api_referer")

        // 灰色通道通用配置
        val GRAY_CHANNEL_ENABLED = booleanPreferencesKey("gray_channel_enabled")
        val PROBE_USER_AGENT = stringPreferencesKey("probe_user_agent")
        val PROBE_COOKIE = stringPreferencesKey("probe_cookie")

        // TMDb 覆盖范围
        val TMDB_INCLUDE_NON_TV_TYPES = booleanPreferencesKey("tmdb_include_non_tv_types")

        // WebDAV
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val WEBDAV_AUTO_SYNC = booleanPreferencesKey("webdav_auto_sync")

        // Bangumi 账号
        val BANGUMI_ACCESS_TOKEN = stringPreferencesKey("bangumi_access_token")
        val BANGUMI_TOKEN_TYPE = stringPreferencesKey("bangumi_token_type")
        val BANGUMI_USERNAME = stringPreferencesKey("bangumi_username")
        val BANGUMI_SYNC_ENABLED = booleanPreferencesKey("bangumi_sync_enabled")
        val BANGUMI_SYNC_PRIORITY = stringPreferencesKey("bangumi_sync_priority")  // BangumiSyncPriority.name
        val BANGUMI_AUTO_SYNC = booleanPreferencesKey("bangumi_auto_sync")

        // Bangumi OAuth（用户自建应用，不内置）
        val BANGUMI_CLIENT_ID = stringPreferencesKey("bangumi_client_id")
        val BANGUMI_CLIENT_SECRET = stringPreferencesKey("bangumi_client_secret")
        val BANGUMI_REDIRECT_URI = stringPreferencesKey("bangumi_redirect_uri")
    }

    // ==================== 默认值 ====================

    private val defaults = AppSettings()

    // ==================== Flow 读取 ====================

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            // 外观
            themeMode = when (prefs[Keys.THEME_MODE]) {
                0 -> ThemeMode.SYSTEM
                1 -> ThemeMode.LIGHT
                2 -> ThemeMode.DARK
                else -> defaults.themeMode
            },
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            oledDark = prefs[Keys.OLED_DARK] ?: defaults.oledDark,
            themeColorIndex = prefs[Keys.THEME_COLOR_INDEX] ?: defaults.themeColorIndex,
            customSeedColor = prefs[Keys.CUSTOM_SEED_COLOR] ?: defaults.customSeedColor,
            wallpaperEnabled = prefs[Keys.WALLPAPER_ENABLED] ?: defaults.wallpaperEnabled,
            wallpaperUri = prefs[Keys.WALLPAPER_URI] ?: defaults.wallpaperUri,
            wallpaperLibraryUri = prefs[Keys.WALLPAPER_LIBRARY_URI] ?: defaults.wallpaperLibraryUri,
            wallpaperDiscoverUri = prefs[Keys.WALLPAPER_DISCOVER_URI] ?: defaults.wallpaperDiscoverUri,
            wallpaperStatsUri = prefs[Keys.WALLPAPER_STATS_URI] ?: defaults.wallpaperStatsUri,
            wallpaperSettingsUri = prefs[Keys.WALLPAPER_SETTINGS_URI] ?: defaults.wallpaperSettingsUri,
            wallpaperBlurDp = prefs[Keys.WALLPAPER_BLUR_DP] ?: defaults.wallpaperBlurDp,
            wallpaperAtmosphere = prefs[Keys.WALLPAPER_ATMOSPHERE]?.let { name ->
                try { WallpaperAtmosphere.valueOf(name) } catch (_: IllegalArgumentException) { defaults.wallpaperAtmosphere }
            } ?: defaults.wallpaperAtmosphere,
            cardGlassLevel = prefs[Keys.CARD_GLASS_LEVEL]?.let { name ->
                try { CardGlassLevel.valueOf(name) } catch (_: IllegalArgumentException) { defaults.cardGlassLevel }
            } ?: defaults.cardGlassLevel,
            splashEnabled = prefs[Keys.SPLASH_ENABLED] ?: defaults.splashEnabled,
            activeThemePackId = prefs[Keys.ACTIVE_THEME_PACK_ID] ?: defaults.activeThemePackId,
            reduceMotion = prefs[Keys.REDUCE_MOTION] ?: defaults.reduceMotion,
            glassEffect = prefs[Keys.GLASS_EFFECT]?.let { name ->
                try { GlassEffectLevel.valueOf(name) } catch (_: IllegalArgumentException) { defaults.glassEffect }
            } ?: defaults.glassEffect,
            customIconMode = prefs[Keys.CUSTOM_ICON_MODE] ?: defaults.customIconMode,
            shareIncludeRating = prefs[Keys.SHARE_INCLUDE_RATING] ?: defaults.shareIncludeRating,
            shareIncludeProgress = prefs[Keys.SHARE_INCLUDE_PROGRESS] ?: defaults.shareIncludeProgress,
            shareIncludeTags = prefs[Keys.SHARE_INCLUDE_TAGS] ?: defaults.shareIncludeTags,

            // 收藏
            defaultSortOrder = prefs[Keys.DEFAULT_SORT_ORDER]?.let { name ->
                try { SortOrder.valueOf(name) } catch (_: IllegalArgumentException) { defaults.defaultSortOrder }
            } ?: defaults.defaultSortOrder,
            showStatusTags = prefs[Keys.SHOW_STATUS_TAGS] ?: defaults.showStatusTags,
            showProgressBar = prefs[Keys.SHOW_PROGRESS_BAR] ?: defaults.showProgressBar,

            // 搜索
            nsfwEnabled = prefs[Keys.NSFW_ENABLED] ?: defaults.nsfwEnabled,
            showSearchSuggestions = prefs[Keys.SHOW_SEARCH_SUGGESTIONS] ?: defaults.showSearchSuggestions,
            discoveryLayout = prefs[Keys.DISCOVERY_LAYOUT] ?: defaults.discoveryLayout,
            airingReminderEnabled = prefs[Keys.AIRING_REMINDER_ENABLED] ?: defaults.airingReminderEnabled,

            // 统计与启动
            showAnnuallySummary = prefs[Keys.SHOW_ANNUALLY_SUMMARY] ?: defaults.showAnnuallySummary,
            startPage = prefs[Keys.START_PAGE] ?: defaults.startPage,

            // 数据源
            activeDataSourceId = prefs[Keys.ACTIVE_DATA_SOURCE_ID] ?: defaults.activeDataSourceId,
            bangumiEndpoint = prefs[Keys.BANGUMI_ENDPOINT]?.let { name ->
                try { BangumiEndpoint.valueOf(name) } catch (_: IllegalArgumentException) { defaults.bangumiEndpoint }
            } ?: defaults.bangumiEndpoint,
            steamApiKey = prefs[Keys.STEAM_API_KEY] ?: defaults.steamApiKey,
            steamId64 = prefs[Keys.STEAM_ID64] ?: defaults.steamId64,
            steamWebApiToken = prefs[Keys.STEAM_WEB_API_TOKEN] ?: defaults.steamWebApiToken,
            externalRatingsEnabled = prefs[Keys.EXTERNAL_RATINGS_ENABLED] ?: defaults.externalRatingsEnabled,
            tmdbApiKey = prefs[Keys.TMDB_API_KEY] ?: defaults.tmdbApiKey,
            tmdbApiUrl = prefs[Keys.TMDB_API_URL] ?: defaults.tmdbApiUrl,
            tmdbImageUrl = prefs[Keys.TMDB_IMAGE_URL] ?: defaults.tmdbImageUrl,
            omdbApiKey = prefs[Keys.OMDB_API_KEY] ?: defaults.omdbApiKey,
            imdbEpisodeRatingsEnabled = prefs[Keys.IMDB_EPISODE_RATINGS_ENABLED] ?: defaults.imdbEpisodeRatingsEnabled,
            igdbClientId = prefs[Keys.IGDB_CLIENT_ID] ?: defaults.igdbClientId,
            igdbClientSecret = prefs[Keys.IGDB_CLIENT_SECRET] ?: defaults.igdbClientSecret,
            rawgApiKey = prefs[Keys.RAWG_API_KEY] ?: defaults.rawgApiKey,
            discogsToken = prefs[Keys.DISCOGS_TOKEN] ?: defaults.discogsToken,
            openCriticApiKey = prefs[Keys.OPENCRITIC_API_KEY] ?: defaults.openCriticApiKey,
            doubanPhotosEnabled = prefs[Keys.DOUBAN_PHOTOS_ENABLED] ?: defaults.doubanPhotosEnabled,
            doubanImageReferer = prefs[Keys.DOUBAN_IMAGE_REFERER] ?: defaults.doubanImageReferer,
            doubanApiReferer = prefs[Keys.DOUBAN_API_REFERER] ?: defaults.doubanApiReferer,
            grayChannelEnabled = prefs[Keys.GRAY_CHANNEL_ENABLED] ?: defaults.grayChannelEnabled,
            probeUserAgent = prefs[Keys.PROBE_USER_AGENT] ?: defaults.probeUserAgent,
            probeCookie = prefs[Keys.PROBE_COOKIE] ?: defaults.probeCookie,
            tmdbIncludeNonTvTypes = prefs[Keys.TMDB_INCLUDE_NON_TV_TYPES] ?: defaults.tmdbIncludeNonTvTypes,

            // WebDAV
            webDavUrl = prefs[Keys.WEBDAV_URL] ?: defaults.webDavUrl,
            webDavUsername = prefs[Keys.WEBDAV_USERNAME] ?: defaults.webDavUsername,
            webDavPassword = prefs[Keys.WEBDAV_PASSWORD] ?: defaults.webDavPassword,
            webDavAutoSync = prefs[Keys.WEBDAV_AUTO_SYNC] ?: defaults.webDavAutoSync,

            // Bangumi 账号
            bangumiAccessToken = prefs[Keys.BANGUMI_ACCESS_TOKEN] ?: defaults.bangumiAccessToken,
            bangumiTokenType = prefs[Keys.BANGUMI_TOKEN_TYPE] ?: defaults.bangumiTokenType,
            bangumiUsername = prefs[Keys.BANGUMI_USERNAME] ?: defaults.bangumiUsername,
            bangumiSyncEnabled = prefs[Keys.BANGUMI_SYNC_ENABLED] ?: defaults.bangumiSyncEnabled,
            bangumiSyncPriority = prefs[Keys.BANGUMI_SYNC_PRIORITY]?.let { name ->
                try { BangumiSyncPriority.valueOf(name) } catch (_: IllegalArgumentException) { defaults.bangumiSyncPriority }
            } ?: defaults.bangumiSyncPriority,
            bangumiAutoSync = prefs[Keys.BANGUMI_AUTO_SYNC] ?: defaults.bangumiAutoSync,
            bangumiClientId = prefs[Keys.BANGUMI_CLIENT_ID] ?: defaults.bangumiClientId,
            bangumiClientSecret = prefs[Keys.BANGUMI_CLIENT_SECRET] ?: defaults.bangumiClientSecret,
            bangumiRedirectUri = prefs[Keys.BANGUMI_REDIRECT_URI] ?: defaults.bangumiRedirectUri,
        )
    }

    // ==================== 写入方法 ====================

    suspend fun setThemeMode(mode: ThemeMode) {
        runCatching { context.dataStore.edit { it[Keys.THEME_MODE] = mode.ordinal } }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled } }
    }

    suspend fun setOledDark(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.OLED_DARK] = enabled } }
    }

    /** 设置主题色下标（0=绿色默认；越界由 ThemePalettes 回退）。 */
    suspend fun setThemeColorIndex(index: Int) {
        runCatching { context.dataStore.edit { it[Keys.THEME_COLOR_INDEX] = index.coerceAtLeast(0) } }
    }

    suspend fun setWallpaperEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.WALLPAPER_ENABLED] = enabled } }
    }

    /** 设置全局壁纸 URI（SAF content://，空串清除）。 */
    suspend fun setWallpaperUri(uri: String) {
        runCatching { context.dataStore.edit { it[Keys.WALLPAPER_URI] = uri.trim() } }
    }

    /** 设置某页壁纸覆盖（空串=继承全局）。 */
    suspend fun setWallpaperPageUri(page: String, uri: String) {
        val key = when (page) {
            "library" -> Keys.WALLPAPER_LIBRARY_URI
            "discover" -> Keys.WALLPAPER_DISCOVER_URI
            "stats" -> Keys.WALLPAPER_STATS_URI
            "settings" -> Keys.WALLPAPER_SETTINGS_URI
            else -> return
        }
        runCatching { context.dataStore.edit { it[key] = uri.trim() } }
    }

    /** 设置壁纸柔化半径（dp）。 */
    suspend fun setWallpaperBlurDp(dp: Int) {
        runCatching { context.dataStore.edit { it[Keys.WALLPAPER_BLUR_DP] = dp.coerceIn(0, 40) } }
    }

    /** 设置壁纸氛围（浓郁/均衡/素净）。 */
    suspend fun setWallpaperAtmosphere(level: WallpaperAtmosphere) {
        runCatching { context.dataStore.edit { it[Keys.WALLPAPER_ATMOSPHERE] = level.name } }
    }

    /** 设置卡片液态玻璃档位（全开 / 仅已收藏 / 关闭）。 */
    suspend fun setCardGlassLevel(level: CardGlassLevel) {
        runCatching { context.dataStore.edit { it[Keys.CARD_GLASS_LEVEL] = level.name } }
    }

    suspend fun setSplashEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.SPLASH_ENABLED] = enabled } }
    }

    suspend fun setActiveThemePackId(id: String) {
        runCatching { context.dataStore.edit { it[Keys.ACTIVE_THEME_PACK_ID] = id.trim() } }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.REDUCE_MOTION] = enabled } }
    }

    /** 设置玻璃/特效强度（阶段 P：低端机降级）。 */
    suspend fun setGlassEffect(level: GlassEffectLevel) {
        runCatching { context.dataStore.edit { it[Keys.GLASS_EFFECT] = level.name } }
    }

    /** 设置自定义图标模式（"none"/"theme"/"image"）。 */
    suspend fun setCustomIconMode(mode: String) {
        runCatching { context.dataStore.edit { it[Keys.CUSTOM_ICON_MODE] = mode } }
    }

    suspend fun setShareIncludeRating(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.SHARE_INCLUDE_RATING] = enabled } }
    }
    suspend fun setShareIncludeProgress(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.SHARE_INCLUDE_PROGRESS] = enabled } }
    }
    suspend fun setShareIncludeTags(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.SHARE_INCLUDE_TAGS] = enabled } }
    }

    /** 设置自定义主题色种子（ARGB；传 null 清除=回退默认品牌绿）。 */
    suspend fun setCustomSeedColor(argb: Int?) {
        runCatching {
            context.dataStore.edit { prefs ->
                if (argb == null) prefs.remove(Keys.CUSTOM_SEED_COLOR)
                else prefs[Keys.CUSTOM_SEED_COLOR] = argb
            }
        }
    }

    suspend fun setDefaultSortOrder(order: SortOrder) {
        runCatching { context.dataStore.edit { it[Keys.DEFAULT_SORT_ORDER] = order.name } }
    }

    suspend fun setShowStatusTags(show: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.SHOW_STATUS_TAGS] = show } }
    }

    suspend fun setShowProgressBar(show: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.SHOW_PROGRESS_BAR] = show } }
    }

    suspend fun setNsfwEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.NSFW_ENABLED] = enabled } }
    }

    suspend fun setShowSearchSuggestions(show: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.SHOW_SEARCH_SUGGESTIONS] = show } }
    }

    /** 发现页布局（第 5 轮 D28）：写 "CARD" / "GRID"。 */
    suspend fun setDiscoveryLayout(layout: String) {
        runCatching { context.dataStore.edit { it[Keys.DISCOVERY_LAYOUT] = layout } }
    }

    suspend fun setAiringReminderEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.AIRING_REMINDER_ENABLED] = enabled } }
    }

    suspend fun setShowAnnuallySummary(show: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.SHOW_ANNUALLY_SUMMARY] = show } }
    }

    suspend fun setStartPage(page: String) {
        runCatching { context.dataStore.edit { it[Keys.START_PAGE] = page } }
    }

    /** 重置所有设置到默认值。 */
    suspend fun resetAll() {
        runCatching { context.dataStore.edit { it.clear() } }
    }

    suspend fun setActiveDataSourceId(id: String) {
        runCatching { context.dataStore.edit { it[Keys.ACTIVE_DATA_SOURCE_ID] = id } }
    }

    /** 设置 Bangumi 接口端点（官方站 / 国内反代）。 */
    suspend fun setBangumiEndpoint(e: BangumiEndpoint) {
        runCatching { context.dataStore.edit { it[Keys.BANGUMI_ENDPOINT] = e.name } }
    }

    /** 设置 Steam Web API key（可留空，公开接口无需 key）。 */
    suspend fun setSteamApiKey(key: String) {
        runCatching { context.dataStore.edit { it[Keys.STEAM_API_KEY] = key.trim() } }
    }

    // ===== 权威评分数据源（阶段 0–1）=====

    suspend fun setExternalRatingsEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.EXTERNAL_RATINGS_ENABLED] = enabled } }
    }

    /** TMDb v3 API Key（用户自备；空串 = 关闭所有 TMDb 功能）。 */
    suspend fun setTmdbApiKey(key: String) {
        runCatching { context.dataStore.edit { it[Keys.TMDB_API_KEY] = key.trim() } }
    }

    /**
     * TMDb 端点。国区 api.themoviedb.org 与 image.tmdb.org 不可直连，
     * 因此 api 与 image 两个地址必须成对设置（空串回退官方）。
     */
    suspend fun setTmdbUrls(apiUrl: String, imageUrl: String) {
        runCatching {
            context.dataStore.edit {
                it[Keys.TMDB_API_URL] = apiUrl.trim()
                it[Keys.TMDB_IMAGE_URL] = imageUrl.trim()
            }
        }
    }

    suspend fun setOmdbApiKey(key: String) {
        runCatching { context.dataStore.edit { it[Keys.OMDB_API_KEY] = key.trim() } }
    }

    suspend fun setImdbEpisodeRatingsEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.IMDB_EPISODE_RATINGS_ENABLED] = enabled } }
    }

    suspend fun setIgdbCredentials(clientId: String, clientSecret: String) {
        runCatching {
            context.dataStore.edit {
                it[Keys.IGDB_CLIENT_ID] = clientId.trim()
                it[Keys.IGDB_CLIENT_SECRET] = clientSecret.trim()
            }
        }
    }

    suspend fun setRawgApiKey(key: String) {
        runCatching { context.dataStore.edit { it[Keys.RAWG_API_KEY] = key.trim() } }
    }

    suspend fun setDiscogsToken(token: String) {
        runCatching { context.dataStore.edit { it[Keys.DISCOGS_TOKEN] = token.trim() } }
    }

    suspend fun setOpenCriticApiKey(key: String) {
        runCatching { context.dataStore.edit { it[Keys.OPENCRITIC_API_KEY] = key.trim() } }
    }

    /** 豆瓣剧照开关（灰色通道，默认关）。 */
    suspend fun setDoubanPhotosEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.DOUBAN_PHOTOS_ENABLED] = enabled } }
    }

    /** 豆瓣图片与 API 的 Referer（失效时可自行修改）。 */
    suspend fun setDoubanReferers(imageReferer: String, apiReferer: String) {
        runCatching {
            context.dataStore.edit {
                it[Keys.DOUBAN_IMAGE_REFERER] = imageReferer.trim()
                it[Keys.DOUBAN_API_REFERER] = apiReferer.trim()
            }
        }
    }

    /** 灰色通道总开关。 */
    suspend fun setGrayChannelEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.GRAY_CHANNEL_ENABLED] = enabled } }
    }

    /** 灰色通道自定义 UA / Cookie（空串 = 不覆盖）。 */
    suspend fun setProbeHeaders(userAgent: String, cookie: String) {
        runCatching {
            context.dataStore.edit {
                it[Keys.PROBE_USER_AGENT] = userAgent.trim()
                it[Keys.PROBE_COOKIE] = cookie.trim()
            }
        }
    }

    /** 是否允许 TMDb 覆盖 GAME / BOOK / MUSIC。 */
    suspend fun setTmdbIncludeNonTvTypes(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.TMDB_INCLUDE_NON_TV_TYPES] = enabled } }
    }

    /** Bangumi OAuth 应用凭据（用户自建；空串 = 清除）。 */
    suspend fun setBangumiOAuthApp(clientId: String, clientSecret: String, redirectUri: String) {
        runCatching {
            context.dataStore.edit {
                it[Keys.BANGUMI_CLIENT_ID] = clientId.trim()
                it[Keys.BANGUMI_CLIENT_SECRET] = clientSecret.trim()
                it[Keys.BANGUMI_REDIRECT_URI] = redirectUri.trim().ifBlank { "niriko://oauth/bangumi" }
            }
        }
    }

    /** OAuth 换到 token 后一次性写入 token / tokenType / 用户名。 */
    suspend fun setBangumiOAuthToken(token: String, tokenType: String, username: String) {
        runCatching {
            context.dataStore.edit {
                it[Keys.BANGUMI_ACCESS_TOKEN] = token.trim()
                it[Keys.BANGUMI_TOKEN_TYPE] = tokenType.trim()
                it[Keys.BANGUMI_USERNAME] = username.trim()
            }
        }
    }

    /** 清除 Bangumi 登录态（保留 client id/secret，便于重新授权）。 */
    suspend fun clearBangumiLogin() {
        runCatching {
            context.dataStore.edit {
                it[Keys.BANGUMI_ACCESS_TOKEN] = ""
                it[Keys.BANGUMI_TOKEN_TYPE] = ""
                it[Keys.BANGUMI_USERNAME] = ""
            }
        }
    }

    /** 设置 Steam 登录用户 SteamID64（空串表示退出登录）。 */
    suspend fun setSteamId64(steamId64: String) {
        runCatching { context.dataStore.edit { it[Keys.STEAM_ID64] = steamId64.trim() } }
    }

    /** 设置 Steam 用户 access token（webapi_token；空串清除）。 */
    suspend fun setSteamWebApiToken(token: String) {
        runCatching { context.dataStore.edit { it[Keys.STEAM_WEB_API_TOKEN] = token.trim() } }
    }

    suspend fun setWebDavUrl(url: String) {
        runCatching { context.dataStore.edit { it[Keys.WEBDAV_URL] = url } }
    }

    suspend fun setWebDavUsername(username: String) {
        runCatching { context.dataStore.edit { it[Keys.WEBDAV_USERNAME] = username } }
    }

    suspend fun setWebDavPassword(password: String) {
        runCatching { context.dataStore.edit { it[Keys.WEBDAV_PASSWORD] = password } }
    }

    suspend fun setWebDavAutoSync(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.WEBDAV_AUTO_SYNC] = enabled } }
    }

    suspend fun setBangumiAccessToken(token: String) {
        runCatching { context.dataStore.edit { it[Keys.BANGUMI_ACCESS_TOKEN] = token } }
    }

    suspend fun setBangumiTokenType(tokenType: String) {
        runCatching { context.dataStore.edit { it[Keys.BANGUMI_TOKEN_TYPE] = tokenType } }
    }

    suspend fun setBangumiUsername(username: String) {
        runCatching { context.dataStore.edit { it[Keys.BANGUMI_USERNAME] = username } }
    }

    suspend fun setBangumiSyncEnabled(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.BANGUMI_SYNC_ENABLED] = enabled } }
    }

    suspend fun setBangumiSyncPriority(priority: BangumiSyncPriority) {
        runCatching { context.dataStore.edit { it[Keys.BANGUMI_SYNC_PRIORITY] = priority.name } }
    }

    suspend fun setBangumiAutoSync(enabled: Boolean) {
        runCatching { context.dataStore.edit { it[Keys.BANGUMI_AUTO_SYNC] = enabled } }
    }

    /** 从备份中恢复全部设置。 */
    suspend fun restoreFrom(settings: AppSettings) {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[Keys.THEME_MODE] = settings.themeMode.ordinal
                prefs[Keys.DYNAMIC_COLOR] = settings.dynamicColor
                prefs[Keys.OLED_DARK] = settings.oledDark
                prefs[Keys.THEME_COLOR_INDEX] = settings.themeColorIndex
                if (settings.customSeedColor == -1) prefs.remove(Keys.CUSTOM_SEED_COLOR)
                else prefs[Keys.CUSTOM_SEED_COLOR] = settings.customSeedColor
                prefs[Keys.WALLPAPER_ENABLED] = settings.wallpaperEnabled
                prefs[Keys.WALLPAPER_URI] = settings.wallpaperUri
                prefs[Keys.WALLPAPER_LIBRARY_URI] = settings.wallpaperLibraryUri
                prefs[Keys.WALLPAPER_DISCOVER_URI] = settings.wallpaperDiscoverUri
                prefs[Keys.WALLPAPER_STATS_URI] = settings.wallpaperStatsUri
                prefs[Keys.WALLPAPER_SETTINGS_URI] = settings.wallpaperSettingsUri
                prefs[Keys.WALLPAPER_BLUR_DP] = settings.wallpaperBlurDp
                prefs[Keys.WALLPAPER_ATMOSPHERE] = settings.wallpaperAtmosphere.name
                prefs[Keys.CARD_GLASS_LEVEL] = settings.cardGlassLevel.name
                prefs[Keys.SPLASH_ENABLED] = settings.splashEnabled
                prefs[Keys.ACTIVE_THEME_PACK_ID] = settings.activeThemePackId
                prefs[Keys.REDUCE_MOTION] = settings.reduceMotion
                prefs[Keys.CUSTOM_ICON_MODE] = settings.customIconMode
                prefs[Keys.SHARE_INCLUDE_RATING] = settings.shareIncludeRating
                prefs[Keys.SHARE_INCLUDE_PROGRESS] = settings.shareIncludeProgress
                prefs[Keys.SHARE_INCLUDE_TAGS] = settings.shareIncludeTags
                prefs[Keys.DEFAULT_SORT_ORDER] = settings.defaultSortOrder.name
                prefs[Keys.SHOW_STATUS_TAGS] = settings.showStatusTags
                prefs[Keys.SHOW_PROGRESS_BAR] = settings.showProgressBar
                prefs[Keys.NSFW_ENABLED] = settings.nsfwEnabled
                prefs[Keys.SHOW_SEARCH_SUGGESTIONS] = settings.showSearchSuggestions
                prefs[Keys.DISCOVERY_LAYOUT] = settings.discoveryLayout
                prefs[Keys.AIRING_REMINDER_ENABLED] = settings.airingReminderEnabled
                prefs[Keys.SHOW_ANNUALLY_SUMMARY] = settings.showAnnuallySummary
                prefs[Keys.START_PAGE] = settings.startPage
                prefs[Keys.ACTIVE_DATA_SOURCE_ID] = settings.activeDataSourceId
                prefs[Keys.BANGUMI_ENDPOINT] = settings.bangumiEndpoint.name
                prefs[Keys.STEAM_API_KEY] = settings.steamApiKey
                prefs[Keys.STEAM_ID64] = settings.steamId64
                prefs[Keys.STEAM_WEB_API_TOKEN] = settings.steamWebApiToken
                prefs[Keys.EXTERNAL_RATINGS_ENABLED] = settings.externalRatingsEnabled
                prefs[Keys.TMDB_API_KEY] = settings.tmdbApiKey
                prefs[Keys.TMDB_API_URL] = settings.tmdbApiUrl
                prefs[Keys.TMDB_IMAGE_URL] = settings.tmdbImageUrl
                prefs[Keys.OMDB_API_KEY] = settings.omdbApiKey
                prefs[Keys.IMDB_EPISODE_RATINGS_ENABLED] = settings.imdbEpisodeRatingsEnabled
                prefs[Keys.IGDB_CLIENT_ID] = settings.igdbClientId
                prefs[Keys.IGDB_CLIENT_SECRET] = settings.igdbClientSecret
                prefs[Keys.RAWG_API_KEY] = settings.rawgApiKey
                prefs[Keys.DISCOGS_TOKEN] = settings.discogsToken
                prefs[Keys.OPENCRITIC_API_KEY] = settings.openCriticApiKey
                prefs[Keys.DOUBAN_PHOTOS_ENABLED] = settings.doubanPhotosEnabled
                prefs[Keys.DOUBAN_IMAGE_REFERER] = settings.doubanImageReferer
                prefs[Keys.DOUBAN_API_REFERER] = settings.doubanApiReferer
                prefs[Keys.GRAY_CHANNEL_ENABLED] = settings.grayChannelEnabled
                prefs[Keys.PROBE_USER_AGENT] = settings.probeUserAgent
                prefs[Keys.PROBE_COOKIE] = settings.probeCookie
                prefs[Keys.TMDB_INCLUDE_NON_TV_TYPES] = settings.tmdbIncludeNonTvTypes
                prefs[Keys.WEBDAV_URL] = settings.webDavUrl
                prefs[Keys.WEBDAV_USERNAME] = settings.webDavUsername
                prefs[Keys.WEBDAV_PASSWORD] = settings.webDavPassword
                prefs[Keys.WEBDAV_AUTO_SYNC] = settings.webDavAutoSync
                prefs[Keys.BANGUMI_ACCESS_TOKEN] = settings.bangumiAccessToken
                prefs[Keys.BANGUMI_TOKEN_TYPE] = settings.bangumiTokenType
                prefs[Keys.BANGUMI_USERNAME] = settings.bangumiUsername
                prefs[Keys.BANGUMI_SYNC_ENABLED] = settings.bangumiSyncEnabled
                prefs[Keys.BANGUMI_SYNC_PRIORITY] = settings.bangumiSyncPriority.name
                prefs[Keys.BANGUMI_AUTO_SYNC] = settings.bangumiAutoSync
                prefs[Keys.BANGUMI_CLIENT_ID] = settings.bangumiClientId
                prefs[Keys.BANGUMI_CLIENT_SECRET] = settings.bangumiClientSecret
                prefs[Keys.BANGUMI_REDIRECT_URI] = settings.bangumiRedirectUri
            }
        }
    }
}
