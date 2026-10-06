package com.otakup.niriko.data.remote.vndb

import com.otakup.niriko.data.local.dao.VndbDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.VndbBindingEntity
import com.otakup.niriko.data.match.MatchScorer
import com.otakup.niriko.data.match.VndbProviderMatcher
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.sources.VndbRatingSource
import com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest
import com.otakup.niriko.data.remote.vndb.dto.VndbQueryResponse
import com.otakup.niriko.data.repository.VndbRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class)
class VndbLiveSchemaTest {
    private val fixture = javaClass.classLoader!!.getResourceAsStream("vndb/witch-trials.json")!!.bufferedReader().use { it.readText() }
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val parsed: VndbQueryResponse get() = json.decodeFromString(VndbQueryResponse.serializer(), fixture)

    @Test fun realDecimalCandidateMatchesChineseTitleAboveUnchangedThreshold() = runBlocking {
        val subject = SubjectEntity(123L, "魔法少女ノ魔女裁判", "魔法少女的魔女审判", SubjectType.GAME, airDate = "2025-07-18")
        var calls = 0
        val api = object : VndbApiService {
            override suspend fun query(request: VndbQueryRequest): VndbQueryResponse { calls++; return parsed }
        }
        val candidates = VndbProviderMatcher(api).query(subject.titleCN!!, subject)
        assertEquals("v50283", candidates.single().externalId)
        assertTrue(MatchScorer.score(listOf(subject.title, subject.titleCN!!), candidates.single(), 2025).score >= 0.92f)
        assertEquals(1, calls)
    }

    @Test fun candidateIdCanBePersistedWithoutTruncatingDecimalDetails() = runBlocking {
        var saved: VndbBindingEntity? = null
        val dao = Proxy.newProxyInstance(VndbDao::class.java.classLoader, arrayOf(VndbDao::class.java)) { _, method, args ->
            when (method.name) {
                "upsertBinding" -> { saved = args!![0] as VndbBindingEntity; Unit }
                "getBindingBySubjectId" -> saved
                else -> error("Unexpected DAO call: " + method.name)
            }
        } as VndbDao
        val repository = VndbRepository(dao, object : VndbApiService {
            override suspend fun query(request: VndbQueryRequest): VndbQueryResponse = parsed
        })
        assertTrue(repository.bindManually(123L, parsed.results.single().id))
        assertEquals("v50283", repository.getBinding(123L)!!.vndbId)
        assertEquals(80.7, repository.getDetail("v50283")!!.rating!!, 0.001)
    }

    @Test fun authoritativeScoreUsesHundredPointNativeScale() {
        val item = json.parseToJsonElement(fixture).jsonObject["results"]!!.jsonArray.single().jsonObject
        val rating = VndbRatingSource().mapRating("v50283", item)!!
        assertEquals(8.07f, rating.score!!, 0.001f)
        assertEquals(80.7f, rating.nativeScore!!, 0.001f)
        assertEquals(100f, rating.scoreMax, 0f)
    }
}
