package com.otakup.niriko.data.local

import android.content.Context
import androidx.room.Room
import com.otakup.niriko.data.local.dao.LibraryFolderDao
import com.otakup.niriko.data.local.entity.LibraryFolderEntity
import com.otakup.niriko.data.local.entity.LibraryFolderSubjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 作品库自定义分区（F09）DAO 的行为单测。
 *
 * 锁的不是「SQL 能跑」，而是 §10.1/§10.2 里凡是**会静默弄坏用户数据**的语义：
 * - 一部作品可属于多个分区（是「加入」而不是「移动」）；
 * - 重复加入同一分区是幂等的（否则分区里会出现两张一样的卡）；
 * - 删除分区**只删关系**，不碰作品主表（§14 回滚条款）；
 * - 成员整块替换不会留下幽灵行（备份恢复用）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryFolderDaoTest {

    private lateinit var db: NirikoDatabase
    private lateinit var dao: LibraryFolderDao

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NirikoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.libraryFolderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun folder(name: String = "本季新番", sortOrder: Int = 0) = LibraryFolderEntity(
        name = name,
        sortOrder = sortOrder,
    )

    private fun member(folderId: Long, subjectId: Long, sortOrder: Int = 0) =
        LibraryFolderSubjectEntity(folderId = folderId, subjectId = subjectId, sortOrder = sortOrder)

    @Test
    fun `新建分区后能按顺序读回来`() = runBlocking {
        val a = dao.insertFolder(folder("先建的", sortOrder = 0))
        val b = dao.insertFolder(folder("后建的", sortOrder = 1))
        val list = dao.getFolders()
        assertEquals(listOf(a, b), list.map { it.id })
        assertEquals("先建的", list[0].name)
        assertFalse("新建分区默认展开", list[0].isCollapsed)
        assertNotNull(dao.getFolder(a))
        assertNull(dao.getFolder(999L))
    }

    @Test
    fun `一部作品可以同时属于多个分区`() = runBlocking {
        val a = dao.insertFolder(folder("漫画", 0))
        val b = dao.insertFolder(folder("待补", 1))
        dao.addSubject(member(a, 100L))
        dao.addSubject(member(b, 100L))
        assertEquals(listOf(100L), dao.getSubjectIdsInFolder(a))
        assertEquals(listOf(100L), dao.getSubjectIdsInFolder(b))
        assertEquals(setOf(a, b), dao.getFolderIdsOfSubject(100L).toSet())
        assertEquals(1, dao.isMember(a, 100L))
        assertEquals(0, dao.isMember(a, 999L))
    }

    @Test
    fun `重复加入同一分区是幂等的`() = runBlocking {
        val f = dao.insertFolder(folder())
        dao.addSubject(member(f, 100L, sortOrder = 0))
        dao.addSubject(member(f, 100L, sortOrder = 5))
        // 联合主键 + REPLACE：只更新那一行，不会变成两行
        assertEquals(1, dao.memberCount(f))
        assertEquals(listOf(100L), dao.getSubjectIdsInFolder(f))
    }

    @Test
    fun `删除分区只删关系_不碰作品主表`() = runBlocking {
        val f = dao.insertFolder(folder())
        dao.addSubject(member(f, 100L))
        dao.addSubject(member(f, 200L))
        // 直接插一条真实作品行（模拟用户已有的收藏数据）
        db.subjectDao().insertAll(
            listOf(
                com.otakup.niriko.data.local.entity.SubjectEntity(
                    subjectId = 100L,
                    title = "作品 100",
                    type = com.otakup.niriko.data.model.SubjectType.ANIME,
                ),
            ),
        )

        dao.deleteFolder(f)

        assertNull("分区行必须被删掉", dao.getFolder(f))
        assertEquals("关系行必须一起清掉，否则下次同名分区会凭空多出成员", 0, dao.memberCount(f))
        assertNotNull("作品主表一动不动（§14：删除分区不触碰作品主表）", db.subjectDao().getById(100L))
    }

    @Test
    fun `移除成员只影响该分区`() = runBlocking {
        val a = dao.insertFolder(folder("A", 0))
        val b = dao.insertFolder(folder("B", 1))
        dao.addSubject(member(a, 100L))
        dao.addSubject(member(b, 100L))
        dao.removeSubject(a, 100L)
        assertEquals(emptyList<Long>(), dao.getSubjectIdsInFolder(a))
        assertEquals(listOf(100L), dao.getSubjectIdsInFolder(b))
    }

    @Test
    fun `批量移除只作用于指定分区`() = runBlocking {
        val a = dao.insertFolder(folder("A", 0))
        val b = dao.insertFolder(folder("B", 1))
        dao.addSubjects(listOf(member(a, 1L), member(a, 2L), member(b, 1L)))
        dao.removeSubjects(a, listOf(1L, 2L))
        assertEquals(0, dao.memberCount(a))
        assertEquals(1, dao.memberCount(b))
    }

    @Test
    fun `整块替换成员不会留下幽灵行`() = runBlocking {
        val f = dao.insertFolder(folder())
        dao.addSubject(member(f, 100L))
        dao.addSubject(member(f, 200L))
        // 替换成只剩一个成员（模拟「某人被移出分区」的恢复结果）
        dao.replaceMembers(f, listOf(member(f, 200L)))
        assertEquals(listOf(200L), dao.getSubjectIdsInFolder(f))
        // 替换成空集合也不允许留下旧行
        dao.replaceMembers(f, emptyList())
        assertEquals(0, dao.memberCount(f))
    }

    @Test
    fun `折叠状态与顺序可持久化`() = runBlocking {
        val f = dao.insertFolder(folder())
        dao.setCollapsed(f, true)
        dao.setSortOrder(f, 7)
        val row = dao.getFolder(f)
        assertTrue(row!!.isCollapsed)
        assertEquals(7, row.sortOrder)
        // 新分区默认排在最后（0 个分区时最大值为 -1）
        assertEquals(7, dao.maxSortOrder())
        assertEquals(1, dao.folderCount())
    }

    @Test
    fun `批量加入同一批里的重复项被去重`() = runBlocking {
        val f = dao.insertFolder(folder())
        dao.addSubjects(listOf(member(f, 1L), member(f, 2L), member(f, 1L)))
        assertEquals(2, dao.memberCount(f))
    }

    /**
     * 成员变化必须**触碰分区行**（updatedAt 前移）。
     *
     * 导航条的成员数来自 observeFolders()，而这条流只在分区行变化时才发新值 ——
     * 只写关系行的话，用户会看到「加进去了但数字没变」。这是真机上一眼能发现的错，
     * 因此在 DAO 这一层就把它锁死（AndTouch 系列必须真的更新 updatedAt）。
     */
    @Test
    fun `加成员与移除都会触碰分区行`() = runBlocking {
        val f = dao.insertFolder(folder().copy(updatedAt = 1_000L))
        assertEquals(1_000L, dao.getFolder(f)!!.updatedAt)

        dao.addSubjectAndTouch(member(f, 100L))
        val afterAdd = dao.getFolder(f)!!.updatedAt
        assertTrue("加入成员后 updatedAt 必须前移，否则导航条计数不会刷新", afterAdd > 1_000L)

        dao.removeSubjectAndTouch(f, 100L)
        val afterRemove = dao.getFolder(f)!!.updatedAt
        assertTrue("移除成员后 updatedAt 也必须前移", afterRemove >= afterAdd)
        assertEquals(0, dao.memberCount(f))
    }

    @Test
    fun `批量加入同样触碰分区行`() = runBlocking {
        val f = dao.insertFolder(folder().copy(updatedAt = 1_000L))
        dao.addSubjectsAndTouch(f, listOf(member(f, 1L), member(f, 2L)))
        assertTrue(dao.getFolder(f)!!.updatedAt > 1_000L)
        assertEquals(2, dao.memberCount(f))
    }

    @Test
    fun `重命名不改成员`() = runBlocking {
        val f = dao.insertFolder(folder("旧名"))
        dao.addSubject(member(f, 100L))
        dao.renameFolder(f, "新名")
        assertEquals("新名", dao.getFolder(f)!!.name)
        assertEquals(1, dao.memberCount(f))
    }
}
