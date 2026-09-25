package com.otakup.niriko.plugin

import android.util.Log
import com.otakup.niriko.data.local.SubjectWriteGateway
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.AllPluginsFailedException
import com.otakup.niriko.data.remote.CalendarDaySchedule
import com.otakup.niriko.data.remote.CharacterDetailInfo
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.PersonDetailInfo
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.data.remote.PluginFailure
import com.otakup.niriko.data.remote.SubjectRelationInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.data.remote.game.GameDataSourceRegistry
import com.otakup.niriko.data.remote.game.GameItemMapper
import com.otakup.niriko.util.PinyinSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "DataSourceChain"

/**
 * 链式调度器 — 实现 SubjectRemoteDataSource。
 *
 * 根据插件的 capabilities 和能力自动调度：
 * - search/searchWithTotal: 按优先级顺序尝试每个插件，首个非空返回
 * - getDetail/characters/staff/episodes: 优先从 Room 缓存获取 sourceId 精确路由
 * - getCalendar/SubjectsByMonth/RatingDistribution: 仅调用有能力的插件
 * - 全部方法均有异常保护，单个插件故障不影响整体
 */
class DataSourceChain(
    private val plugins: List<DataSourcePlugin>,
    private val subjectDao: SubjectDao,
    /** 通用游戏数据源注册表（补充查询通道）。非空时 getDetail 对 game 源条目尝试元数据刷新。 */
    private val gameDataSourceRegistry: GameDataSourceRegistry? = null,
    /** subjects 写入网关（批量单事务 + 内容 diff）。未注入时退化为直写 DAO。 */
    private val writeGateway: SubjectWriteGateway? = null,
) : SubjectRemoteDataSource {

    /** 落库前补齐拼音搜索键（阶段 D）。 */
    private fun withSearchKey(s: SubjectEntity): SubjectEntity =
        if (s.pinyinKey == null) s.copy(pinyinKey = PinyinSearch.pinyinKey(s.title, s.titleCN)) else s

    /**
     * 批量落库（单事务 + diff 写）。
     *
     * 搜索页每敲一次键（300ms 防抖）都会到这里写一批；命中相同结果时 diff 写会**一条都不写**，
     * 否则作品库列表（同时观察 subjects 表）会跟着重算。
     */
    private suspend fun persistAll(subjects: List<SubjectEntity>) {
        val gateway = writeGateway
        if (gateway != null) gateway.upsertAll(subjects) else subjectDao.upsertAll(subjects)
    }

    private suspend fun persistOne(subject: SubjectEntity) {
        val gateway = writeGateway
        if (gateway != null) gateway.upsert(subject) else subjectDao.upsert(subject)
    }

    // ==================== 搜索 ====================

    override suspend fun search(
        keyword: String,
        type: Int?,
        tags: List<String>?,
        airDate: List<String>?,
        rank: List<String>?,
        nsfw: Boolean?,
        sort: String?,
        limit: Int?,
        offset: Int?,
    ): List<SubjectEntity> {
        for (plugin in plugins) {
            try {
                val result = plugin.dataSource.search(keyword, type, tags, airDate, rank, nsfw, sort, limit, offset)
                if (result.isNotEmpty()) {
                    // 标记 sourceId 后批量缓存（单事务，避免逐条提交引发失效风暴）
                    val marked = result.map { it.copy(sourceId = plugin.id) }
                    val now = System.currentTimeMillis()
                    persistAll(marked.map { withSearchKey(it.copy(lastSyncTime = now)) })
                    return marked
                }
            } catch (e: Exception) {
                Log.w(TAG, "${plugin.id}.search failed", e)
            }
        }
        // 主插件（Bangumi/AniList）无结果 → 旁路游戏数据源（Steam/VNDB），
        // 使"Steam 有 / VNDB 有而 Bangumi 没有"的游戏也能被搜到（sourceKey 稳定落库）
        return searchGameSources(keyword, limit)
    }

    /**
     * 搜索建议专用：主插件链（Bangumi/AniList）**并行**搜索，每源限时 2s，任一非空即返回；
     * 主链全部无结果时再走游戏数据源旁路（与正式搜索能力一致）。
     *
     * 并行 + 限时是关键：Bangumi 慢/不可达时 AniList（无需 VPN）仍能立即出建议，
     * 不会串行等待 Bangumi 超时拖死整个建议链路（击键建议必须快）。
     */
    suspend fun searchSuggestions(keyword: String, limit: Int?): List<SubjectEntity> {
        if (keyword.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        val main = withContext(Dispatchers.IO) {
            coroutineScope {
                plugins.map { plugin ->
                    async {
                        try {
                            withTimeout(2000) {
                                plugin.dataSource.search(keyword, null, null, null, null, null, null, limit, null)
                            }.map { it.copy(sourceId = plugin.id) }
                        } catch (e: CancellationException) {
                            // 复审修复：取消不能被当成「这一路没结果」（超时是失败，取消不是）
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().firstOrNull { it.isNotEmpty() } ?: emptyList()
            }
        }
        if (main.isNotEmpty()) {
            try {
                persistAll(main.map { withSearchKey(it.copy(lastSyncTime = now)) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "persist suggestions failed", e)
            }
            return main
        }
        // 主链无结果 → 游戏源旁路（Steam/VNDB/AniListGameDataSource），
        // 使"正式搜索能出结果的关键词，建议也能出"
        return searchGameSources(keyword, limit)
    }

    /**
     * 并行旁路搜索：对 gameDataSourceRegistry 中 supportsSearch 的源（Steam/VNDB）
     * 按标题搜索，经 GameItemMapper 转为本地条目（sourceKey=steam:id / vndb:id）落库。
     * 仅关键词非空时生效（榜单/日历类空关键词不走旁路）。
     */
    private suspend fun searchGameSources(keyword: String, limit: Int?): List<SubjectEntity> {
        val registry = gameDataSourceRegistry ?: return emptyList()
        if (keyword.isBlank()) return emptyList()
        val sources = registry.searchable()
        if (sources.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            coroutineScope {
                sources.map { source ->
                    async {
                        try {
                            source.search(keyword, limit = (limit ?: 10).coerceIn(1, 20))
                                .map { item ->
                                    GameItemMapper.toSubject(
                                        sourceId = source.id,
                                        item = item,
                                    ).copy(lastSyncTime = now)
                                }
                        } catch (e: CancellationException) {
                            // 复审修复：取消不是「这个游戏源没结果」，必须原样抛出
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "${source.id}.gameSource.search failed", e)
                            emptyList()
                        }
                    }
                }.awaitAll().flatten().distinctBy { it.sourceKey }
            }.also { results ->
                if (results.isNotEmpty()) {
                    try {
                        persistAll(results)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "persist game sources failed", e)
                    }
                }
            }
        }
    }

    override suspend fun searchWithTotal(
        keyword: String,
        type: Int?,
        tags: List<String>?,
        airDate: List<String>?,
        rank: List<String>?,
        nsfw: Boolean?,
        sort: String?,
        limit: Int?,
        offset: Int?,
    ): Pair<List<SubjectEntity>, Int> {
        // 第 6 轮 F5：逐个记录插件失败。改造前这里只有 Log.w，失败与「确实没有结果」
        // 在返回值上完全一样（空列表），UI 只能显示「未找到相关作品」——搜索"完全没内容"
        // 的观感正是从这里产生的。
        val failures = PluginFailureTracker()
        var attempted = 0
        for (plugin in plugins) {
            attempted++
            try {
                val (results, total) = plugin.dataSource.searchWithTotal(keyword, type, tags, airDate, rank, nsfw, sort, limit, offset)
                if (results.isNotEmpty()) {
                    val marked = results.map { it.copy(sourceId = plugin.id) }
                    val now = System.currentTimeMillis()
                    persistAll(marked.map { withSearchKey(it.copy(lastSyncTime = now)) })
                    return marked to total
                }
            } catch (e: CancellationException) {
                // 取消不是失败：原样抛出（否则取消会被记成插件故障并吞掉）
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${plugin.id}.searchWithTotal failed", e)
                failures.record(plugin.id, e)
            }
        }
        // 主插件无结果 → 游戏数据源旁路（与 search 一致）
        val fallback = searchGameSources(keyword, limit)
        if (fallback.isNotEmpty()) return fallback to fallback.size
        // 全部插件都抛错 → 让失败可见（部分成功已在循环内直接 return，照常出结果）
        failures.throwIfAllFailed(attempted)
        return fallback to fallback.size
    }

    // ==================== 详情（精确路由） ====================

    override suspend fun getDetail(subjectId: Long): SubjectEntity {
        // 优先从 Room 缓存获取 sourceId，精确路由
        val cached = subjectDao.getById(subjectId)
        val sourceId = cached?.sourceId

        // 尝试指定源
        if (sourceId != null && sourceId.isNotEmpty()) {
            val plugin = plugins.find { it.id == sourceId }
            if (plugin != null) {
                try {
                    val remote = plugin.dataSource.getDetail(subjectId)
                    val now = System.currentTimeMillis()
                    persistOne(remote.copy(lastSyncTime = now, sourceId = sourceId))
                    return remote
                } catch (e: Exception) {
                    Log.w(TAG, "${plugin.id}.getDetail failed, using cache", e)
                    if (cached != null) return cached
                }
            }
            // game 源条目（如 sourceId="steam"）不在插件链中 → 尝试用游戏数据源刷新元数据
            val gameSource = gameDataSourceRegistry?.get(sourceId)
            if (gameSource?.capabilities?.supportsDetail == true) {
                try {
                    val sourceGameId = cached?.sourceKey?.substringAfter(':')
                        ?: sourceId.removePrefix("steam:") // 兼容旧格式
                    if (!sourceGameId.isNullOrBlank()) {
                        val detail = gameSource.getDetail(sourceGameId)
                        if (detail != null) {
                            val refreshed = GameItemMapper.toSubject(
                                sourceId = sourceId,
                                item = detail.item,
                                subjectId = subjectId,
                            ).copy(
                                titleCN = cached?.titleCN ?: detail.item.title,
                                lastSyncTime = System.currentTimeMillis(),
                            )
                            persistOne(refreshed)
                            return refreshed
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "$sourceId gameSource.getDetail failed, using cache", e)
                }
            }
            // game 源刷新失败/无详情 → 回退缓存
            if (cached != null) return cached
        }

        // 无缓存/无 sourceId → 顺序尝试每个插件
        for (plugin in plugins) {
            try {
                val remote = plugin.dataSource.getDetail(subjectId)
                val now = System.currentTimeMillis()
                persistOne(remote.copy(lastSyncTime = now, sourceId = plugin.id))
                return remote
            } catch (e: Exception) {
                Log.w(TAG, "${plugin.id}.getDetail failed", e)
            }
        }

        // 全部失败 → 回退缓存
        if (cached != null) return cached
        throw IllegalStateException("No data source available for subjectId=$subjectId")
    }

    // ==================== 扩展数据（精确路由） ====================

    override suspend fun getCharacters(subjectId: Long): List<CharacterInfo> {
        val sourceId = subjectDao.getById(subjectId)?.sourceId
        val target = sourceId?.let { id -> plugins.find { it.id == id } } ?: plugins.firstOrNull()
        if (target == null) return emptyList()
        return try { target.dataSource.getCharacters(subjectId) } catch (_: Exception) { emptyList() }
    }

    override suspend fun getInfoBox(subjectId: Long): List<InfoBoxEntry> {
        // 优先指定源（Bangumi），否则顺序尝试
        val sourceId = subjectDao.getById(subjectId)?.sourceId
        val ordered = sourceId?.let { id -> plugins.filter { it.id == id } + plugins.filter { it.id != id } } ?: plugins
        for (plugin in ordered) {
            try {
                val box = plugin.dataSource.getInfoBox(subjectId)
                if (box.isNotEmpty()) return box
            } catch (_: Exception) { }
        }
        return emptyList()
    }

    override suspend fun getStaff(subjectId: Long): List<StaffInfo> {
        val sourceId = subjectDao.getById(subjectId)?.sourceId
        val target = sourceId?.let { id -> plugins.find { it.id == id } } ?: plugins.firstOrNull()
        if (target == null) return emptyList()
        return try { target.dataSource.getStaff(subjectId) } catch (_: Exception) { emptyList() }
    }

    override suspend fun getEpisodes(subjectId: Long): List<EpisodeInfo> {
        val sourceId = subjectDao.getById(subjectId)?.sourceId
        val target = sourceId?.let { id -> plugins.find { it.id == id } } ?: plugins.firstOrNull()
        if (target == null) return emptyList()
        return try { target.dataSource.getEpisodes(subjectId) } catch (_: Exception) { emptyList() }
    }

    override suspend fun getRatingDistribution(subjectId: Long): Map<Int, Int> {
        val sourceId = subjectDao.getById(subjectId)?.sourceId
        val target = sourceId?.let { id -> plugins.find { it.id == id } } ?: plugins.firstOrNull()
        if (target == null) return emptyMap()
        return try { target.dataSource.getRatingDistribution(subjectId) } catch (_: Exception) { emptyMap() }
    }

    // ==================== 只有声明能力的插件才调用 ====================

    override suspend fun getCalendar(): List<CalendarDaySchedule> {
        for (plugin in plugins) {
            if (!plugin.capabilities.supportsCalendar) continue
            try { return plugin.dataSource.getCalendar() } catch (_: Exception) {}
        }
        return emptyList()
    }

    override suspend fun getSubjectsByMonth(type: Int, year: Int, month: Int): List<SubjectEntity> {
        for (plugin in plugins) {
            if (!plugin.capabilities.supportsSubjectsByMonth) continue
            try {
                val result = plugin.dataSource.getSubjectsByMonth(type, year, month)
                if (result.isNotEmpty()) {
                    val marked = result.map { it.copy(sourceId = plugin.id) }
                    val now = System.currentTimeMillis()
                    persistAll(marked.map { withSearchKey(it.copy(lastSyncTime = now)) })
                    return marked
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    override suspend fun getSubjectsInDateRange(type: Int, startDate: String, endDate: String): List<SubjectEntity> {
        for (plugin in plugins) {
            try {
                val result = plugin.dataSource.getSubjectsInDateRange(type, startDate, endDate)
                if (result.isNotEmpty()) {
                    val marked = result.map { it.copy(sourceId = plugin.id) }
                    val now = System.currentTimeMillis()
                    persistAll(marked.map { withSearchKey(it.copy(lastSyncTime = now)) })
                    return marked
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    override suspend fun getRankingByType(type: Int, offset: Int, limit: Int): List<SubjectEntity> {
        for (plugin in plugins) {
            try {
                val result = plugin.dataSource.getRankingByType(type, offset, limit)
                if (result.isNotEmpty()) {
                    val marked = result.map { it.copy(sourceId = plugin.id) }
                    val now = System.currentTimeMillis()
                    persistAll(marked.map { withSearchKey(it.copy(lastSyncTime = now)) })
                    return marked
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    // ==================== 详情扩展（角色/人物/关联，首个非空返回） ====================

    override suspend fun getCharacterDetail(characterId: Long): CharacterDetailInfo {
        for (plugin in plugins) {
            try { return plugin.dataSource.getCharacterDetail(characterId) } catch (_: Exception) {}
        }
        throw IllegalStateException("No data source available for characterId=$characterId")
    }

    override suspend fun getCharacterSubjects(characterId: Long): List<PersonSubjectInfo> {
        for (plugin in plugins) {
            try {
                val result = plugin.dataSource.getCharacterSubjects(characterId)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    override suspend fun getPersonDetail(personId: Long): PersonDetailInfo {
        for (plugin in plugins) {
            try { return plugin.dataSource.getPersonDetail(personId) } catch (_: Exception) {}
        }
        throw IllegalStateException("No data source available for personId=$personId")
    }

    override suspend fun getPersonSubjects(personId: Long): List<PersonSubjectInfo> {
        for (plugin in plugins) {
            try {
                val result = plugin.dataSource.getPersonSubjects(personId)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    override suspend fun getPersonCharacters(personId: Long): List<CharacterInfo> {
        for (plugin in plugins) {
            try {
                val result = plugin.dataSource.getPersonCharacters(personId)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    override suspend fun getSubjectRelations(subjectId: Long): List<SubjectRelationInfo> {
        for (plugin in plugins) {
            try {
                val result = plugin.dataSource.getSubjectRelations(subjectId)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    override suspend fun searchPersons(keyword: String): List<PersonDetailInfo> {
        for (plugin in plugins) {
            try {
                val result = plugin.dataSource.searchPersons(keyword)
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {}
        }
        return emptyList()
    }
}

/**
 * 多插件失败记录（第 6 轮 F5）。
 *
 * 刻意做成纯逻辑类（不碰 Android / 网络），这样「全失败才算失败、部分成功照常出结果」
 * 这条不变量可以单测钉死。
 */
internal class PluginFailureTracker {

    private val failures = mutableListOf<PluginFailure>()

    /** 记录一次插件失败；reason 取异常 message，为空时退回类名。 */
    fun record(pluginId: String, error: Throwable) {
        val reason = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        failures += PluginFailure(pluginId, reason)
    }

    /** 已记录的失败数。 */
    val size: Int get() = failures.size

    /** attempted 个插件是否**全部**失败（attempted = 0 时不算失败）。 */
    fun allFailed(attempted: Int): Boolean = attempted > 0 && failures.size == attempted

    /** 全部失败时抛出；否则什么都不做（部分成功时结果已经返回给上层）。 */
    fun throwIfAllFailed(attempted: Int) {
        if (allFailed(attempted)) {
            throw AllPluginsFailedException(failures.toList())
        }
    }

    /** 单行摘要（日志/诊断用）。 */
    fun summary(): String = failures.joinToString("; ") { it.pluginId + "=" + it.reason }
}
