package com.otakup.niriko.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.ui.bottombar.LibraryControlsState
import com.otakup.niriko.ui.common.IosStyleSearchComponent
import com.otakup.niriko.ui.common.LibraryStatusSearchRow
import com.otakup.niriko.ui.common.SearchPhase
import com.otakup.niriko.ui.common.SearchToolButton
import com.otakup.niriko.ui.common.SearchViewModel
import com.otakup.niriko.ui.components.NirikoMenu
import com.otakup.niriko.ui.components.NirikoMenuItem
import com.otakup.niriko.ui.library.FolderNavBar
import com.otakup.niriko.ui.library.LibraryViewStyle
import com.otakup.niriko.viewmodel.CollectionViewModel
import kotlin.math.roundToInt

/**
 * B8：作品库悬浮顶栏（左分区胶囊 + 右搜索按钮）。
 *
 * 从 `LibraryScreenContent` 原样搬出（几何、工具行逐字保留），由 MainPager 在
 * **pager 外层**统一渲染 —— 因此左右滑动翻页时这组按钮 x/y 全程不动，只有内容随页切换。
 *
 * 外壳几何（inset + 8dp / 56dp / zIndex）由 PagerTopBar 提供，这里只做内容与左右对齐。
 *
 * V-14：排序 / 视图切换两个菜单从 M3 DropdownMenu（Popup，安卓原生 8dp 方角面）换成
 * 共享的 [NirikoMenu]（24dp 玻璃面、从锚点缩放、带回退场）。Popup 是独立窗口，kyant 的
 * 玻璃采样会以弹窗自身为原点、与菜单真实位置错开（B5 记录过同一个坑），所以菜单必须和
 * 正文画在同一窗口 —— 这也顺带解决了「菜单长相与详情页不一致」。
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

    // V-14：锚点矩形（根坐标）。触发按钮自己量 —— 菜单不要再猜位置。
    var sortAnchor by remember { mutableStateOf<Rect?>(null) }
    var viewAnchor by remember { mutableStateOf<Rect?>(null) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    // NirikoMenu 的宿主是一层 Layout，用 constraints.maxWidth/maxHeight 当 viewport。
    // 本组件被 PagerTopBar 限制在 56dp 高，直接放菜单会被裁成一条，所以用
    // requiredSize(屏幕宽高) + 负 offset 把宿主铺满窗口并把原点挪到窗口左上角
    // （与 ui/common/VerticalTopSelector.kt:145-147 同一招，那里也是 56dp 顶栏里的浮层）。
    val configuration = LocalConfiguration.current
    val screenWidth = with(configuration) { screenWidthDp.dp }
    val screenHeight = with(configuration) { screenHeightDp.dp }

    Box(
        Modifier.fillMaxWidth().height(56.dp)
            .onGloballyPositioned { boxOrigin = it.positionInRoot() },
    ) {
        if (searchVm.phase == SearchPhase.COLLAPSED) {
            FolderNavBar(
                folders = folders,
                selectedFolderId = state.filter.folderId,
                onSelectFolder = viewModel::selectFolder,
                onCreateFolder = {
                    controls.newFolderName = ""
                    controls.showCreateFolder = true
                },
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
                onSearchSubmit = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                },
                onGestureLockChange = { gestureLock.value = it },
                placeholder = "搜索收藏…",
                filterTitle = "收藏状态",
                tools = {
                    // 只有锚点，菜单本体放在外层（见下方）—— 工具行会随搜索展开/收起被摘掉，
                    // 菜单若挂在这里，收起动画期间会连人带菜单一起消失。
                    Box(Modifier.onGloballyPositioned { sortAnchor = it.boundsInRoot() }) {
                        SearchToolButton("排序：" + state.filter.sort.label, { controls.showSortMenu = true }) {
                            Icon(Icons.Default.FilterList, "排序：" + state.filter.sort.label)
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
                            tint = if (controls.multiSelect) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    if (controls.multiSelect) {
                        SearchToolButton("批量管理：已选 " + controls.selectedIds.size, { controls.showBatchActions = true }) {
                            Icon(Icons.Default.Collections, "批量管理：已选 " + controls.selectedIds.size)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Box(Modifier.onGloballyPositioned { viewAnchor = it.boundsInRoot() }) {
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
        // V-14：两个菜单作为根 Box 的最后两个子节点（与正文同级）。不再写 `if (expanded)` ——
        // NirikoMenu 内部用 keepAlive 播完退场才卸载，外面再包 if 会把退场动画砍掉。
        NirikoMenu(
            expanded = controls.showSortMenu,
            anchorInRoot = sortAnchor,
            onDismissRequest = { controls.showSortMenu = false },
            modifier = Modifier
                .requiredSize(screenWidth, screenHeight)
                .offset { IntOffset(-boxOrigin.x.roundToInt(), -boxOrigin.y.roundToInt()) }
                .zIndex(20f),
        ) {
            SortOrder.entries.forEach { sort ->
                NirikoMenuItem(
                    text = sort.label,
                    onClick = {
                        viewModel.updateFilter { it.copy(sort = sort) }
                        controls.showSortMenu = false
                    },
                    // 当前排序改用前置勾（NirikoMenuItem 只有前后统一的 icon 槽）：
                    // 「已选中」仍然可见，只是从尾部移到行首，与详情页菜单一致。
                    icon = if (state.filter.sort == sort) Icons.Default.Check else null,
                )
            }
        }
        NirikoMenu(
            expanded = controls.showViewMenu,
            anchorInRoot = viewAnchor,
            onDismissRequest = { controls.showViewMenu = false },
            modifier = Modifier
                .requiredSize(screenWidth, screenHeight)
                .offset { IntOffset(-boxOrigin.x.roundToInt(), -boxOrigin.y.roundToInt()) }
                .zIndex(20f),
        ) {
            LibraryViewStyle.entries.forEach { style ->
                NirikoMenuItem(
                    text = style.label,
                    onClick = {
                        controls.viewStyle = style
                        controls.showViewMenu = false
                    },
                    icon = if (style == controls.viewStyle) Icons.Default.Check else null,
                )
            }
        }
    }
}
