package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.dao.ManualAwardDao
import com.otakup.niriko.data.local.entity.ManualAwardEntity

/**
 * 手动录入的权威机构成绩仓储。
 *
 * ## 为什么需要它
 *
 * 调研结论：**Fami通（日本 40 分制）、Billboard、Oricon、RYM、AOTY、Metacritic 都没有
 * 可用的免费公开 API**，抓取又涉及版权与反爬。方案决定：这些机构一律走
 * 「外链 + 用户手动录入」，并在 UI 上明确标注「手动录入」——不伪装成自动获取的数据。
 */
class ManualAwardRepository(
    private val dao: ManualAwardDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun awardsOf(subjectId: Long): List<ManualAwardEntity> =
        runCatching { dao.getBySubject(subjectId) }.getOrDefault(emptyList())

    suspend fun add(
        subjectId: Long,
        sourceId: String,
        score: Float? = null,
        scoreMax: Float = 40f,
        rankPosition: Int? = null,
        note: String? = null,
        url: String? = null,
    ) {
        runCatching {
            dao.upsert(
                ManualAwardEntity(
                    subjectId = subjectId,
                    sourceId = sourceId,
                    score = score,
                    scoreMax = scoreMax,
                    rankPosition = rankPosition,
                    note = note,
                    url = url,
                    createTime = clock(),
                )
            )
        }
    }

    suspend fun remove(id: Long) {
        runCatching { dao.delete(id) }
    }

    suspend fun all(): List<ManualAwardEntity> = runCatching { dao.getAll() }.getOrDefault(emptyList())

    suspend fun insertAll(list: List<ManualAwardEntity>) {
        if (list.isEmpty()) return
        runCatching { dao.insertAll(list) }
    }
}
