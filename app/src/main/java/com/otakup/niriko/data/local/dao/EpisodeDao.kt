package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakup.niriko.data.local.entity.EpisodeEntity

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE subjectId = :subjectId ORDER BY sort ASC")
    suspend fun getBySubject(subjectId: Long): List<EpisodeEntity>

    @Query("DELETE FROM episodes WHERE subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: Long)

    @Query("DELETE FROM episodes WHERE lastSyncTime < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    /** 仅更新剧照（TMDb 每集 still），不触碰其它列与 lastSyncTime（Bangumi 侧新鲜度锚点）。 */
    @Query("UPDATE episodes SET stillUrl = :stillUrl WHERE epId = :epId")
    suspend fun updateStillUrl(epId: Long, stillUrl: String?)

    @Query("SELECT * FROM episodes WHERE subjectId = :subjectId AND type = 0 ORDER BY sort ASC")
    suspend fun getMainEpisodes(subjectId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE epId = :epId LIMIT 1")
    suspend fun getById(epId: Long): EpisodeEntity?
}
