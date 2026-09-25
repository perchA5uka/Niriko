package com.otakup.niriko.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 单次飞行单元测试。
 *
 * 这是「刷新风暴」的直接解药：并发触发的同一次刷新必须只打一遍远端接口。
 */
class AsyncSingleFlightTest {

    @Test
    fun concurrentSameKey_runsBlockOnce() = runBlocking {
        val flight = AsyncSingleFlight()
        val runs = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val jobs = (1..8).map {
            launch(Dispatchers.Default) {
                flight.run("trending") {
                    runs.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    42
                }
            }
        }

        started.await()
        delay(100) // 让其余 7 个协程都进入等待
        release.complete(Unit)
        jobs.forEach { it.join() }

        assertEquals("8 个并发请求只应执行 1 次", 1, runs.get())
    }

    @Test
    fun concurrentSameKey_allGetSameValue() = runBlocking {
        val flight = AsyncSingleFlight()
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()

        val results = mutableListOf<Int>()
        val jobs = (1..5).map {
            launch(Dispatchers.Default) {
                val v = flight.run("k") {
                    started.complete(Unit)
                    release.await()
                    7
                }
                synchronized(results) { results.add(v) }
            }
        }
        started.await()
        delay(80)
        release.complete(Unit)
        jobs.forEach { it.join() }

        assertEquals(5, results.size)
        assertTrue(results.all { it == 7 })
    }

    @Test
    fun differentKeys_runIndependently() = runBlocking {
        val flight = AsyncSingleFlight()
        val runs = AtomicInteger(0)
        val jobs = (1..5).map { i ->
            launch(Dispatchers.Default) {
                flight.run("key-$i") { runs.incrementAndGet() }
            }
        }
        jobs.forEach { it.join() }
        assertEquals(5, runs.get())
    }

    @Test
    fun failurePropagatesToAllCallers() = runBlocking {
        val flight = AsyncSingleFlight()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val failures = AtomicInteger(0)
        val jobs = (1..4).map {
            launch(Dispatchers.Default) {
                try {
                    flight.run<Int>("k") {
                        started.complete(Unit)
                        release.await()
                        throw IllegalStateException("boom")
                    }
                } catch (_: IllegalStateException) {
                    failures.incrementAndGet()
                }
            }
        }
        started.await()
        delay(80)
        release.complete(Unit)
        jobs.forEach { it.join() }

        assertEquals("所有搭车者都应拿到同一个失败", 4, failures.get())
    }

    @Test
    fun keyIsReleasedAfterCompletion_allowsNextRun() = runBlocking {
        val flight = AsyncSingleFlight()
        val runs = AtomicInteger(0)
        repeat(3) {
            flight.run("k") { runs.incrementAndGet() }
        }
        assertEquals(3, runs.get())
        assertEquals(false, flight.isRunning("k"))
    }

    @Test
    fun keyIsReleasedEvenWhenBlockThrows() = runBlocking {
        val flight = AsyncSingleFlight()
        runCatching { flight.run<Int>("k") { throw IllegalStateException("boom") } }
        // 关键：失败后该 key 不能被永久卡住，否则该资源的刷新再也无法触发
        assertEquals(false, flight.isRunning("k"))
        val v = flight.run("k") { 1 }
        assertEquals(1, v)
    }
}
