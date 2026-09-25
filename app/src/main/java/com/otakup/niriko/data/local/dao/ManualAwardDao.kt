package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakup.niriko.data.local.entity.ManualAwardEntity

/** 手动录入的权威机构成绩（Fami通 / Billboard / Oricon / 非 Steam 的 Metacritic）。 */
@Dao
interface ManualAwardDao {

    @Query("SELECT * FROM manual_awards WHERE subjectId = :subjectId ORDER BY createTime DESC")
    suspend fun getBySubject(subjectId: Long): List<ManualAwardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ManualAwardEntity): Long

    @Query("DELETE FROM manual_awards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM manual_awards")
    suspend fun getAll(): List<ManualAwardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ManualAwardEntity>)
}
