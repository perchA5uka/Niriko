package com.otakup.niriko.data.remote.bangumi

import com.otakup.niriko.data.remote.bangumi.dto.SearchRequestDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.SubjectDto
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HistoricalSearchContractTest {
    @Test fun previousMonthUsesSupportedPostSortAndPreservesRangeAcrossPages() = runBlocking {
        val requests = mutableListOf<SearchRequestDto>()
        val offsets = mutableListOf<Int>()
        val api = Proxy.newProxyInstance(BangumiApiService::class.java.classLoader, arrayOf(BangumiApiService::class.java)) { _, method, args ->
            check(method.name == "searchSubjectsByDateRange")
            requests += args!![0] as SearchRequestDto
            val offset = args[2] as Int
            offsets += offset
            val count = if (offset == 0) 100 else 1
            SearchResponseDto(data = List(count) { SubjectDto(id = (offset + it + 1).toLong(), name = "subject", type = 2) }, total = 101, limit = 100, offset = offset)
        } as BangumiApiService
        val result = BangumiDataSource(api).getSubjectsInDateRange(2, "2026-03-01", "2026-10-01")
        assertEquals(101, result.size)
        assertEquals(listOf(0, 100), offsets)
        assertTrue(requests.all { it.sort == "rank" && it.keyword.isEmpty() })
        assertTrue(requests.all { it.filter!!.airDate == listOf(">=2026-03-01", "<2026-10-01") })
    }
}
