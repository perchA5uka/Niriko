package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.otakup.niriko.data.local.entity.AnitabiPointEntity

@Dao
interface AnitabiDao {

    @Query("SELECT * FROM anitabi_points WHERE subjectId = :subjectId LIMIT 1")
    suspend fun getBySubjectId(subjectId: Long): AnitabiPointEntity?

    @Transaction
    suspend fun upsert(point: AnitabiPointEntity) {
        if (update(point) == 0) insert(point)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: AnitabiPointEntity): Long

    @androidx.room.Update
    suspend fun update(point: AnitabiPointEntity): Int
}
