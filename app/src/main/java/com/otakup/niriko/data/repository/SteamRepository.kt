package com.otakup.niriko.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.local.SubjectWriteGateway
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SteamDao
import com.otakup.niriko.data.local.dao.SteamLibraryItemDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SteamBindingEntity
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.remote.game.GameItemMapper
import com.otakup.niriko.data.remote.steam.SteamApiClient
import com.otakup.niriko.data.remote.steam.SteamApiService
import com.otakup.niriko.data.remote.steam.SteamAchievements
import com.otakup.niriko.data.remote.steam.SteamAchievementItem
import com.otakup.niriko.data.remote.steam.SteamChartEntry
import com.otakup.niriko.data.remote.steam.SteamTitleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "SteamRepo"

/**
 * Steam 补充数据仓储。
 *
 * 职责：
 * - 匹配：Bangumi GAME 条目 → Steam appid（storesearch + 标题匹配），绑定落库
 * - 补充：appdetails + 当前游玩人数 → SteamGameEntity，写入 steam_games 表
 * - 读取：按 subjectId 返回 Steam 扩展数据（详情页/卡片展示用）
 *
 * 全部方法异常保护：Steam 是补充数据源，任何失败静默返回，不影响主流程。
 * 当前游玩人数 30 分钟缓存（由 [PLAYER_CACHE_TTL_MS] 控制）。
 */
