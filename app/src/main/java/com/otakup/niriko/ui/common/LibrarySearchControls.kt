package com.otakup.niriko.ui.common

import androidx.compose.runtime.Composable
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.ui.components.NirikoSegmentedControl

/** 某个状态（null = 全部）在计数表里的数量。 */
fun librarySearchStatusCount(counts: Map<WatchStatus, Int>, status: WatchStatus?): Int = if (status == null) counts.values.sum() else counts[status] ?: 0

/**
 * 作品库搜索面板里的「收藏状态」行（带计数）。
 *
 * V-11 残余：改造前这里自绘了一套「计数 + 状态名」两行格（16dp 圆角 + 选中 primary α0.15 +
 * 手写 selected 语义），与全站分段控件是两套长相——同样的「从 N 个里选 1 个」却有两种边框/填充。
 * 现在复用 [NirikoSegmentedControl] 的两行模式（details 参数）：形状、选中淡底 + 描边、等宽分布、
 * reduceMotion 降级全部由组件负责，本行只剩「数据整理」。
 *
 * 两行版的内距被组件压到 2dp，整行仍落在 48dp 触控高度内（外层筛选行只有 58dp，不能更高）。
 */
@Composable
fun LibraryStatusSearchRow(
    counts: Map<WatchStatus, Int>,
    selectedStatus: WatchStatus?,
    onStatusSelected: (WatchStatus?) -> Unit,
) {
    val statuses: List<WatchStatus?> = listOf(null) + WatchStatus.entries
    NirikoSegmentedControl(
        options = statuses.map { librarySearchStatusCount(counts, it).toString() },
        details = statuses.map { it?.label ?: "全部" },
        // 未选中任何状态时 indexOf 返回 -1，组件按约定不高亮任何一段
        selectedIndex = statuses.indexOf(selectedStatus),
        // 本行画在搜索卡（searchGlassSurface）内部：再叠一层玻璃就是「玻璃套玻璃」，
        // FULL 档会多一次实时折射且背后内容糊成一片 —— 与统计页放送日历同一取舍。
        nestedInGlass = true,
        onSelect = { index -> onStatusSelected(statuses[index]) },
    )
}
