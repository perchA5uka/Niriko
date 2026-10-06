package com.otakup.niriko.data.remote

import android.app.Application
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.repository.SubjectRepository
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SeasonalFetcherTest {
    private val start = LocalDate.of(2025, 1, 1)
    private val end = LocalDate.of(2025, 8, 1)
    private val writes = mutableListOf<List<SubjectEntity>>()
    private fun fetcher(load: (String, Int) -> List<SubjectEntity>): SeasonalFetcher {
        val remote = Proxy.newProxyInstance(SubjectRemoteDataSource::class.java.classLoader, arrayOf(SubjectRemoteDataSource::class.java)) { _, method, args ->
            when (method.name) {
                "getSubjectsInDateRange", "getSubjectsByMonth" -> load(method.name, args!![0] as Int)
                else -> error("Unexpected remote call: "+method.name)
            }
        } as SubjectRemoteDataSource
        val dao = Proxy.newProxyInstance(SubjectDao::class.java.classLoader, arrayOf(SubjectDao::class.java)) { _, method, args ->
            when (method.name) {
                "upsertAll" -> { @Suppress("UNCHECKED_CAST") writes.add(args!![0] as List<SubjectEntity>); Unit }
                else -> error("Unexpected DAO call: "+method.name)
            }
        } as SubjectDao
        return SeasonalFetcher(remote, SubjectRepository(dao, remote))
    }

    @Test fun rangeFailureIsNotReturnedOrWrittenAsEmptySuccess() = runBlocking {
        val failure = IllegalStateException("offline")
        val fetcher = fetcher { _, type -> if (type == 6) throw failure else listOf(SubjectEntity(subjectId = 1, title = "Anime", type = SubjectType.ANIME, airDate = "2025-07-01", totalEpisodes = 12)) }
        try { fetcher.fetchSeasonalInRange(start, end); fail("Expected original failure") } catch (e: IllegalStateException) { assertSame(failure, e) }
        assertTrue(writes.isEmpty())
    }

    @Test fun explicitPartialResultRetainsAnimeAndMarksRealFailure() = runBlocking {
        val result = fetcher { _, type ->
            if (type == 6) throw IllegalStateException("offline")
            listOf(SubjectEntity(subjectId = 1, title = "Anime", type = SubjectType.ANIME, airDate = "2025-07-01", totalEpisodes = 12))
        }.fetchSeasonalRangeResult(start, end)
        assertFalse(result.isComplete)
        assertEquals(setOf(6), result.failedTypes)
        assertEquals(listOf(1L), result.subjects.map { it.subject.subjectId })
        assertEquals(listOf(1L), writes.single().map { it.subjectId })
    }

    @Test fun partialApiStillPropagatesAnimeFailureAndCancellation() = runBlocking {
        val failure = IllegalStateException("offline")
        try { fetcher { _, _ -> throw failure }.fetchSeasonalRangeResult(start, end); fail("Expected failure") } catch (e: IllegalStateException) { assertSame(failure, e) }
        val cancellation = CancellationException("cancelled")
        try { fetcher { _, type -> if (type == 6) throw cancellation else emptyList() }.fetchSeasonalRangeResult(start, end); fail("Expected cancellation") } catch (e: CancellationException) { assertSame(cancellation, e) }
        assertTrue(writes.isEmpty())
    }

    @Test fun monthFailureAlsoPropagates() = runBlocking {
        val failure = IllegalStateException("offline")
        try { fetcher { _, _ -> throw failure }.fetchSeasonal(2025, 7); fail("Expected failure") } catch (e: IllegalStateException) { assertSame(failure, e) }
        assertTrue(writes.isEmpty())
    }

    @Test fun legitimateEmptyRangeRemainsSuccessful() = runBlocking {
        assertTrue(fetcher { _, _ -> emptyList() }.fetchSeasonalInRange(start, end).isEmpty())
    }

    @Test fun parentCancellationPropagatesWithoutCacheWrite() = runBlocking {
        val cancellation = CancellationException("cancelled")
        try { fetcher { _, _ -> throw cancellation }.fetchSeasonalInRange(start, end); fail("Expected cancellation") } catch (e: CancellationException) { assertSame(cancellation, e) }
        assertTrue(writes.isEmpty())
    }
}
