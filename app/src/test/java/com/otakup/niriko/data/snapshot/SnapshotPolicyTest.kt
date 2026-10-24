package com.otakup.niriko.data.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快照保留策略：最近 5 个 + 最近 7 天每天 1 个。
 *
 * 这条规则决定「用户能不能回滚到昨天」，所以按天边界逐个断言，
 * 不依赖真实文件系统。
 */
class SnapshotPolicyTest {

    private fun file(dayOffset: Int, hour: Int = 12, name: String) =
        SnapshotFile(name, dayOffset.toLong() * DAY_MS + hour.toLong() * 60 * 60 * 1000, 1024L)

    @Test
    fun emptyInputKeepsNothing() {
        assertTrue(snapshotsToKeep(emptyList()).isEmpty())
        assertTrue(snapshotsToDelete(emptyList()).isEmpty())
    }

    @Test
    fun noMoreThanRecentFivePlusSevenDays() {
        val files = buildList {
            // 今天 3 个
            add(file(0, 10, "today_a"))
            add(file(0, 9, "today_b"))
            add(file(0, 8, "today_c"))
            // 过去 6 天，每天 1 个
            for (d in 1..6) add(file(-d, 12, "day$d"))
            // 更老的三天：既不在最近 5，也不在 7 天窗口
            add(file(-8, 12, "old_8"))
            add(file(-9, 12, "old_9"))
            add(file(-10, 12, "old_10"))
        }
        val keep = snapshotsToKeep(files)
        assertEquals(setOf("today_a", "today_b", "today_c", "day1", "day2", "day3", "day4", "day5", "day6"), keep)
        assertEquals(setOf("old_8", "old_9", "old_10"), snapshotsToDelete(files).map { it.name }.toSet())
    }

    @Test
    fun dailyWindowKeepsTheNewestSnapshotOfEachDay() {
        // 第 3 天有 3 个快照，最近 5 个里不含它们 → 按天规则只留当天最新的
        val files = listOf(
            file(0, 10, "t1"), file(0, 9, "t2"), file(0, 8, "t3"),
            file(0, 7, "t4"), file(0, 6, "t5"),
            file(-3, 20, "d3_new"), file(-3, 18, "d3_mid"), file(-3, 10, "d3_old"),
        )
        val keep = snapshotsToKeep(files)
        assertTrue("d3_new" in keep)
        assertTrue("d3_mid" !in keep)
        assertTrue("d3_old" !in keep)
    }

    @Test
    fun snapshotFileNameIsSafeAndSortable() {
        val name = snapshotFileName(1_700_000_000_000, "Auto Before Sync!!")
        assertTrue(name.startsWith("snapshot_"))
        assertTrue(name.endsWith(".json.gz"))
        assertTrue(name.contains("before_sync"))
        assertTrue("非法字符必须被替换", name.none { it == ' ' || it == '!' })
        // 同一秒不同 tag 名字不同，避免覆盖
        assertTrue(snapshotFileName(1_700_000_000_000, "manual") != snapshotFileName(1_700_000_000_000, "sync"))
    }
}
