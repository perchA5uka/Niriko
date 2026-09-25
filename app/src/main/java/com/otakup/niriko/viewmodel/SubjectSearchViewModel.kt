package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SuggestionItem
import com.otakup.niriko.data.repository.CollectionRepository
import com.otakup.niriko.data.repository.SearchOutcome
import com.otakup.niriko.data.repository.SteamRepository
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.data.repository.networkFailureMessage
import com.otakup.niriko.data.remote.game.GameItemMapper
import com.otakup.niriko.data.remote.steam.SteamTitleMatcher
import com.otakup.niriko.data.local.dao.SearchHistoryDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.calculator.TrendingCalculator
import com.otakup.niriko.data.discover.BrowseArea
import com.otakup.niriko.data.discover.BrowseFilter
import com.otakup.niriko.data.discover.BrowseSort
import com.otakup.niriko.data.discover.BrowseStatus
import com.otakup.niriko.data.discover.BrowseVersion
import com.otakup.niriko.data.discover.BrowseYear
import com.otakup.niriko.data.discover.DiscoveryFeed
import com.otakup.niriko.data.filter.FilterDimension
import com.otakup.niriko.data.filter.PresetFilterLoader
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.search.SearchSortMode
import com.otakup.niriko.data.refresh.AppForegroundSignals
import com.otakup.niriko.data.refresh.RefreshCoordinator
import com.otakup.niriko.data.refresh.RefreshDecision
import com.otakup.niriko.data.refresh.RefreshResource
import com.otakup.niriko.data.model.search.DiscoveryLayout
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.model.search.TrendingMode
import com.otakup.niriko.data.search.SearchQueryParser
import com.otakup.niriko.data.search.TagAliasMap
import com.otakup.niriko.data.seasonal.SeasonalSort
import com.otakup.niriko.data.seasonal.SeasonalTrendingCalculator
import com.otakup.niriko.data.seasonal.SeasonalTrendingRepository
import com.otakup.niriko.data.seasonal.SeasonalTrendingResult
import com.otakup.niriko.data.seasonal.SeasonalTypes
import com.otakup.niriko.data.settings.AppSettings
import com.otakup.niriko.data.settings.SettingsDataStore
import com.otakup.niriko.util.AsyncSingleFlight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

import android.util.Log

private const val TAG = "SearchVM"
private const val TRENDING_COUNT = 15
private const val MIN_RATING_COUNT = 300

/** 关键词检索路的单页条数（正式搜索与建议共用，保证两者命中同一个请求指纹）。 */
private const val KEYWORD_FETCH_LIMIT = 100

/** 建议浮层最多展示的远程条目数（本地历史/前缀命中另计）。 */
private const val SUGGESTION_MAX_REMOTE = 3

/** 建议专用兜底链（游戏源旁路）的单次拉取条数。 */
private const val SUGGESTION_FALLBACK_LIMIT = 5

/** 建议兜底链的整体限时：只用于「主链无命中」的二次尝试，失败不影响已展示的本地建议。 */
private const val SUGGESTION_FALLBACK_TIMEOUT_MS = 4_000L

/** 远程失败但本地有命中时，结果区顶部的离线标注（第 6 轮 F5）。 */
private const val OFFLINE_RESULTS_NOTICE = "网络不可用，以下为本地离线结果"

/** 建议主链没有命中时的默认类型（动画）：与正式搜索的关键词路同一个请求指纹。 */
private const val SUGGESTION_PRIMARY_TYPE = 2

/** 「历史排名 · 全部」的候选池：每次每类取多少条（= 一页）。 */
private const val BROWSE_PAGE_SIZE = 30

/**
 * 「历史排名 · 全部」要拉的类型（动画 / 书籍 / 游戏 / 音乐 / 三次元）。
 *
 * 与当季热门同一套（[SeasonalTypes.ALL]）；顶部类型行选中具体类型时只用那一个。
 */
private val BROWSE_TYPES: List<Int> = SeasonalTypes.ALL

/**
 * v1 规则版的默认 rank 过滤（规格 §3.3 的 filter rank:[">0"]）。
 *
 * 有排名的条目才值得比较；本地再叠 DiscoveryFeed 的质量门槛（rating_total ≥ 100）。
 */
private val DEFAULT_TREND_RANK_RANGE: List<String> = listOf(">0", "<=99999")

/**
 * 趋势刷新看门狗：超过该时长仍未返回就强制释放转圈。
 * 对齐 Bangumi-master「useRefreshState」的 4s 定时器思路（这里放宽到 15s：
 * 发现页一次刷新要并行打 5 个类型，弱网下 4s 明显不够，会误报「已结束」）。
 */
private const val TRENDING_WATCHDOG_MS = 15_000L

/**
 * 作品搜索 ViewModel。
 */
