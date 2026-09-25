package com.otakup.niriko.data.model.search

import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.discover.BrowseFilter
import com.otakup.niriko.data.model.SuggestionItem
import com.otakup.niriko.data.model.search.DiscoveryLayout

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
    /**
     * 发现页布局（第 5 轮 D28）：卡片列表 / 宫格首页。
     *
     * 由「AppSettings.discoveryLayout」驱动，用户可在顶栏一键切换。
     */
    val discoveryLayout: DiscoveryLayout = DiscoveryLayout.CARD,
    val trendingResults: List<SubjectEntity> = emptyList(),

    /**
     * 「历史排名」的筛选器（第 6 轮 §6.3）。
     *
     * Bangumi-master「找条目」的 9 个维度（地区 / 版本 / 年份 / 季度 / 状态 / 类型 /
     * 制作 / 排序 / 收藏）+ 我们保留的三项增强（评分区间 / 排名区间 / NSFW）都在
     * [BrowseFilter] 里；**类型维度仍由顶部类型行驱动**（filter.type 只是它的镜像）。
     */
    val browseFilter: BrowseFilter = BrowseFilter(),
    /** 「历史排名 · 全部」已加载的候选池页码（1 起）：加载更多时按候选来源类型续取 offset。 */
    val browsePage: Int = 1,
    /** 候选池条数（已过筛选的候选，用于「共 N 条 / 候选池 N 条」）。 */
    val browsePoolSize: Int = 0,
    /**
     * 当季热门的候选总数（「候选 N 部」）。
     *
     * 第 6 轮起展示条数是人气选取后的 ≤30，这里的 N 是**质量门槛之前**的候选数，
     * 让用户看出「这个季度有多少部、我们展示了前 30」。
     */
    val seasonalTotalCandidates: Int = 0,

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
    /**
     * 搜索建议「没有内容」时的原因说明（第 6 轮 F4 四态）。
     *
     * 未开启 / 无输入 / 无命中 / 网络失败是四件不同的事，UI 必须能说出是哪一件；
     * null = 无需说明（有建议，或不输入时）。
     */
    val suggestionNotice: String? = null,
    /**
     * 当前结果是否来自**本地离线兜底**（第 6 轮 F5）。
     *
     * 远程失败但本地缓存有命中时会返回结果；这种结果必须被标注，
     * 否则用户会以为是刚拉到的新数据。
     */
    val isOfflineResults: Boolean = false,
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
    // 第 6 轮 §6.4：availableTags（「找条目」的标签候选）随该模块一起删除。
    // 「历史排名」筛选器的类型维度现在是 BrowseFilter.CONTENT_TAGS（Bangumi-master 的 46 词表）。
)
