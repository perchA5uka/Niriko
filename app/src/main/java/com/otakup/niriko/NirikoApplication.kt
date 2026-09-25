package com.otakup.niriko

import android.app.Application
import com.otakup.niriko.data.backup.BackupManager
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.local.SteamPlaceholderMigrator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.otakup.niriko.data.local.SubjectWriteGateway
import com.otakup.niriko.data.refresh.AppForegroundSignals
import com.otakup.niriko.data.refresh.DataStoreFreshnessSource
import com.otakup.niriko.data.refresh.RefreshCommands
import com.otakup.niriko.data.refresh.RefreshCoordinator
import com.otakup.niriko.data.refresh.RefreshKeys
import com.otakup.niriko.data.refresh.RefreshResource
import com.otakup.niriko.data.remote.BangumiClient
import com.otakup.niriko.data.remote.BroadcastFetcher
import com.otakup.niriko.data.remote.SeasonalFetcher
import com.otakup.niriko.data.remote.anilist.AniListClient
import com.otakup.niriko.data.remote.anilist.AniListDataSource
import com.otakup.niriko.data.remote.anilist.AniListGameDataSource
import com.otakup.niriko.data.remote.bangumi.BangumiDataSource
import com.otakup.niriko.data.remote.game.GameDataSourceRegistry
import com.otakup.niriko.data.remote.game.SteamGameDataSource
import com.otakup.niriko.data.remote.vndb.VndbGameDataSource
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.NetworkMonitor
import com.otakup.niriko.data.repository.SteamRepository
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.data.repository.AnitabiRepository
import com.otakup.niriko.data.repository.VndbRepository
import com.otakup.niriko.data.repository.AniListRepository
import com.otakup.niriko.data.repository.EpisodeRepository
import com.otakup.niriko.data.onair.OnAirRepository
import com.otakup.niriko.data.repository.WorkRepository
import com.otakup.niriko.data.repository.ExternalIdRepository
import com.otakup.niriko.data.repository.ExternalRatingRepository
import com.otakup.niriko.data.repository.EpisodeRatingRepository
import com.otakup.niriko.data.repository.ManualAwardRepository
import com.otakup.niriko.data.repository.TmdbRepository
import com.otakup.niriko.data.settings.SettingsDataStore
import com.otakup.niriko.data.sync.SyncManager
import com.otakup.niriko.data.sync.WebDavClient
import com.otakup.niriko.data.sync.bangumi.BangumiSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ImageResult
import com.otakup.niriko.plugin.DataSourceCapabilities
import com.otakup.niriko.plugin.DataSourceChain
import com.otakup.niriko.plugin.DataSourcePlugin
import com.otakup.niriko.plugin.PluginManager
import com.otakup.niriko.util.PinyinSearch
import com.otakup.niriko.viewmodel.WorkViewModelFactory

/**
 * 应用入口：持有 Database / Repository / PluginManager，供 ViewModel 工厂使用。
 */
class NirikoApplication : Application() {

