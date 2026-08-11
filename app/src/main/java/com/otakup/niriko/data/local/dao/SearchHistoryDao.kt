package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.otakup.niriko.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY createTime DESC LIMIT 20")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    /** 插入搜索历史。自动去重：同 keyword 覆盖旧记录，更新 createTime。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SearchHistoryEntity)

    /** 插入搜索历史（去重版）：先删旧记录再插入，避免 autoGenerate id 导致重复。 */
    @Transaction
    suspend fun upsertByKeyword(keyword: String) {
        deleteByKeyword(keyword)
        insert(SearchHistoryEntity(keyword = keyword, createTime = System.currentTimeMillis()))
    }

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun deleteByKeyword(keyword: String)

    @Query("DELETE FROM search_history WHERE keyword LIKE :prefix || '%'")
    suspend fun deleteByKeywordPrefix(prefix: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    /** 查询所有搜索历史（非 Flow，用于导出）。 */
    @Query("SELECT * FROM search_history ORDER BY createTime DESC")
    suspend fun getAll(): List<SearchHistoryEntity>

    /** 批量插入搜索历史（用于导入）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SearchHistoryEntity>)
}
