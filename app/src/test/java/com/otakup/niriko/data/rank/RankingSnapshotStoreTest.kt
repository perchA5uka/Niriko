package com.otakup.niriko.data.rank

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3.7 榜单周快照：周键、上限、名次差、每周一次、上周基线。 */
class RankingSnapshotStoreTest {

    private class FakeStorage(var raw: String? = null) : RankingSnapshotStorage {
        var writes = 0
        override suspend fun read(): String? = raw
        override suspend fun write(json: String) {
            writes++
            raw = json
        }
    }

    private val monday = LocalDate.of(2026, 10, 12)
    private val sunday = LocalDate.of(2026, 10, 18)
    private val nextMonday = LocalDate.of(2026, 10, 19)

    @Test
    fun isoWeekKeyGroupsMondayToSunday() {
        assertEquals("2026-W42", RankingSnapshotCodec.isoWeekKey(monday))
        assertEquals("2026-W42", RankingSnapshotCodec.isoWeekKey(sunday))
        assertEquals("2026-W43", RankingSnapshotCodec.isoWeekKey(nextMonday))
    }

    @Test
    fun rankMapDropsInvalidAndCapsAt200() {
        val entries = buildMap {
            put(-1L, 5)      // 非法 subjectId 不影响
            put(1L, 0)       // 名次 0 丢弃
            put(2L, 3)
            put(3L, 1)
        }
        val mapped = RankingSnapshotCodec.rankMap(entries)
        assertEquals(mapOf("3" to 1, "2" to 3), mapped)

        val big = (1..250).associate { it.toLong() to it }
        val capped = RankingSnapshotCodec.rankMap(big)
        assertEquals(RankingSnapshotCodec.MAX_ENTRIES, capped.size)
        assertTrue(capped.containsKey("200"))
        assertTrue(!capped.containsKey("201"))
    }

    @Test
    fun deltaIsPreviousMinusCurrent() {
        assertEquals(RankDelta(RankDirection.UP, 3), RankingSnapshotCodec.delta(12, 15))
        assertEquals(RankDelta(RankDirection.DOWN, 3), RankingSnapshotCodec.delta(15, 12))
        assertEquals(RankDelta(RankDirection.SAME, 0), RankingSnapshotCodec.delta(15, 15))
        assertNull(RankingSnapshotCodec.delta(null, 15))
        assertNull(RankingSnapshotCodec.delta(15, null))
        assertNull(RankingSnapshotCodec.delta(0, 15))
        assertEquals("▲3", RankDelta(RankDirection.UP, 3).label)
        assertEquals("▼3", RankDelta(RankDirection.DOWN, 3).label)
        assertEquals("—", RankDelta(RankDirection.SAME, 0).label)
    }

    @Test
    fun encodedSnapshotRoundTripsAndBadJsonIsIgnored() {
        val snapshot = RankingSnapshot(
            week = "2026-W42",
            capturedAt = 1L,
            ranks = mapOf("1" to 2),
            previousWeek = "2026-W41",
            previousRanks = mapOf("1" to 5),
        )
        val raw = RankingSnapshotCodec.encode(snapshot)
        assertEquals(snapshot, RankingSnapshotCodec.decode(raw))
        assertNull(RankingSnapshotCodec.decode("{ not json"))
        assertNull(RankingSnapshotCodec.decode(null))
        assertNull(RankingSnapshotCodec.decode(""))
    }

    @Test
    fun captureHappensOncePerWeekAndRollsPreviousWeek() = runBlocking {
        val storage = FakeStorage()
        val store = RankingSnapshotStore(storage)

        // 第一次采集：没有上周 → 不产生箭头
        val first = store.captureIfNeeded(mapOf(1L to 5, 2L to 9), sunday, 111L)
        assertNotNull(first)
        assertEquals("2026-W42", first!!.week)
        assertTrue(first.previousRanks.isEmpty())
        assertTrue(store.deltas(mapOf(1L to 5, 2L to 9), sunday).isEmpty())
        assertEquals(1, storage.writes)

        // 同一周再采集：不覆盖（本周内箭头保持稳定）
        store.captureIfNeeded(mapOf(1L to 1), sunday, 222L)
        assertEquals(1, storage.writes)

        // 下一周：本周 → 上周，比较基线换成 previousRanks
        val rolled = store.captureIfNeeded(mapOf(1L to 2, 2L to 7), nextMonday, 333L)
        assertEquals("2026-W43", rolled!!.week)
        assertEquals("2026-W42", rolled.previousWeek)
        assertEquals(mapOf("1" to 5, "2" to 9), rolled.previousRanks)

        val deltas = store.deltas(mapOf(1L to 2, 2L to 7), nextMonday)
        assertEquals(RankDelta(RankDirection.UP, 3), deltas[1L])
        assertEquals(RankDelta(RankDirection.UP, 2), deltas[2L])
    }

    @Test
    fun beforeThisWeeksCaptureCurrentIsComparedWithLastCapturedWeek() = runBlocking {
        val storage = FakeStorage()
        val store = RankingSnapshotStore(storage)
        store.captureIfNeeded(mapOf(1L to 5), sunday, 1L)
        // 本周尚未采集（例：周一打开）→ 与上次采集（上周）比较
        val deltas = store.deltas(mapOf(1L to 3), nextMonday)
        assertEquals(RankDelta(RankDirection.UP, 2), deltas[1L])
    }

    @Test
    fun firstCaptureShowsNoArrowNewEntriesGetNoneAndTiesShowDash() = runBlocking {
        val storage = FakeStorage()
        val store = RankingSnapshotStore(storage)
        // 首次采集当周没有上周基线 → 整表为空（行尾什么也不显示）
        store.captureIfNeeded(mapOf(1L to 5, 2L to 9), sunday, 1L)
        assertTrue(store.deltas(mapOf(1L to 5, 2L to 9), sunday).isEmpty())

        // 滚动到下一周后有基线：持平 → SAME（一条横线）；新上榜 → null（不显示）
        store.captureIfNeeded(mapOf(1L to 5, 2L to 9), nextMonday, 2L)
        val deltas = store.deltas(mapOf(1L to 5, 2L to 9, 3L to 8), nextMonday)
        assertEquals(RankDirection.SAME, deltas[1L]?.direction)
        assertEquals(RankDirection.SAME, deltas[2L]?.direction)
        assertTrue("新上榜作品不应显示箭头", deltas[3L] == null)
    }
}
