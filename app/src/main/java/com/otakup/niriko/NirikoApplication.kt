package com.otakup.niriko

import android.app.Application
import com.otakup.niriko.data.backup.BackupManager
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.remote.BangumiClient
import com.otakup.niriko.data.remote.BroadcastFetcher
import com.otakup.niriko.data.remote.SeasonalFetcher
import com.otakup.niriko.data.remote.anilist.AniListClient
import com.otakup.niriko.data.remote.anilist.AniListDataSource
import com.otakup.niriko.data.remote.bangumi.BangumiDataSource
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.NetworkMonitor
import com.otakup.niriko.data.repository.SteamRepository
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.data.repository.WorkRepository
import com.otakup.niriko.data.settings.SettingsDataStore
import com.otakup.niriko.data.sync.SyncManager
import com.otakup.niriko.data.sync.WebDavClient
import com.otakup.niriko.data.sync.bangumi.BangumiSyncManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
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
import com.otakup.niriko.viewmodel.WorkViewModelFactory

/**
 * 应用入口：持有 Database / Repository / PluginManager，供 ViewModel 工厂使用。
 */
class NirikoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 启动时恢复 Bangumi 端点（官方/反代），在任何 API 请求前生效
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val settings = settingsDataStore.settings.first()
            BangumiClient.setEndpoint(settings.bangumiEndpoint)
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
        )
    }

    // ===== Repository =====

    val subjectRepository: SubjectRepository by lazy {
        SubjectRepository(
            subjectDao = database.subjectDao(),
            remoteDataSource = dataSourceChain,
        )
    }

    val collectionRepository: CollectionRepository by lazy {
        CollectionRepository(database.collectionDao())
    }

    /** Steam 补充数据仓储（游戏商业数据：价格/开发商/在线人数等）。 */
    val steamRepository: SteamRepository by lazy {
        SteamRepository(steamDao = database.steamDao())
    }

    val searchHistoryDao by lazy { database.searchHistoryDao() }

    val personCollectionDao by lazy { database.personCollectionDao() }

    val bilibiliSyncItemDao by lazy { database.bilibiliSyncItemDao() }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(this)
    }

    val backupManager: BackupManager by lazy {
        BackupManager(database = database)
    }

    val workViewModelFactory: WorkViewModelFactory by lazy {
        WorkViewModelFactory(workRepository)
    }

    val broadcastFetcher: BroadcastFetcher by lazy {
        BroadcastFetcher(
            remoteDataSource = dataSourceChain,
            subjectRepository = subjectRepository,
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