    /** 应用级协程作用域：替代 GlobalScope，随 Application 生命周期统一管理。 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 启动时恢复网络端点，并在设置变更时实时生效（在任何 API 请求前）：
        // - Bangumi 官方站 / 国内反代
        // - TMDb 端点与密钥（国区 api.themoviedb.org 与 image.tmdb.org 均不可直连，需镜像）
        applicationScope.launch(Dispatchers.IO) {
            settingsDataStore.settings.collect { settings ->
                BangumiClient.setEndpoint(settings.bangumiEndpoint)
                com.otakup.niriko.data.remote.tmdb.TmdbClient.setApiKey(settings.tmdbApiKey)
                com.otakup.niriko.data.remote.tmdb.TmdbClient.setEndpoint(
                    settings.tmdbApiUrl,
                    settings.tmdbImageUrl,
                )
            }
        }
        // 启动时迁移旧负数占位条目 → sourceKey 正式条目（幂等；无旧条目立即跳过）
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                SteamPlaceholderMigrator(
                    database = database,
                    subjectDao = database.subjectDao(),
                    collectionDao = database.collectionDao(),
                    steamDao = database.steamDao(),
                ).migrateIfNeeded()
            }
        }
        // 冷启动的**两条远端链**合并为一条串行链，并延后到首帧之后。
        //
        // 改造前它们是两个各自 launch 的独立任务，叠加 Discover/作品库/统计三个 ViewModel
        // 在首次组合时各自的刷新，冷启动瞬间会同时跑 4 条互不知情的远端链路（"刷新风暴"）。
        // 现在：错开首帧 → 有网才跑 → 每条都经 RefreshCoordinator 做**持久化**节流
        // （改造前 autoBind 的 6h 节流是进程内变量，冷启动等于没有节流）。
        applicationScope.launch(Dispatchers.IO) {
            delay(STARTUP_REMOTE_DELAY_MS)
            if (!networkMonitor.isOnline) return@launch
            runCatching { prefetchSteamChart() }
            runCatching { autoBindGames() }
        }
        // 放送提醒（阶段 J）：响应式——开关开启→注册每日周期 Worker，关闭→取消。
        // 冷启动读取到持久化值即按当前开关状态注册/取消；设置页切换时实时生效。
        applicationScope.launch(Dispatchers.IO) {
            settingsDataStore.settings.collect { s ->
                if (s.airingReminderEnabled) {
                    runCatching { com.otakup.niriko.data.notification.AiringReminderScheduler.schedule(this@NirikoApplication) }
                } else {
                    runCatching { com.otakup.niriko.data.notification.AiringReminderScheduler.cancel(this@NirikoApplication) }
                }
            }
        }
        // 阶段 D：为存量条目补齐拼音搜索键（仅一次；新条目在 SubjectRepository 落库时已带）
        applicationScope.launch(Dispatchers.IO) {
            runCatching { backfillPinyinKeys() }
        }
        // ===== 进前台 / 退后台的受控刷新 =====
        //
        // 改造前全仓没有任何 ON_START/ON_RESUME 观察者：App 长期驻留后台回到前台后，
        // 趋势、放送日历、Steam 数据全靠用户手动下拉。这里接上 ProcessLifecycleOwner，
        // 并在 onStop 取消轻量前台任务（省电）。
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // ① 广播给各 ViewModel（它们各自经 RefreshCoordinator 判定是否真的需要刷）
                AppForegroundSignals.notifyForeground()
                // ② 轻量前台预取：可被 onStop 取消
                foregroundJob?.cancel()
                foregroundJob = applicationScope.launch(Dispatchers.IO) {
                    runCatching { prefetchOnForeground() }
                }
                // ③ 自动同步单独跑：一旦开始就不该因为用户切走而被取消
                //    （被取消会白白记一次失败并进入退避梯）
                applicationScope.launch(Dispatchers.IO) {
                    runCatching { runAutoSyncIfEnabled() }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                foregroundJob?.cancel()
                foregroundJob = null
            }
        })

        // 诊断页下发的「立即强制刷新」：绕过新鲜度窗口与退避重跑应用级资源
        applicationScope.launch(Dispatchers.IO) {
            RefreshCommands.appForceRefresh.collect {
                if (!networkMonitor.isOnline) return@collect
                runCatching { forceRefreshAppResources() }
            }
        }

        // 注入 Bangumi 凭据读取器：AuthInterceptor 请求时动态取当前 token（参考 Kazumi 条件 Bearer）
        BangumiClient.authTokenProvider = {
            val s = runBlocking(Dispatchers.IO) { settingsDataStore.settings.first() }
            if (s.bangumiAccessToken.isBlank()) {
                null
            } else {
                BangumiClient.AuthToken(
                    tokenType = s.bangumiTokenType,
                    accessToken = s.bangumiAccessToken,
                )
            }
        }
        // 注入 Steam API key 读取器：设置页配置，公开接口请求时若存在则附带（未来扩展用）
        com.otakup.niriko.data.remote.steam.SteamApiClient.apiKeyProvider = {
            runBlocking(Dispatchers.IO) { settingsDataStore.settings.first() }.steamApiKey
        }
        // 全局图片域重写：切换反代后旧缓存封面（官方图床 lain.bgm.tv）仍可加载
        // （Coil Interceptor 在请求阶段把图片 URL 图床域改写为当前端点对应域）
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(Interceptor { chain ->
                        val request = chain.request
                        val newData = (request.data as? String)?.let(BangumiClient::rewriteImageUrl)
                            ?: request.data
                        val newRequest = if (newData !== request.data) {
                            request.newBuilder().data(newData).build()
                        } else {
                            request
                        }
                        chain.proceed(newRequest)
                    })
                }
                .build(),
        )
    }

    val database: NirikoDatabase by lazy { NirikoDatabase.getInstance(this) }

    /**
     * subjects 写入网关：所有作品元数据落库的唯一入口（批量单事务 + 内容 diff）。
     * 见 [SubjectWriteGateway] 的说明——它是「缓存写放大 → 界面失效风暴」的解药。
     */
    val subjectWriteGateway: SubjectWriteGateway by lazy {
        SubjectWriteGateway(database.subjectDao())
    }

