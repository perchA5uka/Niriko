package com.otakup.niriko.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.calculator.StatsCalculator
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.data.model.stats.StatsUiState
import com.otakup.niriko.data.remote.BroadcastFetcher
import com.otakup.niriko.data.remote.SeasonalFetcher
import com.otakup.niriko.data.refresh.AppForegroundSignals
import com.otakup.niriko.data.refresh.RefreshCoordinator
import com.otakup.niriko.data.refresh.RefreshDecision
import com.otakup.niriko.data.refresh.RefreshKeys
import com.otakup.niriko.data.refresh.RefreshResource
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.EpisodeRepository
import com.otakup.niriko.util.AiringStatus
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate

private const val TAG = "StatsVM"

// ==================== StatsViewModel ====================

class StatsViewModel(
    private val collectionRepository: CollectionRepository,
    private val broadcastFetcher: BroadcastFetcher,
    private val seasonalFetcher: SeasonalFetcher,
    private val episodeRepository: EpisodeRepository? = null,
    /** 刷新编排器（放送日历的新鲜度与失败退避）。null = 不参与编排。 */
    private val refreshCoordinator: RefreshCoordinator? = null,
) : ViewModel() {

    /** 放送日历刷新的单飞槽位：init / 切月 / 手动刷新三路此前都能并发触发。 */
    private var broadcastJob: Job? = null

    /** 放送日历上次成功刷新时间（0 = 从未），供统计页显示陈旧度。 */
    val broadcastLastUpdatedAt: StateFlow<Long> =
        (refreshCoordinator?.snapshots ?: MutableStateFlow(emptyMap()))
            .map { it[RefreshKeys.BROADCAST_CALENDAR]?.lastSuccessAt ?: 0L }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _calendarYear = MutableStateFlow(LocalDate.now().year)
    private val _calendarMonth = MutableStateFlow(LocalDate.now().monthValue)
    private val _calendarMode = MutableStateFlow(CalendarMode.PERSONAL)
    private val _broadcastSchedule = MutableStateFlow<Map<DayOfWeek, List<AiringSubject>>>(emptyMap())
    private val _broadcastError = MutableStateFlow<String?>(null)
    /** 按月缓存的季节性放送数据，key="yyyy-MM"。非当季月份使用。 */
    private val _seasonalAiringMap = MutableStateFlow<Map<String, List<AiringSubject>>>(emptyMap())
    /** 每集数据（已播状态/热力图）。 */
    private val _episodesBySubject = MutableStateFlow<Map<Long, List<EpisodeInfo>>>(emptyMap())
    /** 已预取过的作品 id（避免重复请求）。 */
    private val prefetchedSubjectIds = mutableSetOf<Long>()

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
            _episodesBySubject,
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
        val episodesBySubject = (arrays[7] as? Map<Long, List<EpisodeInfo>>) ?: emptyMap()
        computeStats(items, calYear, calMonth, mode, broadcast, seasonal, broadcastError, episodesBySubject)
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
        // 回到前台：按 30 分钟软 TTL 校验放送日历（命中的话是空操作）
        viewModelScope.launch {
            AppForegroundSignals.events.collect { fetchBroadcastSchedule(force = false) }
        }
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

    /** 每个月度缓存的实际加载时间（用于 TTL 判定；改造前缓存永不失效）。 */
    private val seasonalLoadedAt = mutableMapOf<String, Long>()

    /** 确保指定月份的放送数据已加载（含前 6 个月开播的跨月延续番）。 */
    private fun ensureSeasonalDataForMonth(year: Int, month: Int) {
        val now = LocalDate.now()
        val range = AiringStatus.getCurrentSeasonRange(now)
        val monthStart = LocalDate.of(year, month, 1)

        val key = "%04d-%02d".format(year, month)
        // 已缓存**且未过软 TTL** 才跳过；改造前只要写过 key 就永远不再拉取，
        // 跨月延续的番剧集数在长驻进程里会一直停在旧值。
        val loadedAt = seasonalLoadedAt[key]
        val stillFresh = _seasonalAiringMap.value.containsKey(key) &&
            loadedAt != null &&
            System.currentTimeMillis() - loadedAt < RefreshResource.SEASONAL.softTtlMs
        if (stillFresh) return
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
                val updated = _seasonalAiringMap.value + (key to airingList)
                // 只保留最近 6 个月，避免长时间浏览后内存里堆满历史月份
                _seasonalAiringMap.value = updated.entries.toList().takeLast(MAX_SEASONAL_MONTHS)
                    .associate { it.key to it.value }
                seasonalLoadedAt[key] = System.currentTimeMillis()
                Log.d(TAG, "Seasonal range data loaded for $key: ${airingList.size} subjects")
                // 对当季/翻月所有放送作品预取每集数据（限流 + 去重）。
                prefetchEpisodes(airingList.map { it.subject })
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

    /**
     * 加载放送日历数据。
     *
     * - **单飞**：init / 切月 / 手动刷新三路此前都能并发进入，各自打一遍日历接口；
     * - **新鲜度**：非强制时命中 30 分钟软 TTL 或处于失败退避窗口则跳过。
     */
    private fun fetchBroadcastSchedule(force: Boolean = false) {
        if (broadcastJob?.isActive == true) {
            Log.d(TAG, "fetchBroadcastSchedule: 已有刷新进行中，本次合并")
            return
        }
        broadcastJob = viewModelScope.launch {
            val coordinator = refreshCoordinator
            if (!force && coordinator != null) {
                val decision = coordinator.decide(RefreshKeys.BROADCAST_CALENDAR, RefreshResource.BROADCAST_CALENDAR)
                if (decision == RefreshDecision.FRESH || decision == RefreshDecision.BACKOFF) {
                    Log.d(TAG, "fetchBroadcastSchedule 跳过：$decision")
                    return@launch
                }
            }
            _broadcastError.value = null
            val result = withContext(Dispatchers.IO) {
                broadcastFetcher.fetch()
            }
            _broadcastSchedule.value = result.schedule
            _broadcastError.value = result.error
            val succeeded = result.error == null && result.schedule.isNotEmpty()
            if (result.schedule.isEmpty() && result.error == null) {
                _broadcastError.value = "暂无连载中的作品"
            }
            coordinator?.let {
                if (succeeded) it.recordSuccess(RefreshKeys.BROADCAST_CALENDAR)
                else it.recordFailure(RefreshKeys.BROADCAST_CALENDAR, result.error ?: "暂无连载中的作品")
            }
            // 加载当月季节性数据用于封面补充（v0 API 有完整封面）
            val now = LocalDate.now()
            ensureSeasonalDataForMonth(now.year, now.monthValue)
            // 预取本周放送作品的每集数据（限流 + 失败静默）
            prefetchEpisodes(result.schedule.values.flatten().map { it.subject })
        }
    }

    /**
     * 手动刷新放送日历（统计页日历卡片的下拉/点击刷新）。
     *
     * 必须清掉「已预取过」与月度缓存 —— 改造前它只是再调一次 [fetchBroadcastSchedule]，
     * 而 `_seasonalAiringMap` 永不过期、`prefetchedSubjectIds` 只增不减，
     * 于是「手动刷新」实际上什么都不会重新拉。
     */
    fun refreshBroadcastSchedule() {
        prefetchedSubjectIds.clear()
        seasonalLoadedAt.clear()
        _seasonalAiringMap.value = emptyMap()
        inflightSeasonal.clear()
        fetchBroadcastSchedule(force = true)
    }

    /**
     * 限流预取每集数据：最多 2 并发，单条 3s 超时，失败静默。
     * 主要用于统计页放送信息显示「已播 X / 总 Y」与热力图。
     */
    private fun prefetchEpisodes(subjects: List<SubjectEntity>) {
        val episodeRepo = episodeRepository ?: return
        val ids = subjects.map { it.subjectId }.distinct()
            .filter { prefetchedSubjectIds.add(it) }
            .take(60)
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val semaphore = Semaphore(2)
            val results = ids.map { id ->
                async {
                    runCatching {
                        semaphore.withPermit {
                            withTimeout(3000) { episodeRepo.prefetch(id) }
                        }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
            _episodesBySubject.value = _episodesBySubject.value + ids.zip(results).toMap()
        }
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
        episodesBySubject: Map<Long, List<EpisodeInfo>>,
    ): StatsUiState {
        return try {
            val base = computeBaseStats(items)
            val calendarDayEvents = StatsCalculator.computeCalendarEvents(items, calYear, calMonth, mode, broadcast, seasonal)
            base.copy(
                calendarYear = calYear, calendarMonth = calMonth,
                calendarDayEvents = calendarDayEvents,
                calendarMode = mode, broadcastSchedule = broadcast,
                broadcastError = broadcastError,
                episodesBySubject = episodesBySubject,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Stats calculation failed", e)
            StatsUiState(isLoading = false)
        }
    }

    private companion object {
        /** 月度放送缓存在内存里最多保留几个月。 */
        const val MAX_SEASONAL_MONTHS = 6
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
    private val episodeRepository: EpisodeRepository? = null,
    private val refreshCoordinator: RefreshCoordinator? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            return StatsViewModel(
                collectionRepository, broadcastFetcher, seasonalFetcher,
                episodeRepository, refreshCoordinator,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
