package com.otakup.niriko.viewmodel

import com.otakup.niriko.data.model.WatchStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionEventsTest {
    @Test fun onlySuccessfulNonCompletedToCompletedTransitionsEmit() = runBlocking {
        val source = CompletionEvents()
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { source.events.collect { received.add(it) } }
        for (before in WatchStatus.entries) {
            for (after in WatchStatus.entries) {
                source.onStatusWritten(42L, before, after, succeeded = true)
                yield()
            }
        }
        collector.cancelAndJoin()
        assertEquals(4, received.size)
        assertTrue(received.all { it == 42L })
    }

    @Test fun unsuccessfulWritesNeverCelebrate() = runBlocking {
        val source = CompletionEvents()
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { source.events.collect { received.add(it) } }
        for (before in WatchStatus.entries) {
            source.onStatusWritten(1L, before, WatchStatus.COMPLETED, succeeded = false)
            yield()
        }
        collector.cancelAndJoin()
        assertTrue(received.isEmpty())
    }

    @Test fun repeatedCompletedWritesOnlyCelebrateTheTransition() = runBlocking {
        val source = CompletionEvents()
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { source.events.collect { received.add(it) } }
        source.onStatusWritten(1L, WatchStatus.WATCHING, WatchStatus.COMPLETED, true)
        yield()
        repeat(5) {
            source.onStatusWritten(1L, WatchStatus.COMPLETED, WatchStatus.COMPLETED, true)
            yield()
        }
        collector.cancelAndJoin()
        assertEquals(listOf(1L), received)
    }

    @Test fun eventsBeforeOpeningThePageAreDiscarded() = runBlocking {
        val source = CompletionEvents()
        source.onStatusWritten(1L, WatchStatus.WATCHING, WatchStatus.COMPLETED, true)
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { source.events.collect { received.add(it) } }
        yield()
        collector.cancelAndJoin()
        assertTrue(received.isEmpty())
        assertTrue(source.events.replayCache.isEmpty())
    }

    @Test fun reopeningOrResumingDoesNotReplayConsumedEvents() = runBlocking {
        val source = CompletionEvents()
        val received = mutableListOf<Long>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) { source.events.collect { received.add(it) } }
        source.onStatusWritten(1L, WatchStatus.WATCHING, WatchStatus.COMPLETED, true)
        yield()
        first.cancelAndJoin()
        val second = launch(start = CoroutineStart.UNDISPATCHED) { source.events.collect { received.add(it) } }
        yield()
        second.cancelAndJoin()
        assertEquals(listOf(1L), received)
    }
}
