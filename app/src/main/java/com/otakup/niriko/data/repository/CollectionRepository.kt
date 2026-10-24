package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 用户收藏记录仓库（纯本地）。
 */
class CollectionRepository(
    private val collectionDao: CollectionDao,
) {
    fun observeAll(): Flow<List<CollectionEntity>> = collectionDao.observeAll()

    fun observeById(id: Long): Flow<CollectionEntity?> = collectionDao.observeById(id)

    fun observeBySubjectId(subjectId: Long): Flow<CollectionEntity?> =
        collectionDao.observeBySubjectId(subjectId)

    suspend fun getById(id: Long): CollectionEntity? = collectionDao.getById(id)

    suspend fun getBySubjectId(subjectId: Long): CollectionEntity? =
        collectionDao.getBySubjectId(subjectId)

    suspend fun add(collection: CollectionEntity): Long = collectionDao.insert(collection)

    /**
     * 更新收藏。
     *
     * A-D6：`updateTime` 统一在这里 bump —— 状态、评分、私密标记、备注等任何已持久化
     * 字段的变化都算「最近修改」。改造前只有详情页的调用点自己写 now，长按改状态
     * （CollectionViewModel.updateStatus）与批量改状态（batchUpdateStatus）都不写：
     * 列表默认按 updateTime 排序，改完状态条目不动；WebDAV 的 LWW 也会因此误判新旧。
     *
     * 放在仓库层是为了一处覆盖所有调用点，避免下个新入口再忘记。
     */
    suspend fun update(collection: CollectionEntity): Boolean =
        collectionDao.update(collection.bumpUpdateTime(System.currentTimeMillis())) > 0

    suspend fun delete(collection: CollectionEntity): Boolean =
        collectionDao.delete(collection) > 0

    suspend fun deleteBySubjectId(subjectId: Long): Boolean =
        collectionDao.deleteBySubjectId(subjectId) > 0

    fun observeCount(): Flow<Int> = collectionDao.observeCount()

    /** 查询收藏+作品关联数据。捕获 Room @Relation 缺失异常，过滤无效记录。 */
    fun observeAllWithSubject(): Flow<List<CollectionWithSubject>> =
        collectionDao.observeAllWithSubject().catch { emit(emptyList()) }

    fun observeByKeyword(keyword: String): Flow<List<CollectionWithSubject>> =
        collectionDao.observeByKeyword(keyword).catch { emit(emptyList()) }
}

/**
 * 写入前刷新修改时间（A-D6）。
 *
 * 取 `maxOf(now, updateTime)`：设备时钟回拨或短时间连续写入时时间戳不倒退，
 * 否则「最近修改」排序会跳，WebDAV 的 LWW 也可能把新写入判成旧数据。
 * 纯函数，便于 JVM 单测。
 */
internal fun CollectionEntity.bumpUpdateTime(now: Long): CollectionEntity {
    val bumped = maxOf(now, updateTime)
    return if (bumped == updateTime) this else copy(updateTime = bumped)
}
