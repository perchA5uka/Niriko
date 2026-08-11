package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.remote.BangumiClient
import com.otakup.niriko.data.remote.BangumiClient.BangumiEndpoint
import com.otakup.niriko.data.settings.AppSettings
import com.otakup.niriko.data.settings.SettingsDataStore
import com.otakup.niriko.data.settings.ThemeMode
import com.otakup.niriko.data.sync.SyncManager
import com.otakup.niriko.data.sync.bangumi.BangumiSyncManager
import com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority
import com.otakup.niriko.data.sync.bangumi.BangumiSyncResult
import com.otakup.niriko.plugin.DataSourcePlugin
import com.otakup.niriko.plugin.PluginManager
import com.otakup.niriko.data.sync.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页 ViewModel。
 * 将 DataStore Flow 转换为 StateFlow 供 UI 订阅，
 * 并通过 suspend 函数写入设置变更。
 */
class SettingsViewModel(
    private val dataStore: SettingsDataStore,
    private val pluginManager: PluginManager? = null,
    private val syncManager: SyncManager? = null,
    private val bangumiSyncManager: BangumiSyncManager? = null,
) : ViewModel() {

    /** UI 可直接 collect 的设置状态。 */
    val settings: StateFlow<AppSettings> = dataStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    /** 所有已注册插件（按优先级排序）。 */
    val plugins: List<DataSourcePlugin>
        get() = pluginManager?.plugins ?: emptyList()

    /** 第一个插件（主数据源）ID。 */
    val primaryPluginId: String?
        get() = pluginManager?.primary?.id

    // ===== 外观 =====

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { dataStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { dataStore.setDynamicColor(enabled) }
    }

    fun setOledDark(enabled: Boolean) {
        viewModelScope.launch { dataStore.setOledDark(enabled) }
    }

    // ===== 收藏 =====

    fun setDefaultSortOrder(order: SortOrder) {
        viewModelScope.launch { dataStore.setDefaultSortOrder(order) }
    }

    fun setShowStatusTags(show: Boolean) {
        viewModelScope.launch { dataStore.setShowStatusTags(show) }
    }

    fun setShowProgressBar(show: Boolean) {
        viewModelScope.launch { dataStore.setShowProgressBar(show) }
    }

    // ===== 搜索 =====

    fun setNsfwEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setNsfwEnabled(enabled) }
    }

    fun setShowSearchSuggestions(show: Boolean) {
        viewModelScope.launch { dataStore.setShowSearchSuggestions(show) }
    }

    // ===== 数据源 =====

    fun setActiveDataSourceId(id: String) {
        viewModelScope.launch {
            if (pluginManager?.moveToTop(id) == true) {
                dataStore.setActiveDataSourceId(id)
            }
        }
    }

    /** 切换 Bangumi 接口端点（官方站 / 国内反代）：持久化 + 立即生效（动态拦截器重写请求）。 */
    fun setBangumiEndpoint(e: BangumiEndpoint) {
        viewModelScope.launch {
            dataStore.setBangumiEndpoint(e)
            BangumiClient.setEndpoint(e)
        }
    }

    /** 设置 Steam Web API key（可留空，公开接口无需 key）。 */
    fun setSteamApiKey(key: String) {
        viewModelScope.launch { dataStore.setSteamApiKey(key) }
    }

    // ===== WebDAV 同步 =====

    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    val syncResult: StateFlow<SyncResult?> = _syncResult.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun setWebDavUrl(url: String) {
        viewModelScope.launch { dataStore.setWebDavUrl(url) }
    }

    fun setWebDavUsername(username: String) {
        viewModelScope.launch { dataStore.setWebDavUsername(username) }
    }

    fun setWebDavPassword(password: String) {
        viewModelScope.launch { dataStore.setWebDavPassword(password) }
    }

    fun setWebDavAutoSync(enabled: Boolean) {
        viewModelScope.launch { dataStore.setWebDavAutoSync(enabled) }
    }

    fun uploadToWebDav() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val s = settings.value
                val r = withContext(Dispatchers.IO) {
                    syncManager?.upload(s.webDavUrl, s.webDavUsername, s.webDavPassword)
                        ?: SyncResult(success = false, message = "同步管理器未初始化")
                }
                _syncResult.value = r
            } finally {
                // 任何路径（含 DataStore 读失败）都重置同步状态，避免卡在加载中
                _isSyncing.value = false
            }
        }
    }

    fun downloadFromWebDav() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val s = settings.value
                val r = withContext(Dispatchers.IO) {
                    syncManager?.download(s.webDavUrl, s.webDavUsername, s.webDavPassword)
                        ?: SyncResult(success = false, message = "同步管理器未初始化")
                }
                _syncResult.value = r
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncResult() {
        _syncResult.value = null
    }

    // ===== Bangumi 账号同步 =====

    private val _bangumiSyncResult = MutableStateFlow<BangumiSyncResult?>(null)
    val bangumiSyncResult: StateFlow<BangumiSyncResult?> = _bangumiSyncResult.asStateFlow()

    private val _bangumiProgress = MutableStateFlow("")
    val bangumiProgress: StateFlow<String> = _bangumiProgress.asStateFlow()

    private val _isBangumiSyncing = MutableStateFlow(false)
    val isBangumiSyncing: StateFlow<Boolean> = _isBangumiSyncing.asStateFlow()

    private val _bangumiLoginState = MutableStateFlow<String?>(null)
    @Suppress("unused")
    val bangumiLoginState: StateFlow<String?> = _bangumiLoginState.asStateFlow()

    fun setBangumiAccessToken(token: String) {
        viewModelScope.launch { dataStore.setBangumiAccessToken(token) }
    }

    fun setBangumiSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setBangumiSyncEnabled(enabled) }
    }

    fun setBangumiSyncPriority(priority: BangumiSyncPriority) {
        viewModelScope.launch { dataStore.setBangumiSyncPriority(priority) }
    }

    /** 用当前 Token 校验并显示用户名（对应 Kazumi init→ping）。 */
    fun loginBangumi() {
        viewModelScope.launch {
            _bangumiLoginState.value = "登录中…"
            try {
                val username = withContext(Dispatchers.IO) {
                    bangumiSyncManager?.fetchUsername() ?: error("同步管理器未初始化")
                }
                dataStore.setBangumiUsername(username)
                _bangumiLoginState.value = "已登录:$username"
            } catch (e: Exception) {
                _bangumiLoginState.value = "登录失败:${e.message}"
            }
        }
    }

    /** 手动触发一次双向同步（进度/结果经 StateFlow 供 UI 订阅）。 */
    fun syncBangumi() {
        viewModelScope.launch {
            _isBangumiSyncing.value = true
            _bangumiSyncResult.value = null
            try {
                val r = withContext(Dispatchers.IO) {
                    bangumiSyncManager?.syncOnce { message, _, _ ->
                        _bangumiProgress.value = message
                    } ?: BangumiSyncResult(success = false, message = "同步管理器未初始化")
                }
                _bangumiSyncResult.value = r
            } finally {
                _isBangumiSyncing.value = false
                if (_bangumiProgress.value.isNotEmpty()) {
                    _bangumiProgress.value = ""
                }
            }
        }
    }

    fun clearBangumiSyncResult() {
        _bangumiSyncResult.value = null
    }

    // ===== 统计与启动 =====

    fun setShowAnnuallySummary(show: Boolean) {
        viewModelScope.launch { dataStore.setShowAnnuallySummary(show) }
    }

    fun setStartPage(page: String) {
        viewModelScope.launch { dataStore.setStartPage(page) }
    }
}

class SettingsViewModelFactory(
    private val dataStore: SettingsDataStore,
    private val pluginManager: PluginManager? = null,
    private val syncManager: SyncManager? = null,
    private val bangumiSyncManager: BangumiSyncManager? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(dataStore, pluginManager, syncManager, bangumiSyncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
