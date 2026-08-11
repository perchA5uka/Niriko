package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SuggestionItem
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.SteamRepository
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.data.local.dao.SearchHistoryDao
import com.otakup.niriko.data.calculator.TrendingCalculator
import com.otakup.niriko.data.filter.FilterDimension
import com.otakup.niriko.data.filter.PresetFilterLoader
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.search.SearchSortMode
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.model.search.TrendingMode
import com.otakup.niriko.data.search.SearchQueryParser
import com.otakup.niriko.data.search.TagAliasMap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

import android.util.Log

private const val TAG = "SearchVM"
private const val TRENDING_COUNT = 15
private const val MIN_RATING_COUNT = 300

/**
 * 作品搜索 ViewModel。
 */
@OptIn(FlowPreview::class)
class SubjectSearchViewModel(
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val searchHistoryDao: SearchHistoryDao? = null,
    private val steamRepository: SteamRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectSearchUiState())
    val uiState: StateFlow<SubjectSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var trendingJob: Job? = null

    /** P0-2：输入防抖流。击键只写值，由 debounce+collectLatest 统一调度（替代手动 Job 竞态）。 */
    private val queryInput = MutableStateFlow("")
    private val suggestionInput = MutableStateFlow("")

    /** P0-3：query 独立流——击键只更新本流，不再触发大 uiState 全量 copy。 */
    val query: StateFlow<String> = queryInput.asStateFlow()

    /** 全局异常处理器：防止协程未捕获异常导致 App 闪退。 */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception", throwable)
    }

    init {
        // P0-2：防抖流化 —— collectLatest 自动取消旧搜索，保证最后一键结果最新
        viewModelScope.launch {
            queryInput.debounce(300).collectLatest { doSearch(it) }
        }
        viewModelScope.launch {
            suggestionInput.debounce(120).collectLatest { doLoadSuggestions(it) }
        }
        viewModelScope.launch {
            val collected = withContext(Dispatchers.IO) {
                collectionRepository.observeAll().first()
            }
            _uiState.update { it.copy(collectedSubjectIds = collected.map { c -> c.subjectId }.toSet()) }
            refreshTrending()
            searchHistoryDao?.let { dao ->
                dao.observeRecent().collect { history ->
                    _uiState.update { it.copy(searchHistory = history.map { h -> h.keyword }) }
                }
            }
        }
    }

    /** 加载下一页历史排名数据（追加到当前类型缓存）。 */
    fun loadNextTrendingPage() {
        val s = _uiState.value
        // 刷新进行中时跳过触底分页，避免过期分页结果覆盖刷新结果
        if (s.isLoadingTrending || s.isLoadingMore || !s.hasMore || s.trendingMode != TrendingMode.ALL_TIME) return
        // 全部标签：首屏已混排各类型顶尖作品，不做深度分页
        if (s.selectedType == null) {
            _uiState.update { it.copy(hasMore = false) }
            return
        }
        val type = s.selectedType
        val cur = perTypeTrending[type] ?: TypeTrendingState()
        if (!cur.loaded) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val offset = (cur.page - 1) * 20
                val newRaw = withContext(Dispatchers.IO) {
                    // 历史排名分页：GET 榜单（绕开 POST body 被吞），offset 分页
                    subjectRepository.getRanking(type = type ?: 2, offset = offset, limit = 20)
                }
                // 按 rank 升序（GET 已排好，兜底排序）
                val filtered = newRaw.sortedBy { it.rank ?: Int.MAX_VALUE }
                // 返回不足一页（<20）说明到底，提前终止（Kazumi rawCount==pageSize 同思路）
                val merged = (cur.results + filtered).distinctBy { it.subjectId }
                val hasMoreNext = filtered.size >= 20
                // 追加到该类型缓存
                perTypeTrending[type] = cur.copy(
                    results = merged,
                    page = cur.page + 1,
                    hasMore = hasMoreNext,
                )
                // 竞态防护：期间类型已切换则丢弃
                if (type != _uiState.value.selectedType) return@launch
                _uiState.update {
                    it.copy(
                        trendingResults = merged,
                        page = cur.page + 1,
                        hasMore = hasMoreNext,
                        isLoadingMore = false,
                        // 注意：不递增 trendingVersion —— 追加是原地扩展，位置天然保持；
                        // 递增会触发 UI 的 scrollToItem 硬跳，导致加载后位置错位。
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /** 单个类型的趋势浏览状态（按 selectedType 缓存，切类型保留翻页/位置）。 */
    private data class TypeTrendingState(
        val results: List<SubjectEntity> = emptyList(),
        val page: Int = 1,
        val hasMore: Boolean = false,
        val scrollIndex: Int = 0,
        val scrollOffset: Int = 0,
        val loaded: Boolean = false,
    )

    /** 按类型缓存的趋势浏览状态（null = 全部标签）。 */
    private val perTypeTrending = mutableMapOf<Int?, TypeTrendingState>()

    /** 保存当前类型的滚动位置（像素级：index + offset，UI 滚动时调用）。 */
    fun saveTrendingScrollPos(type: Int?, index: Int, offset: Int) {
        val cur = perTypeTrending[type] ?: TypeTrendingState()
        perTypeTrending[type] = cur.copy(scrollIndex = index, scrollOffset = offset)
    }

    /** 取某类型上次的滚动位置（index, offset）。 */
    fun getTrendingScrollPos(type: Int?): Pair<Int, Int> {
        val s = perTypeTrending[type] ?: return 0 to 0
        return s.scrollIndex to s.scrollOffset
    }

    /** 设置趋势模式并刷新。 */
    fun setTrendingMode(mode: TrendingMode) {
        // 切模式：清空按类型缓存（ALL_TIME↔SEASONAL 是不同列表，不串数据）
        perTypeTrending.clear()
        _uiState.update { it.copy(trendingMode = mode, trendingResults = emptyList(), page = 1, hasMore = true) }
        trendingJob?.cancel()
        trendingJob = viewModelScope.launch {
            doRefreshTrending()
        }
    }

    /**
     * 刷新趋势 — 并行远程拉取。
     * 每个 async 独立 catch，单个类型失败不影响整体。
     * finally 保证 isLoadingTrending 永远被重置。
     * requestId 竞态防护：只允许最新一次请求写入结果（刷新/切类型并发时旧请求丢弃）。
     */
    private var trendingRequestId = 0

    suspend fun refreshTrending() {
        doRefreshTrending(clearCurrentTypeCache = true)
    }

    private suspend fun doRefreshTrending(clearCurrentTypeCache: Boolean = false) {
        val requestId = ++trendingRequestId
        _uiState.update { it.copy(isLoadingTrending = true) }
        try {
            val s = _uiState.value
            val currentType = s.selectedType
            val mode = s.trendingMode
            val filterNsfw = s.nsfwEnabled
            val filterSort = s.sortMode.apiValue
            val dateRange = TrendingCalculator.computeSeasonDateRange()
            val useAirDate = mode == TrendingMode.SEASONAL
            // 下拉刷新：只清当前类型缓存（其他类型保留）
            if (clearCurrentTypeCache) {
                perTypeTrending.remove(currentType)
            }

            // ALL_TIME（历史排名）固定按 rank 排序；SEASONAL 用热度/用户选择
            val actualSort = if (mode == TrendingMode.ALL_TIME) {
                "rank"
            } else if (useAirDate) {
                if (filterSort == "match") "heat" else filterSort
            } else filterSort

            val results = if (currentType == null) {
                val typeList = listOf(2, 1, 4, 3, 6)
                val lists: List<List<SubjectEntity>> = coroutineScope {
                    typeList.map { t ->
                        async(Dispatchers.IO) {
                            try {
                                if (mode == TrendingMode.ALL_TIME) {
                                    // 历史排名：GET 榜单（绕开 POST body 被吞），每类取 top
                                    subjectRepository.getRanking(type = t, offset = 0, limit = 20)
                                } else {
                                    subjectRepository.search(
                                        keyword = "", type = t,
                                        airDate = if (useAirDate) dateRange else null,
                                        rank = listOf(">0", "<=99999"), nsfw = filterNsfw.takeIf { it },
                                        sort = actualSort,
                                        limit = if (useAirDate) 12 else 20, forceRefresh = true,
                                    )
                                }
                            } catch (_: Exception) { emptyList() }
                        }
                    }.awaitAll()
                }
                if (mode == TrendingMode.ALL_TIME) {
                    // 历史排名（全部）：每类按 rank 升序（GET 已排好），按类型轮流交错混排
                    val perTypeTop = lists.map { list -> list.sortedBy { it.rank ?: Int.MAX_VALUE } }
                    val merged = mutableListOf<SubjectEntity>()
                    val maxLen = perTypeTop.maxOfOrNull { it.size } ?: 0
                    for (i in 0 until maxLen) {
                        perTypeTop.forEach { list -> if (i < list.size) merged.add(list[i]) }
                    }
                    merged.take(20)
                } else {
                    val processed = lists.map { list ->
                        if (useAirDate) list.sortedByDescending { it.ratingScore ?: 0f }.take(5)
                        else list.filter { (it.ratingTotal ?: 0) >= MIN_RATING_COUNT }
                    }
                    val merged = mutableListOf<SubjectEntity>()
                    val maxPerType = processed.maxOfOrNull { it.size } ?: 0
                    for (i in 0 until maxPerType) {
                        processed.forEach { list -> if (i < list.size) merged.add(list[i]) }
                    }
                    if (useAirDate) merged else merged.take(20)
                }
            } else {
                val singleResult = try {
                    if (useAirDate) {
                        val seasonal = subjectRepository.search(
                            keyword = "", type = currentType,
                            airDate = dateRange,
                            rank = listOf(">0", "<=99999"), nsfw = filterNsfw.takeIf { it },
                            sort = actualSort, limit = 50, forceRefresh = true,
                        ).sortedByDescending { it.ratingScore ?: 0f }
                        if (seasonal.size >= TRENDING_COUNT) seasonal.take(TRENDING_COUNT) else {
                            val fillCnt = TRENDING_COUNT - seasonal.size
                            val historical = subjectRepository.search(
                                keyword = "", type = currentType,
                                airDate = null,
                                rank = listOf(">0", "<=99999"),
                                sort = "rank", limit = fillCnt, forceRefresh = true,
                            )
                            (seasonal + historical).take(TRENDING_COUNT)
                        }
                    } else {
                        if (mode == TrendingMode.ALL_TIME) {
                            // 历史排名（选中类型）：GET 榜单，按 rank 升序
                            subjectRepository.getRanking(type = currentType, offset = 0, limit = 20)
                                .sortedBy { it.rank ?: Int.MAX_VALUE }.take(20)
                        } else {
                            val single = subjectRepository.search(
                                keyword = "", type = currentType,
                                rank = listOf(">0", "<=99999"), nsfw = filterNsfw.takeIf { it },
                                sort = actualSort, limit = 40, forceRefresh = true,
                            )
                            single.filter { (it.ratingTotal ?: 0) >= MIN_RATING_COUNT }.take(20)
                        }
                    }
                } catch (_: Exception) { emptyList() }
                singleResult
            }

            // 历史排名：全部标签不分页；选中类型可分页
            val canPage = mode == TrendingMode.ALL_TIME && currentType != null
            // 竞态防护：期间已有更新的刷新请求，丢弃本次结果
            if (requestId != trendingRequestId) return
            // 初始加载（offset=0）：写入当前类型缓存（保留原滚动位置），替换列表
            val prev = perTypeTrending[currentType] ?: TypeTrendingState()
            perTypeTrending[currentType] = TypeTrendingState(
                results = results,
                page = 1,
                hasMore = canPage,
                scrollIndex = prev.scrollIndex,
                scrollOffset = prev.scrollOffset,
                loaded = true,
            )
            _uiState.update {
                it.copy(
                    trendingResults = results, isLoadingTrending = false, error = null,
                    hasMore = canPage, page = 1,
                    trendingScrollIndex = prev.scrollIndex,
                    trendingScrollOffset = prev.scrollOffset,
                    trendingVersion = it.trendingVersion + 1,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消：不写状态，重新抛出
            throw e
        } catch (e: Exception) {
            if (requestId != trendingRequestId) return
            val msg = when (e) {
                is java.net.SocketTimeoutException -> "与信息源断开连接"
                is java.net.UnknownHostException -> "与网络断开连接"
                else -> "加载失败，请重试"
            }
            _uiState.update { it.copy(isLoadingTrending = false, error = msg) }
        } finally {
            _uiState.update { it.copy(isLoadingTrending = false) }
        }
    }

    // ==================== 高级筛选 ====================
    fun onQueryChanged(query: String) {
        if (_uiState.value.isPersonSearch) {
            _uiState.update { it.copy(query = query) }
            searchPersons(query)
        } else {
            // P0-2/P0-3：只写输入流（query 独立 StateFlow），防抖与竞态由 debounce+collectLatest 处理
            queryInput.value = query
            suggestionInput.value = query
        }
    }

    fun onSearchSubmit(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                searchHistoryDao?.upsertByKeyword(query)
            }
        }
        clearSuggestions()
        if (_uiState.value.isPersonSearch) {
            searchPersons(query)
        } else {
            // 提交搜索：绕过硬性防抖，立即执行最新结果
            searchJob?.cancel()
            searchJob = viewModelScope.launch { doSearch(query) }
        }
    }

    /** P0-2：建议加载（由 suggestionInput 防抖流调度，120ms）。 */
    private suspend fun doLoadSuggestions(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(suggestions = emptyList(), isSuggestionsLoading = false) }
            return
        }
        _uiState.update { it.copy(isSuggestionsLoading = true) }
        try {
            val localSubjects = withContext(Dispatchers.IO) {
                subjectRepository.searchByPrefix(query)
            }
            val historyMatches = _uiState.value.searchHistory.filter { it.contains(query, ignoreCase = true) }
            val remoteSubjects = try {
                withContext(Dispatchers.IO) {
                    subjectRepository.search(keyword = query, type = null, limit = 5, forceRefresh = true)
                }
            } catch (_: Exception) { emptyList() }

            if (!coroutineContext.isActive) return
            val merged = buildList<SuggestionItem> {
                historyMatches.take(3).forEach { add(SuggestionItem.HistoryKeyword(it)) }
                val historySet = historyMatches.toSet()
                localSubjects.filter { it.title !in historySet && it.titleCN !in historySet }.take(3).forEach { add(SuggestionItem.LocalSubject(it)) }
                val localIdSet = localSubjects.map { it.subjectId }.toSet()
                remoteSubjects.filter { it.subjectId !in localIdSet }.take(3).forEach { add(SuggestionItem.RemoteSuggestion(it)) }
            }
            if (coroutineContext.isActive) {
                _uiState.update { it.copy(suggestions = merged, isSuggestionsLoading = false) }
            }
        } catch (_: Exception) {
            if (coroutineContext.isActive) _uiState.update { it.copy(suggestions = emptyList(), isSuggestionsLoading = false) }
        } finally {
            if (coroutineContext.isActive) _uiState.update { it.copy(isSuggestionsLoading = false) }
        }
    }

    fun clearSuggestions() {
        suggestionInput.value = ""
        _uiState.update { it.copy(suggestions = emptyList(), isSuggestionsLoading = false) }
    }

    // ==================== 高级筛选 ====================

    fun setSortMode(mode: SearchSortMode) {
        _uiState.update { it.copy(sortMode = mode) }
        val q = _uiState.value.query
        if (q.isNotBlank()) {
            debouncedSearch(q, _uiState.value.selectedType, immediate = true)
        } else {
            refreshTrendingWithFilters()
        }
    }

    fun setNsfw(enabled: Boolean) {
        _uiState.update { it.copy(nsfwEnabled = enabled) }
        val q = _uiState.value.query
        if (q.isNotBlank()) {
            debouncedSearch(q, _uiState.value.selectedType, immediate = true)
        } else {
            refreshTrendingWithFilters()
        }
    }

    fun toggleFilterPanel() {
        _uiState.update { it.copy(isFilterPanelExpanded = !it.isFilterPanelExpanded) }
    }

    fun getFilterDimensions(): List<FilterDimension> {
        val type = _uiState.value.selectedType
        return PresetFilterLoader.getDimensions(type?.let { SubjectType.fromBangumiType(it) })
    }

    fun setFilter(dimensionKey: String, optionLabel: String) {
        val dim = getFilterDimensions().find { it.key == dimensionKey } ?: return
        _uiState.update { state ->
            val current = state.filterSelections[dimensionKey] ?: emptyList()
            val newSelections = when (dim.mode) {
                com.otakup.niriko.data.filter.SelectMode.SINGLE -> {
                    if (current.contains(optionLabel)) emptyList() else listOf(optionLabel)
                }
                com.otakup.niriko.data.filter.SelectMode.MULTI -> {
                    if (current.contains(optionLabel)) current - optionLabel else current + optionLabel
                }
            }
            val updatedMap = if (newSelections.isEmpty()) {
                state.filterSelections - dimensionKey
            } else {
                state.filterSelections + (dimensionKey to newSelections)
            }
            state.copy(filterSelections = updatedMap)
        }
        val q = _uiState.value.query
        if (q.isNotBlank()) {
            debouncedSearch(q, _uiState.value.selectedType, immediate = true)
        } else {
            refreshTrendingWithFilters()
        }
    }

    fun clearFilters() {
        _uiState.update { it.copy(filterSelections = emptyMap()) }
        val q = _uiState.value.query
        if (q.isNotBlank()) {
            debouncedSearch(q, _uiState.value.selectedType, immediate = true)
        } else {
            refreshTrendingWithFilters()
        }
    }

    fun hasActiveFilters(): Boolean {
        val s = _uiState.value
        return TrendingCalculator.hasActiveFilters(s.filterSelections, s.nsfwEnabled, s.sortMode)
    }

    private fun refreshTrendingWithFilters() {
        trendingJob?.cancel()
        _uiState.update { it.copy(trendingResults = emptyList(), page = 1, hasMore = true) }
        trendingJob = viewModelScope.launch {
            doRefreshTrending()
        }
    }

    fun onHistoryClick(keyword: String) {
        _uiState.update { it.copy(query = keyword) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                searchHistoryDao?.upsertByKeyword(keyword)
            }
        }
        clearSuggestions()
        debouncedSearch(keyword, _uiState.value.selectedType, immediate = true)
    }

    fun deleteHistoryItem(keyword: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                searchHistoryDao?.deleteByKeyword(keyword)
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                searchHistoryDao?.clearAll()
            }
        }
    }

    fun setType(type: Int?) {
        val s = _uiState.value
        // 切到作品类型时退出人物模式
        _uiState.update { it.copy(selectedType = type, isPersonSearch = false) }
        if (s.query.isNotBlank()) {
            if (s.allResults.isNotEmpty()) {
                val filtered = filterByType(s.allResults, type)
                _uiState.update { it.copy(results = filtered) }
            } else {
                debouncedSearch(s.query, null, immediate = true)
            }
        } else {
            // 趋势模式：切类型走按类型缓存（已有则直接切，无则首次加载）
            val cached = perTypeTrending[type]
            if (cached?.loaded == true) {
                // 该类型已浏览过：直接切缓存，零网络请求
                _uiState.update {
                    it.copy(
                        trendingResults = cached.results,
                        page = cached.page,
                        hasMore = cached.hasMore,
                        trendingScrollIndex = cached.scrollIndex,
                        trendingScrollOffset = cached.scrollOffset,
                        trendingVersion = it.trendingVersion + 1,
                        isLoadingTrending = false,
                        error = null,
                    )
                }
            } else {
                trendingJob?.cancel()
                trendingJob = viewModelScope.launch { doRefreshTrending() }
            }
        }
    }

    /** 切换"人物"搜索模式（声优/导演等）。切模式立即触发对应搜索。 */
    fun setPersonSearch(enabled: Boolean) {
        _uiState.update { it.copy(isPersonSearch = enabled) }
        val q = _uiState.value.query
        if (enabled) {
            searchJob?.cancel()
            trendingJob?.cancel()
            if (q.isNotBlank()) searchPersons(q)
            else _uiState.update { it.copy(personResults = emptyList(), isSearching = false) }
        } else {
            trendingJob?.cancel()
            if (q.isNotBlank()) debouncedSearch(q, _uiState.value.selectedType, immediate = true)
            else {
                trendingJob?.cancel()
                trendingJob = viewModelScope.launch { doRefreshTrending() }
            }
        }
    }

    /** 人物搜索（声优/导演/作者等），结果含代表作品小图。 */
    private fun searchPersons(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSearching = true, error = null) }
                val results = withContext(Dispatchers.IO) {
                    try { subjectRepository.searchPersons(query) } catch (_: Exception) { emptyList() }
                }
                if (coroutineContext.isActive) {
                    _uiState.update {
                        it.copy(personResults = results, isSearching = false, error = null, suggestions = emptyList())
                    }
                }
            } catch (e: Exception) {
                if (coroutineContext.isActive) _uiState.update { it.copy(isSearching = false, error = "搜索失败，请重试") }
            }
        }
    }

    /** P0-2 兼容层：immediate 直接搜索；否则写输入流（防抖由 collectLatest 调度）。 */
    private fun debouncedSearch(query: String, type: Int?, immediate: Boolean = false) {
        if (immediate) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { doSearch(query) }
        } else {
            queryInput.value = query
        }
    }

    /** P0-2 统一搜索执行。collectLatest 取消旧任务；CancellationException 必须 rethrow。 */
    private suspend fun doSearch(query: String) {
        try {
            if (query.isBlank()) {
                _uiState.update { it.copy(results = emptyList(), allResults = emptyList(), totalResults = 0, isSearching = false) }
                return
            }

            _uiState.update { it.copy(isSearching = true, error = null) }
                val s = _uiState.value
                val typeList = listOf(2, 1, 4, 3, 6)
                // 解析 tag:/sort: 语法（对齐 Kazumi SearchParser 子集）
                val parsed = SearchQueryParser.parse(query)
                // 排序：语法显式 sort 优先，否则透传用户选择；默认 HEAT（收藏人数，对齐 Kazumi）
                val effectiveSort = parsed.sort ?: s.sortMode.apiValue
                // 筛选面板选中的预设标签（如 地区/类型），与语法标签叠加
                val panelTags = TrendingCalculator.buildFilterTags(s.filterSelections, getFilterDimensions())
                val rankFilter = if (s.sortMode == SearchSortMode.RANK) listOf(">0", "<=99999") else null

                // ===== 搜索架构（对齐 Kazumi）=====
                // 无 tag: 语法 → 纯 keyword 搜索（keyword 本身匹配标题+标签，heat 排序让大热作排前）
                // 有 tag: 语法 → 纯标签过滤（keyword="" + filter.tag 多值"且"）
                // 无 tag: 语法且输入词命中公司别名表 → 对各变体做纯 tag 查询合并（解决"京都动画"只出 2 个）
                val hasTagSyntax = parsed.hasTagSyntax
                val isAliasHit = !hasTagSyntax && TagAliasMap.expand(parsed.keyword).size > 1
                val variantTags = if (isAliasHit) TagAliasMap.expand(parsed.keyword) else emptyList()

                val (allResults, total) = withContext(Dispatchers.IO) {
                    coroutineScope {
                        // 1) 语法标签路（tag:xxx 显式过滤）
                        val syntaxTagResults = if (hasTagSyntax && parsed.tags.isNotEmpty()) {
                            async {
                                try {
                                    subjectRepository.searchWithTotal(
                                        keyword = "",
                                        type = s.selectedType,
                                        tags = parsed.tags,
                                        nsfw = s.nsfwEnabled.takeIf { it },
                                        sort = effectiveSort,
                                        rank = rankFilter,
                                        limit = 50,
                                    )
                                } catch (_: Exception) {
                                    emptyList<SubjectEntity>() to 0
                                }
                            }.await()
                        } else {
                            emptyList<SubjectEntity>() to 0
                        }

                        // 2) 变体标签路（公司名/标签别名展开，各自精确过滤后合并）
                        val variantResults = if (variantTags.isNotEmpty()) {
                            variantTags.map { variant ->
                                async {
                                    try {
                                        subjectRepository.searchWithTotal(
                                            keyword = "",
                                            type = s.selectedType,
                                            tags = (panelTags + variant).distinct(),
                                            nsfw = s.nsfwEnabled.takeIf { it },
                                            sort = effectiveSort,
                                            rank = rankFilter,
                                            limit = 30,
                                        )
                                    } catch (_: Exception) {
                                        emptyList<SubjectEntity>() to 0
                                    }
                                }
                            }.awaitAll()
                        } else {
                            emptyList()
                        }

                        // 3) keyword 全文检索路（标题/别名匹配；含语法时退化为补充）
                        val keywordResults = if (parsed.keyword.isNotBlank()) {
                            typeList.map { t ->
                                async {
                                    try {
                                        subjectRepository.searchWithTotal(
                                            keyword = parsed.keyword, type = t,
                                            tags = if (hasTagSyntax) (panelTags + parsed.tags).distinct() else panelTags,
                                            nsfw = s.nsfwEnabled.takeIf { it },
                                            sort = effectiveSort,
                                            rank = rankFilter,
                                            limit = 100,
                                        )
                                    } catch (_: Exception) {
                                        emptyList<SubjectEntity>() to 0
                                    }
                                }
                            }.awaitAll()
                        } else {
                            emptyList()
                        }

                        // 合并去重：语法标签路 → 变体标签路 → keyword 路（精确优先）
                        val merged = (syntaxTagResults.first + variantResults.flatMap { it.first } + keywordResults.flatMap { it.first })
                            .distinctBy { it.subjectId }
                        // total 以 keyword 路统计为主（tag 路 total 是标签全集，不参与计数）
                        val totalCount = when {
                            keywordResults.isNotEmpty() -> keywordResults.sumOf { it.second }
                            variantResults.isNotEmpty() -> variantResults.sumOf { it.second }
                            else -> syntaxTagResults.second
                        }
                        merged to totalCount
                    }
                }

                if (coroutineContext.isActive) {
                    val filtered = filterByType(allResults, s.selectedType)
                    _uiState.update {
                        it.copy(
                            results = filtered,
                            allResults = allResults,
                            totalResults = total,
                            isSearching = false,
                            suggestions = emptyList(),
                            error = null,
                        )
                    }
                    // Steam 补充：对 GAME 结果触发匹配 + 加载补充数据（异步，不阻塞结果展示）
                    loadSteamSupplements(allResults)
                }
            } catch (e: Exception) {
                if (coroutineContext.isActive) {
                    val msg = when (e) {
                        is java.net.SocketTimeoutException -> "与信息源断开连接"
                        is java.net.UnknownHostException -> "与网络断开连接"
                        else -> "搜索失败，请重试"
                    }
                    _uiState.update { it.copy(isSearching = false, error = msg, results = emptyList()) }
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }
    }

    private fun filterByType(allResults: List<SubjectEntity>, selectedType: Int?): List<SubjectEntity> {
        return if (selectedType == null) allResults
        else allResults.filter { SubjectType.fromBangumiType(selectedType) == it.type }
    }

    /**
     * Steam 补充数据加载（游戏卡价格/在线展示用）。
     * 对结果中的 GAME 条目：先自动匹配并落库（未绑定的），再批量读取补充数据写入 uiState。
     * 全程异常保护，失败静默不影响搜索主流程。
     */
    private fun loadSteamSupplements(results: List<SubjectEntity>) {
        val steam = steamRepository ?: return
        val games = results.filter { it.type == SubjectType.GAME }
        if (games.isEmpty()) return
        viewModelScope.launch {
            try {
                // 未绑定条目自动匹配（内部已按 type=GAME + 未绑定过滤，且失败静默）
                steam.matchAndBind(games)
                val map = steam.getSupplements(games.map { it.subjectId })
                _uiState.update { it.copy(steamGames = map) }
            } catch (e: Exception) {
                Log.w(TAG, "loadSteamSupplements failed", e)
            }
        }
    }

    fun clearResults() {
        searchJob?.cancel()
        suggestionInput.value = ""
        _uiState.update {
            SubjectSearchUiState(collectedSubjectIds = it.collectedSubjectIds)
        }
    }
}

class SubjectSearchViewModelFactory(
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val searchHistoryDao: SearchHistoryDao? = null,
    private val steamRepository: SteamRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubjectSearchViewModel::class.java)) {
            return SubjectSearchViewModel(subjectRepository, collectionRepository, searchHistoryDao, steamRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
