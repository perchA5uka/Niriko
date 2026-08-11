package com.otakup.niriko.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.calculator.StatsCalculator
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.data.model.stats.StatsUiState
import com.otakup.niriko.data.remote.BroadcastFetcher
import com.otakup.niriko.data.remote.SeasonalFetcher
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.util.AiringStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate

private const val TAG = "StatsVM"

// ==================== StatsViewModel ====================

class StatsViewModel(
    private val collectionRepository: CollectionRepository,
    private val broadcastFetcher: BroadcastFetcher,
    private val seasonalFetcher: SeasonalFetcher,
) : ViewModel() {

    private val _calendarYear = MutableStateFlow(LocalDate.now().year)
    private val _calendarMonth = MutableStateFlow(LocalDate.now().monthValue)
    private val _calendarMode = MutableStateFlow(CalendarMode.PERSONAL)
    private val _broadcastSchedule = MutableStateFlow<Map<DayOfWeek, List<AiringSubject>>>(emptyMap())
    private val _broadcastError = MutableStateFlow<String?>(null)
    /** 按月缓存的季节性放送数据，key="yyyy-MM"。非当季月份使用。 */
    private val _seasonalAiringMap = MutableStateFlow<Map<String, List<AiringSubject>>>(emptyMap())

    /** 全局异常处理器：防止协程未捕获异常导致 App 闪退。 */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
    }

    /** 所有上游 Flow 的异常捕获包装。任何 Flow 出错都降级为空数据，绝不崩溃。 */
    private val safeCollectionFlow = collectionRepository.observeAllWithSubject()
        .catch { Log.e(TAG, "collection flow error", it); emit(emptyList()) }

    val uiState: StateFlow<StatsUiState> = combine(
        listOf(
            safeCollectionFlow,
            _calendarYear,
            _calendarMonth,
            _calendarMode,
            _broadcastSchedule,
            _seasonalAiringMap,
            _broadcastError,
        )
    ) { arrays ->
        @Suppress("UNCHECKED_CAST")
        val items = (arrays[0] as? List<*>)?.filterIsInstance<CollectionWithSubject>() ?: emptyList()
        val calYear = (arrays[1] as? Int) ?: LocalDate.now().year
        val calMonth = (arrays[2] as? Int) ?: LocalDate.now().monthValue
        val mode = (arrays[3] as? CalendarMode) ?: CalendarMode.PERSONAL
        val broadcast = (arrays[4] as? Map<DayOfWeek, List<AiringSubject>>) ?: emptyMap()
        val seasonal = (arrays[5] as? Map<String, List<AiringSubject>>) ?: emptyMap()
        val broadcastError = (arrays[6] as? String)
        computeStats(items, calYear, calMonth, mode, broadcast, seasonal, broadcastError)
    }
        // 全量统计计算移出主线程（combine 收集器在主线程，computeStats 含双重嵌套循环）
        .flowOn(Dispatchers.Default)
        .catch { e ->
        // combine 上游 Flow 抛出异常时，忽略并保持 Flow 活跃
        Log.e(TAG, "Stats combine caught exception", e)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(isLoading = true),
    )

    init {
        fetchBroadcastSchedule()
    }

    fun switchMonth(delta: Int) {
        val current = LocalDate.of(_calendarYear.value, _calendarMonth.value, 1)
        val newDate = current.plusMonths(delta.toLong())
        _calendarYear.value = newDate.year
        _calendarMonth.value = newDate.monthValue
        ensureSeasonalDataForMonth(newDate.year, newDate.monthValue)
        // 切到当前月或未来月份时刷新放送数据，保证 broadcast 不滞后
        if (!newDate.isBefore(LocalDate.now().withDayOfMonth(1))) {
            fetchBroadcastSchedule()
        }
    }

    /** 正在加载中的月份 key 集合：快速翻月时同月只发一次请求（防并发重复大请求）。 */
    private val inflightSeasonal = mutableSetOf<String>()

    /** 确保指定月份的放送数据已加载（含前 6 个月开播的跨月延续番）。 */
    private fun ensureSeasonalDataForMonth(year: Int, month: Int) {
        val now = LocalDate.now()
        val range = AiringStatus.getCurrentSeasonRange(now)
        val monthStart = LocalDate.of(year, month, 1)

        val key = "%04d-%02d".format(year, month)
        if (_seasonalAiringMap.value.containsKey(key)) return // 已缓存
        if (!inflightSeasonal.add(key)) return // 已在加载中

        // 对非当季月份，检查是否在有效范围内（防止翻到太远的过去/未来）
        if (monthStart.isBefore(range.start) || !monthStart.isBefore(range.end)) {
            if (year < now.year - 2 || year > now.year + 1) return
        }

        viewModelScope.launch {
            try {
                // 加载 [目标月初 − 6 个月, 目标月末] 开播的番，跨月延续靠 StatsCalculator inRange 过滤
                val rangeStart = monthStart.minusMonths(6)
                val rangeEnd = monthStart.plusMonths(1)
                val airingList = withContext(Dispatchers.IO) {
                    seasonalFetcher.fetchSeasonalInRange(rangeStart, rangeEnd)
                }
                _seasonalAiringMap.value = _seasonalAiringMap.value + (key to airingList)
                Log.d(TAG, "Seasonal range data loaded for $key: ${airingList.size} subjects")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load seasonal range data for $key", e)
            } finally {
                inflightSeasonal.remove(key)
            }
        }
    }

    fun switchCalendarMode(mode: CalendarMode) {
        _calendarMode.value = mode
    }

    /** 加载放送日历数据。 */
    private fun fetchBroadcastSchedule() {
        viewModelScope.launch {
            _broadcastError.value = null
            val result = withContext(Dispatchers.IO) {
                broadcastFetcher.fetch()
            }
            _broadcastSchedule.value = result.schedule
            _broadcastError.value = result.error
            if (result.schedule.isEmpty() && result.error == null) {
                _broadcastError.value = "暂无连载中的作品"
            }
            // 加载当月季节性数据用于封面补充（v0 API 有完整封面）
            val now = LocalDate.now()
            ensureSeasonalDataForMonth(now.year, now.monthValue)
        }
    }

    /** 手动刷新放送日历。 */
    fun refreshBroadcastSchedule() {
        fetchBroadcastSchedule()
    }

    // ==================== 统计计算 ====================

    /** 基础统计缓存：仅随 items 变化才重算（切月份/模式/放送刷新不重复计算）。 */
    private var baseItemsSignature: Int = 0
    private var cachedBaseStats: StatsUiState? = null

    private fun computeStats(
        items: List<CollectionWithSubject>,
        calYear: Int,
        calMonth: Int,
        mode: CalendarMode,
        broadcast: Map<DayOfWeek, List<AiringSubject>>,
        seasonal: Map<String, List<AiringSubject>>,
        broadcastError: String?,
    ): StatsUiState {
        return try {
            val base = computeBaseStats(items)
            val calendarDayEvents = StatsCalculator.computeCalendarEvents(items, calYear, calMonth, mode, broadcast, seasonal)
            base.copy(
                calendarYear = calYear, calendarMonth = calMonth,
                calendarDayEvents = calendarDayEvents,
                calendarMode = mode, broadcastSchedule = broadcast,
                broadcastError = broadcastError,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Stats calculation failed", e)
            StatsUiState(isLoading = false)
        }
    }

    /** 基础统计：按 items 内容签名缓存，收藏未变化时直接复用上次结果。 */
    private fun computeBaseStats(items: List<CollectionWithSubject>): StatsUiState {
        val signature = items.hashCode()
        val cached = cachedBaseStats
        if (signature == baseItemsSignature && cached != null) {
            return cached
        }
        return StatsCalculator.computeStats(items).also {
            cachedBaseStats = it
            baseItemsSignature = signature
        }
    }
}

class StatsViewModelFactory(
    private val collectionRepository: CollectionRepository,
    private val broadcastFetcher: BroadcastFetcher,
    private val seasonalFetcher: SeasonalFetcher,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            return StatsViewModel(collectionRepository, broadcastFetcher, seasonalFetcher) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
