package com.otakup.niriko.ui.bottombar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.otakup.niriko.data.model.collection.LibraryFolderSummary
import com.otakup.niriko.ui.library.LibraryViewStyle

/**
 * B8：作品库悬浮按钮与页面内容共享的 UI 状态（最小提升集 + 由 bar 触发的对话框开关）。
 *
 * 为什么是「共享状态对象」而不是十几个回调：改造前这些 `var` 都住在 `LibraryScreenContent` 里，
 * 顶栏搬到 pager 外层后无法再访问；把状态本身提出来、页面用 `by controls.xxxState` 继续按
 * 原写法读写（写穿透到同一份 state），搬迁就不改动内容层的任何业务逻辑。
 */
@Stable
class LibraryControlsState(
    initialViewStyle: LibraryViewStyle = LibraryViewStyle.GRID,
    initialMultiSelect: Boolean = false,
    initialSelectedIds: Set<Long> = emptySet(),
) {
    // internal：同模块的 ui.screens 需要 `var x by controls.xxxState` 委托
    internal val viewStyleState = mutableStateOf(initialViewStyle)
    internal val multiSelectState = mutableStateOf(initialMultiSelect)
    internal val selectedIdsState = mutableStateOf(initialSelectedIds)
    internal val showSortMenuState = mutableStateOf(false)
    internal val showViewMenuState = mutableStateOf(false)
    internal val showTagFiltersState = mutableStateOf(false)
    internal val showBatchActionsState = mutableStateOf(false)
    internal val showDeleteConfirmState = mutableStateOf(false)
    internal val showCreateFolderState = mutableStateOf(false)
    internal val newFolderNameState = mutableStateOf("")
    internal val folderMenuTargetState = mutableStateOf<LibraryFolderSummary?>(null)
    internal val renameFolderTargetState = mutableStateOf<LibraryFolderSummary?>(null)
    internal val deleteFolderTargetState = mutableStateOf<LibraryFolderSummary?>(null)

    var viewStyle by viewStyleState
    var multiSelect by multiSelectState
    var selectedIds by selectedIdsState
    var showSortMenu by showSortMenuState
    var showViewMenu by showViewMenuState
    var showTagFilters by showTagFiltersState
    var showBatchActions by showBatchActionsState
    var showDeleteConfirm by showDeleteConfirmState
    var showCreateFolder by showCreateFolderState
    var newFolderName by newFolderNameState
    var folderMenuTarget by folderMenuTargetState
    var renameFolderTarget by renameFolderTargetState
    var deleteFolderTarget by deleteFolderTargetState

    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun exitSelection() {
        multiSelect = false
        selectedIds = emptySet()
    }

    companion object {
        /** 跨旋转保留：视图样式 / 多选态 / 选中集合（对话框开关是瞬时态，不存）。 */
        val Saver: Saver<LibraryControlsState, Any> = listSaver(
            save = { listOf(it.viewStyle.name, it.multiSelect, it.selectedIds.toList()) },
            restore = { saved ->
                LibraryControlsState(
                    initialViewStyle = LibraryViewStyle.entries
                        .firstOrNull { style -> style.name == saved[0] } ?: LibraryViewStyle.GRID,
                    initialMultiSelect = saved[1] as? Boolean ?: false,
                    initialSelectedIds = (saved[2] as? List<*>)
                        .orEmpty()
                        .mapNotNull { (it as? Number)?.toLong() }
                        .toSet(),
                )
            },
        )
    }
}

/**
 * B8：两页悬浮顶栏的**唯一外壳**。
 *
 * 外壳几何逐字沿用改造前两套顶栏的值：`fillMaxWidth + padding(top = inset + 8dp) + height 56dp
 * + zIndex(10)`，且不 clip（展开的搜索卡 200dp 靠溢出绘制）。外壳**不参与任何动画**，
 * 只有内部内容用 150ms 交叉淡入切换 —— 这就是「像 dock 一样，滑页时按钮 x/y 不动」。
 *
 * 具体内容由两个宿主文件提供（各自保住自己的 import 与既有手势/样式）：
 * - [com.otakup.niriko.ui.screens.LibraryTopBar]
 * - [com.otakup.niriko.ui.subject.DiscoverTopBar]
 */
@Composable
fun PagerTopBar(
    slot: TopBarSlot,
    windowTopInset: Dp,
    /** 与 Dock 共用同一个隐藏进度（0=显示，1=完全退出）。两者永远同步，不会「Dock 藏了按钮还挡着」。 */
    hideProgress: Float = 0f,
    modifier: Modifier = Modifier,
    libraryContent: @Composable () -> Unit,
    discoverContent: @Composable () -> Unit,
) {
    val progress = hideProgress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    // 顶栏从上沿退出：位移 = 56dp 本体 + 8dp 间隙（与几何里的 padding 对齐）。
    val exitShiftPx = with(density) { 64.dp.toPx() } * progress
    val fullyHidden = progress >= 0.999f
    Box(
        modifier = modifier
            // 用 offset（placement）而不是 translationY：命中区跟着一起移出屏幕。
            .offset { IntOffset(0, -exitShiftPx.roundToInt()) }
            .graphicsLayer { alpha = 1f - progress }
            .then(if (fullyHidden) Modifier.clearAndSetSemantics { } else Modifier)
            .fillMaxWidth()
            .padding(top = windowTopInset + 8.dp)
            .height(56.dp)
            .zIndex(10f),
    ) {
        // task-25：切页不做交叉淡入 —— 两页内容差异大，150ms 的交叉会闪成「两套按钮叠一下」。
        // 按 settledPage 立刻换内容，位移/alpha 仍只由上面的 hideProgress 驱动，容器不参与动画。
        when (slot) {
            TopBarSlot.LIBRARY -> libraryContent()
            TopBarSlot.DISCOVER -> discoverContent()
        }
    }
}
