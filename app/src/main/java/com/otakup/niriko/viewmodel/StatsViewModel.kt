package com.otakup.niriko.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.calculator.CalendarRangeCalculator
import com.otakup.niriko.data.calculator.StatsCalculator
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.data.model.stats.BroadcastMonthState
import com.otakup.niriko.data.model.stats.BroadcastMonthStatus
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.time.YearMonth

private const val TAG = "StatsVM"

// ==================== StatsViewModel ====================

class StatsViewModel(
    private val collectionRepository: CollectionRepository,
    private val broadcastFetcher: BroadcastFetcher,
    private val seasonalFetcher: SeasonalFetcher,
    private val episodeRepository: EpisodeRepository? = null,
    /** 刷新编排器（放送日历的新鲜度与失败退避）。null = 不参与编排。 */
    private val refreshCoordinator: RefreshCoordinator? = null,
    private val seasonalDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val broadcastDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val statsDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** 放送日历刷新的单飞槽位：init / 切月 / 手动刷新三路此前都能并发触发。 */
    private var broadcastJob: Job? = null

    /** 放送日历上次成功刷新时间（0 = 从未），供统计页显示陈旧度。 */
    val broadcastLastUpdatedAt: StateFlow<Long> =
        (refreshCoordinator?.snapshots ?: MutableStateFlow(emptyMap()))
            .map { it[RefreshKeys.BROADCAST_CALENDAR]?.lastSuccessAt ?: 0L }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * 日历锚点日期：月视图用它定位月份，周视图用它定位周（周日起始）。
     *
     * 周 / 月切换不修改锚点，因此切换前后停留在同一周 / 同一月，不跳变。
     */
    private val _calendarAnchor = MutableStateFlow(today())

    /** 日历视图粒度：true = 月视图（展开，默认，与双模改造前一致），false = 周视图（收起）。 */
    private val _calendarExpanded = MutableStateFlow(true)

    /** 周 / 月视图粒度，供统计页绑定切换控件。 */
    val calendarExpanded: StateFlow<Boolean> = _calendarExpanded.asStateFlow()

    private val _calendarMode = MutableStateFlow(CalendarMode.PERSONAL)
    private val _broadcastSchedule = MutableStateFlow<Map<DayOfWeek, List<AiringSubject>>>(emptyMap())
    private val _broadcastError = MutableStateFlow<String?>(null)
    private val _broadcastMonths = MutableStateFlow<Map<String, BroadcastMonthState>>(emptyMap())
    val broadcastMonths: StateFlow<Map<String, BroadcastMonthState>> = _broadcastMonths.asStateFlow()
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
            _calendarAnchor,
            _calendarExpanded,
            _calendarMode,
            _broadcastSchedule,
            _seasonalAiringMap,
            _broadcastError,
            _episodesBySubject,
            _broadcastMonths,
        )
    ) { arrays ->
        @Suppress("UNCHECKED_CAST")
        val items = (arrays[0] as? List<*>)?.filterIsInstance<CollectionWithSubject>() ?: emptyList()
        val anchor = (arrays[1] as? LocalDate) ?: LocalDate.now()
        val expanded = (arrays[2] as? Boolean) ?: true
        val mode = (arrays[3] as? CalendarMode) ?: CalendarMode.PERSONAL
        val broadcast = (arrays[4] as? Map<DayOfWeek, List<AiringSubject>>) ?: emptyMap()
        val seasonal = (arrays[5] as? Map<String, List<AiringSubject>>) ?: emptyMap()
        val months = (arrays[8] as? Map<String, BroadcastMonthState>) ?: emptyMap()
        val key = YearMonth.from(anchor).toString()
        val monthState = months[key] ?: BroadcastMonthState(key = key)
        // A weekly calendar failure is not evidence that the selected historical month failed.
        val broadcastError = monthState.error ?: if (!expanded) (arrays[6] as? String) else null
        val episodesBySubject = (arrays[7] as? Map<Long, List<EpisodeInfo>>) ?: emptyMap()
        computeStats(items, anchor, expanded, mode, broadcast, seasonal, broadcastError, episodesBySubject, monthState)
    }
        // 全量统计计算移出主线程（combine 收集器在主线程，computeStats 含双重嵌套循环）
        .flowOn(statsDispatcher)
        .catch { e ->
        // combine 上游 Flow 抛出异常时，忽略并保持 Flow 活跃
        Log.e(TAG, "Stats combine caught exception", e)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(isLoading = true),
    )

    /** 正在加载中的月份 key 集合：快速翻月时同月只发一次请求（防并发重复大请求）。 */
    private val inflightSeasonal = mutableSetOf<String>()

    /** 每个月度缓存的实际加载时间（用于 TTL 判定；改造前缓存永不失效）。 */
    private val seasonalLoadedAt = mutableMapOf<String, Long>()

    init {
        fetchBroadcastSchedule()
        // 回到前台：按 30 分钟软 TTL 校验放送日历（命中的话是空操作）
        viewModelScope.launch {
            AppForegroundSignals.events.collect { fetchBroadcastSchedule(force = false) }
        }
    }

    /**
     * 日历头部箭头的翻页：月视图 ±1 月，周视图 ±1 周。
     * 与手势滑动共用同一个锚点，因此两条路径的结果完全一致。
     */
    fun switchMonth(delta: Int) {
        applyCalendarAnchor(
            CalendarRangeCalculator.shiftAnchor(_calendarAnchor.value, _calendarExpanded.value, delta)
        )
    }

    /**
     * 周 / 月视图粒度切换。锚点日期保持不变 → 切换前后停留在同一周 / 同一月，选中日期不跳变。
     */
    fun switchCalendarView(expanded: Boolean) {
        if (_calendarExpanded.value == expanded) return
        _calendarExpanded.value = expanded
        // 粒度变化会改变可见范围（周视图可能同时覆盖相邻两个月），补拉范围内各月的放送数据
        ensureSeasonalForAnchor(_calendarAnchor.value)
    }

    /**
     * 手势翻页回写锚点：月视图传该月任意一天，周视图传该周任意一天。
     *
     * 与 [switchMonth] 构成双向同步：滑动 → 锚点（本函数），锚点变化 → 驱动 pager 动画（UI 侧）。
     * 二者都只在「可见范围真的变了」时才推进，所以收敛、不会来回振荡。
     */
    fun setCalendarAnchor(date: LocalDate) {
        applyCalendarAnchor(date)
    }

    private fun applyCalendarAnchor(newAnchor: LocalDate) {
        val oldAnchor = _calendarAnchor.value
        if (newAnchor == oldAnchor) return
        val expanded = _calendarExpanded.value
        _calendarAnchor.value = newAnchor
        if (CalendarRangeCalculator.visibleRange(oldAnchor, expanded) ==
            CalendarRangeCalculator.visibleRange(newAnchor, expanded)
        ) {
            // 可见范围没变（例如同一月内换了代表日）：无需重复拉取数据
            return
        }
        ensureSeasonalForAnchor(newAnchor)
        // 切到当前月或未来月份时刷新放送数据，保证 broadcast 不滞后
        val monthStart = LocalDate.of(newAnchor.year, newAnchor.monthValue, 1)
        if (!monthStart.isBefore(today().withDayOfMonth(1))) {
            fetchBroadcastSchedule()
        }
    }

    /** 确保锚点当前可见范围内的所有月份都有放送数据（周视图可能横跨两个月）。 */
    private fun ensureSeasonalForAnchor(anchor: LocalDate) {
        val (start, end) = CalendarRangeCalculator.visibleRange(anchor, _calendarExpanded.value)
        CalendarRangeCalculator.monthsCovering(start, end).forEach { month ->
            ensureSeasonalDataForMonth(month.year, month.monthValue)
        }
    }

    /** 确保指定月份的放送数据已加载（含前 6 个月开播的跨月延续番）。 */
    private fun ensureSeasonalDataForMonth(year: Int, month: Int) {
        val now = today()
        val range = AiringStatus.getCurrentSeasonRange(now)
        val monthStart = LocalDate.of(year, month, 1)

        val key = "%04d-%02d".format(year, month)
        // 已缓存**且未过软 TTL** 才跳过；改造前只要写过 key 就永远不再拉取，
        // 跨月延续的番剧集数在长驻进程里会一直停在旧值。
        val loadedAt = seasonalLoadedAt[key]
        val stillFresh = _seasonalAiringMap.value.containsKey(key) &&
            loadedAt != null &&
            _broadcastMonths.value[key]?.status.let { it == BroadcastMonthStatus.SUCCESS || it == BroadcastMonthStatus.EMPTY } &&
            nowMillis() - loadedAt < RefreshResource.SEASONAL.softTtlMs
        if (stillFresh) return
        if (!inflightSeasonal.add(key)) return // 已在加载中

        // 对非当季月份，检查是否在有效范围内（防止翻到太远的过去/未来）
        if (monthStart.isBefore(range.start) || !monthStart.isBefore(range.end)) {
            if (year < now.year - 5 || year > now.year + 1) {
                // B10：超出有效范围时**必须清掉在途标记**。
                // 改造前这里直接 return，而 key 已经 add 进 inflightSeasonal ——
                // 该月份从此永远处于「正在加载」状态，即使后来（例如换了年份）
                // 重新请求也只会被 if (!inflightSeasonal.add(key)) return 挡住，永远不会再拉。
                inflightSeasonal.remove(key)
                updateMonth(BroadcastMonthState(key, BroadcastMonthStatus.ERROR, error = "该月份超出可加载范围"))
                return
            }
        }

        val previous = _broadcastMonths.value[key] ?: BroadcastMonthState(key = key)
        updateMonth(previous.copy(status = BroadcastMonthStatus.LOADING, error = null))
        viewModelScope.launch {
            try {
                // 加载 [目标月初 − 6 个月, 目标月末] 开播的番，跨月延续靠 StatsCalculator inRange 过滤
                val rangeStart = monthStart.minusMonths(6)
                val rangeEnd = monthStart.plusMonths(1)
                Log.d(TAG, "month=$key request range=$rangeStart..<$rangeEnd types=2,6")
                val result = withContext(seasonalDispatcher) {
                    seasonalFetcher.fetchSeasonalRangeResult(rangeStart, rangeEnd)
                }
                val airingList = if (result.isComplete) result.subjects else
                    (result.subjects + _seasonalAiringMap.value[key].orEmpty()).distinctBy { it.subject.subjectId }
                val updated = _seasonalAiringMap.value + (key to airingList)
                // 只保留最近 6 个月，避免长时间浏览后内存里堆满历史月份
                _seasonalAiringMap.value = updated.entries.toList().takeLast(MAX_SEASONAL_MONTHS)
                    .associate { it.key to it.value }
                seasonalLoadedAt.keys.retainAll(_seasonalAiringMap.value.keys)
                if (result.isComplete) seasonalLoadedAt[key] = nowMillis() else seasonalLoadedAt.remove(key)
                val eventCount = StatsCalculator.computeCalendarEvents(
                    emptyList(), year, month, CalendarMode.BROADCAST, emptyMap(), mapOf(key to airingList),
                ).values.sumOf { it.broadcastSubjects.size + it.releaseDateSubjects.size }
                updateMonth(previous.copy(
                    status = if (!result.isComplete) BroadcastMonthStatus.ERROR
                        else if (eventCount == 0) BroadcastMonthStatus.EMPTY else BroadcastMonthStatus.SUCCESS,
                    lastSuccessAt = if (result.isComplete) seasonalLoadedAt.getValue(key) else previous.lastSuccessAt,
                    error = if (result.isComplete) null else "$key 部分加载失败（三次元），已保留可用内容，请重试",
                    isPartial = !result.isComplete, eventCount = eventCount,
                ))
                val coverless = airingList.count { it.subject.coverUrl.isNullOrBlank() }
                Log.d(TAG, "month=$key validDate=${result.subjects.size} eventCount=$eventCount coverlessSubjects=$coverless complete=${result.isComplete}")
                // 对当季/翻月所有放送作品预取每集数据（限流 + 去重）。
                prefetchEpisodes(airingList.map { it.subject })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateMonth(previous.copy(status = BroadcastMonthStatus.ERROR, error = "无法加载 $key 放送信息，请重试"))
                Log.e(TAG, "month=$key failed error=${e.javaClass.simpleName}")
            } finally {
                inflightSeasonal.remove(key)
            }
        }
    }

    private fun updateMonth(state: BroadcastMonthState) {
        _broadcastMonths.value = (_broadcastMonths.value + (state.key to state))
            .entries.toList().takeLast(MAX_SEASONAL_MONTHS).associate { it.key to it.value }
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
            val result = withContext(broadcastDispatcher) {
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
            val now = today()
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
        // Keep stale successes visible until replacement succeeds; do not unlock active requests.
        ensureSeasonalForAnchor(_calendarAnchor.value)
        fetchBroadcastSchedule(force = true)
    }

    /**
     * 限流预取每集数据：最多 2 并发，单条 3s 超时，失败静默。
     * 主要用于统计页放送信息显示「已播 X / 总 Y」与热力图。
     */
    private fun prefetchEpisodes(subjects: List<SubjectEntity>) {
        val episodeRepo = episodeRepository ?: return
        val ids = subjects.map { it.subjectId }.distinct()
            .filter { it !in prefetchedSubjectIds }
            .take(60)
        if (ids.isEmpty()) return
        prefetchedSubjectIds.addAll(ids)
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
        anchorDate: LocalDate,
        expanded: Boolean,
        mode: CalendarMode,
        broadcast: Map<DayOfWeek, List<AiringSubject>>,
        seasonal: Map<String, List<AiringSubject>>,
        broadcastError: String?,
        episodesBySubject: Map<Long, List<EpisodeInfo>>,
        monthState: BroadcastMonthState,
    ): StatsUiState {
        return try {
            val base = computeBaseStats(items)
            val (rangeStart, rangeEnd) = CalendarRangeCalculator.visibleRange(anchorDate, expanded)
            val calendarDayEvents = StatsCalculator.computeCalendarEventsForRange(
                items, rangeStart, rangeEnd, mode, broadcast, seasonal, episodesBySubject,
            )
            val anchorMonth = YearMonth.from(anchorDate)
            val broadcastEvents = if (mode == CalendarMode.BROADCAST && expanded) calendarDayEvents else
                StatsCalculator.computeCalendarEvents(items, anchorMonth.year, anchorMonth.monthValue, CalendarMode.BROADCAST, broadcast, seasonal, episodesBySubject)
            val eventCount = broadcastEvents.values.sumOf { it.broadcastSubjects.size + it.releaseDateSubjects.size }
            val selectedMonth = monthState.copy(
                eventCount = eventCount,
                status = if (monthState.status == BroadcastMonthStatus.SUCCESS || monthState.status == BroadcastMonthStatus.EMPTY)
                    if (eventCount == 0) BroadcastMonthStatus.EMPTY else BroadcastMonthStatus.SUCCESS
                else monthState.status,
            )
            base.copy(
                calendarYear = anchorMonth.year, calendarMonth = anchorMonth.monthValue,
                calendarAnchorDate = anchorDate, calendarExpanded = expanded,
                calendarDayEvents = calendarDayEvents,
                calendarMode = mode, broadcastSchedule = broadcast,
                broadcastError = broadcastError,
                selectedBroadcastMonth = selectedMonth,
                episodesBySubject = episodesBySubject,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Stats calculation failed", e)
            StatsUiState(isLoading = false)
        }
    }

    private companion object {
        /**
         * 月度放送缓存在内存里最多保留几个月。
         *
         * 从 6 提到 24 是为了让「年份切换」不再把刚看过的月份立刻丢掉：
         * 修复 B10 后每个月度缓存本身就是一次分页全量拉取（代价不低），
         * 6 个月的窗口在连续翻月时会反复驱逐刚加载好的月份。
         */
        const val MAX_SEASONAL_MONTHS = 24
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
