package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity

/** 作品外部身份绑定（TMDb / IMDb / IGDB / MusicBrainz / ...）。 */
@Dao
interface ExternalIdDao {

    @Query("SELECT * FROM subject_external_ids WHERE subjectId = :subjectId")
    suspend fun getBySubject(subjectId: Long): List<SubjectExternalIdEntity>

    @Query("SELECT * FROM subject_external_ids WHERE subjectId = :subjectId AND provider = :provider LIMIT 1")
    suspend fun get(subjectId: Long, provider: String): SubjectExternalIdEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubjectExternalIdEntity)

    @Query("DELETE FROM subject_external_ids WHERE subjectId = :subjectId AND provider = :provider")
    suspend fun delete(subjectId: Long, provider: String)

    @Query("SELECT * FROM subject_external_ids")
    suspend fun getAll(): List<SubjectExternalIdEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SubjectExternalIdEntity>)
}
