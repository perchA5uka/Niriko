package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.otakup.niriko.data.local.entity.LibraryFolderEntity
import com.otakup.niriko.data.local.entity.LibraryFolderSubjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * 作品库自定义分区（清单 F09）的读写。
 *
 * 两条语义比「SQL 能跑」重要：
 * 1. **删除分区只删关系，不删作品** —— `deleteFolder` 先删关系行再删分区行，两者同一事务；
 *    任何一步失败都不会留下「分区没了、关系行还在」的孤儿数据（那会让下次同名分区凭空多出成员）。
 * 2. **加入成员是幂等的** —— 联合主键 + REPLACE 语义，重复加入同一部作品只更新顺序与时间，
 *    不会在分区里出现两张一样的卡。
 */
@Dao
interface LibraryFolderDao {

    @Query("SELECT * FROM library_folder ORDER BY sortOrder ASC, id ASC")
    fun observeFolders(): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folder ORDER BY sortOrder ASC, id ASC")
    suspend fun getFolders(): List<LibraryFolderEntity>

    @Query("SELECT * FROM library_folder WHERE id = :folderId")
    suspend fun getFolder(folderId: Long): LibraryFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: LibraryFolderEntity): Long

    @Query("UPDATE library_folder SET name = :name, updatedAt = :updatedAt WHERE id = :folderId")
    suspend fun renameFolder(folderId: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE library_folder SET isCollapsed = :collapsed, updatedAt = :updatedAt WHERE id = :folderId")
    suspend fun setCollapsed(folderId: Long, collapsed: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE library_folder SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :folderId")
    suspend fun setSortOrder(folderId: Long, sortOrder: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM library_folder")
    suspend fun maxSortOrder(): Int

    @Query("SELECT COUNT(*) FROM library_folder")
    suspend fun folderCount(): Int

    // ===== 成员关系 =====

    @Query("SELECT subjectId FROM library_folder_subject WHERE folderId = :folderId ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getSubjectIdsInFolder(folderId: Long): List<Long>

    @Query("SELECT * FROM library_folder_subject")
    suspend fun getAllMembers(): List<LibraryFolderSubjectEntity>

    @Query("SELECT folderId FROM library_folder_subject WHERE subjectId = :subjectId")
    suspend fun getFolderIdsOfSubject(subjectId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM library_folder_subject WHERE folderId = :folderId AND subjectId = :subjectId")
    suspend fun isMember(folderId: Long, subjectId: Long): Int

    @Query("UPDATE library_folder SET updatedAt = :updatedAt WHERE id = :folderId")
    suspend fun touchFolder(folderId: Long, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSubject(member: LibraryFolderSubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSubjects(members: List<LibraryFolderSubjectEntity>)

    /**
     * 加入成员并**触碰分区行**。
     *
     * 为什么必须触碰：导航条上的成员数来自 `observeFolders()` 这条流，它只在分区行变化时发新值。
     * 只写关系行的话，用户会看到「加进去了但数字没变」—— 这是真机上一眼就能发现的错。
     */
    @Transaction
    suspend fun addSubjectAndTouch(member: LibraryFolderSubjectEntity) {
        addSubject(member)
        touchFolder(member.folderId)
    }

    @Transaction
    suspend fun removeSubjectAndTouch(folderId: Long, subjectId: Long) {
        removeSubject(folderId, subjectId)
        touchFolder(folderId)
    }

    @Transaction
    suspend fun removeSubjectsAndTouch(folderId: Long, subjectIds: List<Long>) {
        if (subjectIds.isEmpty()) return
        removeSubjects(folderId, subjectIds)
        touchFolder(folderId)
    }

    @Transaction
    suspend fun addSubjectsAndTouch(folderId: Long, members: List<LibraryFolderSubjectEntity>) {
        if (members.isNotEmpty()) addSubjects(members)
        // 即使 members 为空也触碰一次：调用方认为「加入动作发生过」，让 UI 立刻重算而不是等下次
        touchFolder(folderId)
    }

    @Query("DELETE FROM library_folder_subject WHERE folderId = :folderId AND subjectId = :subjectId")
    suspend fun removeSubject(folderId: Long, subjectId: Long)

    @Query("DELETE FROM library_folder_subject WHERE folderId = :folderId AND subjectId IN (:subjectIds)")
    suspend fun removeSubjects(folderId: Long, subjectIds: List<Long>)

    @Query("DELETE FROM library_folder_subject WHERE folderId = :folderId")
    suspend fun clearFolderMembers(folderId: Long)

    /**
     * 清掉「该作品已经不在收藏里」的成员关系。
     *
     * WebDAV 下载会整体重写 collections 表，而分区不参与同步 —— 不清理就会出现
     * 「分区里显示 12 部、点进去只有 9 部」的幽灵成员（§10.2 第 6 条的边界）。
     * 同时触碰受影响的每个分区行，让导航条上的计数立刻刷新。
     */
    @Query("DELETE FROM library_folder_subject WHERE subjectId NOT IN (SELECT subjectId FROM collections)")
    suspend fun pruneMembersNotInCollections()

    @Query("SELECT DISTINCT folderId FROM library_folder_subject WHERE subjectId NOT IN (SELECT subjectId FROM collections)")
    suspend fun foldersWithOrphanMembers(): List<Long>

    @Query("SELECT COUNT(*) FROM library_folder_subject WHERE folderId = :folderId")
    suspend fun memberCount(folderId: Long): Int

    /** 删除整个分区（含成员关系）。同一事务，不会留下孤儿关系行。 */
    @Transaction
    suspend fun deleteFolder(folderId: Long) {
        clearFolderMembers(folderId)
        deleteFolderRow(folderId)
    }

    @Query("DELETE FROM library_folder WHERE id = :folderId")
    suspend fun deleteFolderRow(folderId: Long)

    /** 整块替换分区成员（备份恢复用）：先清空再写入，避免旧成员被留下变成幽灵。 */
    @Transaction
    suspend fun replaceMembers(folderId: Long, members: List<LibraryFolderSubjectEntity>) {
        clearFolderMembers(folderId)
        if (members.isNotEmpty()) addSubjects(members)
    }

    @Query("DELETE FROM library_folder_subject")
    suspend fun clearAllMembers()

    @Query("DELETE FROM library_folder")
    suspend fun clearAllFolders()

    /** 清空所有分区与成员（备份全覆盖恢复用）。同一事务：要么都清掉，要么都不动。 */
    @Transaction
    suspend fun clearAllFoldersAndMembers() {
        clearAllMembers()
        clearAllFolders()
    }
}