    /**
     * 新鲜度 / 退避状态的数据源。
     * **必须单例**：实现里有内存镜像，多实例会各自漂移（A 实例记录的成功时间 B 看不到）。
     */
    private val freshnessSource: com.otakup.niriko.data.refresh.FreshnessSource by lazy {
        DataStoreFreshnessSource(this)
    }

    /** 刷新编排器：统一裁决「该不该刷 / 并发去重 / 失败退避」。 */
    val refreshCoordinator: RefreshCoordinator by lazy {
        RefreshCoordinator(freshnessSource)
    }

    companion object {
        /** 冷启动远端预取的启动延迟：让首帧与首屏刷新先跑，避免开局抢网络与数据库。 */
        private const val STARTUP_REMOTE_DELAY_MS = 3_000L

    }

    /**
     * 后台批量匹配：对本地全部 GAME 条目执行 Steam 自动绑定（阶段 G 后仅保留 Steam）。
     *
     * - 节流改为**持久化**（经 RefreshCoordinator 的 6h 窗口）：改造前是进程内 `@Volatile` 变量，
     *   每次冷启动都会立刻跑一遍，README 声称的「6h 节流」实际不成立；
     * - 失败会进入退避梯，不会在接口不可用时每次启动都重打；
     * - matchAndBind 内部已过滤（type=GAME、非占位、未绑定、分块 5 并发、失败静默），幂等。
     */
    private suspend fun autoBindGames(force: Boolean = false) {
        val subjectDao = database.subjectDao()
        // 全部非占位 bangumi 词条（占位条目无绑定价值）
        val all = runCatching { subjectDao.getAll() }
            .getOrDefault(emptyList())
            .filter { !it.isSteamPlaceholder }
        if (all.isEmpty()) return
        val games = all.filter { it.type == com.otakup.niriko.data.model.SubjectType.GAME }
        if (games.isEmpty()) return
        // 阶段 G 绑定保守化：仅保留 Steam 自动绑定（Steam 由 appid 精确标识，不会错绑）。
        // VNDB/AniList 不再做标题自动匹配写库（降低错绑）——其标题匹配只产出候选、由用户手动确认。
        refreshCoordinator.refresh(RefreshKeys.AUTO_BIND, RefreshResource.AUTO_BIND, force = force) {
            runCatching { steamRepository.matchAndBind(games) }
        }
    }

    /**
     * 启动预取 Steam 排行（store 热销榜为主）：
     * 1) fetchStoreTopSellers()：一次拉取 store 热销榜 100 条（appid+标题+封面），
     *    内部已落库真实 subjects 条目（sourceKey="steam:{appid}"，覆盖旧占位）；
     * 2) getChartRanks()：顺带预取活跃排名（详情页单作品"活跃排名"用，失败静默）。
     * 预取后用户切到发现页「Steam」标签时数据已就绪，直接展示 100 条热销榜、无需等待网络。
     * 任何失败静默（补充数据源，不影响主流程）。
     */
    private suspend fun prefetchSteamChart(force: Boolean = false) {
        val steam = steamRepository
        refreshCoordinator.refresh(RefreshKeys.STEAM_CHART, RefreshResource.STEAM_CHART, force = force) {
            // 排行主数据源：store 热销榜 100 条（fetchStoreTopSellers 内部落库真实条目）
            val store = runCatching { steam.fetchStoreTopSellers(forceRefresh = force) }.getOrDefault(emptyList())
            // 详情页"活跃排名"数据顺带预取（api 域，失败静默）
            runCatching { steam.getChartRanks(forceRefresh = force) }
            // 空结果按失败处理：让编排器记退避，避免接口不可用时每次冷启动都重打
            if (store.isEmpty()) throw IllegalStateException("Steam 热销榜返回空")
            store.size
        }
    }

    /** 前台轻量预取任务（onStop 取消）。 */
    @Volatile
    private var foregroundJob: Job? = null

