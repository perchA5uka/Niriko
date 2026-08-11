package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakup.niriko.data.local.entity.SteamLibraryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SteamLibraryItemDao {

    /** 全部导入快照（预览页按导入时间倒序）。 */
    @Query("SELECT * FROM steam_library_items ORDER BY importTime DESC")
    fun observeAll(): Flow<List<SteamLibraryItemEntity>>

    /** 全部导入快照（非 Flow，用于导入器合并）。 */
    @Query("SELECT * FROM steam_library_items")
    suspend fun getAll(): List<SteamLibraryItemEntity>

    /** 按 appid 查询单条。 */
    @Query("SELECT * FROM steam_library_items WHERE appId = :appId LIMIT 1")
    suspend fun getByAppId(appId: Int): SteamLibraryItemEntity?

    /** 批量覆盖写入（原始快照整体重写，无外键依赖，REPLACE 即可）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SteamLibraryItemEntity>)

    /** 清空导入快照（重新拉取前调用）。 */
    @Query("DELETE FROM steam_library_items")
    suspend fun clearAll()

    /** 标记指定条目的导入完成状态。 */
    @Query("UPDATE steam_library_items SET imported = :imported WHERE appId = :appId")
    suspend fun setImported(appId: Int, imported: Boolean)

    /** 占位条目升级为正式 Bangumi 词条后，更新快照的匹配结果。 */
    @Query("UPDATE steam_library_items SET bgmSubjectId = :bgmSubjectId, isPlaceholder = 0 WHERE appId = :appId")
    suspend fun upgradeBinding(appId: Int, bgmSubjectId: Long)
}
