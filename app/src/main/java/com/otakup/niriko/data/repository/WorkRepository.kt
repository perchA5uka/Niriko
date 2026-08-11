package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.WorkDao
import com.otakup.niriko.data.local.WorkItem
import com.otakup.niriko.data.model.WorkFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 作品仓库：封装 Dao，对上层屏蔽持久化细节。
 */
class WorkRepository(
    private val workDao: WorkDao,
) {

    /** 观察全部作品（按更新时间倒序）。 */
    fun observeAll(): Flow<List<WorkItem>> = workDao.observeAll()

    /** 观察单条作品；不存在时为 null。 */
    fun observeById(id: Long): Flow<WorkItem?> = workDao.observeById(id)

    /**
     * 按条件观察作品列表。
     * 标签过滤在内存中完成（标签以 JSON 存储，不便做 SQL 包含匹配）。
     */
    fun observeByFilter(filter: WorkFilter): Flow<List<WorkItem>> {
        val keyword = filter.keyword?.trim().orEmpty()
        return workDao.observeFiltered(
            type = filter.type,
            status = filter.status,
            keyword = keyword,
        ).map { list ->
            if (filter.tags.isEmpty()) {
                list
            } else {
                list.filter { work -> filter.tags.all { tag -> tag in work.tags } }
            }
        }
    }

    suspend fun getById(id: Long): WorkItem? = workDao.getById(id)

    /** 新增作品，返回生成的主键 id。 */
    suspend fun add(work: WorkItem): Long {
        val now = System.currentTimeMillis()
        return workDao.insert(
            work.copy(
                id = 0,
                createTime = work.createTime,
                updateTime = now,
            ),
        )
    }

    /** 更新作品；自动刷新 [WorkItem.updateTime]。 */
    suspend fun update(work: WorkItem): Boolean {
        val rows = workDao.update(
            work.copy(updateTime = System.currentTimeMillis()),
        )
        return rows > 0
    }

    suspend fun delete(work: WorkItem): Boolean = workDao.delete(work) > 0

    suspend fun deleteById(id: Long): Boolean = workDao.deleteById(id) > 0

    fun observeCount(): Flow<Int> = workDao.observeCount()
}
