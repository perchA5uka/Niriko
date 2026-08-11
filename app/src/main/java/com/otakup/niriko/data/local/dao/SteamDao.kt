package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.otakup.niriko.data.local.entity.SteamBindingEntity
import com.otakup.niriko.data.local.entity.SteamGameEntity

@Dao
interface SteamDao {

    // ==================== 绑定关系 ====================

    @Query("SELECT * FROM steam_bindings WHERE subjectId = :subjectId LIMIT 1")
    suspend fun getBindingBySubjectId(subjectId: Long): SteamBindingEntity?

    @Query("SELECT * FROM steam_bindings WHERE subjectId IN (:subjectIds)")
    suspend fun getBindingsBySubjectIds(subjectIds: List<Long>): List<SteamBindingEntity>

    @Query("SELECT * FROM steam_bindings WHERE steamAppId = :appId LIMIT 1")
    suspend fun getBindingByAppId(appId: Int): SteamBindingEntity?

    /** 更新或插入绑定（update-first，避免 REPLACE 破坏外键关系）。 */
    @Transaction
    suspend fun upsertBinding(binding: SteamBindingEntity) {
        if (updateBinding(binding) == 0) {
            insertBinding(binding)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBinding(binding: SteamBindingEntity): Long

    @androidx.room.Update
    suspend fun updateBinding(binding: SteamBindingEntity): Int

    /** 批量 upsert 绑定（单事务）。 */
    @Transaction
    suspend fun upsertBindings(bindings: List<SteamBindingEntity>) {
        bindings.forEach { upsertBinding(it) }
    }

    // ==================== 扩展数据 ====================

    @Query("SELECT * FROM steam_games WHERE subjectId = :subjectId LIMIT 1")
    suspend fun getGame(subjectId: Long): SteamGameEntity?

    @Query("SELECT * FROM steam_games WHERE subjectId IN (:subjectIds)")
    suspend fun getGamesBySubjectIds(subjectIds: List<Long>): List<SteamGameEntity>

    /** 更新或插入扩展数据。 */
    @Transaction
    suspend fun upsertGame(game: SteamGameEntity) {
        if (updateGame(game) == 0) {
            insertGame(game)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGame(game: SteamGameEntity): Long

    @androidx.room.Update
    suspend fun updateGame(game: SteamGameEntity): Int

    /** 批量 upsert 扩展数据（单事务）。 */
    @Transaction
    suspend fun upsertGames(games: List<SteamGameEntity>) {
        games.forEach { upsertGame(it) }
    }

    // ==================== 占位条目升级 ====================

    /** 把绑定从旧 subjectId（占位 -appId）迁移到新 subjectId（正式 Bangumi id）。 */
    @Query("UPDATE steam_bindings SET subjectId = :newSubjectId WHERE subjectId = :oldSubjectId")
    suspend fun migrateBindingSubjectId(oldSubjectId: Long, newSubjectId: Long): Int

    /** 删除指定 subjectId 的绑定（新 id 已存在绑定时的冲突清理）。 */
    @Query("DELETE FROM steam_bindings WHERE subjectId = :subjectId")
    suspend fun deleteBindingBySubjectId(subjectId: Long): Int

    /** 把扩展数据从旧 subjectId 迁移到新 subjectId。 */
    @Query("UPDATE steam_games SET subjectId = :newSubjectId WHERE subjectId = :oldSubjectId")
    suspend fun migrateGameSubjectId(oldSubjectId: Long, newSubjectId: Long): Int

    /** 删除指定 subjectId 的扩展数据（新 id 已存在时的冲突清理）。 */
    @Query("DELETE FROM steam_games WHERE subjectId = :subjectId")
    suspend fun deleteGameBySubjectId(subjectId: Long): Int
}
