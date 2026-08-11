package com.otakup.niriko.plugin.bilibili

import com.otakup.niriko.data.local.dao.BilibiliSyncItemDao
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.BilibiliSyncItemEntity
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BilibiliImporter 导入分支测试（本地优先语义）：
 * - 未收藏 → 新建 collection（状态=想看）+ subjects 占位；
 * - 已收藏 → 完全跳过（不更新状态/评分/短评），计入 skippedExisting；
 * - 未匹配 → skippedNotMatched 且不写收藏；
 * - overwrite 参数已无实际作用（本地优先），行为与默认一致；
 * - 未勾选项不导入但快照仍落库。
 */
class BilibiliImporterTest {

    // ==================== fake DAO（内存实现） ====================

    private class FakeBilibiliSyncItemDao : BilibiliSyncItemDao {
        val items = mutableListOf<BilibiliSyncItemEntity>()
        override fun observeAll(): Flow<List<BilibiliSyncItemEntity>> = MutableStateFlow(items.toList())
        override suspend fun getAll(): List<BilibiliSyncItemEntity> = items.toList()
        override suspend fun getByMediaId(mediaId: Long): BilibiliSyncItemEntity? = items.find { it.mediaId == mediaId }
        override suspend fun upsertAll(items: List<BilibiliSyncItemEntity>) {
            val byId = this.items.associateBy { it.mediaId }
            this.items.clear()
            this.items.addAll(items.map { byId[it.mediaId]?.let { old -> it.copy(imported = old.imported) } ?: it })
        }
        override suspend fun clearAll() = items.clear()
        override suspend fun setImported(mediaId: Long, imported: Boolean) {
            val idx = items.indexOfFirst { it.mediaId == mediaId }
            if (idx >= 0) items[idx] = items[idx].copy(imported = imported)
        }
    }

    private class FakeSubjectDao : SubjectDao {
        val subjects = mutableListOf<SubjectEntity>()
        override fun observeAll(): Flow<List<SubjectEntity>> = MutableStateFlow(subjects.toList())
        override fun observeById(id: Long): Flow<SubjectEntity?> = MutableStateFlow(subjects.find { it.subjectId == id })
        override suspend fun getById(id: Long): SubjectEntity? = subjects.find { it.subjectId == id }
        override suspend fun getBySourceKey(sourceKey: String): SubjectEntity? = subjects.find { it.sourceKey == sourceKey }
        override suspend fun getExistingIds(ids: List<Long>): List<Long> = subjects.map { it.subjectId }.filter { it in ids }
        override fun searchByKeyword(keyword: String): Flow<List<SubjectEntity>> = MutableStateFlow(emptyList())
        override suspend fun getAll(): List<SubjectEntity> = subjects.toList()
        override suspend fun clearAll() = subjects.clear()
        override suspend fun insertAll(subjects: List<SubjectEntity>) {
            this.subjects.addAll(subjects)
        }
        override suspend fun insert(subject: SubjectEntity) {
            this.subjects.add(subject)
        }
        override suspend fun update(subject: SubjectEntity): Int {
            val idx = subjects.indexOfFirst { it.subjectId == subject.subjectId }
            if (idx < 0) return 0
            subjects[idx] = subject
            return 1
        }
        override suspend fun deleteById(id: Long) {
            subjects.removeAll { it.subjectId == id }
        }
        override suspend fun getSubjectsInAirDateRange(startDate: String, endDate: String): List<SubjectEntity> = emptyList()
        override suspend fun getAllWithAirDate(): List<SubjectEntity> = emptyList()
        override suspend fun searchByKeywordPrefix(keyword: String): List<SubjectEntity> = emptyList()
        override suspend fun getBySource(sourceId: String): List<SubjectEntity> =
            subjects.filter { it.sourceId == sourceId }
        override suspend fun getLegacySteamPlaceholders(): List<SubjectEntity> =
            subjects.filter { it.sourceId == "steam" && it.subjectId < 0 }
        override suspend fun getMaxSubjectId(): Long =
            subjects.maxOfOrNull { it.subjectId } ?: 0L
    }

