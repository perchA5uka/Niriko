package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY updateTime DESC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collections WHERE subjectId = :subjectId LIMIT 1")
    fun observeBySubjectId(subjectId: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE subjectId = :subjectId LIMIT 1")
    suspend fun getBySubjectId(subjectId: Long): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: CollectionEntity): Long

    @Update
    suspend fun update(collection: CollectionEntity): Int

    @Delete
    suspend fun delete(collection: CollectionEntity): Int

    @Query("DELETE FROM collections WHERE subjectId = :subjectId")
    suspend fun deleteBySubjectId(subjectId: Long): Int

    /** 占位条目升级：把收藏从旧 subjectId 迁移到新 subjectId（新 id 无收藏时）。 */
    @Query("UPDATE collections SET subjectId = :newSubjectId, updateTime = :now WHERE subjectId = :oldSubjectId")
    suspend fun migrateSubjectId(oldSubjectId: Long, newSubjectId: Long, now: Long = System.currentTimeMillis()): Int

    /** 查询所有收藏（非 Flow，用于导出）。 */
    @Query("SELECT * FROM collections ORDER BY updateTime DESC")
    suspend fun getAll(): List<CollectionEntity>

    /** 清空所有收藏（用于导入覆盖）。 */
    @Query("DELETE FROM collections")
    suspend fun clearAll()

    /** 批量插入收藏（用于导入）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CollectionEntity>)

    @Query("SELECT COUNT(*) FROM collections")
    fun observeCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM collections ORDER BY updateTime DESC")
    fun observeAllWithSubject(): Flow<List<CollectionWithSubject>>

    @Transaction
    @Query(
        "SELECT collections.* FROM collections INNER JOIN subjects " +
        "ON collections.subjectId = subjects.subjectId " +
        "WHERE subjects.title LIKE '%' || :keyword || '%' " +
        "OR subjects.titleCN LIKE '%' || :keyword || '%' " +
        "ORDER BY collections.updateTime DESC"
    )
    fun observeByKeyword(keyword: String): Flow<List<CollectionWithSubject>>
}