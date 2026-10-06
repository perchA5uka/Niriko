package com.otakup.niriko.viewmodel

import android.app.Application
import android.os.Looper
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.repository.CollectionRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CollectionCompletionTest {
    private class TestDao : CollectionDao by unusedDao() {
        val rows = mutableMapOf<Long, CollectionEntity>()
        var succeed = true
        override suspend fun getBySubjectId(subjectId: Long): CollectionEntity? = rows[subjectId]
        override suspend fun update(collection: CollectionEntity): Int {
            // Force two rapid status actions to overlap at the write boundary.
            yield()
            if (!succeed || collection.subjectId !in rows) return 0
            rows[collection.subjectId] = collection
            return 1
        }
    }

    @Test fun rapidRepeatedCompletionWritesEmitOnce() = runBlocking {
        val dao = TestDao()
        dao.rows[1L] = CollectionEntity(id = 1L, subjectId = 1L, status = WatchStatus.WATCHING)
        val vm = CollectionViewModel(CollectionRepository(dao))
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.completionEvents.collect { received.add(it) } }
        repeat(3) { vm.updateStatus(1L, WatchStatus.COMPLETED) }
        shadowOf(Looper.getMainLooper()).idle()
        yield()
        collector.cancelAndJoin()
        assertEquals(WatchStatus.COMPLETED, dao.rows[1L]?.status)
        assertEquals(listOf(1L), received)
    }

    @Test fun batchCompletionEmitsOneFeedbackForTheBatch() = runBlocking {
        val dao = TestDao()
        for (id in 1L..3L) dao.rows[id] = CollectionEntity(id = id, subjectId = id, status = WatchStatus.WATCHING)
        val vm = CollectionViewModel(CollectionRepository(dao))
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.completionEvents.collect { received.add(it) } }
        vm.batchUpdateStatus(listOf(1L, 2L, 3L), WatchStatus.COMPLETED)
        shadowOf(Looper.getMainLooper()).idle()
        yield()
        collector.cancelAndJoin()
        assertTrue(dao.rows.values.all { it.status == WatchStatus.COMPLETED })
        assertEquals(listOf(1L), received)
    }

    @Test fun failedWriteAndMissingRecordDoNotCelebrate() = runBlocking {
        val dao = TestDao()
        dao.rows[1L] = CollectionEntity(id = 1L, subjectId = 1L, status = WatchStatus.WATCHING)
        dao.succeed = false
        val vm = CollectionViewModel(CollectionRepository(dao))
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.completionEvents.collect { received.add(it) } }
        vm.updateStatus(1L, WatchStatus.COMPLETED)
        vm.updateStatus(99L, WatchStatus.COMPLETED)
        shadowOf(Looper.getMainLooper()).idle()
        yield()
        collector.cancelAndJoin()
        assertEquals(WatchStatus.WATCHING, dao.rows[1L]?.status)
        assertTrue(received.isEmpty())
    }

    @Test fun restoredCompletedRecordAndProgressUpdatesDoNotCelebrate() = runBlocking {
        val dao = TestDao()
        dao.rows[1L] = CollectionEntity(id = 1L, subjectId = 1L, status = WatchStatus.COMPLETED)
        val vm = CollectionViewModel(CollectionRepository(dao))
        val received = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.completionEvents.collect { received.add(it) } }
        vm.updateStatus(1L, WatchStatus.COMPLETED)
        vm.setProgress(1L, 12)
        shadowOf(Looper.getMainLooper()).idle()
        yield()
        collector.cancelAndJoin()
        assertTrue(received.isEmpty())
    }

    companion object {
        private fun unusedDao(): CollectionDao = Proxy.newProxyInstance(
            CollectionDao::class.java.classLoader,
            arrayOf(CollectionDao::class.java),
        ) { _, method, _ -> error("Unexpected DAO call: " + method.name) } as CollectionDao
    }
}
