package com.otakup.niriko.ui.bottombar

import com.otakup.niriko.ui.common.SearchPhase

/**
 * B8：两页悬浮按钮一体化的**纯判定层**（无 Compose 依赖，JVM 可直接单测）。
 *
 * 迁移前：作品库与发现页各自把「左上 + 右上」画在自己页面里，随 HorizontalPager 横向平移；
 * 迁移后：外壳挂到 pager 外层、位置不参与任何动画，只有内容随当前页切换。
 * 「哪一页显示哪套内容」与「切页后搜索态怎么处置」都是纯规则，先在这里钉死。
 */
enum class TopBarSlot {
    /** 作品库：左 = FolderNavBar（分区），右 = IosStyleSearchComponent（收藏搜索）。 */
    LIBRARY,

    /** 发现页：左 = 趋势标签（SearchToolbar 内），右 = 搜索轨道。 */
    DISCOVER,
}

/**
 * 当前页 → 悬浮顶栏内容；[TopLevelDestination] 的序号约定见 navigation/MainPager.kt。
 *
 * 统计页 / 设置页没有悬浮按钮（返回 null），外壳由调用方直接不渲染。
 * 越界页号（理论上不会出现）同样返回 null，避免索引异常把 pager 打挂。
 *
 * @param page 已 settled 的页号（用 settledPage：滑动中途不要把两套内容都画出来）
 */
fun topBarSlotFor(page: Int): TopBarSlot? = when (page) {
    0 -> TopBarSlot.LIBRARY
    1 -> TopBarSlot.DISCOVER
    else -> null
}

/**
 * 切页副作用（B8 第 7 条）：离开某页时把该页的搜索态收起，避免「在发现页却残留作品库的
 * 展开搜索卡」，返回时又突然弹开。
 *
 * 纯函数形式只表达策略；真正的收起（`SearchViewModel.collapse()` + 清焦 + 收键盘）
 * 由切换页的 `LaunchedEffect` 执行。
 */
fun phaseAfterPageSwitch(@Suppress("UNUSED_PARAMETER") phase: SearchPhase): SearchPhase =
    SearchPhase.COLLAPSED
