package com.otakup.niriko.plugin

import android.util.Log
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.CalendarDaySchedule
import com.otakup.niriko.data.remote.CharacterDetailInfo
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.PersonDetailInfo
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.data.remote.SubjectRelationInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.data.remote.game.GameDataSourceRegistry
import com.otakup.niriko.data.remote.game.GameItemMapper

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
) : SubjectRemoteDataSource {

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
                    subjectDao.upsertAll(marked.map { it.copy(lastSyncTime = now) })
                    return marked
                }
            } catch (e: Exception) {
                Log.w(TAG, "${plugin.id}.search failed", e)
            }
        }
        return emptyList()
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
        for (plugin in plugins) {
            try {
                val (results, total) = plugin.dataSource.searchWithTotal(keyword, type, tags, airDate, rank, nsfw, sort, limit, offset)
                if (results.isNotEmpty()) {
                    val marked = results.map { it.copy(sourceId = plugin.id) }
                    val now = System.currentTimeMillis()
                    subjectDao.upsertAll(marked.map { it.copy(lastSyncTime = now) })
                    return marked to total
                }
            } catch (e: Exception) {
                Log.w(TAG, "${plugin.id}.searchWithTotal failed", e)
            }
        }
        return emptyList<SubjectEntity>() to 0
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
                    subjectDao.upsert(remote.copy(lastSyncTime = now, sourceId = sourceId))
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
                            subjectDao.upsert(refreshed)
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
                subjectDao.upsert(remote.copy(lastSyncTime = now, sourceId = plugin.id))
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
                    subjectDao.upsertAll(marked.map { it.copy(lastSyncTime = now) })
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
                    subjectDao.upsertAll(marked.map { it.copy(lastSyncTime = now) })
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
                    subjectDao.upsertAll(marked.map { it.copy(lastSyncTime = now) })
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
