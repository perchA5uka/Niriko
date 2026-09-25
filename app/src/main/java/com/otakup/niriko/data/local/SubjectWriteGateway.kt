package com.otakup.niriko.data.local

import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * subjects 表写入网关 —— 消灭「缓存写放大」。
 *
 * ## 为什么需要它
 *
 * 改造前，每一次远端返回都会把结果整批 upsert 进 `subjects`，而且很多地方是**逐条**写：
 * `BroadcastFetcher` 对每个放送作品单独 upsert、`fetchStoreTopSellers` 对 100 条热销榜逐条 upsert、
 * `SeasonalFetcher` 同理。而 `CollectionDao.observeAllWithSubject()` 是
 * `@Transaction` + `@Relation(subjects)`，Room **同时观察 collections 与 subjects 两张表**——
 * 于是每写一条，作品库的整个派生状态（排序 + 统计 + 标签计数 + Steam 补充查询）就重算一遍。
 *
 * 结果：一次发现页刷新可以触发上百次全量重算。
 *
 * ## 它做什么
 *
 * 1. **合并成单事务**：一批一次 `upsertAll`（Room 的失效通知在事务结束时只发一次）。
 * 2. **内容 diff**：先按 id 批量读回，只写**真的变了**的行。
 *    搜索同一关键词两次、多数据源返回重叠结果时，第二次是零写入。
 * 3. **规范化时间戳**：只有真的写了，才推进 `lastSyncTime`（该字段目前仅用于导出，无读取逻辑）。
 *
 * ## 有意的取舍
 *
 * 没有做「时间窗口合并」（把 100ms 内的多次调用攒成一次）——那需要后台协程与延迟落库，
 * 会引入「进程被杀则丢数据」与「写入何时可见」的不确定性。本项目的写放大主要来自
 * **逐条写**，批量 + diff 已经覆盖；跨调用合并留给后续按需再加。
 */
class SubjectWriteGateway(private val subjectDao: SubjectDao) {

    /** 一次批量写的统计（诊断/单测用）。 */
    data class WriteReport(
        val received: Int,
        val written: Int,
        val unchanged: Int,
    ) {
        val skippedEverything: Boolean get() = received > 0 && written == 0
    }

    /**
     * 批量 upsert（单事务 + diff 写）。
     *
     * @return 实际写入了多少行；`unchanged` 是因此省掉的写入次数。
     */
    suspend fun upsertAll(subjects: List<SubjectEntity>): WriteReport = withContext(Dispatchers.IO) {
        if (subjects.isEmpty()) return@withContext WriteReport(0, 0, 0)

        // 同一批里可能有重复 id（多数据源合并、榜单与本地条目混排时会发生）→ 后出现者覆盖
        val deduped = subjects.associateBy { it.subjectId }.values.toList()
        val existing = subjectDao.getByIds(deduped.map { it.subjectId }).associateBy { it.subjectId }
        val toWrite = diffWrites(existing, deduped)

        if (toWrite.isNotEmpty()) {
            subjectDao.upsertAll(toWrite.map { it.copy(lastSyncTime = System.currentTimeMillis()) })
        }
        WriteReport(
            received = deduped.size,
            written = toWrite.size,
            unchanged = deduped.size - toWrite.size,
        )
    }

    /** 单条 upsert（内部走同一套 diff 逻辑）。 */
    suspend fun upsert(subject: SubjectEntity): WriteReport = upsertAll(listOf(subject))

    /** 强制写入（不做 diff）：用于「必须刷新时间戳」的少数场景。 */
    suspend fun upsertForcing(subject: SubjectEntity) = withContext(Dispatchers.IO) {
        subjectDao.upsert(subject.copy(lastSyncTime = System.currentTimeMillis()))
    }

    companion object {
        /**
         * 纯函数：算出真正需要写入的行（便于单测）。
         *
         * @param existing 数据库中已存在的行（按 id 索引）
         * @param incoming 本批要写入的行（已按 id 去重）
         */
        internal fun diffWrites(
            existing: Map<Long, SubjectEntity>,
            incoming: List<SubjectEntity>,
        ): List<SubjectEntity> = incoming.filter { row ->
            val current = existing[row.subjectId]
            current == null || !contentEquals(current, row)
        }

        /**
         * 内容是否等价 —— **忽略 `lastSyncTime`**。
         *
         * 不忽略的话，每次刷新时间戳都会推进，diff 永远判定为「变了」，diff 写就形同虚设。
         */
        internal fun contentEquals(a: SubjectEntity, b: SubjectEntity): Boolean =
            a.copy(lastSyncTime = 0L) == b.copy(lastSyncTime = 0L)
    }
}
