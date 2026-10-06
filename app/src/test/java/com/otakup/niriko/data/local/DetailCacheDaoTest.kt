package com.otakup.niriko.data.local

import android.content.Context
import androidx.room.Room
import com.otakup.niriko.data.local.dao.DetailCacheDao
import com.otakup.niriko.data.local.entity.EpisodeExternalCacheEntity
import com.otakup.niriko.data.local.entity.SubjectDetailCacheEntity
import com.otakup.niriko.data.local.entity.SubjectRelationCacheEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * DetailCacheDao 的行为单测（B15 阶段 1）。
 *
 * 这里锁的不是「SQL 能不能跑」（那是 RoomMigrationTest 的职责），而是计划书 §7.3 第 4/5 条的**语义**：
 * - 失败只能写失败信息，**不得覆盖**上一次成功的结果 —— 否则断网打开一次就把好数据洗掉了；
 * - 失效是**按来源**的：手动绑定只需失效 vndb，不能顺手把 infobox 一起清掉；
 * - 集合刷新必须整块替换，否则成员变少时 UI 里会留下幽灵角色。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailCacheDaoTest {

    private lateinit var db: NirikoDatabase
    private lateinit var dao: DetailCacheDao

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NirikoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.detailCacheDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun detail(
        subjectId: Long = 1L,
        sourceKey: String = SubjectDetailCacheEntity.SourceKeys.INFOBOX,
        payload: String? = """{"k":"v"}""",
        fetchedAt: Long = 1_000L,
        expiresAt: Long = 2_000L,
        errorSummary: String? = null,
        lastErrorAt: Long? = null,
    ) = SubjectDetailCacheEntity(
        subjectId = subjectId,
        sourceKey = sourceKey,
        schemaVersion = 1,
        fetchedAt = fetchedAt,
        expiresAt = expiresAt,
        payload = payload,
        errorSummary = errorSummary,
        lastErrorAt = lastErrorAt,
    )

    @Test
    fun `成功结果能读回来_且能区分成功与从未取过`() = runBlocking {
        assertNull("从未取过时没有行", dao.getDetail(1L, SubjectDetailCacheEntity.SourceKeys.INFOBOX))
        dao.putDetail(detail())
        val row = dao.getDetail(1L, SubjectDetailCacheEntity.SourceKeys.INFOBOX)
        assertNotNull(row)
        assertEquals("""{"k":"v"}""", row!!.payload)
        assertNull("成功行的 errorSummary 必须是空的", row.errorSummary)
    }

    @Test
    fun `失败只写失败信息_绝不覆盖上一次成功的结果`() = runBlocking {
        dao.putDetail(detail(payload = """{"good":true}"""))
        val affected = dao.markFailure(1L, SubjectDetailCacheEntity.SourceKeys.INFOBOX, "HTTP 500", 5_000L)
        assertEquals("已有行 → 更新 1 行", 1, affected)

        val row = dao.getDetail(1L, SubjectDetailCacheEntity.SourceKeys.INFOBOX)!!
        assertEquals("断网/失败不能洗掉好数据", """{"good":true}""", row.payload)
        assertEquals("HTTP 500", row.errorSummary)
        assertEquals(5_000L, row.lastErrorAt)
    }

    @Test
    fun `从未成功过的来源记录失败时返回 0_由调用方决定错误态`() = runBlocking {
        assertEquals(0, dao.markFailure(9L, SubjectDetailCacheEntity.SourceKeys.VNDB, "timeout", 1L))
    }

    @Test
    fun `失效是按来源的_不会波及别的来源`() = runBlocking {
        val keys = SubjectDetailCacheEntity.SourceKeys
        dao.putDetail(detail(sourceKey = keys.INFOBOX))
        dao.putDetail(detail(sourceKey = keys.VNDB))
        dao.putDetail(detail(sourceKey = keys.TMDb))

        val removed = dao.invalidateDetail(1L, listOf(keys.VNDB))

        assertEquals(1, removed)
        assertNull(dao.getDetail(1L, keys.VNDB))
        assertNotNull("手动绑定只该失效 vndb，infobox 必须留着", dao.getDetail(1L, keys.INFOBOX))
        assertNotNull(dao.getDetail(1L, keys.TMDb))
        assertEquals(2, dao.getDetailAll(1L).size)
    }

    @Test
    fun `过期清理只删到期的行`() = runBlocking {
        dao.putDetail(detail(sourceKey = "a", expiresAt = 100L))
        dao.putDetail(detail(sourceKey = "b", expiresAt = 5_000L))
        dao.putDetail(detail(sourceKey = "c", expiresAt = 10_000L))

        assertEquals(1, dao.deleteExpiredDetail(now = 1_000L))
        assertEquals(listOf("b", "c"), dao.getDetailAll(1L).map { it.sourceKey }.sorted())
    }

    @Test
    fun `集合刷新是整块替换_成员变少时不留幽灵行`() = runBlocking {
        val kind = SubjectRelationCacheEntity.RelationKinds.CHARACTERS
        fun row(itemId: Long, sortIndex: Int) = SubjectRelationCacheEntity(
            subjectId = 1L, kind = kind, itemId = itemId, sortIndex = sortIndex,
            payload = """{"id":$itemId}""", sourceId = "bangumi",
            fetchedAt = 1L, expiresAt = 10_000L,
        )
        dao.replaceRelations(1L, kind, listOf(row(10L, 0), row(11L, 1), row(12L, 2)))
        assertEquals(3, dao.getRelations(1L, kind).size)

        // 角色被移出制作组：列表变短，旧行必须消失
        dao.replaceRelations(1L, kind, listOf(row(10L, 0), row(12L, 1)))
        assertEquals(listOf(10L, 12L), dao.getRelations(1L, kind).map { it.itemId })
        assertEquals("顺序必须按 sortIndex 还原", listOf(0, 1), dao.getRelations(1L, kind).map { it.sortIndex })

        // 清空也是合法的刷新结果（与「从未取过」区分：这里是真的空）
        dao.replaceRelations(1L, kind, emptyList())
        assertTrue(dao.getRelations(1L, kind).isEmpty())
    }

    @Test
    fun `每集对齐状态与季集号能存能读`() = runBlocking {
        val states = EpisodeExternalCacheEntity.AlignmentStates
        dao.putEpisodeExternal(
            EpisodeExternalCacheEntity(
                subjectId = 1L, epId = 100L, seasonNumber = 2, episodeNumber = 5,
                tmdbTitle = "The Long Night", sourceId = "tmdb",
                alignmentState = states.ALIGNED, fetchedAt = 1L, expiresAt = 10_000L,
            ),
        )
        dao.putEpisodeExternal(
            EpisodeExternalCacheEntity(
                subjectId = 1L, epId = 101L, sourceId = "tmdb",
                alignmentState = states.SOURCE_EMPTY, fetchedAt = 1L, expiresAt = 10_000L,
            ),
        )

        val aligned = dao.getEpisodeExternal(1L, 100L)!!
        assertEquals(2, aligned.seasonNumber)
        assertEquals(5, aligned.episodeNumber)
        assertEquals("The Long Night", aligned.tmdbTitle)
        val empty = dao.getEpisodeExternal(1L, 101L)!!
        assertEquals("来源无图与失败必须能区分", states.SOURCE_EMPTY, empty.alignmentState)
        assertNull(empty.tmdbTitle)
        assertEquals(2, dao.getEpisodeExternalAll(1L).size)
    }
}
