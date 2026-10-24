package com.otakup.niriko.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.ui.bottombar.LibraryControlsState
import com.otakup.niriko.ui.common.IosStyleSearchComponent
import com.otakup.niriko.ui.common.LibraryStatusSearchRow
import com.otakup.niriko.ui.common.SearchPhase
import com.otakup.niriko.ui.common.SearchToolButton
import com.otakup.niriko.ui.common.SearchViewModel
import com.otakup.niriko.ui.components.searchGlassSurface
import com.otakup.niriko.ui.library.FolderNavBar
import com.otakup.niriko.ui.library.LibraryViewStyle
import com.otakup.niriko.viewmodel.CollectionViewModel

/**
 * B8：作品库悬浮顶栏（左分区胶囊 + 右搜索按钮）。
 *
 * 从 `LibraryScreenContent` 原样搬出（几何、工具行、下拉菜单逐字保留），由 MainPager 在
 * **pager 外层**统一渲染 —— 因此左右滑动翻页时这组按钮 x/y 全程不动，只有内容随页切换。
 *
 * 外壳几何（inset + 8dp / 56dp / zIndex）由 PagerTopBar 提供，这里只做内容与左右对齐。
 */
@Composable
fun LibraryTopBar(
    viewModel: CollectionViewModel,
    controls: LibraryControlsState,
    searchVm: SearchViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val statusCounts by viewModel.statusCounts.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val gestureLock = com.otakup.niriko.navigation.LocalSearchGestureLock.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Box(Modifier.fillMaxWidth().height(56.dp)) {
        if (searchVm.phase == SearchPhase.COLLAPSED) {
            FolderNavBar(
                folders = folders,
                selectedFolderId = state.filter.folderId,
                onSelectFolder = viewModel::selectFolder,
                onCreateFolder = { controls.newFolderName = ""; controls.showCreateFolder = true },
                onFolderLongPress = { folder -> controls.folderMenuTarget = folder },
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(start = 16.dp, end = 88.dp),
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).height(56.dp).padding(end = 16.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            IosStyleSearchComponent(
                viewModel = searchVm,
                onModeSelected = {},
                onTypeSelected = {},
                onQueryChanged = { query ->
                    searchVm.syncQuery(query)
                    viewModel.updateFilter { it.copy(keyword = query) }
                },
                onSearchSubmit = { focusManager.clearFocus(); keyboard?.hide() },
                onGestureLockChange = { gestureLock.value = it },
                placeholder = "搜索收藏…",
                filterTitle = "收藏状态",
                tools = {
                    Box {
                        SearchToolButton("排序：" + state.filter.sort.label, { controls.showSortMenu = true }) {
                            Icon(Icons.Default.FilterList, "排序：" + state.filter.sort.label)
                        }
                        DropdownMenu(
                            expanded = controls.showSortMenu,
                            onDismissRequest = { controls.showSortMenu = false },
                            modifier = Modifier.searchGlassSurface(RoundedCornerShape(20.dp)),
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            shadowElevation = 0.dp,
                        ) {
                            SortOrder.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    leadingIcon = { if (state.filter.sort == sort) Icon(Icons.Default.Check, "已选中") },
                                    onClick = {
                                        viewModel.updateFilter { it.copy(sort = sort) }
                                        controls.showSortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    SearchToolButton("个人标签筛选", { controls.showTagFilters = true }) {
                        Icon(Icons.Default.Label, "个人标签筛选")
                    }
                    SearchToolButton(
                        if (controls.multiSelect) "退出多选" else "多选管理",
                        {
                            controls.multiSelect = !controls.multiSelect
                            if (!controls.multiSelect) controls.selectedIds = emptySet()
                        },
                    ) {
                        Icon(
                            Icons.Default.Check,
                            if (controls.multiSelect) "退出多选" else "多选管理",
                            tint = if (controls.multiSelect) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (controls.multiSelect) {
                        SearchToolButton("批量管理：已选 " + controls.selectedIds.size, { controls.showBatchActions = true }) {
                            Icon(Icons.Default.Collections, "批量管理：已选 " + controls.selectedIds.size)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Box {
                        SearchToolButton("切换视图：" + controls.viewStyle.label, { controls.showViewMenu = true }) {
                            Icon(
                                when (controls.viewStyle) {
                                    LibraryViewStyle.GRID -> Icons.Filled.Apps
                                    LibraryViewStyle.GALLERY -> Icons.Filled.Collections
                                    LibraryViewStyle.LIST -> Icons.Filled.List
                                },
                                "切换视图：" + controls.viewStyle.label,
                            )
                        }
                        DropdownMenu(
                            expanded = controls.showViewMenu,
                            onDismissRequest = { controls.showViewMenu = false },
                            modifier = Modifier.searchGlassSurface(RoundedCornerShape(20.dp)),
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            shadowElevation = 0.dp,
                        ) {
                            LibraryViewStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = { Text(style.label) },
                                    leadingIcon = { if (style == controls.viewStyle) Icon(Icons.Default.Check, "已选中") },
                                    onClick = { controls.viewStyle = style; controls.showViewMenu = false },
                                )
                            }
                        }
                    }
                },
                filters = {
                    LibraryStatusSearchRow(
                        counts = statusCounts,
                        selectedStatus = state.filter.status,
                        onStatusSelected = { status -> viewModel.updateFilter { it.copy(status = status) } },
                    )
                },
            )
        }
    }
}