class SteamRepository(
    private val steamDao: SteamDao,
    private val apiService: SteamApiService = SteamApiClient.apiService,
    /** 当前登录用户的 SteamID64（成就等用户维度接口用；未登录返回 null）。 */
    private val steamId64Provider: (() -> String?)? = null,
    /** subjects 写入网关（store 域热销榜落库真实条目用；未注入则降级不落库，仅返回数据）。 */
    private val writeGateway: SubjectWriteGateway? = null,
) {

    companion object {
        /** 当前游玩人数缓存有效期（30 分钟）。 */
        const val PLAYER_CACHE_TTL_MS = 30 * 60 * 1000L

        /** 成就数据缓存有效期（30 分钟；成就频繁刷新意义不大且限流敏感）。 */
        const val ACHIEVEMENT_CACHE_TTL_MS = 30 * 60 * 1000L
    }

    /** 成就内存缓存：appId → (数据, 时间戳)。 */
    private val achievementCache = mutableMapOf<Int, Pair<SteamAchievements, Long>>()

    /** 活跃排行缓存：全量排行 → (数据, 时间戳)。 */
    private var chartCache: Pair<Map<Int, SteamChartEntry>, Long>? = null

    /**
     * store 热销榜原始列表缓存（30 分钟）。
     *
     * 改造前 [fetchStoreTopSellers] **完全没有缓存**：启动预取拉一次，
     * 用户切到发现页「Steam」标签时又拉一次（同一份榜单，两次网络请求 + 两次落库）。
     */
    private var storeTopSellersCache: Pair<List<StoreChartItem>, Long>? = null

    /** store search json=1 响应解析用（宽松模式，未知字段忽略）。 */
    private val chartJson = Json { ignoreUnknownKeys = true }

    // ==================== 匹配与绑定 ====================

    /**
     * 解除绑定：删除 subjectId 的 steam_bindings 与 steam_games。
     * 供详情页「解除绑定」操作（错绑数据手动解绑后重新匹配）。
     * 异常保护（Steam 是补充数据源）。
     */
    suspend fun unbind(subjectId: Long) {
        withContext(Dispatchers.IO) {
            runCatching {
                steamDao.deleteBindingBySubjectId(subjectId)
                steamDao.deleteGameBySubjectId(subjectId)
            }
        }
    }

    /**
     * 对 GAME 条目执行 Steam 匹配并落库。
     *
     * 仅处理 type=GAME、非占位（sourceId=="bangumi" 的正式词条）且尚未绑定的条目；
     * 每个条目并发调 storesearch，标题匹配置信度 ≥ [SteamTitleMatcher.MIN_CONFIDENCE] 才绑定。
     * 匹配时同时用 title 与 titleCN 两个标题（原名 vs 中文名）搜商店并取最佳，
     * 解决"喵斯快跑"↔"Muse Dash"这类跨语言同名游戏绑定失败的问题。
     * 任何失败静默跳过（不影响搜索主流程）。
     *
     * @return 新绑定成功的条目（subjectId → appId 已写入 steam_bindings）
     */
    suspend fun matchAndBind(subjects: List<SubjectEntity>): List<SteamBindingEntity> {
        // 排除占位条目（sourceId=="steam" 的独立作品无需再匹配 bangumi 词条）
        val games = subjects.filter { it.type == SubjectType.GAME && !it.isSteamPlaceholder }
        if (games.isEmpty()) return emptyList()

        val alreadyBound = steamDao.getBindingsBySubjectIds(games.map { it.subjectId })
            .map { it.subjectId }
            .toSet()
        val toMatch = games.filter { it.subjectId !in alreadyBound }
        if (toMatch.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        // 分块并发（每批 5 个），避免收藏页/搜索页对大量未绑定 GAME 一次性打满 Steam 商店接口
        val results = withContext(Dispatchers.IO) {
            toMatch.chunked(5).flatMap { batch ->
                coroutineScope {
                    batch.map { subject ->
                        async {
                            try {
                                // 双标题都搜：title（原名，如 "Muse Dash"）+ titleCN（中文名，如 "喵斯快跑"）
                                // 评分时对每个候选用 bestMatchMulti（查询侧全标题集取最大置信度），
                                // 解决中英文无字符重叠但另一方精确命中的跨语言场景
                                val titles = listOfNotNull(subject.title, subject.titleCN)
                                    .filter { it.isNotBlank() }.distinct()
                                if (titles.isEmpty()) return@async null
                                var best: SteamTitleMatcher.MatchResult? = null
                                for (t in titles) {
                                    val search = runCatching { apiService.searchApps(term = t, count = 8) }
                                        .getOrElse { continue }
                                    val m = SteamTitleMatcher.bestMatchMulti(titles, search.items)
                                    if (m != null && (best == null || m.confidence > best.confidence)) best = m
                                }
                                if (best == null) null else subject to best
                            } catch (e: Exception) {
                                Log.w(TAG, "match failed for ${subject.subjectId} ${subject.title}", e)
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            }
        }

        val bindings = results.map { (subject, match) ->
            SteamBindingEntity(
                subjectId = subject.subjectId,
                steamAppId = match.appId,
                matchMethod = "AUTO",
                confidence = match.confidence,
                createTime = now,
            )
        }
        // 注意：绑定行不写 lastUpdated（保持 0），
        // 让 getSupplement 正确识别"尚未拉取详情"并在详情页打开时立即补全
        // （避免稀疏行被当成 30 分钟新鲜缓存，详情页长时间只显示搜索 stub）。
        val gamesToPersist = results.map { (subject, match) ->
            SteamGameEntity(
                subjectId = subject.subjectId,
                appId = match.appId,
                name = match.steamName,
                priceCents = match.priceCents,
                currency = match.currency,
                headerImage = match.tinyImage,
                lastUpdated = 0L,
            )
        }
        if (bindings.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                steamDao.upsertBindings(bindings)
                steamDao.upsertGames(gamesToPersist)
            }
            Log.i(TAG, "Bound ${bindings.size} games to Steam")
        }
        return bindings
    }

    // ==================== 详情补充 ====================

    /**
     * 获取某条目的 Steam 扩展数据（无绑定返回 null）。
     *
     * 缓存策略：steam_games 存在且 lastUpdated 在 [PLAYER_CACHE_TTL_MS] 内 → 直接返回；
     * 否则拉取 appdetails + 当前游玩人数并落库后返回（失败时返回旧缓存或 null）。
     */
    suspend fun getSupplement(subjectId: Long): SteamGameEntity? {
        val binding = steamDao.getBindingBySubjectId(subjectId) ?: return null
        val cached = steamDao.getGame(subjectId)
        val fresh = cached?.takeIf {
            System.currentTimeMillis() - it.lastUpdated < PLAYER_CACHE_TTL_MS
        }
        if (fresh != null) return fresh
        return fetchDetailAndPersist(subjectId, binding.steamAppId) ?: cached
    }

    /** 最近一次官方排行（GetMostPlayedGames）拉取失败原因；null = 最近一次成功或未尝试。诊断用。 */
    @Volatile
    var lastChartError: String? = null
        private set

    /**
     * 获取全量活跃玩家排行（GetMostPlayedGames），appid → 排名信息。
     * 实测无需 key；30 分钟内存缓存。失败返回空 Map，不抛异常。
     *
     * @param forceRefresh 为 true 时绕过 30 分钟缓存强制拉取（用户主动下拉刷新时用）
     */
    suspend fun getChartRanks(forceRefresh: Boolean = false): Map<Int, SteamChartEntry> {
        val cached = chartCache
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.second < ACHIEVEMENT_CACHE_TTL_MS) {
            return cached.first
        }
        val fresh = withContext(Dispatchers.IO) {
            try {
                apiService.mostPlayedGames(
                    url = SteamApiClient.MOST_PLAYED_GAMES_URL,
                    // 排行接口实测无需 key；不附加避免 key 进入日志
                ).response?.ranks.orEmpty()
                    .associateBy { it.appid }
                    .mapValues { (_, r) ->
                        SteamChartEntry(
                            rank = r.rank,
                            lastWeekRank = r.lastWeekRank,
                            peakInGame = r.peakInGame,
                        )
                    }
            } catch (e: Exception) {
                // 诊断：区分 api 域不可达 / 超时 / 接口异常，便于定位"排行加载不出"根因
                val reason = when (e) {
                    is java.net.UnknownHostException -> "网络不可达（api.steampowered.com）"
                    is java.net.SocketTimeoutException -> "连接超时（api.steampowered.com）"
                    else -> "${e.javaClass.simpleName}: ${e.message ?: ""}"
                }
                lastChartError = reason
                Log.w(TAG, "getChartRanks failed: $reason", e)
                emptyMap()
            }
        }
        if (fresh.isNotEmpty()) {
            chartCache = fresh to System.currentTimeMillis()
            lastChartError = null
            return fresh
        }
        // 拉取"成功"但响应为空 ranks（接口返回空/需 key 等）：同样记录，便于诊断
        if (lastChartError == null) {
            lastChartError = "接口返回空数据"
            Log.w(TAG, "getChartRanks: 接口返回空 ranks")
        }

        // ===== 主源失败 → store 域降级（热销榜，无需 key、store 域一般可达） =====
        // fetchStoreTopSellers 内部已把真实条目落库（sourceKey="steam:{appid}"），
        // 排行榜组装时 localByAppId 直接命中展示，无需 appdetails 二次补全
        val fallback = withContext(Dispatchers.IO) {
            runCatching { fetchStoreTopSellers() }.getOrDefault(emptyList())
        }
        if (fallback.isNotEmpty()) {
            val fallbackRanks = fallback.withIndex().associate { (i, item) ->
                item.appId to SteamChartEntry(rank = i + 1)
            }
            chartCache = fallbackRanks to System.currentTimeMillis()
            Log.w(TAG, "getChartRanks: 主源不可用，已降级 store 域热销榜 ${fallbackRanks.size} 条（${lastChartError}）")
            return fallbackRanks
        }

        // 主源与降级均失败：回退旧缓存（若有），避免界面一片占位；从未成功过则返回空（UI 显示错误态）
        return cached?.first ?: emptyMap()
    }

    /** 排行榜是否曾成功拉取过（有缓存）。供 UI 区分"请求失败"与"正常但无数据"。 */
    fun hasChartCache(): Boolean = chartCache != null

    /**
     * 获取某游戏的活跃玩家排名（GetMostPlayedGames）。
     * 未上榜 / 失败返回 null，不抛异常。
     */
    suspend fun getChartRank(appId: Int): SteamChartEntry? = getChartRanks()[appId]

    /**
     * 获取某条目的成就进度（GetPlayerAchievements + GetSchemaForGame 合并）。
     *
     * 前置条件：已绑定 + 已配置 API key + 已登录（steamId64）。
     * 隐私限制（profile Game details 未公开）或失败时返回 null，不抛异常。
     * 30 分钟内存缓存（[ACHIEVEMENT_CACHE_TTL_MS]），避免高频接口触发限流。
     */
    suspend fun getAchievements(subjectId: Long): SteamAchievements? {
        val binding = steamDao.getBindingBySubjectId(subjectId) ?: return null
        val appId = binding.steamAppId

        // 缓存命中（30 分钟内）
        val cached = achievementCache[appId]
        if (cached != null && System.currentTimeMillis() - cached.second < ACHIEVEMENT_CACHE_TTL_MS) {
            return cached.first
        }

        val key = SteamApiClient.currentApiKey() ?: return null
        val steamId = steamId64Provider?.invoke()?.takeIf { it.isNotBlank() } ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val progress = apiService.playerAchievements(
                    url = SteamApiClient.PLAYER_ACHIEVEMENTS_URL,
                    key = key,
                    steamId = steamId,
                    appId = appId,
                ).playerstats
                // 隐私限制 / 无该游戏成就 → 空数据，不缓存（用户可能随后公开）
                val achievements = progress?.achievements
                if (progress?.success != true || achievements.isNullOrEmpty()) {
                    return@withContext null
                }

                // 成就定义（展示名/描述/图标；失败时降级用 API 名）
                val definitions = runCatching {
                    apiService.schemaForGame(
                        url = SteamApiClient.SCHEMA_FOR_GAME_URL,
                        key = key,
                        appId = appId,
                    ).game?.availableGameStats?.achievements.orEmpty()
                        .associateBy { it.name }
                }.getOrDefault(emptyMap())

                val items = achievements.map { a ->
                    val def = definitions[a.apiname]
                    SteamAchievementItem(
                        apiName = a.apiname ?: "",
                        name = def?.displayName ?: a.apiname ?: "",
                        description = def?.description,
                        achieved = a.achieved,
                        unlockTime = a.unlocktime.takeIf { it > 0 },
                        icon = def?.icon,
                    )
                }
                val result = SteamAchievements(
                    unlocked = items.count { it.achieved },
                    total = items.size,
                    items = items,
                )
                achievementCache[appId] = result to System.currentTimeMillis()
                result
            } catch (e: Exception) {
                Log.w(TAG, "getAchievements failed for appId=$appId", e)
                null
            }
        }
    }

    /**
     * 拉取 appdetails + 当前游玩人数并落库（详情页刷新用）。
     * 返回持久化后的 SteamGameEntity；失败返回 null。
     */
    suspend fun fetchDetailAndPersist(subjectId: Long, appId: Int): SteamGameEntity? {
        return withContext(Dispatchers.IO) {
            try {
                val details = apiService.appDetails(appIds = appId.toString())
                val wrapper = details[appId.toString()] ?: return@withContext null
                val data = wrapper.data ?: return@withContext null

                val players = try {
                    apiService.currentPlayers(
                        url = SteamApiClient.CURRENT_PLAYERS_URL,
                        appId = appId,
                        // 当前公开接口无需 key；为避免 key 进入 URL 被日志/网络记录，
                        // 不在此附加（apiKeyProvider 仅作未来扩展预留）
                    ).response?.playerCount
                } catch (e: Exception) {
                    Log.w(TAG, "currentPlayers failed for appId=$appId", e)
                    null
                }

                val game = SteamGameEntity(
                    subjectId = subjectId,
                    appId = data.steamAppId ?: appId,
                    name = data.name ?: "",
                    shortDescription = data.shortDescription,
                    developers = data.developers,
                    publishers = data.publishers,
                    priceCents = data.priceOverview?.final ?: data.priceOverview?.initial
                        ?: if (data.isFree) 0 else null,
                    currency = data.priceOverview?.currency,
                    metacriticScore = data.metacritic?.score,
                    // 失败/无数据时保留本地旧玩家数，避免覆盖成 null
                    currentPlayers = players ?: steamDao.getGame(subjectId)?.currentPlayers,
                    steamTags = data.genres.mapNotNull { it.description },
                    screenshots = data.screenshots.mapNotNull { it.pathFull },
                    headerImage = data.headerImage,
                    releaseDate = data.releaseDate?.date,
                    lastUpdated = System.currentTimeMillis(),
                )
                steamDao.upsertGame(game)
                game
            } catch (e: Exception) {
                Log.w(TAG, "fetchDetail failed for subjectId=$subjectId appId=$appId", e)
                null
            }
        }
    }

    /** 按 subjectId 列表批量读取 Steam 扩展数据（卡片展示用，走缓存不触发网络）。 */
    suspend fun getSupplements(subjectIds: List<Long>): Map<Long, SteamGameEntity> {
        if (subjectIds.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            steamDao.getGamesBySubjectIds(subjectIds).associateBy { it.subjectId }
        }
    }

    // ==================== 查询 ====================

    /** 榜单条目元数据（appdetails 拉取结果，供卡片直接展示）。 */
    data class ChartGameInfo(
        val name: String,
        val headerImage: String? = null,
        val priceCents: Int? = null,
        val currency: String? = null,
        val shortDescription: String? = null,
    )

    /**
     * 按 appid 反查已绑定的 Bangumi 词条（排行榜优先展示正确词条用）。
     * 收藏库/详情页已正确绑定时，排行榜条目应展示该词条而非本地残留的 steam 占位。
     * 返回 null 表示无绑定（或绑定向自身占位 PLACEHOLDER）。
     */
    suspend fun getBoundSubjectIdByAppId(appId: Int): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val binding = steamDao.getBindingByAppId(appId) ?: return@withContext null
            binding.subjectId.takeIf { it > 0 && binding.matchMethod != "PLACEHOLDER" }
        }.getOrNull()
    }

    /**
     * 批量按 appid 反查已绑定的 Bangumi 词条（发现页 Steam 榜单组装用）。
     *
     * 改造前是 `storeAppIds.mapNotNull { getBoundSubjectIdByAppId(it) }`：一次**串行** 100 次数据库查询。
     * 现在一次 `IN (:appIds)` 查询。
     */
    suspend fun getBoundSubjectIdsByAppIds(appIds: List<Int>): Map<Int, Long> = withContext(Dispatchers.IO) {
        if (appIds.isEmpty()) return@withContext emptyMap()
        runCatching {
            steamDao.getBindingsByAppIds(appIds.distinct())
                .mapNotNull { binding ->
                    binding.subjectId
                        .takeIf { it > 0 && binding.matchMethod != "PLACEHOLDER" }
                        ?.let { binding.steamAppId to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    /**
     * 榜单条目元数据兜底：按 appid 批量拉取 appdetails（标题/封面/价格等）并落库。
     * 用于发现页 Steam 榜单——GetMostPlayedGames 只返回 appid，
     * 标题/封面/价格必须由 appdetails 补齐（分块并发，避免一次打满接口）。
     *
     * @return appid → 完整元数据（拉取成功且 name 非空才返回）；失败静默跳过
     */
    suspend fun fetchChartTopGameNames(appIds: List<Int>): Map<Int, ChartGameInfo> {
        if (appIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Int, ChartGameInfo>()
        withContext(Dispatchers.IO) {
            // 分块并发调小（每批 10）+ 批次间隔：Top 100 一次性 20/批连续请求易触发
            // Steam appdetails 限流导致榜单条目元数据缺失（排行缺项），分批放缓显著降低限流概率
            appIds.distinct().chunked(10).forEach { batch ->
                val names = runCatching {
                    apiService.appDetails(appIds = batch.joinToString(","))
                }.getOrDefault(emptyMap())
                val sparseRows = mutableListOf<SteamGameEntity>()
                batch.forEach { appId ->
                    val data = names[appId.toString()]?.data ?: return@forEach
                    val name = data.name
                    if (name.isNullOrBlank()) return@forEach
                    val info = ChartGameInfo(
                        name = name,
                        headerImage = data.headerImage,
                        priceCents = data.priceOverview?.final ?: data.priceOverview?.initial
                            ?: if (data.isFree) 0 else null,
                        currency = data.priceOverview?.currency,
                        shortDescription = data.shortDescription,
                    )
                    result[appId] = info
                    // 顺带落库 steam_games 稀疏行（详情页打开时会被完整详情刷新）
                    sparseRows += SteamGameEntity(
                        subjectId = GameItemMapper.deriveSubjectId("steam", appId.toString()),
                        appId = appId,
                        name = name,
                        headerImage = data.headerImage,
                        shortDescription = data.shortDescription,
                        priceCents = info.priceCents,
                        currency = info.currency,
                        lastUpdated = 0L,
                    )
                }
                // 改造前逐条 upsertGame（每批 10 次独立事务）；改为整批一次事务
                if (sparseRows.isNotEmpty()) {
                    runCatching { steamDao.upsertGames(sparseRows) }
                }
                if (batch.size >= 10) {
                    // 批次间短间隔（约 120ms），避免连续高压请求触发限流
                    kotlinx.coroutines.delay(120)
                }
            }
        }
        return result
    }

    /**
     * 榜单元数据（标题/封面）：appdetails 为主，不足时用 store search JSON 兜底补全。
     *
     * 用途：GetMostPlayedGames 返回的 appid 列表需 appdetails 补标题/封面；appdetails 在
     * 批量请求下可能被限流/截断导致只补全部分（此前排行榜只显示元数据就绪的少数条目的根因）。
     * store search（json=1，一次请求 100 条 name/logo）作为兜底，保证排行榜不因元数据缺失缺项。
     */
    suspend fun fetchChartTopGameNamesWithFallback(appIds: List<Int>): Map<Int, ChartGameInfo> {
        val fromDetails = fetchChartTopGameNames(appIds)
        if (fromDetails.size >= appIds.size) return fromDetails
        val missing = appIds.filter { it !in fromDetails }
        val storeItems = runCatching { fetchStoreTopSellers() }.getOrDefault(emptyList())
            .associateBy { it.appId }
        val filled = missing.mapNotNull { appId ->
            storeItems[appId]?.let { item ->
                appId to ChartGameInfo(name = item.title, headerImage = item.coverUrl)
            }
        }.toMap()
        if (filled.isNotEmpty()) {
            Log.i(TAG, "fetchChartTopGameNames: appdetails 补全 ${fromDetails.size}/${appIds.size}，store search JSON 再补全 ${filled.size}")
            return fromDetails + filled
        }
        return fromDetails
    }

    /**
     * store 域降级排行源：Steam 商店热销榜（filter=topsellers）。
     *
     * 用途：api.steampowered.com 的 GetMostPlayedGames 不可达（被墙/超时）时，
     * 从 store 域（一般可达）拉取"热门游戏"前 100，保证 Steam 排行可加载。
     * 解析搜索结果 HTML 行（data-ds-appid / 标题 / 封面），结果直接落库真实
     * subjects 条目（sourceKey="steam:{appid}"，同 id 覆盖旧占位），供排行榜直接展示。
     * 失败返回空列表（不抛异常）。
     */
    suspend fun fetchStoreTopSellers(forceRefresh: Boolean = false): List<StoreChartItem> {
        if (!forceRefresh) {
            val cached = storeTopSellersCache
            if (cached != null && System.currentTimeMillis() - cached.second < PLAYER_CACHE_TTL_MS) {
                return cached.first
            }
        }
        // 第一页 US 区 topsellers：cc=US（非 CN）绕过中国区锁区过滤，取完整前 100；
        // l=schinese 保留中文标题
        val merged = buildList {
            val page1 = fetchStoreTopSellersPage(start = 0)
            addAll(page1)
            // 极端兜底：单页仍不足 100 时拉第二页合并去重
            if (page1.size < 100) {
                addAll(fetchStoreTopSellersPage(start = 100))
            }
        }.distinctBy { it.appId }
        if (merged.isEmpty()) return emptyList()
        storeTopSellersCache = merged to System.currentTimeMillis()
        // 改造前：100 条榜单**逐条** upsert = 100 个独立事务 = 100 次 Room 失效通知；
        // 而作品库列表同时观察 subjects 表 → 打开一次 Steam 标签就把作品库全量重算 100 次。
        // 现在：一次批量单事务 + 内容 diff（重复刷新同一份榜单时零写入）。
        val gateway = writeGateway
        if (gateway != null) {
            runCatching {
                gateway.upsertAll(
                    merged.map { item ->
                        SubjectEntity(
                            subjectId = GameItemMapper.deriveSubjectId("steam", item.appId.toString()),
                            title = item.title,
                            titleCN = item.title,
                            type = SubjectType.GAME,
                            coverUrl = item.coverUrl,
                            sourceId = "steam",
                            sourceKey = "steam:${item.appId}",
                        )
                    }
                )
            }
        }
        return merged
    }

    /** 拉取一页 topsellers 结果并解析（start 为偏移）。 */
    private suspend fun fetchStoreTopSellersPage(start: Int): List<StoreChartItem> {
        val html = SteamApiClient.getRawHtml(
            url = "https://store.steampowered.com/search/results/?json=1&filter=topsellers&category1=998&start=$start&count=100&cc=US&l=schinese",
            userAgent = true,
        ) ?: return emptyList()
        val items = parseStoreSearchResults(html)
        // 诊断：json=1 响应为纯 JSON，解析条数即有效条目数
        Log.i(TAG, "fetchStoreTopSellers[$start]: 解析到 ${items.size} 条")
        return items
    }

    /**
     * 解析 store search 响应（json=1 时返回**纯 JSON** 而非 HTML）：
     * `{"desc":"","items":[{"name":"...","logo":"https://.../apps/{appid}/...capsule_sm_120...jpg"}, ...]}`
     * appid 无独立字段，从 logo URL 的 `/apps/{appid}/` 段提取；无 logo 的条目跳过。
     */
    private fun parseStoreSearchResults(raw: String): List<StoreChartItem> {
        return runCatching {
            val root = chartJson.parseToJsonElement(raw).jsonObject
            val items = root["items"]?.jsonArray ?: return@runCatching emptyList()
            items.mapNotNull { el ->
                val obj = el.jsonObject
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val logo = obj["logo"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val appId = Regex("""/apps/(\d+)/""").find(logo)?.groupValues?.get(1)
                    ?.toIntOrNull() ?: return@mapNotNull null
                StoreChartItem(appId = appId, title = name, coverUrl = logo)
            }
        }.getOrDefault(emptyList())
    }

    /** 查询绑定关系（无则 null）。 */
    suspend fun getBinding(subjectId: Long): SteamBindingEntity? =
        steamDao.getBindingBySubjectId(subjectId)

    /** 手动绑定（设置/详情页未来扩展用）。 */
    suspend fun bindManually(subjectId: Long, appId: Int): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                steamDao.upsertBinding(
                    SteamBindingEntity(
                        subjectId = subjectId,
                        steamAppId = appId,
                        matchMethod = "MANUAL",
                        confidence = 1f,
                        createTime = System.currentTimeMillis(),
                    )
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "bindManually failed", e)
            false
        }
    }

    // ==================== 占位条目升级 ====================

    /**
     * 占位条目（subjectId = -appId）升级为正式 Bangumi 词条。
     *
     * 原子事务内完成：
     * 1. 确保目标 Bangumi subjectId 存在（缺则创建占位 subject）；
     * 2. 迁移 collections：占位收藏 → 正式 subjectId（新 id 已有收藏则删占位收藏，避免重复）；
     * 3. 迁移 steam_bindings / steam_games：subjectId -appId → 正式 id（新 id 已有则先删旧）；
     * 4. 删除占位 SubjectEntity；
     * 5. 更新 steam_library_items 快照（bgmSubjectId / isPlaceholder=0）。
     *
     * @param appId Steam appid（占位 id = -appId）
     * @param newSubjectId 匹配到的正式 Bangumi subjectId
     * @return 升级是否成功（false = 失败，事务回滚）
     */
    suspend fun upgradePlaceholder(
        appId: Int,
        newSubjectId: Long,
        database: NirikoDatabase,
        subjectDao: SubjectDao,
        collectionDao: CollectionDao,
        steamLibraryItemDao: SteamLibraryItemDao,
    ): Boolean {
        if (newSubjectId <= 0) return false
        // 占位条目 id：正数 sourceKey 体系（deriveSubjectId），与创建时一致
        val oldSubjectId = GameItemMapper.deriveSubjectId("steam", appId.toString())
        return try {
            database.withTransaction {
                // 1) 目标词条必须存在
                if (subjectDao.getById(newSubjectId) == null) {
                    // 从占位条目复制元数据（详情页打开时会被真实数据刷新）
                    val old = subjectDao.getById(oldSubjectId)
                    subjectDao.upsert(
                        (old ?: SubjectEntity(
                            subjectId = newSubjectId,
                            title = "App $appId",
                            type = SubjectType.GAME,
                        )).copy(
                            subjectId = newSubjectId,
                            sourceId = "bangumi",
                            lastSyncTime = System.currentTimeMillis(),
                        )
                    )
                }

                // 2) collections 迁移（新 id 已有收藏则不重复）
                if (collectionDao.getBySubjectId(newSubjectId) == null) {
                    collectionDao.migrateSubjectId(oldSubjectId, newSubjectId)
                } else {
                    collectionDao.deleteBySubjectId(oldSubjectId)
                }

                // 3) steam_bindings / steam_games 迁移（新 id 已有则先删旧）
                if (steamDao.getBindingBySubjectId(newSubjectId) != null) {
                    steamDao.deleteBindingBySubjectId(newSubjectId)
                }
                steamDao.migrateBindingSubjectId(oldSubjectId, newSubjectId)
                if (steamDao.getGame(newSubjectId) != null) {
                    steamDao.deleteGameBySubjectId(newSubjectId)
                }
                steamDao.migrateGameSubjectId(oldSubjectId, newSubjectId)

                // 4) 删除占位 subject（外键 CASCADE 会清掉残留关联）
                subjectDao.deleteById(oldSubjectId)

                // 5) 快照标记升级
                steamLibraryItemDao.upgradeBinding(appId, newSubjectId)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "upgradePlaceholder failed appId=$appId → $newSubjectId", e)
            false
        }
    }
}

/** store 域降级排行条目（商店热销榜解析结果：appid + 标题 + 封面）。 */
data class StoreChartItem(
    val appId: Int,
    val title: String,
    val coverUrl: String?,
)
