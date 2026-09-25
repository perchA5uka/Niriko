package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakup.niriko.data.local.entity.EpisodeMyRatingEntity
import com.otakup.niriko.data.local.entity.EpisodeRatingEntity
import com.otakup.niriko.data.local.entity.SubjectExternalRatingEntity

/**
 * 权威评分缓存：作品级（subject_external_ratings）、每集级（episode_ratings）
 * 与我的每集评分（episode_my_ratings）。
 *
 * 注意：**我的每集评分是用户数据**（随备份同步），另两张是纯远端缓存（可重抓）。
 */
@Dao
interface ExternalRatingDao {

    // ==================== 作品级权威评分 ====================

    @Query("SELECT * FROM subject_external_ratings WHERE subjectId = :subjectId")
    suspend fun getSubjectRatings(subjectId: Long): List<SubjectExternalRatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubjectRatings(entities: List<SubjectExternalRatingEntity>)

    @Query("DELETE FROM subject_external_ratings WHERE subjectId = :subjectId")
    suspend fun deleteSubjectRatings(subjectId: Long)

    @Query("SELECT * FROM subject_external_ratings")
    suspend fun getAllSubjectRatings(): List<SubjectExternalRatingEntity>

    // ==================== 每集评分 ====================

    @Query("SELECT * FROM episode_ratings WHERE subjectId = :subjectId")
    suspend fun getEpisodeRatings(subjectId: Long): List<EpisodeRatingEntity>

    @Query("SELECT * FROM episode_ratings WHERE subjectId = :subjectId AND sourceId = :sourceId")
    suspend fun getEpisodeRatingsBySource(subjectId: Long, sourceId: String): List<EpisodeRatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeRatings(entities: List<EpisodeRatingEntity>)

    @Query("DELETE FROM episode_ratings WHERE subjectId = :subjectId AND sourceId = :sourceId")
    suspend fun deleteEpisodeRatingsBySource(subjectId: Long, sourceId: String)

    // ==================== 我的每集评分（用户数据） ====================

    @Query("SELECT * FROM episode_my_ratings WHERE subjectId = :subjectId")
    suspend fun getMyEpisodeRatings(subjectId: Long): List<EpisodeMyRatingEntity>

    @Query("SELECT * FROM episode_my_ratings WHERE epId = :epId LIMIT 1")
    suspend fun getMyEpisodeRating(epId: Long): EpisodeMyRatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMyEpisodeRating(entity: EpisodeMyRatingEntity)

    @Query("DELETE FROM episode_my_ratings WHERE epId = :epId")
    suspend fun deleteMyEpisodeRating(epId: Long)

    @Query("SELECT * FROM episode_my_ratings")
    suspend fun getAllMyEpisodeRatings(): List<EpisodeMyRatingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMyEpisodeRatings(entities: List<EpisodeMyRatingEntity>)
}