    private class FakeCollectionDao : CollectionDao {
        val collections = mutableListOf<CollectionEntity>()
        override fun observeAll(): Flow<List<CollectionEntity>> = MutableStateFlow(collections.toList())
        override fun observeById(id: Long): Flow<CollectionEntity?> = MutableStateFlow(collections.find { it.id == id })
        override fun observeBySubjectId(subjectId: Long): Flow<CollectionEntity?> = MutableStateFlow(collections.find { it.subjectId == subjectId })
        override suspend fun getById(id: Long): CollectionEntity? = collections.find { it.id == id }
        override suspend fun getBySubjectId(subjectId: Long): CollectionEntity? = collections.find { it.subjectId == subjectId }
        override suspend fun insert(collection: CollectionEntity): Long {
            collections += collection.copy(id = (collections.maxOfOrNull { it.id } ?: 0) + 1)
            return collections.last().id
        }
        override suspend fun update(collection: CollectionEntity): Int {
            val idx = collections.indexOfFirst { it.id == collection.id }
            if (idx < 0) return 0
            collections[idx] = collection
            return 1
        }
        override suspend fun delete(collection: CollectionEntity): Int {
            val before = collections.size
            collections.removeAll { it.id == collection.id }
            return before - collections.size
        }
        override suspend fun deleteBySubjectId(subjectId: Long): Int {
            val before = collections.size
            collections.removeAll { it.subjectId == subjectId }
            return before - collections.size
        }
        override suspend fun migrateSubjectId(oldSubjectId: Long, newSubjectId: Long, now: Long): Int {
            var migrated = 0
            collections.forEachIndexed { index, c ->
                if (c.subjectId == oldSubjectId) {
                    collections[index] = c.copy(subjectId = newSubjectId, updateTime = now)
                    migrated++
                }
            }
            return migrated
        }
        override suspend fun getAll(): List<CollectionEntity> = collections.toList()
        override suspend fun clearAll() = collections.clear()
        override suspend fun insertAll(entities: List<CollectionEntity>) = entities.forEach { insert(it) }
        override fun observeCount(): Flow<Int> = MutableStateFlow(collections.size)
        override fun observeAllWithSubject(): Flow<List<CollectionWithSubject>> = MutableStateFlow(emptyList())
        override fun observeByKeyword(keyword: String): Flow<List<CollectionWithSubject>> = MutableStateFlow(emptyList())
    }

    // ==================== fixture ====================

    private fun preview(
        mediaId: Long,
        bgmSubjectId: Long?,
        title: String = "测试番",
        followStatus: Int = 3,
        progress: Int? = 12,
        totalEpisodes: Int? = 24,
        score: Float? = 8.5f,
        comment: String? = "很好看",
        selected: Boolean = true,
    ) = BilibiliSyncPreview(
        mediaId = mediaId,
        seasonId = null,
        bgmSubjectId = bgmSubjectId,
        title = title,
        cover = null,
        followStatus = followStatus,
        progress = progress,
        totalEpisodes = totalEpisodes,
        biliScore = score,
        biliComment = comment,
        localCollection = null,
        localSubjectTitle = null,
        selected = selected,
    )

    private fun importer(bili: FakeBilibiliSyncItemDao, subject: FakeSubjectDao, collection: FakeCollectionDao) =
        BilibiliImporter(bili, subject, collection)

    // ==================== 用例 ====================

    @Test
    fun `未收藏时新建收藏题并占位 subject 且状态为想看`() = runBlocking {
        val bili = FakeBilibiliSyncItemDao()
        val subject = FakeSubjectDao()
        val collection = FakeCollectionDao()
        val result = importer(bili, subject, collection).import(
            listOf(preview(mediaId = 100L, bgmSubjectId = 123L, score = 8.5f, comment = "很棒")),
        )

        assertEquals(1, result.imported)
        assertEquals(0, result.skippedNotMatched)
        assertEquals(0, result.skippedExisting)
        assertEquals(0, result.failed)

        // subject 占位
        val s = subject.getById(123L)
        assertNotNull(s)
        assertEquals("测试番", s?.title)
        assertEquals("bilibili", s?.sourceId)

        // collection 新建；状态固定想看（bili followStatus=3 看过 也不影响，本地优先=想看）
        val c = collection.getBySubjectId(123L)
        assertNotNull(c)
        assertEquals(WatchStatus.PLAN_TO_WATCH, c?.status)
        assertEquals(12, c?.watchedEpisodes)
        assertEquals(8.5f, c?.rating)
        assertEquals("很棒", c?.personalImpression)

        // 原始快照落库 + 标记 imported
        assertEquals(1, bili.items.size)
        assertTrue(bili.items.first().imported)
        assertEquals(100L, bili.items.first().mediaId)
    }

