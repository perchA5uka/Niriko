package com.otakup.niriko.data.repository

import android.content.Context
import androidx.room.Room
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.local.entity.SubjectDetailCacheEntity
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.SubjectRelationInfo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * DetailCacheStore 的行为单测（B15 阶段 2）。
 *
 * 锁的是「重启后能不能直接展示」这件事的三个前提：
 * 1. 写进去的集合能原样读回来（含**合法的空集合**）；
 * 2. 从没取过时返回 null（调用方才会去走网络），而不是返回一份空列表冒充「取过了」；
 * 3. 过期仍然返回数据（先显示旧内容、再后台刷新），而失败不会把已落库的好数据洗掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailCacheStoreTest {

    private lateinit var db: NirikoDatabase
    private var now = 1_000L
    private lateinit var store: DetailCacheStore

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NirikoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = DetailCacheStore(db.detailCacheDao(), clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun character(id: Long) = CharacterInfo(
        id = id, name = "cv-$id", nameCn = "角色$id", roleName = "主角", imageUrl = null,
        actors = listOf(StaffInfo(id = id + 100, name = "actor$id", nameCn = null, roleName = null, imageUrl = null)),
    )

    @Test
    fun `写入的集合能原样读回来_顺序与嵌套都不丢`() = runBlocking {
        val characters = listOf(character(1L), character(2L))
        val staff = listOf(StaffInfo(9L, "监督", "导演", "导演", null))
        val relations = listOf(SubjectRelationInfo(77L, "前作", "前作", 2, "prequel", null))

        store.writeCollections(1L, characters, staff, relations)

        val read = store.readCollections(1L)
        assertEquals(characters, read!!.characters)
        assertEquals(staff, read.staff)
        assertEquals(relations, read.relations)
        assertFalse("刚写完不该是过期的", read.isExpired(now))
    }

    @Test
    fun `从没取过返回 null_而不是拿空列表冒充取过了`() = runBlocking {
        assertNull(store.readCollections(42L))
    }

    @Test
    fun `空集合是合法结果_写入后读回来仍然是「取过了且为空」`() = runBlocking {
        store.writeCollections(2L, emptyList(), emptyList(), emptyList())
        val read = store.readCollections(2L)
        assertEquals("取过了 → 不是 null", true, read != null)
        assertTrue(read!!.characters.isEmpty())
        assertTrue(read.relations.isEmpty())
    }

    @Test
    fun `过期仍然返回数据_由调用方决定先显示再刷新`() = runBlocking {
        store.writeCollections(3L, listOf(character(1L)), emptyList(), emptyList())
        now += DetailCacheStore.COLLECTIONS_TTL_MS + 1
        val read = store.readCollections(3L)
        assertTrue("过期也必须能读到旧内容", read!!.isExpired(now))
        assertEquals(1, read.characters.size)
    }

    @Test
    fun `失败不覆盖已落库的成功数据`() = runBlocking {
        store.writeCollections(4L, listOf(character(1L)), emptyList(), emptyList())
        store.markCollectionsFailure(4L, "HTTP 500")

        val read = store.readCollections(4L)
        assertEquals("断网一次不能把好数据洗掉", 1, read!!.characters.size)
        val meta = db.detailCacheDao().getDetail(
            4L,
            com.otakup.niriko.data.local.entity.SubjectDetailCacheEntity.SourceKeys.COLLECTIONS_META,
        )
        assertEquals("HTTP 500", meta!!.errorSummary)
    }

    @Test
    fun `元信息行与数据行漂移时当作没缓存_避免静默显示空列表`() = runBlocking {
        store.writeCollections(
            6L,
            listOf(character(1L), character(2L)),
            listOf(StaffInfo(3L, "cv", null, "导演", null)),
            listOf(SubjectRelationInfo(4L, "前作", null, 2, "prequel", null)),
        )
        assertEquals(2, store.readCollections(6L)!!.characters.size)

        // 模拟「数据行被删、元信息行还在」（v30→v31 迁移就是这样，真机实测撞到过）：
        // 以前会读成「取过了且为空」→ 界面整块消失、还不发网络请求。
        db.detailCacheDao().replaceRelations(6L, com.otakup.niriko.data.local.entity.SubjectRelationCacheEntity.RelationKinds.CHARACTERS, emptyList())
        assertNull("条数对不上就必须当作没缓存，让调用方重取", store.readCollections(6L))
    }

    @Test
    fun `标量类来源_能写能读_且空值也算成功记录`() = runBlocking {
        val entries = listOf(InfoBoxEntry("话数", "24"), InfoBoxEntry("放送开始", "2008-10-02"))
        store.writeInfoBox(7L, entries)
        assertEquals(entries, store.readInfoBox(7L))

        val distribution = mapOf(10 to 12, 9 to 40, 8 to 7)
        store.writeRatingDistribution(7L, distribution)
        assertEquals(distribution, store.readRatingDistribution(7L))

        val ratings = listOf(ExternalRating(sourceId = "tmdb", label = "TMDb", score = 8.6f, nativeScore = 86f, scoreMax = 100f))
        store.writeExternalRatings(7L, ratings)
        assertEquals(ratings, store.readExternalRatings(7L))

        // 空的 infobox 是合法结果：写过就该算「取过了」，不能每次进来都重拉
        store.writeInfoBox(8L, emptyList())
        assertEquals(emptyList<InfoBoxEntry>(), store.readInfoBox(8L))
    }

    @Test
    fun `标量类来源_失败不覆盖已落库的成功数据`() = runBlocking {
        store.writeInfoBox(9L, listOf(InfoBoxEntry("话数", "24")))
        store.markValueFailure(9L, SubjectDetailCacheEntity.SourceKeys.INFOBOX, "timeout")
        assertEquals(1, store.readInfoBox(9L)!!.size)
        val row = store.readRow(9L, SubjectDetailCacheEntity.SourceKeys.INFOBOX)!!
        assertEquals("timeout", row.errorSummary)
    }

    @Test
    fun `失效之后回到「从未取过」`() = runBlocking {
        store.writeCollections(5L, listOf(character(1L)), emptyList(), emptyList())
        store.invalidateCollections(5L)
        assertNull(store.readCollections(5L))
    }
}
