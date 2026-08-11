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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.filter.FilterDimension
import com.otakup.niriko.data.model.search.SearchSortMode
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.subject.PersonSearchCard
import com.otakup.niriko.ui.subject.SearchFilterPanel
import com.otakup.niriko.ui.subject.SearchHistorySection
import com.otakup.niriko.ui.subject.SubjectResultCard
import com.otakup.niriko.ui.subject.SuggestionDropdown
import com.otakup.niriko.util.toCardDisplayModel

/**
 * 搜索展开态的内容区（历史 / 建议 / 结果）。
 * 从旧 SearchExpandedPane 中拆出，专注数据呈现；交互（状态/类型/模式）由 SearchContainer 管理。
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
                // 未输入：搜索历史（顶部按 topInset 避让浮动搜索卡片）
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = topInset)) {
                    if (state.searchHistory.isNotEmpty()) {
                        SearchHistorySection(
                            history = state.searchHistory,
                            onHistoryClick = onHistoryClick,
                            onDeleteItem = onDeleteHistoryItem,
                            onClearAll = onClearSearchHistory,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "输入关键词搜索作品",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            state.suggestions.isNotEmpty() -> {
                // 有输入未提交：搜索建议（顶部按 topInset 避让）
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = topInset)) {
                    SuggestionDropdown(
                        suggestions = state.suggestions,
                        onHistoryClick = onHistoryClick,
                        onSubjectClick = onSubjectClick,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            state.isSearching -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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

            state.error != null && query.isNotBlank() -> {
                ErrorContent(
                    message = state.error ?: "",
                    onRetry = onRetry,
                )
            }

            state.results.isNotEmpty() && state.allResults.isNotEmpty() -> {
                // 作品结果：筛选部件作为列表首个 item，随信息流滚动向上隐藏
                val totalInfo = if (state.selectedType == null)
                    "共 ${state.totalResults} 条结果"
                else
                    "共 ${state.totalResults} 条结果 · 当前 ${state.results.size} 条"
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = topInset, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "filterHeader") {
                        Column {
                            Text(
                                text = totalInfo,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                            )
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

            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    }
                }
            }
        }
    }
}
