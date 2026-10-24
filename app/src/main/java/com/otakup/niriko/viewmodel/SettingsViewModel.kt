package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.refresh.RefreshCoordinator
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.remote.BangumiClient
import com.otakup.niriko.data.remote.BangumiClient.BangumiEndpoint
import com.otakup.niriko.data.settings.AppSettings
import com.otakup.niriko.data.settings.SettingsDataStore
import com.otakup.niriko.data.settings.ThemeMode
import com.otakup.niriko.data.sync.SyncFailureKind
import com.otakup.niriko.data.sync.SyncManager
import com.otakup.niriko.data.sync.bangumi.BangumiSyncManager
import com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority
import com.otakup.niriko.data.sync.bangumi.BangumiSyncResult
import com.otakup.niriko.plugin.PluginManager
import com.otakup.niriko.data.sync.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    /** 刷新编排器（设置页展示自动同步的「上次同步 / 下次可同步」）。 */
    private val refreshCoordinator: RefreshCoordinator? = null,
    /** 预加载的设置（开屏期间同步读取，避免冷启动首帧主题闪烁）。 */
    initialSettings: AppSettings? = null,
    /**
     * C-1 体检用的只读计数（由 NavHost 用 DAO 构造后注入）。
     *
     * 可空默认：既有的工厂调用点 / 预览 / 单测都不必改；
     * null 时体检会跳过计数类检查，而不是伪造 0。
     */
    private val collectionCountsProvider: com.otakup.niriko.data.diagnose.CollectionCountsProvider? = null,
) : ViewModel() {

    /** C-1：读取体检计数；失败返回 null（体检页显示「读取失败」，不假装干净）。 */
    suspend fun loadCollectionCounts(): com.otakup.niriko.data.diagnose.CollectionCounts? =
        collectionCountsProvider?.let { provider ->
            runCatching { provider.load() }.getOrNull()
        }

    /**
     * 自动同步的新鲜度/退避状态。
     * 改造前「自动同步」开关打开后什么都不会发生，界面上也无从判断 —— 这里把它显示出来。
     */
    val refreshSnapshots: kotlinx.coroutines.flow.StateFlow<
        Map<String, com.otakup.niriko.data.refresh.FreshnessSnapshot>
        > = refreshCoordinator?.snapshots
        ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())

    /** 正在刷新的资源 key（诊断页「进行中」区）。 */
    val refreshRunningKeys: kotlinx.coroutines.flow.StateFlow<Set<String>> =
        refreshCoordinator?.runningKeys ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())

    /**
     * 清除某个资源的新鲜度记录。
     * **不会立即发起请求** —— 只是让该资源的下一次刷新绕过新鲜度窗口与退避。
     */
    fun resetRefreshKey(key: String) {
        viewModelScope.launch { refreshCoordinator?.reset(key) }
    }

    /** 清除全部刷新记录（下次进入各页面会重新拉取所有资源）。 */
    fun resetAllRefreshRecords() {
        viewModelScope.launch { refreshCoordinator?.resetAll() }
    }

    /** 立即强制刷新应用级资源（Steam 排行榜 / Steam 自动匹配），绕过新鲜度与退避。 */
    fun forceRefreshAppResources() {
        com.otakup.niriko.data.refresh.RefreshCommands.requestAppForceRefresh()
    }

    /** UI 可直接 collect 的设置状态。 */
    val settings: StateFlow<AppSettings> = dataStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initialSettings ?: AppSettings(),
        )

    // ===== 外观 =====

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { dataStore.setThemeMode(mode) }
    }

    /** C · 13.1：标题 logo 三档（off / always / text_when_unclear）。 */
    fun setTitleLogoMode(mode: String) {
        viewModelScope.launch { dataStore.setTitleLogoMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { dataStore.setDynamicColor(enabled) }
    }

    fun setOledDark(enabled: Boolean) {
        viewModelScope.launch { dataStore.setOledDark(enabled) }
    }

    /** 设置主题色（0=绿色默认；见 ThemePalettes）。 */
    fun setThemeColorIndex(index: Int) {
        viewModelScope.launch { dataStore.setThemeColorIndex(index) }
    }

    /** 设置自定义主题色种子（ARGB；null=恢复默认品牌绿）。 */
    fun setCustomSeedColor(argb: Int?) {
        viewModelScope.launch { dataStore.setCustomSeedColor(argb) }
    }

    fun setWallpaperParallaxEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setWallpaperParallaxEnabled(enabled) }
    }

    /** F06：写入「详情页隐藏的部件」（已编码的 key 串；编码规则在 ui 层的 DetailLayoutPolicy）。 */
    fun setDetailHiddenSections(encoded: String) {
        viewModelScope.launch { dataStore.setDetailHiddenSections(encoded) }
    }

    /** F06 第二步：写入「详情页部件顺序」（已编码的 key 串；空串 = 默认顺序）。 */
    fun setDetailSectionOrder(encoded: String) {
        viewModelScope.launch { dataStore.setDetailSectionOrder(encoded) }
    }

    /** F18：书籍 / 漫画网点总开关。 */
    fun setHalftoneEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setHalftoneEnabled(enabled) }
    }

    /** F08：CRT 老电视模式开关。 */
    fun setCrtModeEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setCrtModeEnabled(enabled) }
    }

    fun setWallpaperEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setWallpaperEnabled(enabled) }
    }

    /** 设置全局壁纸 URI（空串清除）。 */
    fun setWallpaperUri(uri: String) {
        viewModelScope.launch { dataStore.setWallpaperUri(uri) }
    }

    /** 设置某页壁纸覆盖（空串=继承全局）。 */
    fun setWallpaperPageUri(page: String, uri: String) {
        viewModelScope.launch { dataStore.setWallpaperPageUri(page, uri) }
    }

    fun setWallpaperBlurDp(dp: Int) {
        viewModelScope.launch { dataStore.setWallpaperBlurDp(dp) }
    }

    /** 设置壁纸氛围（浓郁 / 均衡 / 素净）。 */
    fun setWallpaperAtmosphere(level: com.otakup.niriko.data.settings.WallpaperAtmosphere) {
        viewModelScope.launch { dataStore.setWallpaperAtmosphere(level) }
    }

    /** 设置壁纸库整表（R3 壁纸库）。 */
    fun setWallpaperLibraryEntries(entries: List<com.otakup.niriko.data.wallpaper.WallpaperLibraryEntry>) {
        viewModelScope.launch { dataStore.setWallpaperLibraryEntries(entries) }
    }

    /** 设置壁纸每日轮换开关。 */
    fun setWallpaperRotationEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setWallpaperRotationEnabled(enabled) }
    }

    /** 设置轮换是否只取收藏项。 */
    fun setWallpaperRotationFavoritesOnly(enabled: Boolean) {
        viewModelScope.launch { dataStore.setWallpaperRotationFavoritesOnly(enabled) }
    }

    /** 设置卡片液态玻璃档位（全开 / 仅已收藏 / 关闭）。 */
    fun setCardGlassLevel(level: com.otakup.niriko.data.settings.CardGlassLevel) {
        viewModelScope.launch { dataStore.setCardGlassLevel(level) }
    }

    fun setSplashEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setSplashEnabled(enabled) }
    }

    fun setActiveThemePackId(id: String) {
        viewModelScope.launch { dataStore.setActiveThemePackId(id) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { dataStore.setReduceMotion(enabled) }
    }

    /** 设置玻璃/特效强度（阶段 P：低端机降级）。 */
    fun setGlassEffect(level: com.otakup.niriko.data.settings.GlassEffectLevel) {
        viewModelScope.launch { dataStore.setGlassEffect(level) }
    }

    /** 10.1：AUTO = 按设备能力自动降档；MANUAL = 只听用户档位。 */
    fun setGlassAutoTier(mode: String) {
        viewModelScope.launch { dataStore.setGlassAutoTier(mode) }
    }

    fun setCustomIconMode(mode: String) {
        viewModelScope.launch { dataStore.setCustomIconMode(mode) }
    }

    fun setShareIncludeRating(enabled: Boolean) {
        viewModelScope.launch { dataStore.setShareIncludeRating(enabled) }
    }
    fun setShareIncludeProgress(enabled: Boolean) {
        viewModelScope.launch { dataStore.setShareIncludeProgress(enabled) }
    }
    fun setShareIncludeTags(enabled: Boolean) {
        viewModelScope.launch { dataStore.setShareIncludeTags(enabled) }
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

    /** 在看条目新集放送提醒开关（阶段 J）。 */
    fun setAiringReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setAiringReminderEnabled(enabled) }
    }

    // ===== 数据源 =====

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

    // ===== 权威评分数据源（阶段 0–1）=====

    /** 权威评分总开关：关闭后详情页不出现任何外部评分区块。 */
    fun setExternalRatingsEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setExternalRatingsEnabled(enabled) }
    }

    /** TMDb v3 API Key；写入后立即生效（TmdbClient 由 Application 的设置收集器同步）。 */
    fun setTmdbApiKey(key: String) {
        viewModelScope.launch { dataStore.setTmdbApiKey(key) }
    }

    /** TMDb 端点（官方 / 自建镜像）；api 与 image 成对设置。 */
    fun setTmdbUrls(apiUrl: String, imageUrl: String) {
        viewModelScope.launch { dataStore.setTmdbUrls(apiUrl, imageUrl) }
    }

    fun setOmdbApiKey(key: String) {
        viewModelScope.launch { dataStore.setOmdbApiKey(key) }
    }

    fun setImdbEpisodeRatingsEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setImdbEpisodeRatingsEnabled(enabled) }
    }

    fun setIgdbCredentials(clientId: String, clientSecret: String) {
        viewModelScope.launch { dataStore.setIgdbCredentials(clientId, clientSecret) }
    }

    fun setRawgApiKey(key: String) {
        viewModelScope.launch { dataStore.setRawgApiKey(key) }
    }

    fun setDiscogsToken(token: String) {
        viewModelScope.launch { dataStore.setDiscogsToken(token) }
    }

    fun setOpenCriticApiKey(key: String) {
        viewModelScope.launch { dataStore.setOpenCriticApiKey(key) }
    }

    /** 豆瓣剧照开关（灰色通道，默认关）。 */
    fun setDoubanPhotosEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setDoubanPhotosEnabled(enabled) }
    }

    /** 豆瓣剧照防剧透（B4 · 4-13：用户开关，默认开）。 */
    fun setDoubanAntiSpoiler(enabled: Boolean) {
        viewModelScope.launch { dataStore.setDoubanAntiSpoiler(enabled) }
    }

    fun setDoubanReferers(imageReferer: String, apiReferer: String) {
        viewModelScope.launch { dataStore.setDoubanReferers(imageReferer, apiReferer) }
    }

    // ===== 灰色通道 / TMDb 覆盖 =====

    /** 灰色通道总开关（非官方接口；默认关）。 */
    fun setGrayChannelEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.setGrayChannelEnabled(enabled) }
    }

    /** 灰色通道自定义 UA / Cookie。 */
    fun setProbeHeaders(userAgent: String, cookie: String) {
        viewModelScope.launch { dataStore.setProbeHeaders(userAgent, cookie) }
    }

    /** 是否允许 TMDb 覆盖 GAME / BOOK / MUSIC。 */
    fun setTmdbIncludeNonTvTypes(enabled: Boolean) {
        viewModelScope.launch { dataStore.setTmdbIncludeNonTvTypes(enabled) }
    }

    // ===== WebDAV 同步 =====

    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    val syncResult: StateFlow<SyncResult?> = _syncResult.asStateFlow()

    /**
     * 412 冲突弹窗内容（A-D4）。非 null 时设置页弹「先合并 / 覆盖云端 / 取消」。
     *
     * 改造前 `_syncResult` 没有任何 UI 收集，412 检测对了用户也看不到；
     * 这里把冲突单独做成状态，避免和普通失败混在 Snackbar 里被自动清掉。
     */
    private val _syncConflict = MutableStateFlow<String?>(null)
    val syncConflict: StateFlow<String?> = _syncConflict.asStateFlow()

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

    fun uploadToWebDav() = runWebDavOperation(upload = true)

    fun downloadFromWebDav() = runWebDavOperation(upload = false)

    /**
     * 冲突弹窗「先合并（推荐）」：下载合并 → 再上传合并结果。
     * 合并语义与自动同步一致（字段级 LWW / 进度 MAX / 私密并集）。
     */
    fun mergeAndUploadWebDav() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncConflict.value = null
            try {
                val s = settings.value
                val r = withContext(Dispatchers.IO) {
                    val manager = syncManager
                        ?: return@withContext SyncResult(success = false, message = "同步管理器未初始化")
                    val merged = manager.download(s.webDavUrl, s.webDavUsername, s.webDavPassword)
                    if (!merged.success) {
                        merged
                    } else {
                        manager.upload(s.webDavUrl, s.webDavUsername, s.webDavPassword)
                    }
                }
                applySyncResult(r)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** 冲突弹窗「覆盖云端」：忽略 If-Match 再 PUT 一次。**只有用户显式点击**才会走到这里。 */
    fun overwriteWebDav() = runWebDavOperation(upload = true, forceOverwrite = true)

    fun dismissSyncConflict() {
        _syncConflict.value = null
    }

    fun clearSyncResult() {
        _syncResult.value = null
    }

    private fun runWebDavOperation(upload: Boolean, forceOverwrite: Boolean = false) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncConflict.value = null
            try {
                val s = settings.value
                val r = withContext(Dispatchers.IO) {
                    val manager = syncManager
                        ?: return@withContext SyncResult(success = false, message = "同步管理器未初始化")
                    if (upload) {
                        manager.upload(
                            s.webDavUrl, s.webDavUsername, s.webDavPassword,
                            forceOverwrite = forceOverwrite,
                        )
                    } else {
                        manager.download(s.webDavUrl, s.webDavUsername, s.webDavPassword)
                    }
                }
                applySyncResult(r)
            } finally {
                // 任何路径（含 DataStore 读失败）都重置同步状态，避免卡在加载中
                _isSyncing.value = false
            }
        }
    }

    private fun applySyncResult(result: SyncResult) {
        _syncResult.value = result
        // 412 需要三选一，不能只弹一条会自动消失的 Snackbar
        if (!result.success && result.failure == SyncFailureKind.CONFLICT) {
            _syncConflict.value = result.message.ifBlank { "云端在你上次同步后已更新" }
        }
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

    /**
     * Bangumi 自动同步开关。
     * 该设置此前只存在于 AppSettings/DataStore/备份里，**没有 UI、也没有消费方**。
     */
    fun setBangumiAutoSync(enabled: Boolean) {
        viewModelScope.launch { dataStore.setBangumiAutoSync(enabled) }
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

    // ===== Bangumi OAuth 登录（R4c：治本，NSFW 的前提） =====

    /**
     * OAuth 工作流状态。
     *
     * [authorizeUrl] 非空即表示「授权页应该在 UI 上打开」；[completed] 用于让 UI 知道
     * 可以自动收起 WebView（授权已换到 token，不必让用户手动关）。
     */
    data class BangumiOAuthState(
        val authorizeUrl: String? = null,
        val redirectPrefix: String = "",
        val isExchanging: Boolean = false,
        val message: String? = null,
        val error: String? = null,
        val completed: Boolean = false,
    )

    private val _bangumiOAuth = MutableStateFlow(BangumiOAuthState())
    val bangumiOAuth: StateFlow<BangumiOAuthState> = _bangumiOAuth.asStateFlow()

    /** 保存 OAuth 应用凭据（用户自建，不内置）。 */
    fun setBangumiOAuthApp(clientId: String, clientSecret: String, redirectUri: String) {
        viewModelScope.launch { dataStore.setBangumiOAuthApp(clientId, clientSecret, redirectUri) }
    }

    /**
     * 开始 OAuth 授权码流程：校验凭据 → 生成 authorize URL 交给 UI 打开。
     *
     * 凭据缺失时**不发请求**，直接把缺什么告诉用户——这比打开一个必然报错的页面好。
     */
    fun startBangumiOAuth() {
        viewModelScope.launch {
            val settings = dataStore.settings.first()
            val clientId = settings.bangumiClientId.trim()
            val redirectUri = settings.bangumiRedirectUri.trim()
                .ifBlank { "niriko://oauth/bangumi" }

            if (clientId.isEmpty()) {
                _bangumiOAuth.value = BangumiOAuthState(
                    error = "尚未填写 Bangumi OAuth Client ID。请先在 bgm.tv 创建一个应用，" +
                        "并把 Client ID / Secret / 回跳地址填到上方「OAuth 应用」里。",
                )
                return@launch
            }

            val state = java.util.UUID.randomUUID().toString()
            val url = com.otakup.niriko.data.remote.bangumi.BangumiOAuthClient.buildAuthorizeUrl(
                clientId = clientId,
                redirectUri = redirectUri,
                state = state,
            )
            _bangumiOAuth.value = BangumiOAuthState(
                authorizeUrl = url,
                redirectPrefix = redirectUri,
                message = "请在下方网页里完成 Bangumi 授权。",
            )
        }
    }

    /**
     * WebView 命中回跳地址：解析 code → 换 token → 落库 → 调 /v0/me 校验。
     *
     * 任何一步失败都把**原始错误**带回 UI（OAuth 排障全靠这个）。
     */
    fun onBangumiOAuthRedirect(callbackUrl: String) {
        viewModelScope.launch {
            _bangumiOAuth.update { it.copy(isExchanging = true, error = null) }

            val parsed = runCatching { android.net.Uri.parse(callbackUrl) }.getOrNull()
            val code = parsed?.getQueryParameter("code")
            val oauthError = parsed?.getQueryParameter("error")
            val errorDesc = parsed?.getQueryParameter("error_description")

            if (code.isNullOrBlank()) {
                _bangumiOAuth.update {
                    it.copy(
                        isExchanging = false,
                        error = if (oauthError != null) {
                            "授权被拒绝：$oauthError ${errorDesc.orEmpty()}".trim()
                        } else {
                            "回跳地址里没有 code 参数：$callbackUrl"
                        },
                    )
                }
                return@launch
            }

            val settings = dataStore.settings.first()
            try {
                val token = com.otakup.niriko.data.remote.bangumi.BangumiOAuthClient.exchangeCode(
                    clientId = settings.bangumiClientId.trim(),
                    clientSecret = settings.bangumiClientSecret.trim(),
                    redirectUri = settings.bangumiRedirectUri.trim()
                        .ifBlank { "niriko://oauth/bangumi" },
                    code = code,
                )
                // 先落库再校验：/v0/me 的 Authorization 由 Application 从设置里读
                dataStore.setBangumiOAuthToken(token.accessToken, token.tokenType, "")

                val username = com.otakup.niriko.data.remote.bangumi.BangumiOAuthClient.verify()
                dataStore.setBangumiUsername(username)
                _bangumiOAuth.value = BangumiOAuthState(
                    message = "已登录：$username（NSFW 检索现在可用）",
                    completed = true,
                )
            } catch (e: Exception) {
                _bangumiOAuth.update {
                    it.copy(
                        isExchanging = false,
                        error = "换取 token 失败：${e.message ?: e.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    /** 放弃当前 OAuth 工作流（关闭 WebView）。 */
    fun cancelBangumiOAuth() {
        _bangumiOAuth.value = BangumiOAuthState()
    }

    /** 退出 Bangumi 登录（保留 OAuth 应用凭据，便于重新授权）。 */
    fun logoutBangumi() {
        viewModelScope.launch {
            dataStore.clearBangumiLogin()
            _bangumiOAuth.value = BangumiOAuthState(message = "已退出 Bangumi 登录")
        }
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
    private val refreshCoordinator: RefreshCoordinator? = null,
    /** 预加载的设置（开屏期间同步读取，避免冷启动首帧主题闪烁）。 */
    private val initialSettings: AppSettings? = null,
    private val collectionCountsProvider: com.otakup.niriko.data.diagnose.CollectionCountsProvider? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                dataStore, pluginManager, syncManager, bangumiSyncManager,
                refreshCoordinator, initialSettings, collectionCountsProvider,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
