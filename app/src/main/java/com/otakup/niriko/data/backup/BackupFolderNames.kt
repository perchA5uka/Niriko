package com.otakup.niriko.data.backup

/**
 * 备份里的作品库分区（F09）。
 *
 * **刻意不导出数据库自增 id**：id 只在本机数据库里有意义，换一台设备/重建数据库就完全变了。
 * 备份里用**分区名**做身份（导出时写入 `folderName`），恢复时按名字找本地分区、
 * 找不到就新建 —— 这样「重复导入同一份备份」是幂等的（§10.2 的验收项：
 * 搜索与标签互不混淆、重复导入备份幂等）。
 *
 * 名称比较用 [normalizedFolderName]（去首尾空白 + 大小写无关）。
 * 不做全局唯一约束：用户就是想要两个都叫「待补」的分区时不该拦他 ——
 * 我们只在**按名字匹配**时用规范化结果，创建时一律允许。
 */
object BackupFolderNames {

    /**
     * 规范化分区名（匹配用）：去首尾空白 + 大小写折叠。
     *
     * 空名返回空串，调用方据此丢弃该条备份记录（空名分区无法被匹配，保留下来只会变成孤儿）。
     */
    fun normalizedFolderName(raw: String?): String = raw?.trim()?.lowercase().orEmpty()

    /** 这条备份记录能不能用（名字非空）。 */
    fun isUsableFolderName(raw: String?): Boolean = normalizedFolderName(raw).isNotEmpty()

    /**
     * 恢复时「哪些分区要按备份填充成员」。
     *
     * @param backupFolderNames 备份里出现的分区名（规范化后）
     * @param existingFolderNames 本地已有的分区名（规范化后）
     * @param merge true = 合并恢复：本地已有同名分区**一律保持原样**（本地优先）；
     *   false = 全覆盖恢复：开始前已清空分区表，因此每个备份分区都要写入。
     */
    fun foldersToPopulate(
        backupFolderNames: Set<String>,
        existingFolderNames: Set<String>,
        merge: Boolean,
    ): Set<String> = if (merge) backupFolderNames - existingFolderNames else backupFolderNames
}
