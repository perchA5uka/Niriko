package com.otakup.niriko.data.sync

import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.bumpUpdateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-D6：收藏写入必须刷新 `updateTime`。
 *
 * 改造前只有详情页自己写 now，长按改状态 / 批量改状态都不写 —— 「最近修改」排序不动，
 * WebDAV 的整行 LWW 也会把新状态判成旧数据。现在统一在 [CollectionRepository.update] 里 bump。
 */
class CollectionTimestampTest {

    private fun entity(
        updateTime: Long = 1_000L,
        status: WatchStatus = WatchStatus.WATCHING,
        watchedEpisodes: Int? = 3,
        watchedVolumes: Int? = null,
        rating: Float? = 8f,
        isPrivate: Boolean = false,
        remark: String? = "备注",
    ) = CollectionEntity(
        id = 1,
        subjectId = 100,
        status = status,
        watchedEpisodes = watchedEpisodes,
        watchedVolumes = watchedVolumes,
        isPrivate = isPrivate,
        rating = rating,
        remark = remark,
        createTime = 500L,
        updateTime = updateTime,
    )

    @Test
    fun bumpUpdatesTimeAndKeepsEveryOtherField() {
        val before = entity(updateTime = 1_000L)
        val after = before.bumpUpdateTime(5_000L)

        assertEquals(5_000L, after.updateTime)
        // 其余字段逐一不变（用 copy 对比，避免逐字段手写漏项）
        assertEquals(before.copy(updateTime = 5_000L), after)
    }

    @Test
    fun bumpNeverGoesBackwards() {
        // 设备时钟回拨 / 短时间连续写入：时间戳不能倒退，否则 LWW 会把新数据判旧
        val before = entity(updateTime = 9_000L)
        val after = before.bumpUpdateTime(3_000L)
        assertEquals(9_000L, after.updateTime)
    }

    @Test
    fun bumpWithSameTimeReturnsSameInstance() {
        val before = entity(updateTime = 7_000L)
        assertSame(before, before.bumpUpdateTime(7_000L))
    }

    @Test
    fun repositoryUpdateBumpsTimestampBeforeWriting() = runBlocking {
        val dao = FakeCollectionDao()
        val repository = CollectionRepository(dao)

        val written = repository.update(entity(updateTime = 1_000L))

        assertTrue(written)
        val persisted = requireNotNull(dao.lastUpdated)
        assertTrue("updateTime 必须被 bump 到当前时间", persisted.updateTime > 1_000L)
        assertEquals(WatchStatus.WATCHING, persisted.status)
        assertEquals(3, persisted.watchedEpisodes)
        assertEquals(8f, persisted.rating)
    }

    @Test
    fun repositoryUpdateReturnsFalseWhenNoRowChanged() = runBlocking {
        val dao = FakeCollectionDao().apply { updateResult = 0 }
        val repository = CollectionRepository(dao)
        assertFalse(repository.update(entity()))
    }

    /** 只记录写入的假 DAO；其它查询返回空，不碰 Room / Android。 */
    private class FakeCollectionDao : CollectionDao {
        var lastUpdated: CollectionEntity? = null
        var updateResult: Int = 1

        override fun observeAll(): Flow<List<CollectionEntity>> =
            flowOf(listOfNotNull(lastUpdated))

        override fun observeById(id: Long): Flow<CollectionEntity?> = flowOf(null)

        override fun observeBySubjectId(subjectId: Long): Flow<CollectionEntity?> = flowOf(null)

        override suspend fun getById(id: Long): CollectionEntity? = null

        override suspend fun getBySubjectId(subjectId: Long): CollectionEntity? = null

        override suspend fun insert(collection: CollectionEntity): Long = collection.id

        override suspend fun update(collection: CollectionEntity): Int {
            lastUpdated = collection
            return updateResult
        }

        override suspend fun delete(collection: CollectionEntity): Int = 1

        override suspend fun deleteBySubjectId(subjectId: Long): Int = 1

        override suspend fun migrateSubjectId(oldSubjectId: Long, newSubjectId: Long, now: Long): Int = 0

        override suspend fun getAll(): List<CollectionEntity> = listOfNotNull(lastUpdated)

        override suspend fun clearAll() = Unit

        override suspend fun insertAll(entities: List<CollectionEntity>) = Unit

        override fun observeCount(): Flow<Int> = flowOf(0)

        // 数据体检（C-1）新增的只读计数：测试替身返回 0 即可。
        override suspend fun countAll(): Int = 0

        override suspend fun countWithoutProgress(): Int = 0

        override suspend fun countWithoutRating(): Int = 0

        override fun observeAllWithSubject(): Flow<List<CollectionWithSubject>> = flowOf(emptyList())

        override fun observeByKeyword(keyword: String): Flow<List<CollectionWithSubject>> =
            flowOf(emptyList())
    }
}
