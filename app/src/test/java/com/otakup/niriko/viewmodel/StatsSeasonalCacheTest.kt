package com.otakup.niriko.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.remote.BroadcastFetcher
import com.otakup.niriko.data.remote.SeasonalFetcher
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.data.refresh.RefreshResource
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StatsSeasonalCacheTest {
    private val year = LocalDate.now().year - 1
    private val key = "%04d-01".format(year)
    private val expectedStart = LocalDate.of(year, 1, 1).minusMonths(6).toString()
    private var fail = true
    private var requests = 0
    private val ranges = mutableListOf<Pair<String, String>>()

    private fun vm(): StatsViewModel {
        val remote = proxy<SubjectRemoteDataSource> { method, args ->
            when (method) {
                "getCalendar" -> emptyList<Any>()
                "getSubjectsInDateRange" -> {
                    if (args!![1] == expectedStart) {
                        requests++
                        ranges += (args[1] as String) to (args[2] as String)
                        if (fail) throw IllegalStateException("offline")
                    }
                    emptyList<Any>()
                }
                else -> error("Unexpected remote call: " + method)
            }
        }
        val subjects = SubjectRepository(proxy<SubjectDao> { name, _ -> error("Unexpected DAO call: " + name) }, remote)
        val collections = CollectionRepository(proxy<CollectionDao> { name, _ ->
            if (name == "observeAllWithSubject") flowOf(emptyList<Any>()) else error("Unexpected collection call: " + name)
        })
        return StatsViewModel(collections, BroadcastFetcher(remote, subjects), SeasonalFetcher(remote, subjects), seasonalDispatcher = Dispatchers.Unconfined)
    }

    private fun request(vm: StatsViewModel) {
        val method = StatsViewModel::class.java.getDeclaredMethod("ensureSeasonalDataForMonth", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(vm, year, 1)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun timestamps(vm: StatsViewModel): MutableMap<String, Long> = field(vm, "seasonalLoadedAt") as MutableMap<String, Long>
    private fun field(vm: StatsViewModel, name: String): Any {
        val field = StatsViewModel::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(vm)!!
    }

    @Test fun failedMonthRetriesAndOnlySuccessfulEmptyMonthGetsTtl() {
        val vm = vm()
        val store = ViewModelStore()
        store.put("stats", vm)
        try {
            request(vm)
            assertFalse(timestamps(vm).containsKey(key))
            assertFalse((field(vm, "inflightSeasonal") as Set<*>).contains(key))
            fail = false
            request(vm)
            assertTrue(timestamps(vm).containsKey(key))
            assertEquals(3, requests) // failed ANIME, successful ANIME + REAL
            assertTrue(ranges.all { it.first == expectedStart && it.second == LocalDate.of(year, 2, 1).toString() })
            request(vm)
            assertEquals(3, requests) // legitimate empty result is fresh
            timestamps(vm)[key] = System.currentTimeMillis() - RefreshResource.SEASONAL.softTtlMs - 1
            fail = true
            request(vm)
            assertEquals(4, requests)
            assertTrue(timestamps(vm)[key]!! < System.currentTimeMillis() - RefreshResource.SEASONAL.softTtlMs)
            fail = false
            request(vm)
            assertEquals(6, requests)
        } finally { store.clear() }
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, args -> call(method.name, args) } as T
}
