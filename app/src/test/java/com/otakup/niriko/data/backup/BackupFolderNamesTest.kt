package com.otakup.niriko.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分区备份的**命名身份**规则（F09）纯函数测试。
 *
 * 备份里刻意不放数据库自增 id（换设备/重建库即失效），改用分区名做身份。
 * 因此「什么算同一个名字」必须是一个可测的确定规则，而不是各自在调用点上随手 trim。
 */
class BackupFolderNamesTest {

    @Test
    fun `规范化去首尾空白并折叠大小写`() {
        assertEquals("本季新番", BackupFolderNames.normalizedFolderName("  本季新番 "))
        assertEquals("favorites", BackupFolderNames.normalizedFolderName("Favorites"))
        assertEquals("favorites", BackupFolderNames.normalizedFolderName("  FAVORITES  "))
    }

    @Test
    fun `空名与纯空白不是合法分区名`() {
        assertFalse(BackupFolderNames.isUsableFolderName(null))
        assertFalse(BackupFolderNames.isUsableFolderName(""))
        assertFalse(BackupFolderNames.isUsableFolderName("   "))
        assertTrue(BackupFolderNames.isUsableFolderName("待补"))
        assertTrue(BackupFolderNames.isUsableFolderName("  待补  "))
    }

    @Test
    fun `合并恢复时本地同名分区保持原样`() {
        // 本地已有「待补」，备份里也有「待补」和「漫画」→ 只填「漫画」
        val toPopulate = BackupFolderNames.foldersToPopulate(
            backupFolderNames = setOf("待补", "漫画"),
            existingFolderNames = setOf("待补"),
            merge = true,
        )
        assertEquals(setOf("漫画"), toPopulate)
    }

    @Test
    fun `全覆盖恢复时每个备份分区都要填充`() {
        val toPopulate = BackupFolderNames.foldersToPopulate(
            backupFolderNames = setOf("待补", "漫画"),
            existingFolderNames = setOf("待补"),
            merge = false,
        )
        assertEquals(setOf("待补", "漫画"), toPopulate)
    }
}
