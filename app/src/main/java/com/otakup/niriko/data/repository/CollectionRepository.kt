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

    suspend fun update(collection: CollectionEntity): Boolean =
        collectionDao.update(collection) > 0

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