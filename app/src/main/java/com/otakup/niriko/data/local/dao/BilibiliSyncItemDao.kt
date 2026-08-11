package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakup.niriko.data.local.entity.BilibiliSyncItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BilibiliSyncItemDao {

    /** 全部导入快照（预览页按导入时间倒序）。 */
    @Query("SELECT * FROM bilibili_sync_items ORDER BY importTime DESC")
    fun observeAll(): Flow<List<BilibiliSyncItemEntity>>

    /** 全部导入快照（非 Flow，用于导入器合并）。 */
    @Query("SELECT * FROM bilibili_sync_items")
    suspend fun getAll(): List<BilibiliSyncItemEntity>

    /** 按 mediaId 查询单条。 */
    @Query("SELECT * FROM bilibili_sync_items WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getByMediaId(mediaId: Long): BilibiliSyncItemEntity?

    /** 批量覆盖写入（原始快照整体重写，无外键依赖，REPLACE 即可）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BilibiliSyncItemEntity>)

    /** 清空导入快照（重新获取前调用）。 */
    @Query("DELETE FROM bilibili_sync_items")
    suspend fun clearAll()

    /** 标记指定条目的导入完成状态。 */
    @Query("UPDATE bilibili_sync_items SET imported = :imported WHERE mediaId = :mediaId")
    suspend fun setImported(mediaId: Long, imported: Boolean)
}