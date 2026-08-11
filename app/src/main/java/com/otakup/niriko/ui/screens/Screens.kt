@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.ui.components.UniversalSearchBar
import com.otakup.niriko.ui.library.CollectionCard
import com.otakup.niriko.ui.library.CollectionDashboard
import com.otakup.niriko.ui.library.PersonCollectionSection
import com.otakup.niriko.data.model.collection.CollectionFilter
import com.otakup.niriko.data.model.collection.CollectionListUiState
import com.otakup.niriko.viewmodel.CollectionViewModel
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.model.collection.TagInfo

/** 作品库 Tab。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    viewModel: CollectionViewModel,
    onNavigateToSearch: () -> Unit = {},
    onSubjectClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    // 观察人物收藏（独立于作品收藏）
    val context = androidx.compose.ui.platform.LocalContext.current
    val personCollections by context.nirikoApp.personCollectionDao.observeAll()
        .collectAsState(initial = emptyList())

    LibraryScreenContent(
        state = state,
        personCollections = personCollections,
        onQueryChanged = { query -> viewModel.updateFilter { it.copy(keyword = query) } },
        onStatusFilter = { status -> viewModel.updateFilter { it.copy(status = status) } },
        onSortChange = { sort -> viewModel.updateFilter { it.copy(sort = sort) } },
        onTagFilter = { tags -> viewModel.updateFilter { it.copy(selectedTags = tags) } },
        onSubjectClick = onSubjectClick,
        onPersonClick = onPersonClick,
        onNavigateToSearch = onNavigateToSearch,
        onStatusQuickSet = viewModel::updateStatus,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryScreenContent(
    state: CollectionListUiState,
    personCollections: List<com.otakup.niriko.data.local.entity.PersonCollectionEntity>,
    onQueryChanged: (String) -> Unit,
    onStatusFilter: (WatchStatus?) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onTagFilter: (Set<String>) -> Unit = {},
    onSubjectClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onStatusQuickSet: (Long, WatchStatus) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showTagFilters by remember { mutableStateOf(false) }
    var localQuery by rememberSaveable(state.filter.keyword) { mutableStateOf(state.filter.keyword) }

    Column(modifier = modifier.fillMaxSize()) {
        // 搜索栏
        UniversalSearchBar(
            query = localQuery,
            onQueryChange = { localQuery = it; onQueryChanged(it) },
            placeholder = "搜索收藏…",
        )
        Spacer(Modifier.height(4.dp))

        // Dashboard
        CollectionDashboard(stats = state.stats)
        Spacer(Modifier.height(4.dp))

        // 人物收藏分区（独立于作品收藏，无观看状态）
        if (personCollections.isNotEmpty() && state.filter.keyword.isBlank() && state.filter.status == null) {
            PersonCollectionSection(
                persons = personCollections,
                onPersonClick = onPersonClick,
            )
            Spacer(Modifier.height(4.dp))
        }

        // 标签筛选（默认折叠，点"标签"展开；有选中标签时自动展开）
        if (state.availableTags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.filter.selectedTags.isNotEmpty(),
                    onClick = { showTagFilters = !showTagFilters },
                    label = {
                        Text(
                            if (state.filter.selectedTags.isNotEmpty())
                                "标签 (${state.filter.selectedTags.size})"
                            else "标签"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (showTagFilters || state.filter.selectedTags.isNotEmpty())
                                Icons.Filled.ExpandMore else Icons.Filled.Label,
                            contentDescription = "标签筛选",
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
            AnimatedVisibility(visible = showTagFilters || state.filter.selectedTags.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    TagFilterBar(
                        tags = state.availableTags,
                        selectedTags = state.filter.selectedTags,
                        onTagClick = { tag ->
                            val newSet = if (tag in state.filter.selectedTags)
                                state.filter.selectedTags - tag
                            else
                                state.filter.selectedTags + tag
                            onTagFilter(newSet)
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // 筛选行：状态芯片 + 排序
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.filter.status == null,
                onClick = { onStatusFilter(null) },
                label = { Text("全部") },
            )

            WatchStatus.entries.forEach { status ->
                FilterChip(
                    selected = state.filter.status == status,
                    onClick = { onStatusFilter(status) },
                    label = { Text(status.label) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // 排序按钮
            Box {
                FilterChip(
                    selected = false,
                    onClick = { showSortMenu = true },
                    label = { Text(state.filter.sort.label) },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = "排序", modifier = Modifier.size(16.dp)) },
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    SortOrder.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            onClick = { onSortChange(sort); showSortMenu = false },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // 列表 / 空状态
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.filter.keyword.isNotBlank()) {
                        Icon(Icons.Outlined.SearchOff, contentDescription = "无搜索结果", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("没有找到匹配的作品", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("试试其他关键词", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = "暂无收藏", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        if (state.filter.status == null) {
                            Text("还没有收藏作品", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("探索你喜欢的动画，它们会出现在这里", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onNavigateToSearch) { Text("开始探索") }
                        } else {
                            Text("没有符合条件的收藏", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.collection.id }) { item ->
                    var showStatusMenu by remember { mutableStateOf(false) }
                    val haptic = LocalHapticFeedback.current
                    Box(Modifier.animateItem()) {
                        CollectionCard(
                            subject = item.subject,
                            status = item.collection.status,
                            watchedEpisodes = item.collection.watchedEpisodes,
                            totalEpisodes = item.subject.totalEpisodes,
                            myRating = item.collection.rating,
                            updateTime = item.collection.updateTime,
                            personalTags = item.collection.personalTags,
                            steam = state.steamGames[item.subject.subjectId],
                            onClick = { onSubjectClick(item.subject.subjectId) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showStatusMenu = true
                            },
                            sharedElementKey = "cover_${item.subject.subjectId}",
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier.animateContentSize(animationSpec = tween(durationMillis = 300)),
                        )
                        // 长按快速标记状态
                        DropdownMenu(
                            expanded = showStatusMenu,
                            onDismissRequest = { showStatusMenu = false },
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            WatchStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.label) },
                                    trailingIcon = {
                                        if (item.collection.status == status) Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        showStatusMenu = false
                                        onStatusQuickSet(item.subject.subjectId, status)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagFilterBar(
    tags: List<TagInfo>,
    selectedTags: Set<String>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tagInfo ->
            FilterChip(
                selected = tagInfo.name in selectedTags,
                onClick = { onTagClick(tagInfo.name) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tagInfo.name, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Text("${tagInfo.count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: com.otakup.niriko.viewmodel.StatsViewModel? = null,
    onSubjectClick: (Long) -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    if (viewModel != null) {
        com.otakup.niriko.ui.stats.StatsScreen(
            viewModel = viewModel,
            onSubjectClick = onSubjectClick,
            onNavigateToDiscover = onNavigateToDiscover,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("统计", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    NirikoTheme {
        LibraryScreenContent(
            state = CollectionListUiState(),
            personCollections = emptyList(),
            onQueryChanged = {},
            onStatusFilter = {},
            onSortChange = {},
            onSubjectClick = {},
        )
    }
}