    /**
     * 回到前台时的一轮**轻量**预取。
     * 每一步都经 [RefreshCoordinator]，命中软 TTL 时直接跳过 ——
     * 所以「频繁切前后台」不会变成「频繁打接口」（对齐 AniShelf 的分层廉价化思路：
     * 入口不做时间节流，靠下游判定把重复请求变成空操作）。
     */
    private suspend fun prefetchOnForeground() {
        if (!networkMonitor.isOnline) return
        // Steam 排行榜（30 分钟软 TTL）
        runCatching { prefetchSteamChart() }
        // Steam 自动绑定（6 小时窗口）
        runCatching { autoBindGames() }
    }

    /**
     * 诊断页「立即强制刷新」：绕过新鲜度与退避重跑应用级资源，
     * 并顺带击穿 Steam 仓储的 30 分钟榜单缓存。
     */
    private suspend fun forceRefreshAppResources() {
        prefetchSteamChart(force = true)
        autoBindGames(force = true)
    }

    /**
     * 自动同步 —— 补上一直无效的开关。
     *
     * 改造前 `webDavAutoSync` / `bangumiAutoSync` 在设置页有开关、有 DataStore 读写、
     * 有备份导入导出，但**全仓没有任何消费方**：用户打开后什么都不会发生。
     *
     * 语义（对齐 Kazumi 的 syncCollectibles = 下载合并 + 上传）：
     * 先下载并按 LWW 合并到本地，再把合并结果发布回远端；只上传会把别的设备的改动盖掉。
     * 冲突策略沿用现有 LWW / Bangumi 优先级设置，不新增。
     */
    private suspend fun runAutoSyncIfEnabled() {
        val settings = settingsDataStore.settings.first()
        if (!networkMonitor.isOnline) return

        if (settings.webDavAutoSync && settings.webDavUrl.isNotBlank()) {
            refreshCoordinator.refresh(RefreshKeys.AUTO_SYNC_WEBDAV, RefreshResource.AUTO_SYNC_WEBDAV) {
                val merged = syncManager.download(
                    settings.webDavUrl, settings.webDavUsername, settings.webDavPassword,
                )
                if (!merged.success) throw IllegalStateException(merged.message)
                val published = syncManager.upload(
                    settings.webDavUrl, settings.webDavUsername, settings.webDavPassword,
                )
                if (!published.success) throw IllegalStateException(published.message)
                true
            }
        }

        // Bangumi 自动同步还需「Bangumi 同步」总开关打开（BangumiSyncManager 内部同样会校验）
        if (settings.bangumiAutoSync && settings.bangumiSyncEnabled &&
            settings.bangumiAccessToken.isNotBlank()
        ) {
            refreshCoordinator.refresh(RefreshKeys.AUTO_SYNC_BANGUMI, RefreshResource.AUTO_SYNC_BANGUMI) {
                val result = bangumiSyncManager.syncOnce()
                if (!result.success) throw IllegalStateException(result.message)
                true
            }
        }
    }

    /** 阶段 D：为存量无 pinyinKey 的条目补齐拼音搜索键（幂等，仅跑一次）。 */
    private suspend fun backfillPinyinKeys() {
        val dao = database.subjectDao()
        val need = runCatching { dao.getAll() }.getOrDefault(emptyList())
            .filter { it.pinyinKey == null }
        if (need.isEmpty()) return
        for (s in need) {
            val key = PinyinSearch.pinyinKey(s.title, s.titleCN)
            if (key.isNotBlank()) {
                runCatching { dao.upsert(s.copy(pinyinKey = key)) }
            }
        }
    }

    private val bangumiClient by lazy { BangumiClient }

    val bangumiDataSource by lazy {
        BangumiDataSource(
            apiService = bangumiClient.apiService,
        )
    }

    val workRepository: WorkRepository by lazy {
        WorkRepository(database.workDao())
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(this)
    }

    // ===== AniList 数据源（需在 pluginManager 之前定义） =====

    val anilistClient: AniListClient by lazy { AniListClient() }

    val anilistDataSource: AniListDataSource by lazy {
        AniListDataSource(client = anilistClient)
    }

    // ===== 插件系统 =====

