package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.otakup.niriko.data.local.entity.AniListBindingEntity

@Dao
interface AniListDao {

    @Query("SELECT * FROM anilist_bindings WHERE subjectId = :subjectId LIMIT 1")
    suspend fun getBindingBySubjectId(subjectId: Long): AniListBindingEntity?

    @Query("SELECT * FROM anilist_bindings WHERE anilistId = :anilistId LIMIT 1")
    suspend fun getBindingByAnilistId(anilistId: Long): AniListBindingEntity?

    @Query("SELECT * FROM anilist_bindings WHERE subjectId IN (:subjectIds)")
    suspend fun getBindingsBySubjectIds(subjectIds: List<Long>): List<AniListBindingEntity>

    /** 更新或插入绑定（update-first，避免 REPLACE 破坏外键关系）。 */
    @Transaction
    suspend fun upsertBinding(binding: AniListBindingEntity) {
        if (updateBinding(binding) == 0) {
            insertBinding(binding)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBinding(binding: AniListBindingEntity): Long

    @androidx.room.Update
    suspend fun updateBinding(binding: AniListBindingEntity): Int

    /** 删除绑定（解绑操作）。 */
    @Query("DELETE FROM anilist_bindings WHERE subjectId = :subjectId")
    suspend fun deleteBindingBySubjectId(subjectId: Long): Int
}
