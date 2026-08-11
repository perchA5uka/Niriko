package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakup.niriko.data.local.entity.PersonCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonCollectionDao {

    @Query("SELECT * FROM person_collections ORDER BY createTime DESC")
    fun observeAll(): Flow<List<PersonCollectionEntity>>

    @Query("SELECT * FROM person_collections WHERE personId = :personId LIMIT 1")
    suspend fun getByPersonId(personId: Long): PersonCollectionEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM person_collections WHERE personId = :personId)")
    suspend fun exists(personId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PersonCollectionEntity): Long

    @Query("DELETE FROM person_collections WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: Long): Int

    /** 查询所有人物收藏（非 Flow，用于导出）。 */
    @Query("SELECT * FROM person_collections ORDER BY createTime DESC")
    suspend fun getAll(): List<PersonCollectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PersonCollectionEntity>)

    /** 清空所有人物收藏（用于导入覆盖）。 */
    @Query("DELETE FROM person_collections")
    suspend fun clearAll()
}
