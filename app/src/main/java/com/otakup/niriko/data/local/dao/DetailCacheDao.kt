package com.otakup.niriko.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.otakup.niriko.data.local.entity.EpisodeExternalCacheEntity
import com.otakup.niriko.data.local.entity.SubjectDetailCacheEntity
import com.otakup.niriko.data.local.entity.SubjectRelationCacheEntity

/**
 * 详情页外部结果缓存（B15 阶段 1）的三张表共用一个 DAO。
 *
 * 为什么三张表一个 DAO：它们是**同一个关注点**（「详情页这次打开，哪些来源已经取过了」），
 * 拆成三个 DAO 只会让调用方在仓储里拿到三个互相依赖的句柄；而按表拆行保存的收益
 * （按来源失效、按来源判过期）已经由各自的键给出，不需要再拆一层。
 *
 * ## 读路径必须能区分四种状态（§7.3 步骤 5 的验收）
 *
 * - 没有行 = 从未取过；
 * - [getDetail] 有行且 payload != null、errorSummary == null = 成功（payload 可以是「空的合法值」）；
 * - [getDetail] 有行且 errorSummary != null = 最近一次失败，**payload 仍是上一次成功的值**；
 * - [expiresAt] < now = 过期：先展示旧内容、后台刷新，而不是清空（同一行自然承担这件事）。
 */
@Dao
interface DetailCacheDao {

    // ─────────────── subject_detail_cache：按来源的状态与标量结果 ───────────────

    @Query("SELECT * FROM subject_detail_cache WHERE subjectId = :subjectId AND sourceKey = :sourceKey LIMIT 1")
    suspend fun getDetail(subjectId: Long, sourceKey: String): SubjectDetailCacheEntity?

    @Query("SELECT * FROM subject_detail_cache WHERE subjectId = :subjectId")
    suspend fun getDetailAll(subjectId: Long): List<SubjectDetailCacheEntity>

    /** 记录一次**成功**：整行覆盖（含清空上一次的失败信息）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDetail(entry: SubjectDetailCacheEntity)

    /**
     * 记录一次**失败**：只写失败信息，**绝不碰 payload** —— 失败不得覆盖上一次成功的结果。
     *
     * 返回受影响行数：0 表示这一来源从未成功过（连行都没有），调用方据此决定要不要显示错误态。
     */
    @Query(
        "UPDATE subject_detail_cache SET errorSummary = :error, lastErrorAt = :at " +
            "WHERE subjectId = :subjectId AND sourceKey = :sourceKey",
    )
    suspend fun markFailure(subjectId: Long, sourceKey: String, error: String, at: Long): Int

    /**
     * 只失效指定来源（手动绑定 / 解绑 / 换季 / 评分 / 封面修改后按需调用）。
     * 一条都不传就等于什么都不做 —— 调用方不该用空列表表达「全清」，那应该用 [deleteDetailForSubject]。
     */
    @Query("DELETE FROM subject_detail_cache WHERE subjectId = :subjectId AND sourceKey IN (:sourceKeys)")
    suspend fun invalidateDetail(subjectId: Long, sourceKeys: List<String>): Int

    @Query("DELETE FROM subject_detail_cache WHERE subjectId = :subjectId")
    suspend fun deleteDetailForSubject(subjectId: Long): Int

    @Query("DELETE FROM subject_detail_cache WHERE expiresAt < :now")
    suspend fun deleteExpiredDetail(now: Long): Int

    // ─────────────── subject_relation_cache：集合类结果 ───────────────

    @Query(
        "SELECT * FROM subject_relation_cache WHERE subjectId = :subjectId AND kind = :kind " +
            "ORDER BY sortIndex ASC",
    )
    suspend fun getRelations(subjectId: Long, kind: String): List<SubjectRelationCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRelations(entries: List<SubjectRelationCacheEntity>)

    @Query("DELETE FROM subject_relation_cache WHERE subjectId = :subjectId AND kind = :kind")
    suspend fun deleteRelations(subjectId: Long, kind: String): Int

    /**
     * 整块替换某个集合：先删旧行再写新行，放在同一事务里。
     *
     * 为什么必须是整块替换而不是逐条 upsert：角色列表会**变短**（某人被移出制作组），
     * 逐条 upsert 会把已消失的旧行留下来，UI 里就会出现幽灵角色。
     */
    @Transaction
    suspend fun replaceRelations(subjectId: Long, kind: String, entries: List<SubjectRelationCacheEntity>) {
        deleteRelations(subjectId, kind)
        if (entries.isNotEmpty()) putRelations(entries)
    }

    @Query("DELETE FROM subject_relation_cache WHERE expiresAt < :now")
    suspend fun deleteExpiredRelations(now: Long): Int

    // ─────────────── episode_external_cache：每集对齐结果 ───────────────

    @Query("SELECT * FROM episode_external_cache WHERE subjectId = :subjectId AND epId = :epId LIMIT 1")
    suspend fun getEpisodeExternal(subjectId: Long, epId: Long): EpisodeExternalCacheEntity?

    @Query("SELECT * FROM episode_external_cache WHERE subjectId = :subjectId")
    suspend fun getEpisodeExternalAll(subjectId: Long): List<EpisodeExternalCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEpisodeExternal(entry: EpisodeExternalCacheEntity)

    @Query("DELETE FROM episode_external_cache WHERE subjectId = :subjectId")
    suspend fun deleteEpisodeExternalForSubject(subjectId: Long): Int

    @Query("DELETE FROM episode_external_cache WHERE expiresAt < :now")
    suspend fun deleteExpiredEpisodeExternal(now: Long): Int
}
