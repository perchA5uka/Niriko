package com.otakup.niriko.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.stats.BroadcastMonthStatus
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.data.remote.BroadcastFetcher
import com.otakup.niriko.data.remote.CalendarDaySchedule
import com.otakup.niriko.data.remote.SeasonalFetcher
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.SubjectRepository
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StatsBroadcastMonthTest {
    private val september = "2025-09"
    private fun anime(id: Long, date: String, episodes: Int, cover: String? = "https://example.invalid/cover.jpg") =
        SubjectEntity(subjectId = id, title = "Fixture", type = SubjectType.ANIME, airDate = date, totalEpisodes = episodes, platform = "TV", coverUrl = cover, pinyinKey = "fixture")
    private val continuing = anime(1, "2025-07-01", 24)
    private val debut = anime(2, "2025-09-03", 12, cover = null)
    private val october = anime(3, "2025-10-01", 12)

    private inner class Fixture {
        var clock = 1000L
        var behavior = "success"
        var hold = false
        var pending: Continuation<List<SubjectEntity>>? = null
        val requests = mutableListOf<Triple<Int, String, String>>()
        val remote = proxy<SubjectRemoteDataSource> { name, args ->
            when (name) {
                "getCalendar" -> listOf(CalendarDaySchedule(october.airDate!!.let(LocalDate::parse).dayOfWeek, listOf(october)))
                "getSubjectsInDateRange" -> {
                    val type = args!![0] as Int
                    val start = args[1] as String
                    val end = args[2] as String
                    requests += Triple(type, start, end)
                    if (end == "2025-10-01") {
                        if (behavior == "failure") throw IllegalStateException("offline")
                        if (type == 6 && behavior == "partial") throw IllegalStateException("offline")
                        if (hold && type == 2) {
                            @Suppress("UNCHECKED_CAST")
                            pending = args.last() as Continuation<List<SubjectEntity>>
                            COROUTINE_SUSPENDED
                        } else if (type == 6 || behavior == "empty") emptyList<SubjectEntity>()
                        else listOf(continuing, debut)
                    } else if (type == 2) listOf(october) else emptyList<SubjectEntity>()
                }
                else -> error("Unexpected remote: $name")
            }
        }
        val subjects = SubjectRepository(proxy<SubjectDao> { name, _ ->
            when (name) { "getById" -> null; "upsertAll" -> Unit; else -> error("Unexpected DAO: $name") }
        }, remote)
        val vm = StatsViewModel(
            CollectionRepository(proxy<CollectionDao> { name, _ ->
                if (name == "observeAllWithSubject") flowOf(emptyList<Any>()) else error(name)
            }), BroadcastFetcher(remote, subjects), SeasonalFetcher(remote, subjects),
            seasonalDispatcher = Dispatchers.Unconfined, today = { LocalDate.of(2025, 10, 15) },
            nowMillis = { clock }, broadcastDispatcher = Dispatchers.Unconfined, statsDispatcher = Dispatchers.Unconfined,
        )
        val store = ViewModelStore().also { it.put("stats", vm) }
        val collector = CoroutineScope(Dispatchers.Unconfined).launch { vm.uiState.collect {} }
        init { pump(); vm.switchCalendarMode(CalendarMode.BROADCAST); vm.switchCalendarView(true); pump() }
        fun pump() { shadowOf(Looper.getMainLooper()).idle() }
        fun back() { vm.switchMonth(-1); pump() }
        fun refresh() { vm.refreshBroadcastSchedule(); pump() }
        fun state() = vm.broadcastMonths.value.getValue(september)
        fun requestCount() = requests.count { it.first == 2 && it.third == "2025-10-01" }
        fun close() { collector.cancel(); store.clear(); pump() }
        fun assertSeptember() {
            val state = vm.uiState.value
            assertEquals(9, state.calendarMonth)
            assertTrue(state.calendarExpanded)
            assertEquals(september, state.selectedBroadcastMonth.key)
            val events = state.calendarDayEvents
            assertTrue(events.isNotEmpty())
            assertTrue(events.keys.all { it.monthValue == 9 })
            val ids = events.values.flatMap { it.broadcastSubjects }.map { it.subjectId }.toSet()
            assertTrue(ids.containsAll(setOf(1L, 2L)))
            assertFalse(ids.contains(3L))
            assertTrue(events.values.any { it.hasContinuing })
            // Day-cell and day-detail consume the same event snapshot, including a coverless event.
            assertTrue(events.values.all { it.hasEvents })
            assertTrue(events.values.any { day -> day.broadcastSubjects.any { it.subjectId == 2L && it.coverUrl == null } })
            assertEquals(BroadcastMonthStatus.SUCCESS, state.selectedBroadcastMonth.status)
        }
    }

    @Test fun octoberBackToSeptemberAndRefreshRemainNonEmpty() {
        val f = Fixture()
        try {
            assertTrue(f.vm.uiState.value.calendarDayEvents.values.any { day -> day.broadcastSubjects.any { it.subjectId == 3L } })
            f.back(); f.assertSeptember()
            assertTrue(f.requests.filter { it.third == "2025-10-01" }.all { it.second == "2025-03-01" })
            assertEquals(1000L, f.state().lastSuccessAt)
            f.clock = 2000L; f.refresh(); f.assertSeptember()
            assertEquals(2, f.requestCount())
            assertEquals(2000L, f.state().lastSuccessAt)
        } finally { f.close() }
    }

    @Test fun failureThenRefreshAndEmptyThenRefreshBothRecover() {
        for (initial in listOf("failure", "empty")) {
            val f = Fixture()
            try {
                f.behavior = initial; f.back()
                assertEquals(if (initial == "failure") BroadcastMonthStatus.ERROR else BroadcastMonthStatus.EMPTY, f.state().status)
                assertEquals(if (initial == "failure") 0L else 1000L, f.state().lastSuccessAt)
                f.behavior = "success"; f.refresh(); f.assertSeptember()
                assertEquals(2, f.requestCount())
            } finally { f.close() }
        }
    }

    @Test fun partialKeepsEventsAndLastSuccessWithoutFreshTtl() {
        val f = Fixture()
        try {
            f.back(); f.assertSeptember()
            f.behavior = "partial"; f.clock = 2000L; f.refresh()
            assertEquals(BroadcastMonthStatus.ERROR, f.state().status)
            assertTrue(f.state().isPartial)
            assertNotNull(f.state().error)
            assertEquals(1000L, f.state().lastSuccessAt)
            assertTrue(f.vm.uiState.value.calendarDayEvents.isNotEmpty())
            // Returning without manual invalidation must retry: partial is not a full-success TTL.
            f.vm.switchMonth(1); f.pump(); f.back()
            assertEquals(3, f.requestCount())
            f.behavior = "success"; f.refresh(); f.assertSeptember()
            assertFalse(f.state().isPartial)
        } finally { f.close() }
    }

    @Test fun refreshDuringInflightMonthIsDeduplicatedAndShowsLoading() {
        val f = Fixture()
        try {
            f.hold = true; f.back()
            assertEquals(BroadcastMonthStatus.LOADING, f.state().status)
            assertEquals(0L, f.state().lastSuccessAt)
            f.refresh(); f.refresh()
            assertEquals(1, f.requestCount())
            f.hold = false; f.pending!!.resume(listOf(continuing, debut)); f.pump()
            f.assertSeptember()
        } finally { f.close() }
    }

    @Test fun failedRefreshKeepsSuccessfulContentAndTime() {
        val f = Fixture()
        try {
            f.back(); val before = f.vm.uiState.value.calendarDayEvents
            f.behavior = "failure"; f.clock = 2000L; f.refresh()
            assertEquals(BroadcastMonthStatus.ERROR, f.state().status)
            assertEquals(1000L, f.state().lastSuccessAt)
            assertEquals(before, f.vm.uiState.value.calendarDayEvents)
        } finally { f.close() }
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, args -> call(method.name, args) } as T
}
