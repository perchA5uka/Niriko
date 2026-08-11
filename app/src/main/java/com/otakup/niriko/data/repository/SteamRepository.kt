package com.otakup.niriko.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.otakup.niriko.data.local.NirikoDatabase
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
     * 仅处理 type=GAME 且尚未绑定的条目；每个条目并发调 storesearch，
     * 标题匹配置信度 ≥ [SteamTitleMatcher.MIN_CONFIDENCE] 才绑定。
     * 任何失败静默跳过（不影响搜索主流程）。
     *
     * @return 新绑定成功的条目（subjectId → appId 已写入 steam_bindings）
     */
    suspend fun matchAndBind(subjects: List<SubjectEntity>): List<SteamBindingEntity> {
        val games = subjects.filter { it.type == SubjectType.GAME }
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
                                val title = subject.titleCN?.takeIf { it.isNotBlank() } ?: subject.title
                                val search = apiService.searchApps(term = title, count = 8)
                                val match = SteamTitleMatcher.bestMatch(title, search.items)
                                if (match == null) null
                                else subject to match
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

    /**
     * 获取全量活跃玩家排行（GetMostPlayedGames），appid → 排名信息。
     * 实测无需 key；30 分钟内存缓存。失败返回空 Map，不抛异常。
     */
    suspend fun getChartRanks(): Map<Int, SteamChartEntry> {
        val cached = chartCache
        if (cached != null && System.currentTimeMillis() - cached.second < ACHIEVEMENT_CACHE_TTL_MS) {
            return cached.first
        }
        val fresh = withContext(Dispatchers.IO) {
            runCatching {
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
            }.getOrNull() ?: emptyMap()
        }
        if (fresh.isNotEmpty()) {
            chartCache = fresh to System.currentTimeMillis()
        }
        return fresh
    }

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
