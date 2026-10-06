package com.otakup.niriko.data.backup

import android.content.Context
import androidx.room.Room
import com.otakup.niriko.data.local.NirikoDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 分区备份**恢复**（F09）的行为测试。
 *
 * 验收线来自 §10.2：「备份恢复」「重复导入备份幂等」。这两条都是**会静默出错**的地方 ——
 * 重复导入如果每次新建一个同名分区，用户会在导航条上看到三个「待补」，而他只建过一个。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupFolderRestoreTest {

    private lateinit var db: NirikoDatabase
    private val dao get() = db.libraryFolderDao()

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, NirikoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun folder(
        name: String,
        members: List<Long> = emptyList(),
        sortOrder: Int = 0,
        collapsed: Boolean = false,
    ) = BackupFolder(
        name = name,
        sortOrder = sortOrder,
        isCollapsed = collapsed,
        createdAt = 1_000L,
        subjectIds = members,
    )

    @Test
    fun `全覆盖恢复写入分区与成员_且成员保序`() = runBlocking {
        val outcome = BackupFolderRestore.restore(
            dao = dao,
            folders = listOf(folder("漫画", members = listOf(30L, 10L, 20L))),
            fullReplace = false,
        )
        assertEquals(1, outcome.foldersCreated)
        assertEquals(1, outcome.foldersPopulated)
        val saved = dao.getFolders().single()
        assertEquals("漫画", saved.name)
        // 成员顺序 = 备份里的顺序（不重排，用户排过的顺序要保住）
        assertEquals(listOf(30L, 10L, 20L), dao.getSubjectIdsInFolder(saved.id))
    }

    @Test
    fun `重复导入同一份备份是幂等的`() = runBlocking {
        val backup = listOf(folder("待补", members = listOf(1L, 2L)))
        BackupFolderRestore.restore(dao, backup, fullReplace = true)
        val second = BackupFolderRestore.restore(dao, backup, fullReplace = true)
        assertEquals("全覆盖恢复每次都先清空，因此不会出现两个同名分区", 1, dao.getFolders().size)
        assertEquals("第二次仍然是「新建一个」", 1, second.foldersCreated)
        assertEquals(2, dao.memberCount(dao.getFolders().single().id))
    }

    @Test
    fun `合并恢复不动本地同名分区的成员`() = runBlocking {
        // 本地已有「待补」并放了作品 99
        val localId = dao.insertFolder(
            com.otakup.niriko.data.local.entity.LibraryFolderEntity(name = "待补", sortOrder = 0),
        )
        dao.addSubject(
            com.otakup.niriko.data.local.entity.LibraryFolderSubjectEntity(folderId = localId, subjectId = 99L),
        )
        // 备份里「待补」的成员是 1、2（旧快照）
        val outcome = BackupFolderRestore.restore(
            dao = dao,
            folders = listOf(folder("待补", members = listOf(1L, 2L))),
            fullReplace = false,
        )
        assertEquals("同名分区不新建", 0, outcome.foldersCreated)
        assertEquals("本地成员一律保留，绝不掺入备份旧成员", listOf(99L), dao.getSubjectIdsInFolder(localId))
    }

    @Test
    fun `合并恢复会补上本地没有的分区`() = runBlocking {
        val outcome = BackupFolderRestore.restore(
            dao = dao,
            folders = listOf(folder("漫画", members = listOf(5L))),
            fullReplace = false,
        )
        assertEquals(1, outcome.foldersCreated)
        assertEquals(listOf(5L), dao.getSubjectIdsInFolder(dao.getFolders().single().id))
    }

    @Test
    fun `空名分区被丢弃_不产生无名字的导航项`() = runBlocking {
        val outcome = BackupFolderRestore.restore(
            dao = dao,
            folders = listOf(folder("   "), folder(""), folder("正常")),
            fullReplace = false,
        )
        assertEquals(1, dao.getFolders().size)
        assertEquals("正常", dao.getFolders().single().name)
        assertEquals(1, outcome.foldersCreated)
    }

    @Test
    fun `成员去重_同一部作品不会在分区里出现两次`() = runBlocking {
        BackupFolderRestore.restore(
            dao = dao,
            folders = listOf(folder("漫画", members = listOf(7L, 7L, 8L))),
            fullReplace = false,
        )
        val id = dao.getFolders().single().id
        assertEquals(2, dao.memberCount(id))
        assertEquals(listOf(7L, 8L), dao.getSubjectIdsInFolder(id))
    }

    @Test
    fun `覆盖恢复会清掉本地已有分区`() = runBlocking {
        dao.insertFolder(com.otakup.niriko.data.local.entity.LibraryFolderEntity(name = "本地旧分区"))
        BackupFolderRestore.restore(
            dao = dao,
            folders = listOf(folder("备份分区")),
            fullReplace = true,
        )
        assertEquals(listOf("备份分区"), dao.getFolders().map { it.name })
        assertTrue(dao.getFolders().none { it.name == "本地旧分区" })
    }

    @Test
    fun `折叠状态随分区一起恢复`() = runBlocking {
        BackupFolderRestore.restore(
            dao = dao,
            folders = listOf(folder("收起的", collapsed = true)),
            fullReplace = false,
        )
        assertTrue(dao.getFolders().single().isCollapsed)
    }
}
