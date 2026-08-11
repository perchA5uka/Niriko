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

        // 收藏
        val DEFAULT_SORT_ORDER = stringPreferencesKey("default_sort_order")  // SortOrder.name
        val SHOW_STATUS_TAGS = booleanPreferencesKey("show_status_tags")
        val SHOW_PROGRESS_BAR = booleanPreferencesKey("show_progress_bar")

        // 搜索
        val NSFW_ENABLED = booleanPreferencesKey("nsfw_enabled")
        val SHOW_SEARCH_SUGGESTIONS = booleanPreferencesKey("show_search_suggestions")

        // 统计与启动
        val SHOW_ANNUALLY_SUMMARY = booleanPreferencesKey("show_annually_summary")
        val START_PAGE = stringPreferencesKey("start_page")

        // 数据源
        val ACTIVE_DATA_SOURCE_ID = stringPreferencesKey("active_data_source_id")
        val BANGUMI_ENDPOINT = stringPreferencesKey("bangumi_endpoint")
        val STEAM_API_KEY = stringPreferencesKey("steam_api_key")
        val STEAM_ID64 = stringPreferencesKey("steam_id64")
        val STEAM_WEB_API_TOKEN = stringPreferencesKey("steam_web_api_token")

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

            // 收藏
            defaultSortOrder = prefs[Keys.DEFAULT_SORT_ORDER]?.let { name ->
                try { SortOrder.valueOf(name) } catch (_: IllegalArgumentException) { defaults.defaultSortOrder }
            } ?: defaults.defaultSortOrder,
            showStatusTags = prefs[Keys.SHOW_STATUS_TAGS] ?: defaults.showStatusTags,
            showProgressBar = prefs[Keys.SHOW_PROGRESS_BAR] ?: defaults.showProgressBar,

            // 搜索
            nsfwEnabled = prefs[Keys.NSFW_ENABLED] ?: defaults.nsfwEnabled,
            showSearchSuggestions = prefs[Keys.SHOW_SEARCH_SUGGESTIONS] ?: defaults.showSearchSuggestions,

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
                prefs[Keys.DEFAULT_SORT_ORDER] = settings.defaultSortOrder.name
                prefs[Keys.SHOW_STATUS_TAGS] = settings.showStatusTags
                prefs[Keys.SHOW_PROGRESS_BAR] = settings.showProgressBar
                prefs[Keys.NSFW_ENABLED] = settings.nsfwEnabled
                prefs[Keys.SHOW_SEARCH_SUGGESTIONS] = settings.showSearchSuggestions
                prefs[Keys.SHOW_ANNUALLY_SUMMARY] = settings.showAnnuallySummary
                prefs[Keys.START_PAGE] = settings.startPage
                prefs[Keys.ACTIVE_DATA_SOURCE_ID] = settings.activeDataSourceId
                prefs[Keys.BANGUMI_ENDPOINT] = settings.bangumiEndpoint.name
                prefs[Keys.STEAM_API_KEY] = settings.steamApiKey
                prefs[Keys.STEAM_ID64] = settings.steamId64
                prefs[Keys.STEAM_WEB_API_TOKEN] = settings.steamWebApiToken
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
            }
        }
    }
}
