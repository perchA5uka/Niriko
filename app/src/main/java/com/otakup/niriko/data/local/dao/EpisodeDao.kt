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

    /**
     * 仅更新剧照（TMDb 每集 still），不触碰其它列与 lastSyncTime（Bangumi 侧新鲜度锚点）。
     *
     * `AND :stillUrl IS NOT NULL` 是必要的护栏：TMDb 的 still_path 经常为空，
     * 改造前无条件 `SET stillUrl = :stillUrl` 会把已经回填好的剧照清成 NULL——
     * 用户看到的「单集详情没有图片」正是被这一步擦掉的。
     */
    @Query("UPDATE episodes SET stillUrl = :stillUrl WHERE epId = :epId AND :stillUrl IS NOT NULL")
    suspend fun updateStillUrl(epId: Long, stillUrl: String?)

    /**
     * 仅在标题为空时写入（B13：TMDb 补 Bangumi 缺失的分集标题）。
     *
     * 条件写在 SQL 里是双保险：已有标题永远不会被 TMDb 覆盖。
     */
    @Query("UPDATE episodes SET name = :name WHERE epId = :epId AND (name IS NULL OR TRIM(name) = '')")
    suspend fun updateNameIfBlank(epId: Long, name: String)

    @Query("SELECT * FROM episodes WHERE subjectId = :subjectId AND type = 0 ORDER BY sort ASC")
    suspend fun getMainEpisodes(subjectId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE epId = :epId LIMIT 1")
    suspend fun getById(epId: Long): EpisodeEntity?
}
