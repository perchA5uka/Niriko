package com.otakup.niriko.data.model.search

import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SuggestionItem
import com.otakup.niriko.data.filter.FilterDimension

/**
 * 作品搜索 UI 快照。
 */
data class SubjectSearchUiState(
    val query: String = "",
    val results: List<SubjectEntity> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val selectedType: Int? = null,
    /** 是否处于"人物"搜索模式（声优/导演等）。 */
    val isPersonSearch: Boolean = false,
    /** 人物搜索结果（含代表作品小图）。 */
    val personResults: List<com.otakup.niriko.data.remote.PersonDetailInfo> = emptyList(),
    val collectedSubjectIds: Set<Long> = emptySet(),
    val trendingMode: TrendingMode = TrendingMode.SEASONAL,
    val trendingResults: List<SubjectEntity> = emptyList(),
    /** 趋势列表数据版本号：每次列表替换/加载时 +1（用于 UI 恢复滚动位置）。 */
    val trendingVersion: Int = 0,
    /** 当前类型上次浏览的 firstVisibleItemIndex（像素级位置，配 trendingScrollOffset）。 */
    val trendingScrollIndex: Int = 0,
    /** 当前类型上次浏览的 scrollOffset（像素偏移）。 */
    val trendingScrollOffset: Int = 0,
    val isLoadingTrending: Boolean = false,
    val isLoadingMore: Boolean = false,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val searchHistory: List<String> = emptyList(),
    val suggestions: List<SuggestionItem> = emptyList(),
    val isSuggestionsLoading: Boolean = false,
    // 高级筛选
    val sortMode: SearchSortMode = SearchSortMode.HEAT,
    val nsfwEnabled: Boolean = false,
    val isFilterPanelExpanded: Boolean = false,
    /** 维度筛选选中状态：dimensionKey → [选中选项的 label]。SINGLE 模式最多 1 项，MULTI 可多项。 */
    val filterSelections: Map<String, List<String>> = emptyMap(),
    /** 最近一次全类型搜索的缓存结果（用于客户端类型过滤和计数）。 */
    val allResults: List<SubjectEntity> = emptyList(),
    /** API 返回的总结果数。 */
    val totalResults: Int = 0,
    /** Steam 补充数据（已绑定游戏，subjectId → 扩展数据）。 */
    val steamGames: Map<Long, SteamGameEntity> = emptyMap(),
)
