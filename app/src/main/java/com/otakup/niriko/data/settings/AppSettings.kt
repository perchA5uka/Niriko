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
 * 应用设置数据类。
 * 所有可持久化的用户偏好集中于此。
 */
data class AppSettings(
    // ===== 外观 =====
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val oledDark: Boolean = false,

    // ===== 收藏 =====
    val defaultSortOrder: SortOrder = SortOrder.UPDATE_TIME,
    val showStatusTags: Boolean = true,
    val showProgressBar: Boolean = true,

    // ===== 搜索 =====
    val nsfwEnabled: Boolean = false,
    val showSearchSuggestions: Boolean = true,

    // ===== 数据源 =====
    val activeDataSourceId: String = "bangumi",
    /** Bangumi 接口端点：官方站 / 国内反代。 */
    val bangumiEndpoint: BangumiEndpoint = BangumiEndpoint.OFFICIAL,
    /** Steam Web API key（可选，公开接口无需 key，仅未来扩展用）。 */
    val steamApiKey: String = "",

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