    @Test
    fun `未匹配项跳过且不写库`() = runBlocking {
        val bili = FakeBilibiliSyncItemDao()
        val subject = FakeSubjectDao()
        val collection = FakeCollectionDao()
        val result = importer(bili, subject, collection).import(
            listOf(preview(mediaId = 1L, bgmSubjectId = null)),
        )

        assertEquals(1, result.skippedNotMatched)
        assertEquals(0, result.imported)
        assertTrue(collection.collections.isEmpty())
        assertTrue(subject.subjects.isEmpty())
        assertEquals(1, bili.items.size)
        assertNull(bili.items.first().bgmSubjectId)
    }

    @Test
    fun `本地已有收藏时完全跳过不更新任何字段`() = runBlocking {
        val bili = FakeBilibiliSyncItemDao()
        val subject = FakeSubjectDao()
        val collection = FakeCollectionDao()
        subject.subjects += SubjectEntity(subjectId = 200L, title = "测试番", type = SubjectType.ANIME)
        collection.collections += CollectionEntity(
            subjectId = 200L,
            status = WatchStatus.WATCHING,
            rating = 7f,
            personalImpression = "我的评论",
            watchedEpisodes = 5,
        )

        val result = importer(bili, subject, collection).import(
            listOf(preview(mediaId = 100L, bgmSubjectId = 200L, score = 8.5f, comment = "bili评论")),
        )

        assertEquals(1, result.skippedExisting)
        assertEquals(0, result.imported)
        // 本地值完全不变（状态/评分/短评/进度都保留）
        val c = collection.getBySubjectId(200L)
        assertEquals(7f, c?.rating)
        assertEquals("我的评论", c?.personalImpression)
        assertEquals(5, c?.watchedEpisodes)
        assertEquals(WatchStatus.WATCHING, c?.status)
    }

    @Test
    fun `本地已有收藏时即使 overwrite 也跳过`() = runBlocking {
        val bili = FakeBilibiliSyncItemDao()
        val subject = FakeSubjectDao()
        val collection = FakeCollectionDao()
        subject.subjects += SubjectEntity(subjectId = 200L, title = "测试番", type = SubjectType.ANIME)
        collection.collections += CollectionEntity(
            subjectId = 200L,
            status = WatchStatus.COMPLETED,
            rating = 9f,
            personalImpression = "旧评论",
            watchedEpisodes = 24,
        )

        val result = importer(bili, subject, collection).import(
            listOf(preview(mediaId = 100L, bgmSubjectId = 200L, score = 6f, comment = "新评论")),
            overwrite = true,
        )

        // overwrite 无意义：本地优先，完全跳过
        assertEquals(1, result.skippedExisting)
        assertEquals(0, result.imported)
        val c = collection.getBySubjectId(200L)
        assertEquals(9f, c?.rating)
        assertEquals("旧评论", c?.personalImpression)
        assertEquals(24, c?.watchedEpisodes)
        assertEquals(WatchStatus.COMPLETED, c?.status)
    }

    @Test
    fun `未勾选项不导入但其快照仍落库`() = runBlocking {
        val bili = FakeBilibiliSyncItemDao()
        val subject = FakeSubjectDao()
        val collection = FakeCollectionDao()
        val result = importer(bili, subject, collection).import(
            listOf(
                preview(mediaId = 1L, bgmSubjectId = 10L, selected = false),
                preview(mediaId = 2L, bgmSubjectId = 20L, selected = true),
            ),
        )

        assertEquals(1, result.total)
        assertEquals(1, result.imported)
        assertEquals(2, bili.items.size)
        assertTrue(collection.getBySubjectId(20L) != null)
        assertTrue(collection.getBySubjectId(10L) == null)
    }
}