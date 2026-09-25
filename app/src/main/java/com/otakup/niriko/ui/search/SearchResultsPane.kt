package com.otakup.niriko.ui.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.filter.FilterDimension
import com.otakup.niriko.data.model.search.SearchSortMode
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.probe.ProbeResult
import com.otakup.niriko.data.probe.SearchChainProbe
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.subject.PersonSearchCard
import com.otakup.niriko.ui.subject.SearchFilterPanel
import com.otakup.niriko.ui.subject.SearchHistorySection
import com.otakup.niriko.ui.subject.SubjectResultCard
import com.otakup.niriko.ui.subject.SuggestionDropdown
import com.otakup.niriko.util.toCardDisplayModel
import kotlinx.coroutines.launch

/**
 * 搜索展开态的内容区（历史 / 建议 / 结果）。
 * 从旧 SearchExpandedPane 中拆出，专注数据呈现；交互（状态/类型/模式）由 SearchContainer 管理。
 *
 * 第 6 轮 F3 的分支重排（关键修复）：
 * - 改造前 suggestions / isSuggestionsLoading 两个分支排在**结果之前**，只要建议有内容或还在
 *   加载，搜索结果就永远不显示；
 * - 现在**结果分支只依赖 results**，建议排在结果列表首行（或各空态顶部），二者真正同时可见；
 * - 失败态用 state.error（网络失败 + 重试），与「确实没有结果」分开；
 *   离线兜底结果仍然展示，但顶部标明来源。
 *
 * 复审修复（建议块不再悬浮）：
 * - 之前建议是覆盖在内容之上的浮层，会挡住首屏的筛选面板 —— 既看不清也算不上「同时可见」；
 * - 现在建议是**内容流里的第一块**（结果态即列表首项），不遮挡任何内容，并带「收起」按钮，
 *   把 onClearSuggestions 接到明确的关闭动作上。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchResultsPane(
    state: SubjectSearchUiState,
    query: String,
    listState: LazyListState,
    onHistoryClick: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    filterDimensions: List<FilterDimension>,
    onSetSortMode: (SearchSortMode) -> Unit,
    onSetFilter: (dimensionKey: String, optionLabel: String) -> Unit,
    onSetNsfw: (Boolean) -> Unit,
    onToggleFilterPanel: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    /** 顶部避让距离（浮动搜索卡片高度 + 间距），由调用方按菜单状态传入；默认 72dp 兼容旧调用。 */
    topInset: Dp = 72.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            query.isBlank() -> {
                // 未输入：搜索历史 + 搜索链路自检入口（F9；刻意只放空态，不动设置页）
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = topInset)) {
                    if (state.searchHistory.isNotEmpty()) {
                        SearchHistorySection(
                            history = state.searchHistory,
                            onHistoryClick = onHistoryClick,
                            onDeleteItem = onDeleteHistoryItem,
                            onClearAll = onClearSearchHistory,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "输入关键词搜索作品",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SearchChainDiagnostics()
                }
            }

            state.isPersonSearch && state.personResults.isNotEmpty() -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            text = "共 ${state.personResults.size} 位人物",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    items(state.personResults, key = { it.id }) { person ->
                        PersonSearchCard(
                            person = person,
                            onClick = { onPersonClick(person.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            state.isPersonSearch -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "未找到相关人物",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "试试中文名、别名或完整姓名",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // F3：结果分支**只依赖 results** —— 建议存在或建议加载中都不再阻断结果渲染。
            // 复审修复：建议作为列表**首项**（不是覆盖层），与结果同时可见且不遮挡筛选面板。
            state.results.isNotEmpty() -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = topInset, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.suggestions.isNotEmpty() || state.isSuggestionsLoading) {
                        item(key = "suggestions") {
                            SuggestionBlock(
                                state = state,
                                onHistoryClick = onHistoryClick,
                                onSubjectClick = onSubjectClick,
                                onClearSuggestions = onClearSuggestions,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    item(key = "filterHeader") {
                        SearchHeaderBlock(
                            state = state,
                            query = query,
                            filterDimensions = filterDimensions,
                            onSetSortMode = onSetSortMode,
                            onSetFilter = onSetFilter,
                            onSetNsfw = onSetNsfw,
                            onToggleFilterPanel = onToggleFilterPanel,
                            showTotal = true,
                        )
                    }
                    // F5：远程失败但本地有命中 → 结果照常展示，顶部标明「离线结果」
                    if (state.isOfflineResults) {
                        item(key = "offlineNotice") {
                            Text(
                                text = state.error ?: "网络不可用，以下为本地离线结果",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                            )
                        }
                    }
                    items(state.results, key = { it.subjectId }) { subject ->
                        SubjectResultCard(
                            model = subject.toCardDisplayModel(state.steamGames[subject.subjectId]),
                            isInCollection = subject.subjectId in state.collectedSubjectIds,
                            onClick = { onSubjectClick(subject.subjectId) },
                            sharedElementKey = "cover_${subject.subjectId}",
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem()
                                .animateContentSize(animationSpec = tween(durationMillis = 300)),
                        )
                    }
                }
            }

            state.isSearching -> {
                Column(Modifier.fillMaxSize().padding(top = topInset)) {
                    SuggestionBlock(
                        state = state,
                        onHistoryClick = onHistoryClick,
                        onSubjectClick = onSubjectClick,
                        onClearSuggestions = onClearSuggestions,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // F5：远程全部失败 → 网络失败 + 重试（而不是「未找到相关作品」）
            state.error != null -> {
                Column(Modifier.fillMaxSize().padding(top = topInset)) {
                    SuggestionBlock(
                        state = state,
                        onHistoryClick = onHistoryClick,
                        onSubjectClick = onSubjectClick,
                        onClearSuggestions = onClearSuggestions,
                    )
                    SearchHeaderBlock(
                        state = state,
                        query = query,
                        filterDimensions = filterDimensions,
                        onSetSortMode = onSetSortMode,
                        onSetFilter = onSetFilter,
                        onSetNsfw = onSetNsfw,
                        onToggleFilterPanel = onToggleFilterPanel,
                        showTotal = false,
                    )
                    ErrorContent(
                        message = state.error,
                        onRetry = onRetry,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            else -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = topInset),
                ) {
                    SuggestionBlock(
                        state = state,
                        onHistoryClick = onHistoryClick,
                        onSubjectClick = onSubjectClick,
                        onClearSuggestions = onClearSuggestions,
                    )
                    SearchHeaderBlock(
                        state = state,
                        query = query,
                        filterDimensions = filterDimensions,
                        onSetSortMode = onSetSortMode,
                        onSetFilter = onSetFilter,
                        onSetNsfw = onSetNsfw,
                        onToggleFilterPanel = onToggleFilterPanel,
                        showTotal = false,
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "未找到相关作品",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "试试原名、别名或更短的关键词",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // F4 四态：没有建议时把原因说出来（未开启 / 无命中 / 网络失败）
                        state.suggestionNotice?.let { notice ->
                            Text(
                                text = notice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 建议块（复审修复）：排在内容流里（结果态是列表首项），不做覆盖层。
 *
 * 这样既保证「建议 + 结果」真正同时可见、不遮挡筛选面板，也提供了一个明确的关闭动作
 *（右上角「收起」接 [onClearSuggestions]）。
 */
@Composable
private fun SuggestionBlock(
    state: SubjectSearchUiState,
    onHistoryClick: (String) -> Unit,
    onSubjectClick: (Long) -> Unit,
    onClearSuggestions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.suggestions.isEmpty() && !state.isSuggestionsLoading) return

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "搜索建议",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClearSuggestions) {
                Text(
                    text = "收起",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (state.suggestions.isNotEmpty()) {
            SuggestionDropdown(
                suggestions = state.suggestions,
                onHistoryClick = onHistoryClick,
                onSubjectClick = onSubjectClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (state.isSuggestionsLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * 结果计数 + 筛选面板（结果态作为列表首项，空态/失败态作为顶部固定块）。
 *
 * 抽出来是因为：改造前筛选面板只存在于「结果非空」分支里，一旦搜索没有结果，
 * 用户连改排序/筛选的入口都没有了 —— 而「改筛选要重查」正是本轮 F1 的修复点。
 */
@Composable
private fun SearchHeaderBlock(
    state: SubjectSearchUiState,
    query: String,
    filterDimensions: List<FilterDimension>,
    onSetSortMode: (SearchSortMode) -> Unit,
    onSetFilter: (dimensionKey: String, optionLabel: String) -> Unit,
    onSetNsfw: (Boolean) -> Unit,
    onToggleFilterPanel: () -> Unit,
    showTotal: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (showTotal) {
            val totalInfo = if (state.selectedType == null) {
                "共 ${state.totalResults} 条结果"
            } else {
                "共 ${state.totalResults} 条结果 · 当前 ${state.results.size} 条"
            }
            Text(
                text = totalInfo,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        if (!state.isPersonSearch && query.isNotBlank()) {
            SearchFilterPanel(
                dimensions = filterDimensions,
                filterSelections = state.filterSelections,
                sortMode = state.sortMode,
                nsfwEnabled = state.nsfwEnabled,
                isExpanded = state.isFilterPanelExpanded,
                hasActiveFilters = state.nsfwEnabled || state.sortMode != SearchSortMode.HEAT || state.filterSelections.isNotEmpty(),
                onToggleExpand = onToggleFilterPanel,
                onSortModeSelected = onSetSortMode,
                onFilterSelected = onSetFilter,
                onNsfwToggled = onSetNsfw,
            )
        }
    }
}

/**
 * 搜索链路自检（第 6 轮 F9）。
 *
 * 只放在**搜索空态**：用户看到「搜索完全没内容」时就在这个界面，点一下即可依次探测三层
 * （GET 榜单 / POST 检索 / GET 旧版兜底），逐条显示 HTTP 状态码、耗时、返回条数、异常原文。
 * 不写任何状态到 uiState，所以不受搜索状态机影响，也不会与设置页改造冲突。
 */
@Composable
private fun SearchChainDiagnostics() {
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ProbeResult>?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(
            enabled = !running,
            onClick = {
                running = true
                scope.launch {
                    results = runCatching { SearchChainProbe.run() }.getOrDefault(emptyList())
                    running = false
                }
            },
        ) {
            Text(if (running) "诊断中…" else "诊断搜索链路")
        }
        Text(
            text = "依次探测：GET 榜单 / POST 检索 / GET 旧版兜底",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        results?.forEach { result ->
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(
                    text = result.endpointName,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = result.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                result.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (result.bodyPreview.isNotBlank()) {
                    Text(
                        text = result.bodyPreview.take(200),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
