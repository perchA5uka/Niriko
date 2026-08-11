package com.otakup.niriko.data.local

import androidx.room.withTransaction
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SteamDao
import com.otakup.niriko.data.local.dao.SubjectDao

/**
 * 负数占位条目 → sourceKey 正式条目迁移器（一次性，幂等）。
 *
 * 背景：旧版 Steam 独占占位条目用负数 subjectId（-appId）+ sourceId="steam" 表示，
 * 与 Bangumi 正数 id 冲突风险/语义混乱。v18 起改用正 subjectId + sourceKey（"steam:{appId}"）。
 *
 * 迁移策略（满足 collections 外键约束，避免改动外键定义）：
 * 1. 查出所有旧占位条目（subjectId < 0 且 sourceId="steam"）；
 * 2. 对每个分配新正 id（当前最大 subjectId + 递增，保证不与任何现有 id 冲突）；
 * 3. 先 INSERT 新 subjects 行（正 id + sourceKey）——此时外键 parent 已存在；
 * 4. UPDATE collections / steam_bindings / steam_games 把 subjectId 从旧负数改为新正 id；
 * 5. DELETE 旧 subjects 行（关联表已改指向新 id，ON DELETE CASCADE 兜底）。
 *
 * 幂等：迁移后不存在 subjectId<0 的 steam 条目，再次运行自动跳过。
 */
class SteamPlaceholderMigrator(
    private val database: NirikoDatabase,
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao,
    private val steamDao: SteamDao,
) {

    /** 执行迁移（幂等；无旧占位条目时直接返回）。返回迁移的条目数。 */
    suspend fun migrateIfNeeded(): Int {
        val placeholders = subjectDao.getLegacySteamPlaceholders()
        if (placeholders.isEmpty()) return 0

        return database.withTransaction {
            var nextId = subjectDao.getMaxSubjectId() + 1

            placeholders.forEach { old ->
                val appId = (-old.subjectId)
                val newId = nextId++
                val newSubject = old.copy(
                    subjectId = newId,
                    sourceKey = "steam:$appId",
                )
                // 3) 先插入新行（外键 parent 就位）
                subjectDao.insert(newSubject)
                // 4) 迁移关联表
                collectionDao.migrateSubjectId(old.subjectId, newId, System.currentTimeMillis())
                steamDao.migrateBindingSubjectId(old.subjectId, newId)
                steamDao.migrateGameSubjectId(old.subjectId, newId)
                // 5) 删除旧行
                subjectDao.deleteById(old.subjectId)
            }
            placeholders.size
        }
    }
}
