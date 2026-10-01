package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.dao.ExternalIdDao
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 手动指定季号（计划 B3 · 4-14）单元测试。
 *
 * 关键回归：改季号**不能**把外部 ID / 标题快照 / 置信度 / 绑定方式 / 绑定时间冲掉
 * —— 直接用 bind() 重写就会冲掉，所以单独提供 updateSubKey。
 */
class ExternalIdRepositorySubKeyTest {

    /** 内存版 ExternalIdDao：只实现本用例需要的语义（主键 subjectId + provider）。 */
    private class FakeDao : ExternalIdDao {
        private val rows = mutableListOf<SubjectExternalIdEntity>()

        override suspend fun getBySubject(subjectId: Long): List<SubjectExternalIdEntity> =
            rows.filter { it.subjectId == subjectId }

        override suspend fun get(subjectId: Long, provider: String): SubjectExternalIdEntity? =
            rows.firstOrNull { it.subjectId == subjectId && it.provider == provider }

        override suspend fun upsert(entity: SubjectExternalIdEntity) {
            rows.removeAll { it.subjectId == entity.subjectId && it.provider == entity.provider }
            rows.add(entity)
        }

        override suspend fun delete(subjectId: Long, provider: String) {
            rows.removeAll { it.subjectId == subjectId && it.provider == provider }
        }

        override suspend fun getAll(): List<SubjectExternalIdEntity> = rows.toList()

        override suspend fun insertAll(entities: List<SubjectExternalIdEntity>) {
            entities.forEach { upsert(it) }
        }

        // WebDAV 同步写回用（B4 · 4-8 给 DAO 加的 clearAll）
        override suspend fun clearAll() {
            rows.clear()
        }
    }

    private val provider = SubjectExternalIdEntity.PROVIDER_TMDB_TV

    @Test
    fun updateSubKey_keepsOtherBindingFields() = runBlocking {
        val dao = FakeDao()
        dao.upsert(
            SubjectExternalIdEntity(
                subjectId = 1L,
                provider = provider,
                externalId = "12345",
                titleSnapshot = "葬送的芙莉莲",
                confidence = 1f,
                bindMethod = SubjectExternalIdEntity.METHOD_MANUAL,
                subKey = null,
                boundAt = 1_700_000_000_000L,
            ),
        )

        ExternalIdRepository(dao).updateSubKey(1L, provider, "2")

        val after = dao.get(1L, provider)!!
        assertEquals("2", after.subKey)
        assertEquals("12345", after.externalId)
        assertEquals("葬送的芙莉莲", after.titleSnapshot)
        assertEquals(1f, after.confidence, 0f)
        assertEquals(SubjectExternalIdEntity.METHOD_MANUAL, after.bindMethod)
        assertEquals(1_700_000_000_000L, after.boundAt)
    }

    @Test
    fun updateSubKey_missingBinding_isNoOp() = runBlocking {
        val dao = FakeDao()
        ExternalIdRepository(dao).updateSubKey(9L, provider, "3")
        assertNull(dao.get(9L, provider))
    }

    @Test
    fun updateSubKey_null_clearsManualSeason() = runBlocking {
        val dao = FakeDao()
        dao.upsert(
            SubjectExternalIdEntity(subjectId = 2L, provider = provider, externalId = "8", subKey = "4"),
        )
        ExternalIdRepository(dao).updateSubKey(2L, provider, null)
        assertNull(dao.get(2L, provider)!!.subKey)
    }
}
