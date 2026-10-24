package com.otakup.niriko.data.sync

import com.otakup.niriko.data.local.entity.CollectionEntity

/**
 * WebDAV 同步合并策略（计划 B4 · 4-8）。
 *
 * 与 JSON 恢复（B2-4「冲突则本地优先」）不同：**同步是双向的**，
 * 因此有修改时间的数据按「最后写入胜出」（LWW）合并；没有时间戳的数据（封面覆盖）
 * 退回本地优先。
 *
 * 纯逻辑，便于 JVM 单测。
 */
object SyncMergePolicy {

    /** LWW：两端按 [keyOf] 去重，取 [timeOf] 更大的；只在一边出现的保留。 */
    fun <T, K> lastWriteWins(
        local: List<T>,
        remote: List<T>,
        keyOf: (T) -> K,
        timeOf: (T) -> Long,
    ): List<T> {
        val map = LinkedHashMap<K, T>()
        local.forEach { map[keyOf(it)] = it }
        remote.forEach { r ->
            val existing = map[keyOf(r)]
            if (existing == null || timeOf(r) > timeOf(existing)) {
                map[keyOf(r)] = r
            }
        }
        return map.values.toList()
    }

    /** null = 没有值：一端缺值取另一端，两端都有取较大者（进度类字段用）。 */
    fun maxOfNullable(a: Int?, b: Int?): Int? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    /**
     * 收藏合并（A-D3 + A-D8）。
     *
     * 旧实现是**整行 LWW**：A 机改评分、B 机改卷进度，合并后必丢一边。
     * 现在先按 updateTime 选出基准行（其余字段以它为准），再对下列字段做字段级合并：
     * - `watchedEpisodes` / `watchedVolumes` 取 MAX，与既有「集数取大」口径一致，
     *   且远端缺键（旧客户端）不会把本地卷进度清零；
     * - `isPrivate` 取**并集**：上传侧本来就过滤私密项，远端行永远是非私密，
     *   若按基准行覆盖，本地私密标记每次下载都会被洗掉（A-D8）。
     *   代价：一台设备取消私密、另一台没同步时取消不会传播 —— 有意的保守选择。
     */
    fun mergeCollections(
        local: List<CollectionEntity>,
        remote: List<CollectionEntity>,
    ): List<CollectionEntity> {
        val map = LinkedHashMap<Long, CollectionEntity>() // subjectId → entity
        local.forEach { map[it.subjectId] = it }
        remote.forEach { r ->
            val existing = map[r.subjectId]
            map[r.subjectId] = if (existing == null) r else mergeCollectionRow(existing, r)
        }
        return map.values.toList()
    }

    private fun mergeCollectionRow(
        local: CollectionEntity,
        remote: CollectionEntity,
    ): CollectionEntity {
        // 基准行：updateTime 较新的一方；相等保留本地（不来回抖动，与 lastWriteWins 一致）
        val base = if (remote.updateTime > local.updateTime) remote else local
        return base.copy(
            watchedEpisodes = maxOfNullable(local.watchedEpisodes, remote.watchedEpisodes),
            watchedVolumes = maxOfNullable(local.watchedVolumes, remote.watchedVolumes),
            // 本地私密不可被远端取消；远端私密（理论上不会上传）也要带回本地
            isPrivate = local.isPrivate || remote.isPrivate,
        )
    }

    /**
     * 无时间戳的数据：**本地优先**，只并入远端独有的键。
     * 封面覆盖只存在 DataStore 里、没有修改时间，无法 LWW。
     */
    fun <K, V> localFirst(local: Map<K, V>, remote: Map<K, V>): Map<K, V> =
        if (local.isEmpty()) remote else remote.filterKeys { it !in local } + local
}