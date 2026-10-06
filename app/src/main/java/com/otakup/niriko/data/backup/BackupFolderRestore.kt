package com.otakup.niriko.data.backup

import com.otakup.niriko.data.local.dao.LibraryFolderDao
import com.otakup.niriko.data.local.entity.LibraryFolderEntity
import com.otakup.niriko.data.local.entity.LibraryFolderSubjectEntity

/**
 * 备份里的一个分区（F09）。成员用 **subjectId** 而不是文件夹 id —— 见 [BackupFolderNames]。
 */
data class BackupFolder(
    val name: String,
    val sortOrder: Int,
    val isCollapsed: Boolean,
    val createdAt: Long,
    /** 成员作品（导出时已按分区内顺序排好；恢复时顺序即本列表顺序）。 */
    val subjectIds: List<Long>,
)

/**
 * 作品库分区的备份恢复（F09）。
 *
 * 恢复语义与 [RestoreMergePolicy] 的项目约定一致 —— **本地优先**：
 * - `fullReplace`：先清空分区与成员（备份即现状，重复导入天然幂等）；
 * - 合并：只有「本地没有的分区」才落库，本地同名分区**连成员一起保持原样**。
 *
 * id 是自增的，因此「先建分区拿到本地 id、再写成员」必须顺序执行 —— 这是这里唯一
 * 不能被打乱的地方，逻辑抽成函数也有一部分原因是为了让它在 JVM 里可测（DAO 用内存库）。
 */
object BackupFolderRestore {

    /** 恢复结果：新建了几个分区、填充了几个分区的成员。 */
    data class Outcome(
        val foldersCreated: Int,
        val foldersPopulated: Int,
    ) {
        val touched: Int get() = foldersCreated + foldersPopulated
    }

    suspend fun restore(
        dao: LibraryFolderDao,
        folders: List<BackupFolder>,
        fullReplace: Boolean,
    ): Outcome {
        if (fullReplace) {
            // F09：清空顺序是「先成员后分区」——反过来的话会留下指向已删分区的孤儿关系行。
            // 两步在同一事务里（见 LibraryFolderDao.clearAllFoldersAndMembers）。
            dao.clearAllFoldersAndMembers()
        }

        // 丢弃名字为空的分区：它既无法被匹配，也不能出现在导航条上
        val usable = folders.filter { BackupFolderNames.isUsableFolderName(it.name) }
        // 本地已有分区按「规范化名字 → id」建索引：合并模式要按名字找本地分区，
        // 不能每来一个分区就全表扫一遍（分区多时会变成 N²）。
        val existingByName = dao.getFolders().associate {
            BackupFolderNames.normalizedFolderName(it.name) to it.id
        }

        // 哪些分区才允许按备份写成员：合并模式下**本地已有同名分区一律跳过** ——
        // 只建空分区是安全的，往别人的分区里塞旧成员不是（用户会在自己的分区里
        // 看到一堆已经不关心的作品）。见 BackupFolderNames.foldersToPopulate。
        val toPopulate = BackupFolderNames.foldersToPopulate(
            backupFolderNames = usable.map { BackupFolderNames.normalizedFolderName(it.name) }.toSet(),
            existingFolderNames = existingByName.keys,
            merge = !fullReplace,
        )

        var created = 0
        var populated = 0
        usable.forEach { folder ->
            val normalized = BackupFolderNames.normalizedFolderName(folder.name)
            val localId = existingByName[normalized]
            // 跳过成员填充的分区：本地已存在，直接用它的 id 即可（不改名字、不改成员、不改顺序）
            val id = if (fullReplace || localId == null) {
                created++
                dao.insertFolder(
                    LibraryFolderEntity(
                        name = folder.name.trim(),
                        sortOrder = folder.sortOrder,
                        isCollapsed = folder.isCollapsed,
                        createdAt = folder.createdAt,
                    ),
                )
            } else {
                localId
            }
            if (normalized !in toPopulate) return@forEach

            // distinct：备份手工改过也不能让同一部作品在分区里出现两行（会渲染成两张一样的卡）
            val members = folder.subjectIds.distinct().mapIndexed { index, subjectId ->
                LibraryFolderSubjectEntity(
                    folderId = id,
                    subjectId = subjectId,
                    sortOrder = index,
                    addedAt = folder.createdAt + index,
                )
            }
            if (members.isNotEmpty()) {
                dao.replaceMembers(id, members)
                populated++
            }
        }
        return Outcome(foldersCreated = created, foldersPopulated = populated)
    }
}
