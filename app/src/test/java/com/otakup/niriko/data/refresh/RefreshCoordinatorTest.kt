package com.otakup.niriko.data.refresh

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 刷新编排器单元测试：把「该不该刷 / 并发去重 / 失败退避」这三条不变量钉死。
 */
class RefreshCoordinatorTest {

    private var now = 1_700_000_000_000L

    private fun coordinator(): Pair<RefreshCoordinator, InMemoryFreshnessSource> {
        val store = InMemoryFreshnessSource()
        return RefreshCoordinator(store, clock = { now }) to store
    }

    @Test
    fun firstCall_fetchesAndRecordsFreshness() = runBlocking {
        val (coord, store) = coordinator()
        val calls = AtomicInteger(0)

        val outcome = coord.refresh("trending", RefreshResource.TRENDING) {
            calls.incrementAndGet()
            listOf("a")
        }

        assertTrue(outcome is RefreshOutcome.Success)
        assertEquals(1, calls.get())
        assertEquals(now, store.snapshot("trending")?.lastSuccessAt)
    }

    @Test
    fun secondCallWithinSoftTtl_isSkipped() = runBlocking {
        val (coord, _) = coordinator()
        val calls = AtomicInteger(0)
        val block = suspend {
            calls.incrementAndGet()
            listOf("a")
        }

        coord.refresh("trending", RefreshResource.TRENDING, block = block)
        now += 60_000L // 1 分钟后，仍在 5 分钟软 TTL 内
        val second = coord.refresh("trending", RefreshResource.TRENDING, block = block)

        assertEquals(1, calls.get())
        assertTrue(second is RefreshOutcome.Skipped)
        assertEquals(RefreshDecision.FRESH, (second as RefreshOutcome.Skipped).decision)
    }

    @Test
    fun forceBypassesFreshnessWindow() = runBlocking {
        val (coord, _) = coordinator()
        val calls = AtomicInteger(0)
        val block = suspend {
            calls.incrementAndGet()
            listOf("a")
        }

        coord.refresh("trending", RefreshResource.TRENDING, block = block)
        // 用户下拉刷新：即便刚刷过也必须真的打一次接口
        coord.refresh("trending", RefreshResource.TRENDING, force = true, block = block)

        assertEquals(2, calls.get())
    }

    @Test
    fun revalidateWindow_fetchesAgain() = runBlocking {
        val (coord, _) = coordinator()
        val calls = AtomicInteger(0)
        val block = suspend {
            calls.incrementAndGet()
            listOf("a")
        }

        coord.refresh("trending", RefreshResource.TRENDING, block = block)
        now += 10 * 60_000L // 10 分钟：软过期、未硬过期
        coord.refresh("trending", RefreshResource.TRENDING, block = block)

        assertEquals(2, calls.get())
    }

    @Test
    fun failure_recordsBackoffAndBlocksNextAutomaticAttempt() = runBlocking {
        val (coord, store) = coordinator()
        val calls = AtomicInteger(0)
        val block: suspend () -> List<String> = {
            calls.incrementAndGet()
            throw IllegalStateException("network down")
        }

        val first = coord.refresh("trending", RefreshResource.TRENDING, block = block)
        assertTrue(first is RefreshOutcome.Failure)
        assertEquals(1, store.snapshot("trending")?.failureCount)

        // 退避窗口内：不再自动重打
        val second = coord.refresh("trending", RefreshResource.TRENDING, block = block)
        assertTrue(second is RefreshOutcome.Skipped)
        assertEquals(RefreshDecision.BACKOFF, (second as RefreshOutcome.Skipped).decision)
        assertEquals("退避期内不应再发请求", 1, calls.get())
    }

    @Test
    fun forceOverridesBackoff() = runBlocking {
        val (coord, _) = coordinator()
        val calls = AtomicInteger(0)
        val block: suspend () -> List<String> = {
            calls.incrementAndGet()
            throw IllegalStateException("network down")
        }

        coord.refresh("trending", RefreshResource.TRENDING, block = block)
        // 用户主动重试：必须真的再试一次，而不是被退避静默吞掉
        coord.refresh("trending", RefreshResource.TRENDING, force = true, block = block)

        assertEquals(2, calls.get())
    }

    @Test
    fun successAfterFailure_clearsBackoff() = runBlocking {
        val (coord, store) = coordinator()
        var shouldFail = true
        val block: suspend () -> List<String> = {
            if (shouldFail) throw IllegalStateException("boom") else listOf("ok")
        }

        coord.refresh("trending", RefreshResource.TRENDING, block = block)
        shouldFail = false
        now += 60_000L // 越过 30s 退避
        val ok = coord.refresh("trending", RefreshResource.TRENDING, block = block)

        assertTrue(ok is RefreshOutcome.Success)
        assertEquals(0, store.snapshot("trending")?.failureCount)
        assertEquals(0L, store.snapshot("trending")?.backoffUntil)
    }

    @Test
    fun concurrentRefreshes_singleFlightDeduplicates() = runBlocking {
        val (coord, _) = coordinator()
        val calls = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val jobs = (1..6).map {
            launch(Dispatchers.Default) {
                coord.refresh("trending", RefreshResource.TRENDING) {
                    calls.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    listOf("a")
                }
            }
        }
        started.await()
        delay(120)
        release.complete(Unit)
        jobs.forEach { it.join() }

        assertEquals("并发刷新必须被合并成一次远端请求", 1, calls.get())
    }

    @Test
    fun runningKeys_tracksInFlightAndClearsAfterwards() = runBlocking {
        val (coord, _) = coordinator()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val job = launch(Dispatchers.Default) {
            coord.refresh("trending", RefreshResource.TRENDING) {
                started.complete(Unit)
                release.await()
                listOf("a")
            }
        }
        started.await()
        assertTrue(coord.isRunning("trending"))
        release.complete(Unit)
        job.join()
        assertEquals(false, coord.isRunning("trending"))
    }

    @Test
    fun reset_makesNextCallFetchAgain() = runBlocking {
        val (coord, _) = coordinator()
        val calls = AtomicInteger(0)
        val block = suspend {
            calls.incrementAndGet()
            listOf("a")
        }

        coord.refresh("trending", RefreshResource.TRENDING, block = block)
        coord.reset("trending")
        assertNull("reset 应清除该 key 的新鲜度", coord.snapshot("trending"))
        coord.refresh("trending", RefreshResource.TRENDING, block = block)

        assertEquals(2, calls.get())
    }

    @Test
    fun resetAll_clearsEveryResourceAndItsBackoff() = runBlocking {
        val (coord, store) = coordinator()
        val okBlock = suspend { listOf("a") }
        val failBlock: suspend () -> List<String> = { throw IllegalStateException("down") }

        coord.refresh("trending:A", RefreshResource.TRENDING, block = okBlock)
        coord.refresh("trending:B", RefreshResource.TRENDING, block = failBlock)
        assertEquals(2, store.snapshots.value.size)
        assertTrue(store.snapshot("trending:B")!!.backoffUntil > now)

        coord.resetAll()

        assertTrue("resetAll 应清空全部记录", store.snapshots.value.isEmpty())
        // 关键：清空后即便处于退避窗口的资源也应重新尝试
        val calls = AtomicInteger(0)
        coord.refresh("trending:B", RefreshResource.TRENDING) { calls.incrementAndGet(); listOf("b") }
        assertEquals(1, calls.get())
    }
}
