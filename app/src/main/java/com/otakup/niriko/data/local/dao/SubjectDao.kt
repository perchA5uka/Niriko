package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.otakup.niriko.data.local.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Query("SELECT * FROM subjects ORDER BY title ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE subjectId = :id LIMIT 1")
    fun observeById(id: Long): Flow<SubjectEntity?>

    @Query("SELECT * FROM subjects WHERE subjectId = :id LIMIT 1")
    suspend fun getById(id: Long): SubjectEntity?

    /** 查询指定 id 集合中已存在的 subjectId（用于外键完整性校验）。 */
    @Query("SELECT subjectId FROM subjects WHERE subjectId IN (:ids)")
    suspend fun getExistingIds(ids: List<Long>): List<Long>

    @Query(
        """
        SELECT * FROM subjects 
        WHERE title LIKE '%' || :keyword || '%' 
           OR titleCN LIKE '%' || :keyword || '%'
        ORDER BY title ASC
        """
    )
    fun searchByKeyword(keyword: String): Flow<List<SubjectEntity>>

    /** 查询所有作品（非 Flow，用于导出）。 */
    @Query("SELECT * FROM subjects")
    suspend fun getAll(): List<SubjectEntity>

    /** 清空所有作品（用于导入覆盖）。 */
    @Query("DELETE FROM subjects")
    suspend fun clearAll()

    /** 批量插入作品（用于导入）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(subjects: List<SubjectEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(subject: SubjectEntity)

    @Update
    suspend fun update(subject: SubjectEntity): Int

    @Query("DELETE FROM subjects WHERE subjectId = :id")
    suspend fun deleteById(id: Long)

    /**
     * 查询 airDate 在指定范围内的作品（ISO 日期字符串，天然字典序可比较）。
     * 用于本地回退放送日历数据。
     */
    @Query(
        """
        SELECT * FROM subjects 
        WHERE airDate IS NOT NULL 
          AND airDate >= :startDate
          AND airDate < :endDate
        ORDER BY airDate ASC, ratingScore DESC
        """
    )
    suspend fun getSubjectsInAirDateRange(startDate: String, endDate: String): List<SubjectEntity>

    /** 查询所有有 airDate 的作品。 */
    @Query("SELECT * FROM subjects WHERE airDate IS NOT NULL ORDER BY airDate DESC")
    suspend fun getAllWithAirDate(): List<SubjectEntity>

    /** 按数据源查询本地作品（如 sourceId="steam" 的 Steam 条目）。 */
    @Query("SELECT * FROM subjects WHERE sourceId = :sourceId ORDER BY subjectId ASC")
    suspend fun getBySource(sourceId: String): List<SubjectEntity>

    /** 旧负数占位条目（sourceId="steam" 且 subjectId<0；sourceKey 迁移前遗留）。 */
    @Query("SELECT * FROM subjects WHERE sourceId = 'steam' AND subjectId < 0 ORDER BY subjectId ASC")
    suspend fun getLegacySteamPlaceholders(): List<SubjectEntity>

    /** 当前最大 subjectId（迁移分配新 id 用）。 */
    @Query("SELECT COALESCE(MAX(subjectId), 0) FROM subjects")
    suspend fun getMaxSubjectId(): Long

    /** 按标题前缀搜索本地作品（用于自动补全建议）。 */
    @Query(
        """
        SELECT * FROM subjects 
        WHERE title LIKE :keyword || '%' 
           OR titleCN LIKE :keyword || '%'
        ORDER BY 
            CASE 
                WHEN title LIKE :keyword || '%' THEN 0 
                ELSE 1 
            END,
            ratingScore DESC
        LIMIT 5
        """
    )
    suspend fun searchByKeywordPrefix(keyword: String): List<SubjectEntity>

    /** 更新或插入（update-first 模式，避免 REPLACE 影响外键关系）。 */
    @Transaction
    suspend fun upsert(subject: SubjectEntity) {
        if (update(subject) == 0) {
            insert(subject)
        }
    }

    /**
     * 批量更新或插入（单事务提交，合并为一次失效通知）。
     * 搜索/榜单等远程结果批量落库时使用，避免逐条提交导致的多次查询失效风暴。
     */
    @Transaction
    suspend fun upsertAll(subjects: List<SubjectEntity>) {
        subjects.forEach { upsert(it) }
    }
}