@OptIn(FlowPreview::class)
class SubjectSearchViewModel(
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val searchHistoryDao: SearchHistoryDao? = null,
    private val steamRepository: SteamRepository? = null,
    private val subjectDao: SubjectDao? = null,
    /** 设置存储（读取「显示搜索建议」开关；null = 总是开启）。 */
    private val settingsDataStore: SettingsDataStore? = null,
    /** 刷新编排器（新鲜度判定 + 失败退避）。null = 不参与编排（单测/降级）。 */
    private val refreshCoordinator: RefreshCoordinator? = null,
    /**
     * 「当季热门」数据源（第 5 轮 D26）。
     *
     * null = 未注入（单测/降级）：退回旧的「本季度开播」检索，
     * 但**同样不做历史排名填充**。
     */
    private val seasonalTrendingRepository: SeasonalTrendingRepository? = null,
) : ViewModel() {

    /** 「显示搜索建议」开关状态（设置页可实时切换；默认开）。 */
    private val suggestionsEnabled: StateFlow<Boolean> by lazy {
        (settingsDataStore?.settings
            ?.map { it.showSearchSuggestions }
            ?.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings().showSearchSuggestions))
            ?: MutableStateFlow(AppSettings().showSearchSuggestions)
    }

    private val _uiState = MutableStateFlow(SubjectSearchUiState())
    val uiState: StateFlow<SubjectSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var trendingJob: Job? = null

    /**
     * 关键词检索的请求合并（第 6 轮 F4）。
     *
     * 建议（120ms 防抖）与正式搜索（300ms 防抖）打的是同一个关键词、同一组筛选条件，
     * 因此共用同一个请求指纹：建议先发起的请求会被正式搜索直接复用，不会各打一遍。
     */
    private val requestShare = SearchRequestShare()

    /** 刷新进行中又收到用户主动刷新（下拉）：标记 pending，当前请求结束后补一轮（第 6 轮 R5）。 */
    private var pendingTrendingRefresh = false

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
        // 发现页布局（第 5 轮 D28）：以 DataStore 为准单向驱动 uiState，
        // 这样别处改动也能即时反映到发现页，不会出现两份状态漂移。
        viewModelScope.launch {
            settingsDataStore?.settings?.collect { settings ->
                val layout = DiscoveryLayout.fromKey(settings.discoveryLayout)
                if (_uiState.value.discoveryLayout != layout) {
                    _uiState.update { it.copy(discoveryLayout = layout) }
                }
            }
        }
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
            // 第 6 轮 §6.4：availableTags（「找条目」的标签候选）随该模块删除；
            // 「历史排名」筛选器的类型维度改用 BrowseFilter.CONTENT_TAGS（46 词表）。
            refreshTrending()
            searchHistoryDao?.let { dao ->
                dao.observeRecent().collect { history ->
                    _uiState.update { it.copy(searchHistory = history.map { h -> h.keyword }) }
                }
            }
        }
        // 回到前台：**静默**校验趋势区（不显示下拉指示器）。
        // 是否真的发请求由 RefreshCoordinator 判定：5 分钟软 TTL 内是空操作，
        // 因此频繁切前后台不会变成频繁打接口。
        viewModelScope.launch {
            AppForegroundSignals.events.collect {
                doRefreshTrending(force = false, silent = true)
            }
        }
    }

    /**
     * 加载下一页历史排名数据。
     *
     * - 单类型：按 rank 榜单 offset 追加（原有行为）。
     * - **全部**（第 6 轮 §3.3 / §4.2 R3）：按**候选来源类型**继续取 offset，把候选池扩大后
     *   重跑一遍 DiscoveryFeed。统一 Feed 的全局顺序不可分页（规格 §3.2 末尾），
     *   但贪心是**前缀稳定**的：targetSize 变大时前 30 条不变，新条目接在尾部。
     */
    fun loadNextTrendingPage() {
        val s = _uiState.value
        // 刷新进行中时跳过触底分页，避免过期分页结果覆盖刷新结果
        if (s.isLoadingTrending || s.isLoadingMore || !s.hasMore || s.trendingMode != TrendingMode.ALL_TIME) return
        if (s.selectedType == null) {
            loadMoreAllTypes(s)
            return
        }
        val type = s.selectedType
        val cur = perTypeTrending[type] ?: TypeTrendingState()
        if (!cur.loaded) return
        val browse = s.browseFilter
        val today = LocalDate.now()
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                // offset = 当前已加载页数 × 每页大小（cur.page 是"已加载到第几页"，
                // 首次触底 cur.page=1 → offset=20 拉第二页；旧写法 (page-1)*20 首次 offset=0
                // 重复拉第一页 → distinctBy 去重后无新增 → 列表不增长、触底反复触发抽搐）
                val offset = cur.page * 20
                val rankOnly = browse.baseTagClauses().isEmpty() && browse.airDateRange(today) == null
                val newRaw = withContext(Dispatchers.IO) {
                    if (browse.sort == BrowseSort.RANK && rankOnly) {
                        // 排序维度 = 排名：GET 榜单（绕开 POST body 被吞）、offset 分页。
                        // R4：缓存 key 带查询指纹（nsfw + 筛选 + sort），切筛选不会命中旧榜单
                        subjectRepository.getRanking(
                            type = type,
                            offset = offset,
                            limit = 20,
                            fingerprint = rankingFingerprint(
                                nsfw = browse.nsfw,
                                sort = browse.sort.requestSort,
                                tags = browse.baseTagClauses(),
                            ),
                        )
                    } else {
                        // 其他排序/条件：v0 检索分页，本地再过增强项
                        searchForBrowse(
                            type = type,
                            filter = browse,
                            today = today,
                            limit = 20,
                            offset = offset,
                        )
                    }
                }
                // 本地增强项（评分/评分人数/状态/收藏）+ 排序
                val filtered = when {
                    browse.sort == BrowseSort.RANK && rankOnly ->
                        browse.applyLocalFilters(newRaw, s.collectedSubjectIds, today)
                            .sortedBy { it.rank ?: Int.MAX_VALUE }
                    else ->
                        browse.sortLocally(
                            browse.applyLocalFilters(newRaw, s.collectedSubjectIds, today),
                        )
                }
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

    // ==================== 历史排名 · 全部（第 6 轮 §3.3 v1 规则版） ====================

    /** 一次「全部」Feed 的结果 + 筛选后的候选池大小。 */
    private data class BrowseFeedResult(val items: List<SubjectEntity>, val poolSize: Int)

    /**
     * 「加载更多」（全部）：按**候选来源类型**续取 offset，把候选池扩大后重跑 DiscoveryFeed。
     */
    private fun loadMoreAllTypes(s: SubjectSearchUiState) {
        val expectedPage = s.browsePage
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val nextPage = expectedPage + 1
                val hasMorePool = loadBrowsePoolPage(
                    filter = s.browseFilter,
                    today = LocalDate.now(),
                    offset = (nextPage - 1) * BROWSE_PAGE_SIZE,
                    limit = BROWSE_PAGE_SIZE,
                )
                // 竞态防护：期间发生过刷新/切筛选（browsePage 被重置）则丢弃本次结果
                if (_uiState.value.browsePage != expectedPage) {
                    _uiState.update { it.copy(isLoadingMore = false) }
                    return@launch
                }
                val feed = buildAllTypesResult(
                    filter = s.browseFilter,
                    today = LocalDate.now(),
                    collectedIds = s.collectedSubjectIds,
                    page = nextPage,
                )
                _uiState.update {
                    it.copy(
                        trendingResults = feed.items,
                        browsePage = nextPage,
                        browsePoolSize = feed.poolSize,
                        hasMore = hasMorePool,
                        isLoadingMore = false,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * 拉一页候选池（每类型 limit 条）并合并进 [browsePool]。
     *
     * 制作维度用 TagAliasMap 展开变体后**多请求合并**（官方没有公司字段，只能按标签匹配）。
     * 全部类型都失败时**抛出**（与榜单一致：失败要说话，不能被当成「没有数据」）。
     *
     * @return 是否有任一类型拿满一页（= 还有更多）
     */
    private suspend fun loadBrowsePoolPage(
        filter: BrowseFilter,
        today: LocalDate,
        offset: Int,
        limit: Int,
    ): Boolean = coroutineScope {
        val fetches: List<Pair<Int, RemoteFetch<List<SubjectEntity>>>> = BROWSE_TYPES.map { type ->
            type to async(Dispatchers.IO) {
                attemptRemote {
                    val variants = filter.studioVariants()
                    if (variants.isEmpty()) {
                        searchForBrowse(type, filter, today, limit, offset)
                    } else {
                        val merged = LinkedHashMap<Long, SubjectEntity>()
                        variants.forEach { variant ->
                            searchForBrowse(type, filter, today, limit, offset, variant)
                                .forEach { merged.putIfAbsent(it.subjectId, it) }
                        }
                        merged.values.toList()
                    }
                }
            }
        }.map { (type, deferred) -> type to deferred.await() }

        if (fetches.isNotEmpty() && fetches.all { it.second.isFailure }) {
            val firstError = fetches.first { it.second.isFailure }.second.error
            throw (firstError ?: IllegalStateException("候选池加载失败"))
        }
        var hasMore = false
        fetches.forEach { (type, fetch) ->
            val page = fetch.value ?: return@forEach
            if (page.size >= limit) hasMore = true
            val existing = browsePool[type].orEmpty()
            browsePool[type] = (existing + page).distinctBy { it.subjectId }
        }
        hasMore
    }

    /**
     * 单类型的候选检索（BrowseFilter → v0 参数）。
     *
     * 评分区间 / 评分人数由调用方用 [BrowseFilter.applyLocalFilters] 本地再过一遍
     * （v0 的搜索通路没有 rating / rating_count 参数）。
     */
    private suspend fun searchForBrowse(
        type: Int,
        filter: BrowseFilter,
        today: LocalDate,
        limit: Int,
        offset: Int,
        studioVariant: String? = null,
    ): List<SubjectEntity> {
        val tags = buildList {
            addAll(filter.baseTagClauses())
            studioVariant?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.distinct()
        return subjectRepository.search(
            keyword = "",
            type = type,
            tags = tags.takeIf { it.isNotEmpty() },
            airDate = filter.airDateRange(today),
            rank = filter.rankRange() ?: DEFAULT_TREND_RANK_RANGE,
            nsfw = filter.nsfw.takeIf { it },
            sort = filter.sort.requestSort,
            limit = limit,
            offset = offset.takeIf { it > 0 },
        )
    }

    /**
     * 候选池 → 本地增强过滤 →（随机/名称本地排序）→ DiscoveryFeed → 前 30 × 页数。
     *
     * 统一 Feed 的全局顺序不可分页（规格 §3.2 末尾），但贪心是**前缀稳定**的：
     * targetSize 变大时前 30 条不变，新条目接在尾部 —— 这正是「加载更多」能用的原因。
     */
    private fun buildAllTypesResult(
        filter: BrowseFilter,
        today: LocalDate,
        collectedIds: Set<Long>,
        page: Int,
    ): BrowseFeedResult {
        val raw = browsePool.values.flatten().distinctBy { it.subjectId }
        var pool = filter.applyLocalFilters(raw, collectedIds, today)
        if (filter.sort.isLocalOnly) pool = filter.sortLocally(pool)
        val feed = DiscoveryFeed.build(
            groups = DiscoveryFeed.groupByType(pool),
            params = DiscoveryFeed.Params(targetSize = DiscoveryFeed.TARGET_SIZE * page.coerceAtLeast(1)),
        )
        return BrowseFeedResult(items = feed.subjects, poolSize = pool.size)
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

    /**
     * 「历史排名 · 全部」的候选池（第 6 轮 §3.3 v1）：类型 → 已加载的候选。
     *
     * 只在「全部」路径使用；下拉刷新时清空重建。同一 subjectId 保留首次出现的那条
     * （它来自更靠前的页）。
     */
    private val browsePool = mutableMapOf<Int, List<SubjectEntity>>()

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
        // 「全部」候选池同理：它是历史排名专用的，切模式后必须重拉
        browsePool.clear()
        // 切到 Steam 标签不强制绕过缓存：NirikoApplication 启动时已预取排行数据
        //（打开应用即自动加载），这里直接用预取缓存立即展示，避免重复请求/等待
        _uiState.update { it.copy(trendingMode = mode, trendingResults = emptyList(), page = 1, hasMore = true) }
        trendingJob?.cancel()
        pendingTrendingRefresh = false
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

    /**
     * 趋势刷新的**唯一入口**（下拉刷新 / 重试按钮 / 进入页面）。
     *
     * - 已有一次刷新在跑时**合并**到那一次：改造前六条路径（下拉、重试、切模式、切类型、
     *   筛选变更、人物模式切换）都能并发触发刷新，每次都会各自打一遍 5 个类型的并行请求。
     * - 「force = true」：绕过新鲜度窗口与失败退避 —— 用户主动操作时必须真的再拉一次。
     */
    /**
     * 最近一次「当季热门」的完整结果。
     *
     * 切换排序/类型时用它**本地重排**，做到零网络请求（验收项之一）。
     */
    private var lastSeasonalResult: SeasonalTrendingResult? = null

    /**
     * 最近一次「当季热门」候选池覆盖的类型（第 6 轮 §2.4）。
     *
     * 顶部类型行切换时用它判断能否**本地重选**（零请求）：
     * 只有缓存池已经覆盖新类型集合时才成立（例如从「全部」切到「动画」）；
     * 反向（动画 → 全部）必须重新取候选，否则书籍/游戏/音乐那几类的候选根本不在池子里。
     */
    private var lastSeasonalTypes: List<Int> = emptyList()

    // ==================== 当季热门：排序 / 类型（第 5 轮 D26） ====================

    /**
     * 切换发现页布局（卡片 / 宫格）。
     *
     * 先乐观更新 uiState（点击立刻有反馈），再写 DataStore 持久化；
     * 写入后由 init 里的 collector 回写一次（幂等）。
     */
    fun setDiscoveryLayout(layout: DiscoveryLayout) {
        if (_uiState.value.discoveryLayout == layout) return
        _uiState.update { it.copy(discoveryLayout = layout) }
        val store = settingsDataStore ?: return
        viewModelScope.launch { runCatching { store.setDiscoveryLayout(layout.key) } }
    }

    /** 在卡片 / 宫格之间切换。 */
    fun toggleDiscoveryLayout() = setDiscoveryLayout(_uiState.value.discoveryLayout.toggled)

    /**
     * 用最近一次候选池按当前类型重排（第 6 轮 §2.3：**本地，零请求**）。
     *
     * 走同一条选取流水线（类内热度序 → 质量门槛 → 多样性重排 → 取前 30）。
     * 还没有成功加载过（冷启动直接切类型）时退化为一次强制刷新，保证点了就有反应。
     *
     * 第 6 轮返工：排序 chips 与「第 N 话」副标题已按用户要求回退（当季热门回到纯卡片列表），
     * 因此这里固定热度序，不再有季节排序状态。
     */
    private fun applySeasonalLocally() {
        val cached = lastSeasonalResult
        if (cached == null) {
            viewModelScope.launch { doRefreshTrending(clearCurrentTypeCache = true, force = true) }
            return
        }
        val st = _uiState.value
        val selection = SeasonalTrendingCalculator.select(
            items = cached.all,
            types = SeasonalTypes.of(st.selectedType),
            sort = SeasonalSort.HEAT,
            targetSize = DiscoveryFeed.TARGET_SIZE,
        )
        _uiState.update {
            it.copy(
                trendingResults = selection.items.map { item -> item.subject },
                seasonalTotalCandidates = selection.candidateCount,
                trendingVersion = it.trendingVersion + 1,
            )
        }
    }

    fun refreshTrending() {
        // 击穿仓储层 30s 榜单微缓存：否则下拉刷新后 30 秒内拿到的仍是旧榜
        // （SubjectRepository.clearRankingCache 此前全仓无调用方）
        subjectRepository.clearRankingCache()
        if (trendingJob?.isActive == true) {
            // 第 6 轮 R5：改造前这里直接 return —— 用户的主动下拉被静默丢弃，
            // 表现为「刷新没反应」。现在标记 pending，当前请求结束后自动补一轮。
            pendingTrendingRefresh = true
            Log.d(TAG, "refreshTrending: 已有刷新进行中，标记 pending 并在结束后补一轮")
            return
        }
        trendingJob = viewModelScope.launch { runTrendingRefreshes(clearCurrentTypeCache = true) }
    }

    /**
     * 趋势刷新循环：把刷新期间累积的用户主动刷新补跑一轮（第 6 轮 R5）。
     *
     * 只有用户**主动**刷新才会置 pending；自动刷新（回前台/新鲜度校验）不补跑，
     * 避免退避窗口被绕过。
     */
    private suspend fun runTrendingRefreshes(clearCurrentTypeCache: Boolean) {
        while (true) {
            pendingTrendingRefresh = false
            try {
                doRefreshTrending(clearCurrentTypeCache = clearCurrentTypeCache, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "trending refresh failed", e)
            }
            if (!pendingTrendingRefresh) break
        }
    }

    /**
     * 当前趋势视图的上次成功刷新时间（0 = 从未）。
     * 供 UI 显示「上次更新 X 分钟前」——改造前用户完全无从判断屏幕上是新数据还是缓存。
     */
    val trendingLastUpdatedAt: StateFlow<Long> = combine(
        refreshCoordinator?.snapshots ?: MutableStateFlow(emptyMap()),
        _uiState.map { trendingRefreshKey(it) }.distinctUntilChanged(),
    ) { snaps, key -> snaps[key]?.lastSuccessAt ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 趋势资源的新鲜度 key：不同模式/类型/筛选是不同的数据，不能互相顶掉。 */
    private fun trendingRefreshKey(s: SubjectSearchUiState): String = buildString {
        append("trending:")
        append(s.trendingMode.name)
        append(':')
        append(s.selectedType ?: "all")
        append(':')
        append(s.nsfwEnabled)
        append(':')
        // 第 6 轮 §6.3：历史排名的筛选条件是 BrowseFilter（9 维度 + 增强），
        // 它一变就是另一份数据 —— 指纹必须跟着变，否则会命中旧的榜单缓存/退避窗口。
        append(s.browseFilter)
    }

    /**
     * 转圈看门狗：兜底释放。
     * 只有在仍属于本次请求时才释放，避免误改已完成/更新的状态（Bangumi-master 同款守卫）。
     */
    private fun armTrendingWatchdog(requestId: Int) {
        viewModelScope.launch {
            delay(TRENDING_WATCHDOG_MS)
            if (requestId == trendingRequestId && _uiState.value.isLoadingTrending) {
                Log.w(TAG, "trending 刷新超过 ${TRENDING_WATCHDOG_MS}ms 未返回，强制释放转圈")
                _uiState.update { it.copy(isLoadingTrending = false) }
            }
        }
    }

    private suspend fun doRefreshTrending(
        clearCurrentTypeCache: Boolean = false,
        force: Boolean = false,
        /** 静默刷新：不显示下拉指示器（回到前台的后台校验用）。 */
        silent: Boolean = false,
    ) {
        val snapshot = _uiState.value
        val coordinator = refreshCoordinator
        val refreshKey = trendingRefreshKey(snapshot)
        // 仅当屏幕上已有内容时才允许「跳过」：否则会把刚清空的列表留在空态
        if (!force && coordinator != null && snapshot.trendingResults.isNotEmpty()) {
            val decision = coordinator.decide(refreshKey, RefreshResource.TRENDING)
            if (decision == RefreshDecision.FRESH || decision == RefreshDecision.BACKOFF) {
                // 5 分钟内已成功过，或正处于失败退避窗口 → 不重复打接口，保留现有列表
                Log.d(TAG, "doRefreshTrending 跳过：$decision")
                return
            }
        }

        val requestId = ++trendingRequestId
        if (!silent) {
            _uiState.update { it.copy(isLoadingTrending = true) }
            armTrendingWatchdog(requestId)
        }
        try {
            val s = _uiState.value
            val currentType = s.selectedType
            val mode = s.trendingMode
            val filterNsfw = s.nsfwEnabled
            val filterSort = s.sortMode.apiValue
            // 第 6 轮 §6.3：历史排名改用 BrowseFilter（9 维度 + 评分/排名/NSFW 增强）
            val browse = s.browseFilter
            val today = LocalDate.now()
            val dateRange = TrendingCalculator.computeSeasonDateRange()
            val useAirDate = mode == TrendingMode.SEASONAL
            // 历史排名的 tag 条件 = BrowseFilter 的基础标签（内容标签 + 地区 + 版本）
            val panelTags = if (mode == TrendingMode.ALL_TIME) browse.baseTagClauses() else emptyList()
            // 下拉刷新：只清当前类型缓存（其他类型保留）
            if (clearCurrentTypeCache) {
                perTypeTrending.remove(currentType)
            }

            // ALL_TIME（历史排名）的排序由筛选面板的「排序」维度决定（默认评分人数，
            // 与 Bangumi-master 的找条目一致）；SEASONAL 用热度/用户选择。
            val actualSort = when {
                mode == TrendingMode.ALL_TIME -> browse.sort.requestSort
                useAirDate -> if (filterSort == "match") "heat" else filterSort
                else -> filterSort
            }

            // STEAM 模式：store 热销榜 topsellers 为主数据源（一次 100 条 appid+标题+封面，
            // store 域稳定、无缺项；不再依赖 GetMostPlayedGames(api 域) + appdetails 两跳补全）
            if (mode == TrendingMode.STEAM) {
                val dao = subjectDao
                // 本地已落库的 steam 条目（按 appid 索引，供已绑定反查与兜底）
                val localItems = if (dao != null) {
                    runCatching { withContext(Dispatchers.IO) { dao.getBySource("steam") } }
                        .getOrDefault(emptyList())
                } else emptyList()
                val localByAppId = localItems.mapNotNull { item ->
                    item.sourceKey?.removePrefix("steam:")?.toIntOrNull()?.let { it to item }
                }.toMap()

                // 排行主数据源：store 热销榜（fetchStoreTopSellers 内部已落库真实条目）
                // 下拉刷新（force）才绕过 SteamRepository 的 30 分钟榜单缓存；
                // 其余路径直接复用启动预取拉到的同一份榜单（改造前这里会重复拉一次）。
                val storeItems = runCatching {
                    steamRepository?.fetchStoreTopSellers(forceRefresh = force).orEmpty()
                }.getOrDefault(emptyList())

                if (storeItems.isEmpty()) {
                    if (requestId != trendingRequestId) return
                    _uiState.update {
                        it.copy(
                            trendingResults = emptyList(),
                            isLoadingTrending = false,
                            error = "Steam 排行暂不可用，请检查网络后重试",
                        )
                    }
                    return
                }
                val storeAppIds = storeItems.map { it.appId }

                // 已绑定 bangumi 反查：appid → bangumi subjectId（收藏库/详情页已绑定则优先展示词条）。
                // 改造前是对榜内每个 appid 串行调 getBoundSubjectIdByAppId → 100 条榜单 100 次串行数据库往返；
                // 现在一次 IN 查询。
                val boundSubjectIds = steamRepository
                    ?.getBoundSubjectIdsByAppIds(storeAppIds)
                    .orEmpty()

                // 组装：已绑定 → bangumi 词条；否则 → store 条目（真实标题/封面，一步到位无缺项）
                val chartSubjects = storeItems.mapNotNull { item ->
                    val boundId = boundSubjectIds[item.appId]
                    if (boundId != null) {
                        runCatching {
                            withContext(Dispatchers.IO) { dao?.getById(boundId) }
                        }.getOrNull()
                    } else {
                        localByAppId[item.appId]?.takeIf { !it.title.startsWith("Steam 热门 #") }
                            ?: SubjectEntity(
                                subjectId = GameItemMapper.deriveSubjectId("steam", item.appId.toString()),
                                title = item.title,
                                titleCN = item.title,
                                type = SubjectType.GAME,
                                coverUrl = item.coverUrl,
                                sourceId = "steam",
                                sourceKey = "steam:${item.appId}",
                                lastSyncTime = System.currentTimeMillis(),
                            )
                    }
                }

                // Steam 排行 = store 热销榜 100 条（按热销顺序），本地作品不混入。
                // 榜中条目若用户已收藏，由 UI 的 isInCollection 标记展示。

                // === Bangumi 匹配移出刷新关键路径 ===
                // 改造前 autoBindNewSteamToBangumi 在刷新协程内**同步**执行：100 条按 8/批串行 13 批、
                // 每条 withTimeout(2s) 包一次 Bangumi 搜索 → 最坏约 26 秒才更新 UI，下拉转圈长时间不消失。
                // 现在先出榜（立即渲染），匹配在后台完成后原地替换为 Bangumi 词条。
                val steamItems = chartSubjects

                // 空态：store 源也失败 → 明确错误提示 + 下拉重试
                if (steamItems.isEmpty()) {
                    if (requestId != trendingRequestId) return
                    _uiState.update {
                        it.copy(
                            trendingResults = emptyList(),
                            isLoadingTrending = false,
                            error = "Steam 排行暂不可用，请检查网络后重试",
                        )
                    }
                    return
                }

                // 竞态防护
                if (requestId != trendingRequestId) return
                perTypeTrending[currentType] = TypeTrendingState(
                    results = steamItems,
                    page = 1,
                    hasMore = false,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    loaded = true,
                )
                loadSteamSupplements(steamItems)
                coordinator?.recordSuccess(refreshKey)
                _uiState.update {
                    it.copy(
                        trendingResults = steamItems, isLoadingTrending = false, error = null,
                        hasMore = false, page = 1,
                        trendingScrollIndex = 0, trendingScrollOffset = 0,
                        trendingVersion = it.trendingVersion + 1,
                    )
                }
                // 榜单已出，后台把新出现的 Steam 条目匹配成 Bangumi 词条（不阻塞转圈）
                startSteamBindingUpgrade(steamItems, requestId, currentType)
                return
            }

            // ===== 当季热门（第 5 轮 D26）：语义是「正在放送」，不再走季度 air_date 窗口 =====
            // 改造前用「本季度开播」的窗口检索 → 上季度开播、至今仍在播的长连载（播一年的特摄）
            // 永不展示；数量不足时还用历史排名填充（用户已明确否掉）。
            // 数据源见 SeasonalTrendingRepository：/calendar（权威）+ 放宽窗口检索（兜底）取并集。
            val seasonalRepo = seasonalTrendingRepository
            if (mode == TrendingMode.SEASONAL && seasonalRepo != null) {
                val st = _uiState.value
                // 复审修复：runCatching 会把 CancellationException 一起吞掉 —— 取消必须原样抛出，
                // 否则「刷新被新请求取消」会被当成加载失败并写 error。
                var seasonalFailure: Throwable? = null
                val seasonalResult = try {
                    seasonalRepo.load(
                        // 类型统一由顶部类型行驱动（第 6 轮 §2.4）：null = 全部（5 类全查）
                        types = SeasonalTypes.of(st.selectedType),
                        sort = SeasonalSort.HEAT,
                        targetSize = DiscoveryFeed.TARGET_SIZE,
                        nsfw = filterNsfw.takeIf { it },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "seasonal load failed", e)
                    seasonalFailure = e
                    null
                }
                if (requestId != trendingRequestId) return

                // 第 6 轮 R7：SEASONAL 的失败**也必须写 state.error**。
                // 改造前这里把异常吞成 null、error 恒为空，于是「断网」与「本季确实没有在播作品」
                // 在界面上完全一样（用户只会看到一句空态文案）。现在：
                // - 抛异常，或两条数据源都失败（loadFailed）→ error = 网络失败文案（UI 显示重试）；
                // - error 为空 + 列表为空 → 才是真的「没有可展示的候选」。
                val failure = seasonalFailure
                if (seasonalResult == null || seasonalResult.loadFailed) {
                    val reason = failure?.let { networkFailureMessage(it) }
                        ?: "放送数据加载失败，请检查网络后重试"
                    coordinator?.recordFailure(refreshKey, reason)
                    _uiState.update {
                        it.copy(
                            isLoadingTrending = false,
                            error = reason,
                            seasonalTotalCandidates = 0,
                        )
                    }
                    return
                }

                lastSeasonalResult = seasonalResult
                lastSeasonalTypes = SeasonalTypes.of(st.selectedType)

                val seasonalItems = seasonalResult.items
                coordinator?.recordSuccess(refreshKey)
                _uiState.update {
                    it.copy(
                        trendingResults = seasonalItems.map { item -> item.subject },
                        seasonalTotalCandidates = seasonalResult.all.size,
                        isLoadingTrending = false,
                        error = null,
                        hasMore = false,
                        page = 1,
                        trendingScrollIndex = 0,
                        trendingScrollOffset = 0,
                        trendingVersion = it.trendingVersion + 1,
                    )
                }
                return
            }

            // ===== 历史排名 · 全部（第 6 轮 §3.3 v1 规则版）=====
            // 5 类各取 30 条候选（sort=heat + rank>0）→ 本地过 BrowseFilter 的增强项
            // → DiscoveryFeed（质量门槛 + 多样性重排）→ 取前 30。**不做固定配额交错**。
            if (mode == TrendingMode.ALL_TIME && currentType == null) {
                browsePool.clear()
                val hasMorePool = loadBrowsePoolPage(
                    filter = browse,
                    today = today,
                    offset = 0,
                    limit = BROWSE_PAGE_SIZE,
                )
                if (requestId != trendingRequestId) return
                val feed = buildAllTypesResult(
                    filter = browse,
                    today = today,
                    collectedIds = s.collectedSubjectIds,
                    page = 1,
                )
                coordinator?.recordSuccess(refreshKey)
                _uiState.update {
                    it.copy(
                        trendingResults = feed.items,
                        isLoadingTrending = false,
                        error = null,
                        hasMore = hasMorePool,
                        page = 1,
                        browsePage = 1,
                        browsePoolSize = feed.poolSize,
                        trendingScrollIndex = 0,
                        trendingScrollOffset = 0,
                        trendingVersion = it.trendingVersion + 1,
                    )
                }
                return
            }

            val results = if (currentType == null) {
                val typeList = listOf(2, 1, 4, 3, 6)
                // R1：每个类型的失败都要留下来（attemptRemote 只捕获业务异常，取消照旧抛出）。
                // 改造前这里是 catch (_: Exception) { emptyList() } —— 「榜单全挂」与「榜单为空」
                // 在上层完全一样，用户看到的只是空白，没有任何可诊断信息。
                val fetches: List<RemoteFetch<List<SubjectEntity>>> = coroutineScope {
                    typeList.map { t ->
                        async(Dispatchers.IO) {
                            attemptRemote {
                                if ((mode == TrendingMode.ALL_TIME)) {
                                    // 历史排名：GET 榜单（绕开 POST body 被吞），每类取 top；有分类标签时改用 search+tag
                                    if (panelTags.isNotEmpty()) {
                                        subjectRepository.search(
                                            keyword = "", type = t,
                                            tags = panelTags,
                                            rank = listOf(">0", "<=99999"),
                                            sort = "rank", limit = 20,
                                        )
                                    } else {
                                        subjectRepository.getRanking(
                                            type = t, offset = 0, limit = 20,
                                            fingerprint = rankingFingerprint(filterNsfw, filterSort, panelTags),
                                        )
                                    }
                                } else {
                                    subjectRepository.search(
                                        keyword = "", type = t,
                                        airDate = if (useAirDate) dateRange else null,
                                        rank = listOf(">0", "<=99999"), nsfw = filterNsfw.takeIf { it },
                                        sort = actualSort,
                                        limit = if (useAirDate) 12 else 20,
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
                if (fetches.all { it.isFailure }) {
                    // 全部类型都失败 → 抛出，由外层 catch 写入 state.error（UI 显示「加载失败 + 重试」）
                    val firstError = fetches.first { it.isFailure }.error
                    throw (firstError ?: IllegalStateException("榜单加载失败"))
                }
                val lists: List<List<SubjectEntity>> = fetches.mapNotNull { it.value }
                if ((mode == TrendingMode.ALL_TIME)) {
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
                // R1：单类型榜单失败不再被吞掉（getRanking 现在会抛），交给外层 catch 写 state.error
                if (useAirDate) {
                    val seasonal = subjectRepository.search(
                        keyword = "", type = currentType,
                        airDate = dateRange,
                        rank = listOf(">0", "<=99999"), nsfw = filterNsfw.takeIf { it },
                        sort = actualSort, limit = 50,
                    ).sortedByDescending { it.ratingScore ?: 0f }
                    // 第 5 轮 D26：**删除「不足则用历史排名填充」**。
                    // 用户明确否掉了这个做法——补进来的是「历来排名高」而不是「现在在播」，
                    // 会让人误以为那些老片是当季热门。不足就是不足，
                    // UI 会在列表尾部给「查看历史排名 ›」入口。
                    seasonal.take(TRENDING_COUNT)
                } else if ((mode == TrendingMode.ALL_TIME)) {
                    // 历史排名（选中类型）：
                    //  - 排序维度 = 排名 → 走 GET 榜单（保留 R1「失败抛出」与 R4「查询指纹缓存」）；
                    //  - 其他排序/筛选 → 走 v0 检索，再本地过增强项（评分/评分人数/状态/收藏）并本地排序。
                    // GET 榜单只能表达「类型 + 排名序」，因此只有在**没有**标签/时间条件时才用它
                    // （保留 R1「失败抛出」与 R4「查询指纹缓存」）；一旦有标签或年份/状态条件，
                    // 必须走 v0 检索，否则筛选项会被静默忽略。
                    val rankOnly = panelTags.isEmpty() && browse.airDateRange(today) == null
                    if (browse.sort == BrowseSort.RANK && rankOnly) {
                        subjectRepository.getRanking(
                            type = currentType, offset = 0, limit = 20,
                            fingerprint = rankingFingerprint(browse.nsfw, actualSort, panelTags),
                        ).let { raw ->
                            browse.applyLocalFilters(raw, s.collectedSubjectIds, today)
                                .sortedBy { it.rank ?: Int.MAX_VALUE }
                                .take(20)
                        }
                    } else {
                        browse.applyLocalFilters(
                            searchForBrowse(
                                type = currentType,
                                filter = browse,
                                today = today,
                                limit = 30,
                                offset = 0,
                            ),
                            s.collectedSubjectIds,
                            today,
                        ).let { filtered -> browse.sortLocally(filtered).take(20) }
                    }
                } else {
                    val single = subjectRepository.search(
                        keyword = "", type = currentType,
                        rank = listOf(">0", "<=99999"), nsfw = filterNsfw.takeIf { it },
                        sort = actualSort, limit = 40,
                    )
                    single.filter { (it.ratingTotal ?: 0) >= MIN_RATING_COUNT }.take(20)
                }
            }

            // 历史排名：全部标签不分页；选中类型可分页
            val canPage = (mode == TrendingMode.ALL_TIME) && currentType != null
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
            coordinator?.recordSuccess(refreshKey)
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
            coordinator?.recordFailure(refreshKey, msg)
            _uiState.update { it.copy(isLoadingTrending = false, error = msg) }
        } finally {
            // 关键：只有**仍是最新**的请求才允许关闭转圈。
            // 改造前无条件关闭 → 旧请求会把新请求的转圈提前关掉（下拉指示器闪烁/提前消失）。
            if (requestId == trendingRequestId) {
                _uiState.update { it.copy(isLoadingTrending = false) }
            }
        }
    }

    // ==================== 历史排名筛选器（第 6 轮 §6.3） ====================

    /**
     * 改筛选条件（「改动即查询」）。
     *
     * 统一走 [refreshForActiveQuery]：有活跃关键词 → 重查搜索；没有 → 立刻重查历史排名
     * （与 t1 定的「活跃查询唯一来源」一致，**不引入第二个查询源**）。
     */
    fun setBrowseFilter(update: (BrowseFilter) -> BrowseFilter) {
        val current = _uiState.value.browseFilter
        val next = update(current)
        if (next == current) return
        // 条件变了 → 候选池页码回到第 1 页
        _uiState.update { it.copy(browseFilter = next, browsePage = 1) }
        refreshForActiveQuery()
    }

    /** 清空全部条件（含 NSFW 与排序回到默认「评分人数」）。 */
    fun clearBrowseFilter() = setBrowseFilter { BrowseFilter() }

    fun setBrowseArea(area: BrowseArea?) = setBrowseFilter { it.copy(area = area) }

    fun setBrowseVersion(version: BrowseVersion?) = setBrowseFilter { it.copy(version = version) }

    fun setBrowseYear(year: BrowseYear?) = setBrowseFilter { it.copy(year = year) }

    fun setBrowseQuarter(quarter: Int?) = setBrowseFilter { it.copy(quarter = quarter) }

    fun setBrowseStatus(status: BrowseStatus?) = setBrowseFilter { it.copy(status = status) }

    /** 类型维度（46 词表）多选且。 */
    fun toggleBrowseTag(tag: String) = setBrowseFilter {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    /** 制作维度：公司名当标签用（标注「按标签匹配」）。 */
    fun setBrowseStudio(studio: String?) = setBrowseFilter {
        it.copy(studio = studio?.trim()?.takeIf { value -> value.isNotEmpty() })
    }

    fun setBrowseSort(sort: BrowseSort) = setBrowseFilter { it.copy(sort = sort) }

    /** 收藏维度：隐藏已收藏（本地过滤）。 */
    fun setBrowseHideCollected(hide: Boolean) = setBrowseFilter { it.copy(hideCollected = hide) }

    fun setBrowseRatingRange(min: Float?, max: Float?) = setBrowseFilter {
        it.copy(minRating = min, maxRating = max)
    }

    fun setBrowseRatingCountRange(min: Int?, max: Int?) = setBrowseFilter {
        it.copy(minRatingCount = min, maxRatingCount = max)
    }

    fun setBrowseRankRange(from: Int?, to: Int?) = setBrowseFilter {
        it.copy(rankFrom = from, rankTo = to)
    }

    fun setBrowseNsfw(enabled: Boolean) = setBrowseFilter { it.copy(nsfw = enabled) }

    // ==================== 高级筛选 ====================
    fun onQueryChanged(query: String) {
        if (_uiState.value.isPersonSearch) {
            _uiState.update { it.copy(query = query) }
            searchPersons(query)
        } else {
            // 第 6 轮 F1：输入同时写回 uiState.query —— 它是「活跃查询」，也是
            // setSortMode/setNsfw/setFilter/clearFilters 唯一读取的来源。
            // 改造前只写 queryInput，那四个 setter 读到的恒为空串，于是「有输入时改筛选」
            // 会去刷新趋势区而不是重查搜索（用户观感：筛选没用）。
            _uiState.update { it.copy(query = query) }
            // queryInput 仍然保留：防抖与竞态由 debounce+collectLatest 调度（击键不直接发请求）
            queryInput.value = query
            suggestionInput.value = query
        }
    }

    /**
     * 活跃查询（第 6 轮 F1 + 复审修复，唯一来源）：与 UI 可见查询同源。
     */
    private fun activeQuery(): String {
        val s = _uiState.value
        return resolveActiveQuery(queryInput.value, s.query, s.isPersonSearch)
    }

    /**
     * 筛选/排序/R18 变更后的动作（第 6 轮 F1）：有活跃查询 → 重查搜索；否则只刷新趋势区。
     */
    private fun refreshForActiveQuery() {
        val q = activeQuery()
        if (shouldResearchOnFilterChange(q)) {
            debouncedSearch(q, immediate = true)
        } else {
            refreshTrendingWithFilters()
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
            // 四态之二：无输入 —— 不显示任何原因说明（空输入框不需要解释）
            _uiState.update {
                it.copy(suggestions = emptyList(), isSuggestionsLoading = false, suggestionNotice = null)
            }
            return
        }
        // 四态之一：设置页关闭了「显示搜索建议」。不能静默什么都不发生，要把原因说出来。
        if (!suggestionsEnabled.value) {
            _uiState.update {
                it.copy(
                    suggestions = emptyList(),
                    isSuggestionsLoading = false,
                    suggestionNotice = suggestionNoticeOf(SuggestionState.DISABLED),
                )
            }
            return
        }
        _uiState.update { it.copy(isSuggestionsLoading = true, suggestionNotice = null) }

        try {
            // 1) 本地即时：本地前缀命中 + 历史匹配立即展示，不依赖网络（击键即有建议响应）
            val localSubjects = withContext(Dispatchers.IO) {
                // t14 清理：runCatching 会把取消一起吞成「没有本地命中」——
                // 与 R5 同一不变量：取消必须原样抛出。
                try {
                    subjectRepository.searchByPrefix(query)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val historyMatches = _uiState.value.searchHistory.filter { it.contains(query, ignoreCase = true) }
            val localItems = buildSuggestionItems(localSubjects, historyMatches)
            if (coroutineContext.isActive && localItems.isNotEmpty()) {
                _uiState.update { it.copy(suggestions = localItems, isSuggestionsLoading = true) }
            }

            // 2) 远程：与正式搜索共用同一次请求（F4）。改造前这里对远程建议整体限时 2.5s，
            //    弱网/代理下必然超时 → 建议永远为空且不给任何原因；现在既不限时（取消由
            //    debounce+collectLatest 负责），也只打一次请求（正式搜索会复用同一份结果）。
            val remoteAttempt = remoteSuggestionAttempt(query)

            if (!coroutineContext.isActive) return
            val merged = mergeSuggestions(
                local = localItems,
                remote = remoteAttempt.results,
                history = historyMatches,
                maxRemote = SUGGESTION_MAX_REMOTE,
            )
            val state = suggestionStateOf(
                enabled = true,
                query = query,
                itemCount = merged.size,
                remoteFailed = remoteAttempt.error != null && remoteAttempt.results.isEmpty(),
            )
            _uiState.update {
                it.copy(
                    suggestions = merged,
                    isSuggestionsLoading = false,
                    suggestionNotice = suggestionNoticeOf(state),
                )
            }
        } catch (e: CancellationException) {
            // 第 6 轮 F2/F4：取消不是失败 —— 不清已有建议、不写 error；
            // loading 由 finally 兜底清掉，避免建议区永远转圈。
            throw e
        } catch (e: Exception) {
            if (coroutineContext.isActive) {
                val state = suggestionStateOf(
                    enabled = true,
                    query = query,
                    itemCount = _uiState.value.suggestions.size,
                    remoteFailed = true,
                )
                _uiState.update {
                    it.copy(isSuggestionsLoading = false, suggestionNotice = suggestionNoticeOf(state))
                }
            }
        } finally {
            // 第 6 轮 F4 + 复审修复：任何出口都**无条件**清 isSuggestionsLoading。
            // collectLatest 会先 join 上一个块再启动新块，因此这里不会误清新一轮刚设的 true；
            // 而「只在取消路径清」会让正常异常出口留下永久转圈。
            _uiState.update { it.copy(isSuggestionsLoading = false) }
        }
    }

    /**
     * 建议的远程部分（第 6 轮 F4）。
     *
     * 走与正式搜索**完全相同**的关键词检索参数（类型/标签/排序/R18 + 同一个请求指纹），
     * 于是 120ms 发起的这一次请求会被 300ms 的正式搜索直接复用 —— 一次击键只打一次网络。
     * 主链无命中时再退到既有的建议专用链（含游戏源旁路），保留「正式搜索能出的关键词建议也能出」。
     */
    private suspend fun remoteSuggestionAttempt(query: String): SearchAttempt {
        val s = _uiState.value
        val parsed = SearchQueryParser.parse(query)
        val effectiveSort = parsed.sort ?: s.sortMode.apiValue
        val panelTags = TrendingCalculator.buildFilterTags(s.filterSelections, getFilterDimensions())
        val rankFilter = if (s.sortMode == SearchSortMode.RANK) listOf(">0", "<=99999") else null
        val type = s.selectedType ?: SUGGESTION_PRIMARY_TYPE

        val primary = keywordAttempt(
            query = parsed.keyword.ifBlank { query },
            type = type,
            tags = panelTags,
            nsfw = s.nsfwEnabled.takeIf { it },
            sort = effectiveSort,
            rank = rankFilter,
            limit = KEYWORD_FETCH_LIMIT,
        )
        if (primary.results.isNotEmpty() || primary.error != null) return primary

        // 主链无命中：走既有建议链（AniList/游戏源旁路），整体限时，失败不阻塞已展示的本地建议
        val fallback = withTimeoutOrNull(SUGGESTION_FALLBACK_TIMEOUT_MS) {
            attemptRemote { subjectRepository.searchForSuggestions(query, limit = SUGGESTION_FALLBACK_LIMIT) }
        } ?: return SearchAttempt(error = "建议请求超时")
        val failure = fallback.error
        if (failure != null) return SearchAttempt(error = networkFailureMessage(failure))
        val value = fallback.value.orEmpty()
        return SearchAttempt(results = value, total = value.size)
    }

    /**
     * 关键词检索路（第 6 轮 F4）：与建议链路共用同一个请求指纹。
     *
     * 同一 key 的并发调用由 AsyncSingleFlight 合并为一次，完成后短窗口内继续复用，
     * 因此「建议 120ms + 搜索 300ms」只会打一遍网络。
     */
    private suspend fun keywordAttempt(
        query: String,
        type: Int,
        tags: List<String>?,
        nsfw: Boolean?,
        sort: String?,
        rank: List<String>?,
        limit: Int,
    ): SearchAttempt {
        val key = keywordShareKey(query, type, tags, nsfw, sort, rank, limit)
        return requestShare.run(key) {
            attemptRemote {
                subjectRepository.searchWithTotal(
                    keyword = query, type = type, tags = tags,
                    nsfw = nsfw, sort = sort, rank = rank, limit = limit,
                )
            }.toAttempt()
        }
    }

    fun clearSuggestions() {
        suggestionInput.value = ""
        _uiState.update {
            it.copy(suggestions = emptyList(), isSuggestionsLoading = false, suggestionNotice = null)
        }
    }

    // ==================== 高级筛选 ====================

    fun setSortMode(mode: SearchSortMode) {
        _uiState.update { it.copy(sortMode = mode) }
        // 第 6 轮 F1：判定用活跃查询（activeQuery），不再用只被历史记录/清空写入的 uiState.query
        refreshForActiveQuery()
    }

    fun setNsfw(enabled: Boolean) {
        _uiState.update { it.copy(nsfwEnabled = enabled) }
        refreshForActiveQuery()
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
        refreshForActiveQuery()
    }

    fun clearFilters() {
        _uiState.update { it.copy(filterSelections = emptyMap()) }
        refreshForActiveQuery()
    }

    fun hasActiveFilters(): Boolean {
        val s = _uiState.value
        return TrendingCalculator.hasActiveFilters(s.filterSelections, s.nsfwEnabled, s.sortMode)
    }

    private fun refreshTrendingWithFilters() {
        trendingJob?.cancel()
        // 取消旧刷新后 pending 已无意义（那一轮不会再补跑）
        pendingTrendingRefresh = false
        // 条件变了 = 候选池作废（第 6 轮 §6.3 的「改动即查询」）
        browsePool.clear()
        _uiState.update { it.copy(trendingResults = emptyList(), page = 1, hasMore = true, browsePage = 1) }
        trendingJob = viewModelScope.launch {
            doRefreshTrending()
        }
    }

    fun onHistoryClick(keyword: String) {
        _uiState.update { it.copy(query = keyword) }
        // 关键：写入 queryInput 独立流——UI 侧从 viewModel.query 读关键词
        // 决定显示搜索态（否则点击后仍停在历史/趋势区）
        queryInput.value = keyword
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                searchHistoryDao?.upsertByKeyword(keyword)
            }
        }
        clearSuggestions()
        debouncedSearch(keyword, immediate = true)
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
        // round 4 复审修复（R1-residual）：**先取词、再翻标志**。
        // 复现：作品搜「巨人」→ 切人物改成「花泽」（人物分支只写 uiState，queryInput 仍是「巨人」）
        // → 点类型行「动画」→ 修复前先翻 isPersonSearch 再调 activeQuery()，判定落到作品模式、
        // 按「输入流优先」取到陈旧的「巨人」（allResults 非空时还会直接客户端过滤旧结果），
        // 而输入框写着「花泽」。
        val plan = typeSwitchPlan(queryInput.value, s.query)
        _uiState.update { it.copy(selectedType = type, isPersonSearch = plan.isPersonSearch) }
        val q = plan.query
        if (q.isNotBlank()) {
            if (s.allResults.isNotEmpty()) {
                val filtered = filterByType(s.allResults, type)
                _uiState.update { it.copy(results = filtered) }
            } else {
                debouncedSearch(q, immediate = true)
            }
        } else if (
            s.trendingMode == TrendingMode.SEASONAL &&
            lastSeasonalResult != null &&
            lastSeasonalTypes.containsAll(SeasonalTypes.of(type))
        ) {
            // 当季热门：缓存池已覆盖新类型 → **本地重选**（零请求，第 6 轮 §2.4/§2.5 的验收项）。
            applySeasonalLocally()
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
        // 复审修复（round 3）：必须在**翻转标志之前**决定用哪个查询 ——
        // activeQuery() 按「当前」模式分支取值，翻转后再取会走错分支
        //（回切作品模式时读到 queryInput 里残留的上一次作品搜索词）。
        val q = queryAfterModeSwitch(enabled, queryInput.value, _uiState.value.query)
        _uiState.update { it.copy(isPersonSearch = enabled) }
        if (enabled) {
            searchJob?.cancel()
            trendingJob?.cancel()
            if (q.isNotBlank()) searchPersons(q)
            else _uiState.update { it.copy(personResults = emptyList(), isSearching = false) }
        } else {
            trendingJob?.cancel()
            if (q.isNotBlank()) debouncedSearch(q, immediate = true)
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
                    // t14 清理：显式 rethrow 取消（与 R5 同一不变量）
                    try {
                        subjectRepository.searchPersons(query)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
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

    /**
     * P0-2 兼容层：immediate 直接搜索；否则写输入流（防抖由 collectLatest 调度）。
     *
     * 复审修复：去掉没用到的 type 形参 —— doSearch 内部一律从 uiState.selectedType 读类型，
     * 形参只会让调用方误以为可以指定类型。
     */
    private fun debouncedSearch(query: String, immediate: Boolean = false) {
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
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        allResults = emptyList(),
                        totalResults = 0,
                        isSearching = false,
                        isOfflineResults = false,
                        error = null,
                    )
                }
                return
            }
            // 复审修复：人物模式下 queryInput 的防抖流不应触发**作品**搜索
            //（人物检索走 searchPersons，切模式时也会取消作品搜索任务）
            if (_uiState.value.isPersonSearch) return

            _uiState.update { it.copy(isSearching = true, error = null, isOfflineResults = false) }
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

                // 第 6 轮 F5：多路结果各自记录失败。部分成功照常出结果，
                // 只有**全部路都失败**才置 error —— 这样「断网」与「没有结果」不再混淆。
                val batch: SearchBatch = withContext(Dispatchers.IO) {
                    coroutineScope {
                        // 1) 语法标签路（tag:xxx 显式过滤）
                        val syntaxTagAttempts: List<SearchAttempt> = if (hasTagSyntax && parsed.tags.isNotEmpty()) {
                            listOf(
                                attemptRemote {
                                    subjectRepository.searchWithTotal(
                                        keyword = "",
                                        type = s.selectedType,
                                        tags = parsed.tags,
                                        nsfw = s.nsfwEnabled.takeIf { it },
                                        sort = effectiveSort,
                                        rank = rankFilter,
                                        limit = 50,
                                    )
                                }.toAttempt(),
                            )
                        } else {
                            emptyList()
                        }

                        // 2) 变体标签路（公司名/标签别名展开，各自精确过滤后合并）
                        val variantAttempts: List<SearchAttempt> = if (variantTags.isNotEmpty()) {
                            variantTags.map { variant ->
                                async {
                                    attemptRemote {
                                        subjectRepository.searchWithTotal(
                                            keyword = "",
                                            type = s.selectedType,
                                            tags = (panelTags + variant).distinct(),
                                            nsfw = s.nsfwEnabled.takeIf { it },
                                            sort = effectiveSort,
                                            rank = rankFilter,
                                            limit = 30,
                                        )
                                    }.toAttempt()
                                }
                            }.awaitAll()
                        } else {
                            emptyList()
                        }

                        // 3) keyword 全文检索路（标题/别名匹配；含语法时退化为补充）
                        //    第 6 轮 F4：与搜索建议共用同一个请求指纹 —— 建议（120ms）先发的请求
                        //    会被这里的正式搜索（300ms）直接复用，两者不再各打一遍网络
                        val keywordAttempts: List<SearchAttempt> = if (parsed.keyword.isNotBlank()) {
                            typeList.map { t ->
                                async {
                                    keywordAttempt(
                                        query = parsed.keyword,
                                        type = t,
                                        tags = if (hasTagSyntax) (panelTags + parsed.tags).distinct() else panelTags,
                                        nsfw = s.nsfwEnabled.takeIf { it },
                                        sort = effectiveSort,
                                        rank = rankFilter,
                                        limit = KEYWORD_FETCH_LIMIT,
                                    )
                                }
                            }.awaitAll()
                        } else {
                            emptyList()
                        }

                        // 合并去重：语法标签路 → 变体标签路 → keyword 路（精确优先）
                        val allAttempts = syntaxTagAttempts + variantAttempts + keywordAttempts
                        // total 以 keyword 路统计为主（tag 路 total 是标签全集，不参与计数）
                        val total = when {
                            keywordAttempts.isNotEmpty() -> keywordAttempts.sumOf { it.total }
                            variantAttempts.isNotEmpty() -> variantAttempts.sumOf { it.total }
                            else -> syntaxTagAttempts.sumOf { it.total }
                        }
                        SearchBatch(
                            attempts = allAttempts,
                            total = total,
                            results = mergeSearchAttempts(allAttempts),
                        )
                    }
                }

                if (coroutineContext.isActive) {
                    if (batch.allFailed) {
                        // 第 6 轮 F5：远程全挂 → 明确「网络失败 + 重试」，而不是「未找到相关作品」
                        _uiState.update {
                            it.copy(
                                results = emptyList(),
                                allResults = emptyList(),
                                totalResults = 0,
                                isSearching = false,
                                error = batch.firstFailure ?: "网络请求失败，请重试",
                                isOfflineResults = false,
                            )
                        }
                        return
                    }
                    val filtered = filterByType(batch.results, s.selectedType)
                    _uiState.update {
                        it.copy(
                            results = filtered,
                            allResults = batch.results,
                            totalResults = batch.total,
                            isSearching = false,
                            // 离线兜底：结果照常展示，但要标注来源（不是「全屏错误」）
                            error = if (batch.hasOffline) OFFLINE_RESULTS_NOTICE else null,
                            isOfflineResults = batch.hasOffline,
                        )
                    }
                    // Steam 补充：对 GAME 结果触发匹配 + 加载补充数据（异步，不阻塞结果展示）
                    loadSteamSupplements(batch.results)
                }
            } catch (e: CancellationException) {
                // 第 6 轮 F2：取消必须排在 Exception **之前** —— 否则 debounce/collectLatest 的取消
                // 会被当成搜索失败吞掉（写 error、清空已有结果、转圈停不下来）。
                throw e
            } catch (e: Exception) {
                if (coroutineContext.isActive) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            error = networkFailureMessage(e),
                            results = emptyList(),
                            allResults = emptyList(),
                            totalResults = 0,
                            isOfflineResults = false,
                        )
                    }
                }
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

    /**
     * 对排行中未绑定 bangumi 的 steam 条目**同步**匹配 bangumi 词条并绑定，返回替换后的条目列表。
     *
     * 复用 SteamLibraryMatcher 的匹配思路：标题搜 bangumi GAME 词条 → SteamTitleMatcher
     * 置信度 ≥ MIN_CONFIDENCE → steamRepository.bindManually 写入 steam_bindings。
     * 同步执行保证"刷新后即显示 bangumi 词条"（不再异步延迟）；命中 appid 立即替换为
     * Bangumi 词条（中文名/封面/评分/收藏联动）。
     * 分块并发（8/批）、单条 withTimeout(2s)、失败/超时静默保持 steam 条目、已绑定跳过。
     */
    /**
     * 榜单出榜**之后**，异步把新出现的 Steam 条目匹配成 Bangumi 词条。
     *
     * 结果**原地替换**（只换真的变了的那几个 id），保持列表顺序与滚动位置，
     * 不打断用户浏览（对齐「追加分页不递增 trendingVersion」的既有取舍）。
     */
    private fun startSteamBindingUpgrade(
        displayed: List<SubjectEntity>,
        requestId: Int,
        type: Int?,
    ) {
        if (steamRepository == null) return
        viewModelScope.launch {
            val replaced = try {
                autoBindNewSteamToBangumi(displayed, requestId)
            } catch (_: Exception) {
                return@launch
            }
            if (requestId != trendingRequestId) return@launch
            // 只保留 subjectId 真的变了的项（命中绑定 → 换成 bangumi 词条）
            val replacements = displayed.zip(replaced)
                .filter { (before, after) -> before.subjectId != after.subjectId }
                .associate { (before, after) -> before.subjectId to after }
            if (replacements.isEmpty()) return@launch

            perTypeTrending[type]?.let { cached ->
                perTypeTrending[type] = cached.copy(
                    results = cached.results.map { replacements[it.subjectId] ?: it },
                )
            }
            _uiState.update { st ->
                st.copy(trendingResults = st.trendingResults.map { replacements[it.subjectId] ?: it })
            }
        }
    }

    private suspend fun autoBindNewSteamToBangumi(
        subjects: List<SubjectEntity>,
        requestId: Int,
    ): List<SubjectEntity> {
        val steam = steamRepository ?: return subjects
        // 只匹配榜中未绑定的 steam 独立条目（真实标题，非占位）
        val toMatch = subjects.filter {
            it.sourceKey?.startsWith("steam:") == true && !it.title.startsWith("Steam 热门 #")
        }
        if (toMatch.isEmpty()) return subjects
        val newBindings = mutableMapOf<Int, Long>()
        val startTime = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            toMatch.chunked(8).forEach { batch ->
                coroutineScope {
                    batch.map { item ->
                        async {
                            val appId = item.sourceKey?.removePrefix("steam:")?.toIntOrNull()
                                ?: return@async
                            // 已绑定跳过（含收藏库已有绑定）
                            if (steam.getBoundSubjectIdByAppId(appId) != null) return@async
                            try {
                                withTimeout(2000) {
                                    val title = item.title
                                    if (title.isBlank()) return@withTimeout
                                    // bangumi GAME 类型搜索（type=4），只接受游戏词条
                                    val bangumiCandidates = subjectRepository.search(
                                        keyword = title, type = 4, limit = 5,
                                    )
                                    val best = bangumiCandidates
                                        .filter {
                                            it.type == SubjectType.GAME && it.subjectId > 0 &&
                                                it.sourceKey == null // 只接受真 bangumi 词条，排除 steam/vndb/anilist 独立条目（防匹配到自己）
                                        }
                                        .mapNotNull { c ->
                                            val score = SteamTitleMatcher.bestConfidence(
                                                queryTitles = listOf(title),
                                                candidateTitles = listOfNotNull(c.title, c.titleCN),
                                            )
                                            if (score >= SteamTitleMatcher.MIN_CONFIDENCE) c to score else null
                                        }
                                        .maxByOrNull { it.second }?.first
                                    if (best != null && steam.bindManually(best.subjectId, appId)) {
                                        newBindings[appId] = best.subjectId
                                    }
                                }
                            } catch (_: Exception) {
                                // 单条失败/超时静默（保持 steam 条目）
                            }
                        }
                    }.awaitAll()
                }
            }
        }
        if (requestId != trendingRequestId) return subjects
        if (newBindings.isEmpty()) {
            Log.i(TAG, "自动匹配 Bangumi：无新绑定（已绑定/未命中/超时）")
            return subjects
        }
        Log.i(TAG, "自动匹配 Bangumi：绑定 ${newBindings.size} 条（耗时 ${System.currentTimeMillis() - startTime}ms）")
        // 替换命中 appid 为 bangumi 词条
        val dao = subjectDao
        return subjects.map { subject ->
            val appId = subject.sourceKey?.removePrefix("steam:")?.toIntOrNull()
            val boundId = appId?.let { newBindings[it] }
            if (boundId != null) {
                runCatching {
                    withContext(Dispatchers.IO) { dao?.getById(boundId) }
                }.getOrNull() ?: subject
            } else {
                subject
            }
        }
    }

    fun clearResults() {
        searchJob?.cancel()
        suggestionInput.value = ""
        // 同步清空输入流（queryInput 与 suggestionInput 一致）：否则系统手势返回收起搜索后
        // query 仍残留上次搜索词 → TrendingSection 因 query.isBlank() 判据落空，
        // 趋势内容/错误态被"输入关键词搜索作品"空文案吞掉（发现页当季热门等不显示）
        queryInput.value = ""
        _uiState.update { s ->
            // 只清搜索态，保留趋势区数据（trendingMode/trendingResults/版本/滚动位置）——
            // 否则系统返回收起搜索后发现页趋势区（当季热门/历史排名）被重置为空
            s.copy(
                query = "",
                results = emptyList(),
                allResults = emptyList(),
                totalResults = 0,
                isSearching = false,
                error = null,
                suggestions = emptyList(),
                isSuggestionsLoading = false,
                suggestionNotice = null,
                isOfflineResults = false,
                isFilterPanelExpanded = false,
                filterSelections = emptyMap(),
            )
        }
    }
}

// ==================== 搜索链路的纯逻辑（第 6 轮 F1–F5） ====================
//
// 这些函数刻意不依赖 Android / ViewModel：搜索链路的语义（活跃查询、取消不写状态、
// 部分失败仍出结果、全失败才算失败、建议四态）必须能被单测钉死，
// 而 SubjectSearchViewModel 本身在 JVM 单测里无法实例化（viewModelScope 需要主线程）。

/**
 * 活跃查询（F1 + 复审修复）：必须与 UI 可见查询同源。
 *
 * - 作品模式：输入流优先（击键实时），回退 uiState.query；
 * - 人物模式：以 uiState.query 为准 —— 人物分支只写 uiState（不写 queryInput），
 *   若仍让输入流优先，切回作品模式时 setter 会拿到上一次作品搜索的残留旧词。
 */
internal fun resolveActiveQuery(
    input: String,
    stateQuery: String,
    isPersonSearch: Boolean = false,
): String = if (isPersonSearch) stateQuery else input.ifBlank { stateQuery }

/**
 * 模式切换后该用哪个查询（round 3 复审修复，纯函数）。
 *
 * 必须在**翻转 isPersonSearch 之前**取值：切换目标决定语义 ——
 * - 切到人物（enabled = true）：用「切换前」的作品查询（输入流优先，回退 uiState）；
 * - 切回作品（enabled = false）：用 uiState.query —— 人物模式只写 uiState，
 *   而 queryInput 里还留着更早的作品搜索词，用它会把人物的关键词顶掉。
 *
 * 与 [resolveActiveQuery] 的关系：后者按「当前」模式取值，用于模式内的 setter；
 * 本函数按「切换目标」取值，用于 setPersonSearch 与 setType（见 [typeSwitchPlan]）
 * 这两次性的模式翻转 —— 两处都必须在写 _uiState 之前调用。
 */
internal fun queryAfterModeSwitch(enabled: Boolean, input: String, stateQuery: String): String =
    if (enabled) input.ifBlank { stateQuery } else stateQuery

/** [typeSwitchPlan] 的结果：该用哪个查询 + 切换后的目标模式。 */
internal data class TypeSwitchPlan(val query: String, val isPersonSearch: Boolean)

/**
 * setType（点类型行）的取词决策（round 4 复审修复，纯函数，可单测）。
 *
 * 点类型行**必然退出人物模式**，因此取词方向是 [queryAfterModeSwitch] 的 enabled = false 分支：
 * 以 uiState.query（输入框里显示的那个词）为准，而不是 queryInput 里残留的上一次作品搜索词。
 *
 * 调用方（setType）必须**先调用本函数、再写 _uiState**：顺序反了就会重演
 * 「作品搜『巨人』→ 人物搜『花泽』→ 点类型行 → 用『巨人』出结果」这条 medium finding。
 */
internal fun typeSwitchPlan(input: String, stateQuery: String): TypeSwitchPlan = TypeSwitchPlan(
    query = queryAfterModeSwitch(enabled = false, input = input, stateQuery = stateQuery),
    isPersonSearch = false,
)

/**
 * 筛选/排序/R18 变更后该重查搜索还是只刷新趋势区（F1）。
 *
 * 判据只能是「有没有活跃查询」：改造前读的是 uiState.query，它不会被击键写入，
 * 于是有输入时改筛选也走刷新趋势 —— 用户观感就是「筛选没用」。
 */
internal fun shouldResearchOnFilterChange(activeQuery: String): Boolean = activeQuery.isNotBlank()

/** 榜单缓存查询指纹（R4）：nsfw + 排序 + 筛选标签。 */
internal fun rankingFingerprint(nsfw: Boolean, sort: String, tags: List<String>): String = buildString {
    append("nsfw=").append(nsfw)
    append(";sort=").append(sort)
    if (tags.isNotEmpty()) append(";tags=").append(tags.sorted().joinToString(","))
}

/** 远程取数结果（F2/R1）：失败要留下证据，不能悄悄变成空列表。 */
internal data class RemoteFetch<T>(
    val value: T? = null,
    val error: Throwable? = null,
) {
    val isFailure: Boolean get() = error != null
}

/**
 * 远程调用守卫（F2/R1）。
 *
 * 取消必须原样抛出：它既不是失败（不能写 error、不能清空结果），也不能被当成空结果。
 */
internal suspend fun <T> attemptRemote(block: suspend () -> T): RemoteFetch<T> =
    try {
        RemoteFetch(value = block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RemoteFetch(error = e)
    }

/**
 * 一路搜索的结果（F5）。
 *
 * @param error 该路的失败原因文案（null = 该路正常返回）
 * @param offline 该路结果来自本地离线兜底（远程失败但本地有命中）
 */
internal data class SearchAttempt(
    val results: List<SubjectEntity> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
    val offline: Boolean = false,
)

/** RemoteFetch<SearchOutcome> → SearchAttempt（含「远程失败」与「离线兜底」的区分）。 */
internal fun RemoteFetch<SearchOutcome>.toAttempt(): SearchAttempt {
    val failure = error
    if (failure != null) return SearchAttempt(error = networkFailureMessage(failure))
    val outcome = value ?: return SearchAttempt(error = "网络请求失败，请重试")
    return SearchAttempt(
        results = outcome.results,
        total = outcome.total,
        error = if (outcome.remoteFailed) (outcome.failureReason ?: "网络请求失败，请重试") else null,
        offline = outcome.offline,
    )
}

/** 多路结果合并去重（F5）：部分失败照常出结果，首次出现的条目优先。 */
internal fun mergeSearchAttempts(attempts: List<SearchAttempt>): List<SubjectEntity> =
    attempts.flatMap { it.results }.distinctBy { it.subjectId }

/**
 * 是否所有路都失败了（F5）。
 *
 * 有结果的路不算失败（离线兜底也算有结果）；空列表不算失败（没有任何请求发生）。
 */
internal fun allSearchAttemptsFailed(attempts: List<SearchAttempt>): Boolean =
    attempts.isNotEmpty() && attempts.all { it.error != null && it.results.isEmpty() }

/** 一次搜索的合并结果（F5，纯数据便于单测）。 */
internal data class SearchBatch(
    val attempts: List<SearchAttempt>,
    val total: Int,
    val results: List<SubjectEntity>,
) {
    /** 全部路都失败 → UI 必须显示「网络失败 + 重试」。 */
    val allFailed: Boolean get() = allSearchAttemptsFailed(attempts)

    /** 结果里有本地离线兜底成分 → UI 标注「离线结果」。 */
    val hasOffline: Boolean get() = attempts.any { it.offline }

    /** 第一条失败原因（UI 文案）。 */
    val firstFailure: String? get() = attempts.firstOrNull { it.error != null }?.error
}

/** 搜索建议的四态（F4）：未开启 / 无输入 / 无命中 / 网络失败。 */
internal enum class SuggestionState {
    DISABLED,
    NO_INPUT,
    NO_MATCH,
    NETWORK_FAILED,
    AVAILABLE,
}

/**
 * 判定建议四态（F4，纯函数，可单测）。
 */
internal fun suggestionStateOf(
    enabled: Boolean,
    query: String,
    itemCount: Int,
    remoteFailed: Boolean,
): SuggestionState = when {
    !enabled -> SuggestionState.DISABLED
    query.isBlank() -> SuggestionState.NO_INPUT
    itemCount > 0 -> SuggestionState.AVAILABLE
    remoteFailed -> SuggestionState.NETWORK_FAILED
    else -> SuggestionState.NO_MATCH
}

/** 四态 → UI 可显示的原因；不需要解释的状态返回 null。 */
internal fun suggestionNoticeOf(state: SuggestionState): String? = when (state) {
    SuggestionState.AVAILABLE, SuggestionState.NO_INPUT -> null
    SuggestionState.DISABLED -> "搜索建议已在设置中关闭"
    SuggestionState.NO_MATCH -> "没有匹配的建议，可直接回车搜索"
    SuggestionState.NETWORK_FAILED -> "建议不可用（网络请求失败），可直接回车搜索"
}

/**
 * 建议的本地部分（F4）：历史关键字命中 + 本地前缀/拼音命中。
 *
 * 纯函数，零网络：击键即应有建议响应，不能等远端。
 */
internal fun buildSuggestionItems(
    localSubjects: List<SubjectEntity>,
    historyMatches: List<String>,
): List<SuggestionItem> = buildList {
    historyMatches.take(3).forEach { add(SuggestionItem.HistoryKeyword(it)) }
    val historySet = historyMatches.toSet()
    localSubjects
        .filter { it.title !in historySet && it.titleCN !in historySet }
        .take(3)
        .forEach { add(SuggestionItem.LocalSubject(it)) }
}

/**
 * 建议合并去重（F4，纯函数）：本地（历史 + 前缀）在前，远程补足，按 id / 标题去重。
 */
internal fun mergeSuggestions(
    local: List<SuggestionItem>,
    remote: List<SubjectEntity>,
    history: List<String>,
    maxRemote: Int = 3,
): List<SuggestionItem> {
    val historySet = history.toSet()
    val localSubjects = local.filterIsInstance<SuggestionItem.LocalSubject>().map { it.subject }
    val localIds = localSubjects.map { it.subjectId }.toSet()
    val localTitles = localSubjects
        .flatMap { listOf(it.title, it.titleCN ?: "") }
        .filter { it.isNotBlank() }
        .toSet()
    val out = local.toMutableList()
    remote.asSequence()
        .filter { it.subjectId !in localIds }
        .filter { it.title !in historySet && (it.titleCN ?: "") !in historySet }
        .filter { it.title !in localTitles && (it.titleCN ?: "") !in localTitles }
        .distinctBy { it.subjectId }
        .take(maxRemote)
        .forEach { out += SuggestionItem.RemoteSuggestion(it) }
    return out
}

/** 关键词检索的请求指纹（F4）：参数不同就不能共用同一次请求。 */
internal fun keywordShareKey(
    query: String,
    type: Int?,
    tags: List<String>?,
    nsfw: Boolean?,
    sort: String?,
    rank: List<String>?,
    limit: Int?,
): String = buildString {
    append("kw:").append(query.trim())
    append(":t").append(type?.toString() ?: "all")
    append(":tag").append(tags?.sorted()?.joinToString(",") ?: "")
    append(":nsfw").append(nsfw == true)
    append(":sort").append(sort ?: "")
    append(":rank").append(rank?.joinToString(",") ?: "")
    append(":lim").append(limit ?: 0)
}

/**
 * 关键词检索的请求合并（第 6 轮 F4）。
 *
 * 建议（120ms 防抖）与正式搜索（300ms 防抖）打的是同一端点、同一关键词、同一组筛选参数，
 * 因此共用同一个 key：[AsyncSingleFlight] 把并发调用合并成一次请求，完成后在 [ttlMs]
 * 窗口内继续复用（300ms 的正式搜索通常直接命中建议刚拿到的那份结果）。
 *
 * 这样「建议」不再是一条独立的、会因 2s 超时而永远为空的链路。
 */
internal class SearchRequestShare(
    private val ttlMs: Long = 5_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private data class Cached(val key: String, val at: Long, val attempt: SearchAttempt)

    private val flight = AsyncSingleFlight()

    @Volatile
    private var cached: Cached? = null

    /** 运行/复用一次请求。同 key 并发 → 合并；ttl 内已完成 → 直接复用。 */
    suspend fun run(key: String, block: suspend () -> SearchAttempt): SearchAttempt {
        cached?.takeIf { it.key == key && clock() - it.at < ttlMs }?.let { return it.attempt }
        val attempt = flight.run(key) { block() }
        cached = Cached(key, clock(), attempt)
        return attempt
    }
}

class SubjectSearchViewModelFactory(
    private val subjectRepository: SubjectRepository,
    private val collectionRepository: CollectionRepository,
    private val searchHistoryDao: SearchHistoryDao? = null,
    private val steamRepository: SteamRepository? = null,
    private val subjectDao: SubjectDao? = null,
    private val settingsDataStore: com.otakup.niriko.data.settings.SettingsDataStore? = null,
    private val refreshCoordinator: RefreshCoordinator? = null,
    private val seasonalTrendingRepository: SeasonalTrendingRepository? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubjectSearchViewModel::class.java)) {
            return SubjectSearchViewModel(
                subjectRepository, collectionRepository, searchHistoryDao,
                steamRepository, subjectDao, settingsDataStore, refreshCoordinator,
                seasonalTrendingRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
