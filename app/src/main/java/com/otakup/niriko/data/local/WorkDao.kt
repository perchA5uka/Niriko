package com.otakup.niriko.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.WorkType
import kotlinx.coroutines.flow.Flow

/**
 * 作品表数据访问。
 * 列表查询返回 [Flow]，增删改使用 suspend。
 */
@Dao
interface WorkDao {

    @Query("SELECT * FROM work_items ORDER BY updateTime DESC")
    fun observeAll(): Flow<List<WorkItem>>

    @Query("SELECT * FROM work_items WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<WorkItem?>

    @Query("SELECT * FROM work_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkItem?

    /**
     * 按类型、状态、关键字过滤。
     * 传入 null 表示该维度不限制；[keyword] 为空字符串表示不按关键字过滤。
     */
    @Query(
        """
        SELECT * FROM work_items
        WHERE (:type IS NULL OR type = :type)
          AND (:status IS NULL OR status = :status)
          AND (
                :keyword = ''
                OR title LIKE '%' || :keyword || '%'
                OR IFNULL(remark, '') LIKE '%' || :keyword || '%'
              )
        ORDER BY updateTime DESC
        """,
    )
    fun observeFiltered(
        type: WorkType?,
        status: WatchStatus?,
        keyword: String,
    ): Flow<List<WorkItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(work: WorkItem): Long

    @Update
    suspend fun update(work: WorkItem): Int

    @Delete
    suspend fun delete(work: WorkItem): Int

    @Query("DELETE FROM work_items WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT COUNT(*) FROM work_items")
    fun observeCount(): Flow<Int>

    /** 查询所有手工作品（非 Flow，用于导出）。 */
    @Query("SELECT * FROM work_items ORDER BY updateTime DESC")
    suspend fun getAll(): List<WorkItem>

    /** 清空所有手工作品（用于导入覆盖）。 */
    @Query("DELETE FROM work_items")
    suspend fun clearAll()

    /** 批量插入手工作品（用于导入）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<WorkItem>)
}
