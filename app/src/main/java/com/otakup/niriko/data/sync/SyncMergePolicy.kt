package com.otakup.niriko.data.sync

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

    /**
     * 无时间戳的数据：**本地优先**，只并入远端独有的键。
     * 封面覆盖只存在 DataStore 里、没有修改时间，无法 LWW。
     */
    fun <K, V> localFirst(local: Map<K, V>, remote: Map<K, V>): Map<K, V> =
        if (local.isEmpty()) remote else remote.filterKeys { it !in local } + local
}
