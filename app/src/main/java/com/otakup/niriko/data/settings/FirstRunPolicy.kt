package com.otakup.niriko.data.settings

/**
 * 首次启动引导判定（计划 B1-1）的纯逻辑。
 *
 * 规则（用户确认）：
 * - [firstRunCompleted] 为 null（键从未写入）且 DataStore 里也没有任何历史设置键 → 全新安装，展示引导；
 * - [firstRunCompleted] 为 null 但已有历史设置键 → 升级安装的老用户，**视为已完成**，不打扰；
 * - [firstRunCompleted] 非 null → 以键值为准（false 表示上次没走完，继续展示）。
 *
 * 不依赖 Android，便于 JVM 单测。
 */
object FirstRunPolicy {

    /** 是否展示首次启动引导。 */
    fun shouldShowFirstRun(firstRunCompleted: Boolean?, hasStoredSettings: Boolean): Boolean =
        when (firstRunCompleted) {
            null -> !hasStoredSettings
            else -> !firstRunCompleted
        }
}
