package com.otakup.niriko.data.repository

import android.app.Application
import com.otakup.niriko.data.local.dao.EpisodeDao
import com.otakup.niriko.data.local.entity.EpisodeEntity
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EpisodeRepositoryFailureTest {
    private val cached = listOf(EpisodeEntity(1L, 414461L, 10.0, 10.0, airdate = "2023-12-25", lastSyncTime = 1L))
    private var deletes = 0
    private fun repository(failure: Exception): EpisodeRepository {
        val dao = proxy<EpisodeDao> { name ->
            when (name) {
                "getBySubject" -> cached
                "deleteBySubject" -> { deletes++; Unit }
                else -> error("Unexpected DAO call: " + name)
            }
        }
        val remote = proxy<SubjectRemoteDataSource> { name ->
            if (name == "getEpisodes") throw failure else error("Unexpected source call: " + name)
        }
        return EpisodeRepository(dao, remote)
    }
    @Test fun failedRefreshKeepsHistoricalDatesAndDoesNotDeleteCache() = runBlocking {
        val result = repository(IllegalStateException("offline")).getEpisodes(414461L, forceRefresh = true)
        assertEquals("2023-12-25", result.single().airdate)
        assertEquals(0, deletes)
    }
    @Test fun cancellationDoesNotBecomeSuccessfulEmptyOrFallback() = runBlocking {
        val cancelled = CancellationException("user cancelled")
        try { repository(cancelled).getEpisodes(414461L, forceRefresh = true); fail("Cancellation swallowed") }
        catch (actual: CancellationException) {
            assertEquals(cancelled.message, actual.message)
            // Coroutine stack recovery may copy the exception across the IO dispatcher.
            assertTrue(actual === cancelled || generateSequence<Throwable>(actual) { it.cause }.any { it === cancelled })
        }
        assertEquals(0, deletes)
    }
    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ -> call(method.name) } as T
}