    val pluginManager: PluginManager by lazy {
        PluginManager().apply {
            // 注册主数据源 — Bangumi
            register(object : DataSourcePlugin {
                override val id = "bangumi"
                override val name = "Bangumi 番组计划"
                override val description = "中文动漫元数据，部分功能需 VPN 访问"
                override val capabilities = DataSourceCapabilities(
                    supportsCalendar = true,
                    supportsSubjectsByMonth = true,
                    supportsRatingDistribution = true,
                )
                override val dataSource = bangumiDataSource
            })
            // 注册补充数据源 — AniList（无需 VPN，Bangumi 无结果时自动兜底）
            register(object : DataSourcePlugin {
                override val id = "anilist"
                override val name = "AniList"
                override val description = "国际动漫数据库，无需 VPN"
                override val capabilities = DataSourceCapabilities(
                    supportsCalendar = false,
                    supportsSubjectsByMonth = false,
                    supportsRatingDistribution = false,
                )
                override val dataSource = anilistDataSource
            })
        }
    }

    /** 链式调度器 — 所有数据源请求经过此对象转发到对应插件。 */
    val dataSourceChain: DataSourceChain by lazy {
        DataSourceChain(
            plugins = pluginManager.plugins,
            subjectDao = database.subjectDao(),
            gameDataSourceRegistry = gameDataSourceRegistry,
            writeGateway = subjectWriteGateway,
        )
    }

    /** 通用游戏数据源注册表（补充查询通道：Steam / VNDB / AniList 等）。 */
    val gameDataSourceRegistry: GameDataSourceRegistry by lazy {
        GameDataSourceRegistry().apply {
            register(SteamGameDataSource())
            register(VndbGameDataSource())
            register(AniListGameDataSource())
            // 后续接入：register(RawgGameDataSource(...))、register(NeoDbGameDataSource(...))
        }
    }

    // ===== Repository =====

    val subjectRepository: SubjectRepository by lazy {
        SubjectRepository(
            subjectDao = database.subjectDao(),
            remoteDataSource = dataSourceChain,
            onAirRepository = onAirRepository,
            writeGateway = subjectWriteGateway,
        )
    }

    /** 每日放送静态数据源（阶段 A：精确放送时刻）。 */
    val onAirRepository: OnAirRepository by lazy {
        OnAirRepository(this)
    }

    /** 圣地巡礼（Anitabi）取景地标仓储（阶段 K）。 */
    val anitabiRepository: AnitabiRepository by lazy {
        AnitabiRepository(database.anitabiDao())
    }

    val collectionRepository: CollectionRepository by lazy {
        CollectionRepository(database.collectionDao())
    }

    /** Steam 补充数据仓储（游戏商业数据：价格/开发商/在线人数等）。 */
    val steamRepository: SteamRepository by lazy {
        SteamRepository(
            steamDao = database.steamDao(),
            steamId64Provider = {
                runBlocking(Dispatchers.IO) { settingsDataStore.settings.first().steamId64 }
                    .takeIf { it.isNotBlank() }
            },
            writeGateway = subjectWriteGateway,
        )
    }

    /** VNDB 补充数据仓储（视觉小说信息源：评分/开发者/时长/平台/语言/截图）。 */
    val vndbRepository: VndbRepository by lazy {
        VndbRepository(vndbDao = database.vndbDao())
    }

    /** AniList 补充数据仓储（不限制类型：动画/漫画等任意 bangumi 词条绑定补充信息）。 */
    val anilistRepository: AniListRepository by lazy {
        AniListRepository(anilistDao = database.anilistDao())
    }

    /** 剧集/章节仓储（每集播出状态与热力图）。 */
    val episodeRepository: EpisodeRepository by lazy {
        EpisodeRepository(
            episodeDao = database.episodeDao(),
            remoteDataSource = dataSourceChain,
        )
    }

    // ===== 权威评分 / 每集评分（阶段 0–1） =====

    /** 作品外部身份绑定（TMDb / IMDb / IGDB / RAWG / Discogs / MusicBrainz / Google Books / Open Library）。 */
    val externalIdRepository: ExternalIdRepository by lazy {
        ExternalIdRepository(database.externalIdDao())
    }

    /** 手动录入的权威机构成绩（Fami通 / Billboard / Oricon 等无 API 的机构）。 */
    val manualAwardRepository: ManualAwardRepository by lazy {
        ManualAwardRepository(database.manualAwardDao())
    }

