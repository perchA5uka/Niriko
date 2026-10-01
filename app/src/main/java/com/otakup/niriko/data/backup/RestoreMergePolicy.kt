package com.otakup.niriko.data.backup

/**
 * 恢复备份时的合并策略（计划 B2-4）。
 *
 * 用户确认：**冲突则本地优先**——备份里「本地已存在」的记录一律丢弃（本地值保留），
 * 只把本地没有的记录并入。
 *
 * 副作用（有意为之）：这条策略让「从备份恢复」不再具备回滚能力——想真正回滚某条记录，
 * 需要先删除本地记录再恢复。全覆盖模式（`merge = false`）仍保留在 [BackupManager] 内部，
 * 但设置页不再使用它。
 *
 * 不依赖 Android / Room，便于 JVM 单测。
 */
object RestoreMergePolicy {

    /**
     * 只保留本地尚不存在的备份记录。
     *
     * @param backup 备份中的记录
     * @param localKeys 本地已有的身份键集合
     * @param keyOf 记录 → 身份键
     */
    fun <T, K> keepOnlyNew(backup: List<T>, localKeys: Set<K>, keyOf: (T) -> K): List<T> =
        if (localKeys.isEmpty()) backup else backup.filterNot { keyOf(it) in localKeys }
}
