package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SteamLibraryImporter 状态推断单元测试。
 */
class SteamLibraryImporterTest {

    @Test
    fun inferStatus_playtimePositive_watching() {
        assertEquals(WatchStatus.WATCHING, SteamLibraryImporter.inferStatus(1))
        assertEquals(WatchStatus.WATCHING, SteamLibraryImporter.inferStatus(10_000))
    }

    @Test
    fun inferStatus_zeroPlaytime_planToWatch() {
        assertEquals(WatchStatus.PLAN_TO_WATCH, SteamLibraryImporter.inferStatus(0))
    }

    @Test
    fun placeholderSubjectId_negativeMapping() {
        // 占位 id 约定：-appId，稳定可逆
        val preview = SteamLibraryPreview(
            appId = 2358720,
            name = "黑神话：悟空",
        )
        assertEquals(2358720L, preview.placeholderSubjectId)
        assertNull(preview.bgmSubjectId)
        assertNull(preview.coverUrl)
    }
}