    /** TMDb 数据仓储（详情 / 季 / 每集 / 海报候选）。 */
    val tmdbRepository: TmdbRepository by lazy {
        TmdbRepository(
            externalIdRepository = externalIdRepository,
            settingsProvider = {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) { settingsDataStore.settings.first() }
            },
        )
    }

    /** 权威评分聚合（多源并行 + 缓存）。 */
    val externalRatingRepository: ExternalRatingRepository by lazy {
        ExternalRatingRepository(
            externalIdDao = database.externalIdDao(),
            externalRatingDao = database.externalRatingDao(),
            steamDao = database.steamDao(),
            vndbDao = database.vndbDao(),
            anilistDao = database.anilistDao(),
            settingsProvider = {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) { settingsDataStore.settings.first() }
            },
        )
    }

    /** 每集评分仓储（TMDb 逐集 + 可选 IMDb 逐集）。 */
    val episodeRatingRepository: EpisodeRatingRepository by lazy {
        EpisodeRatingRepository(
            episodeDao = database.episodeDao(),
            externalRatingDao = database.externalRatingDao(),
            externalIdRepository = externalIdRepository,
            settingsProvider = {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) { settingsDataStore.settings.first() }
            },
        )
    }

    /** 作品元数据 DAO（本地 subjects 查询，发现页 Steam 标签用）。 */
    val subjectDao by lazy { database.subjectDao() }

    // 第 6 轮 §5/§6：「找条目」仓储与「评分月刊」仓储随各自模块一起删除。
    // 「找条目」的能力并进「历史排名」的筛选器（BrowseFilter + SubjectRepository），
    // 评分月刊连本地快照表一起移除（DB v28 → v29）。

    val searchHistoryDao by lazy { database.searchHistoryDao() }

    val personCollectionDao by lazy { database.personCollectionDao() }

    val bilibiliSyncItemDao by lazy { database.bilibiliSyncItemDao() }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(this)
    }

    /** 封面覆盖存储（阶段 E：用户更换封面）。 */
    val coverOverrideStore: com.otakup.niriko.data.settings.CoverOverrideStore by lazy {
        com.otakup.niriko.data.settings.CoverOverrideStore(this)
    }

    val backupManager: BackupManager by lazy {
        // 阶段 7：封面覆盖也随备份迁移（此前只存 DataStore，换设备即丢）
        BackupManager(database = database, coverOverrideStore = coverOverrideStore)
    }

    val workViewModelFactory: WorkViewModelFactory by lazy {
        WorkViewModelFactory(workRepository)
    }

    /**
     * /calendar 响应的共享内存缓存（第 5 轮 D26）。
     *
     * 追番日历（BroadcastFetcher）与发现页「当季热门」（SeasonalTrendingRepository）
     * 共用它，避免同一份数据被请求两次；接口失败时它还负责退回上次结果。
     */
    val calendarCache: com.otakup.niriko.data.remote.CalendarCache by lazy {
        com.otakup.niriko.data.remote.CalendarCache()
    }

    val broadcastFetcher: BroadcastFetcher by lazy {
        BroadcastFetcher(
            remoteDataSource = dataSourceChain,
            subjectRepository = subjectRepository,
            calendarCache = calendarCache,
        )
    }

    /**
     * 「当季热门」数据编排（第 5 轮 D26）。
     *
     * 语义是「正在放送」：/calendar（权威）+ 放宽窗口检索（兜底长连载）取并集。
     */
    val seasonalTrendingRepository: com.otakup.niriko.data.seasonal.SeasonalTrendingRepository by lazy {
        com.otakup.niriko.data.seasonal.SeasonalTrendingRepository(
            remoteDataSource = dataSourceChain,
            subjectRepository = subjectRepository,
            calendarCache = calendarCache,
        )
    }

    val seasonalFetcher: SeasonalFetcher by lazy {
        SeasonalFetcher(
            remoteDataSource = dataSourceChain,
            subjectRepository = subjectRepository,
        )
    }

    // ===== WebDAV 同步 =====

    val webDavClient: WebDavClient by lazy { WebDavClient() }

    val syncManager: SyncManager by lazy {
        SyncManager(
            database = database,
            webDavClient = webDavClient,
        )
    }

    // ===== Bangumi 账号同步 =====

    /** .nirikotheme 主题包管理（导入/导出/应用载荷）。 */
    val themePackManager: com.otakup.niriko.data.themepack.ThemePackManager by lazy {
        com.otakup.niriko.data.themepack.ThemePackManager(this)
    }

    val bangumiSyncManager: BangumiSyncManager by lazy {
        BangumiSyncManager(
            authApi = bangumiClient.authApiService,
            database = database,
            subjectDao = database.subjectDao(),
            collectionDao = database.collectionDao(),
            settings = settingsDataStore,
        )
    }
}
