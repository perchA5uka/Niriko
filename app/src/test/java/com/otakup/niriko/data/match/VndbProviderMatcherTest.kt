package com.otakup.niriko.data.match

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.vndb.VndbApiService
import com.otakup.niriko.data.remote.vndb.dto.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VndbProviderMatcherTest {
    private val subject = SubjectEntity(subjectId = 1, title = "Witch Trial", titleCN = "Trial", type = SubjectType.GAME, airDate = "2025-07-17")
    private class Api(val respond: (VndbQueryRequest) -> List<VndbVisualNovelDto>) : VndbApiService {
        val requests = mutableListOf<VndbQueryRequest>()
        override suspend fun query(request: VndbQueryRequest): VndbQueryResponse {
            requests += request
            return VndbQueryResponse(respond(request))
        }
    }

    @Test fun nonemptyLowQualityPrimaryMergesFallbackAndDeduplicates() = runBlocking {
        val weak = VndbVisualNovelDto(id = "v1", title = "Unrelated")
        val exact = VndbVisualNovelDto(id = "v2", title = "Witch Trial")
        val api = Api { if (it.filters.toString().startsWith("[\"search\"")) listOf(weak) else listOf(weak, exact) }
        val results = VndbProviderMatcher(api).query("Trial", subject)
        assertEquals(listOf("v1", "v2"), results.map { it.externalId })
        assertEquals(2, api.requests.size)
        assertTrue(api.requests.all { it.results == 10 })
    }

    @Test fun emptyPrimaryUsesOneFallbackAndBlankQueryMakesNoRequest() = runBlocking {
        val api = Api { emptyList() }
        val matcher = VndbProviderMatcher(api)
        assertTrue(matcher.query("", subject).isEmpty())
        assertTrue(api.requests.isEmpty())
        assertTrue(matcher.query("missing", subject).isEmpty())
        assertEquals(2, api.requests.size)
    }

    @Test fun exactPrimaryUsingInfoboxAliasDoesNotSpendFallback() = runBlocking {
        val api = Api { listOf(VndbVisualNovelDto(id = "v1", title = "Alternate Name")) }
        val matcher = VndbProviderMatcher(api)
        val results = ExternalMatchService(listOf(matcher)).match(matcher.provider, subject, listOf(InfoBoxEntry("别名", "Alternate Name")))
        assertEquals(1, api.requests.size)
        assertTrue(results.first().confidence >= 0.92f)
    }

    @Test fun subBindingConfidenceDoesNotStopLaterOriginalTitleRecall() = runBlocking {
        // 0.78 * 0.9 + 0.12 = 0.822: above the shared 0.80, below 0.92.
        val original = subject.copy(title = "abcdefghij", titleCN = "ABCDEFGHIJ", airDate = "2025-01-01")
        val api = Api { request ->
            val text = request.filters.toString()
            if (text.contains("ABCDEFGHIJ")) listOf(VndbVisualNovelDto(id = "v1", title = "abcdefghiX", released = "2025-01-01"))
            else listOf(VndbVisualNovelDto(id = "v2", title = "abcdefghij"))
        }
        val matcher = VndbProviderMatcher(api)
        val results = ExternalMatchService(listOf(matcher)).match(matcher.provider, original)
        assertEquals("v2", results.first().externalId)
        assertEquals(3, api.requests.size)
    }

    @Test fun requestBudgetCapsAutomaticSearchEvenWhenCallerRequestsMore() = runBlocking {
        val api = Api { emptyList() }
        val matcher = VndbProviderMatcher(api)
        ExternalMatchService(listOf(matcher)).match(matcher.provider, subject, listOf(InfoBoxEntry("别名", "Alias1 / Alias2 / Alias3 / Alias4 / Alias5")), maxQueries = 99)
        assertEquals(8, api.requests.size)
    }

    @Test fun manualSearchKeepsLowConfidenceResults() = runBlocking {
        val api = Api { listOf(VndbVisualNovelDto(id = "v1", title = "Unrelated")) }
        val matcher = VndbProviderMatcher(api)
        val results = ExternalMatchService(listOf(matcher)).searchByQuery(matcher.provider, "user query", subject)
        assertEquals(1, results.size)
        assertEquals(MatchCandidate.SOURCE_MANUAL, results.single().source)
        assertTrue(results.single().confidence < 0.92f)
    }
}
