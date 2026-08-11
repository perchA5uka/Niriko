package com.otakup.niriko.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * 搜索交互前端状态机。
 *
 * 职责（纯 UI 层）：
 * - 持有 [SearchPhase] 四态（COLLAPSED / MENU_EXPANDED / DRAGGING / IMMERSIVE_SEARCH）
 * - 持有轨道拖拽进度 [dragProgress]（0..1，指示器拉长跟手）
 * - 持有 [mode] / [contentType] / [query] 的 UI 镜像（由 SubjectSearchViewModel 派生，不双写）
 *
 * 注意：本类**不碰 Repository / Database / 业务逻辑**，
 * 模式/类型/查询的持久数据仍由 SubjectSearchViewModel 驱动，
 * 前端状态机只负责交互阶段与动画进度。
 */
class SearchViewModel {

    /** 当前搜索交互阶段。 */
    var phase by mutableStateOf(SearchPhase.COLLAPSED)
        private set

    /** 搜索模式镜像（WORKS / CHARACTERS）。 */
    var mode by mutableStateOf(SearchMode.WORKS)
        private set

    /** 作品类型镜像。 */
    var contentType by mutableStateOf(ContentType.ALL)
        private set

    /** 查询词镜像（由 SubjectSearchViewModel 同步）。 */
    var query by mutableStateOf("")
        private set

    /** 轨道拖拽进度 0..1（绿色指示器拉长跟手；拖到最左阈值 → 沉浸态）。 */
    var dragProgress by mutableFloatStateOf(0f)
        private set

    /** 是否正在拖拽（指示器亮起/拉长的驱动标志）。 */
    var dragActive by mutableStateOf(false)
        private set

    /** 搜索一体卡片的类型/模式行是否展开（上划结果时折叠缩短，下拉拉长恢复）。 */
    var filterRowExpanded by mutableStateOf(true)
        private set

    /** 输入框是否正在编辑（光标存在）——编辑时菜单强制保持展开，不随滚动折叠。 */
    var editing by mutableStateOf(false)
        private set

    // ==================== 阶段迁移 ====================

    /** 置位/复位"编辑中"（BasicTextField 聚焦/失焦时调用）。 */
    fun markEditing(editing: Boolean) {
        this.editing = editing
    }

    /** 进入/退出菜单态。 */
    fun setMenuExpanded(expanded: Boolean) {
        phase = if (expanded) SearchPhase.MENU_EXPANDED else SearchPhase.COLLAPSED
        if (!expanded) dragProgress = 0f
    }

    /** 长按放大镜 → 进入拖拽态（指示器亮起）。 */
    fun enterDragging() {
        phase = SearchPhase.DRAGGING
    }

    /** 拖拽未达阈值松手 → 退回菜单态（指示器缩回消失）。 */
    fun exitDraggingToMenu() {
        if (phase == SearchPhase.DRAGGING) {
            phase = SearchPhase.MENU_EXPANDED
            dragProgress = 0f
        }
    }

    /** 进入沉浸搜索态（并重置拖拽进度到完成态）。 */
    fun expandSearch() {
        phase = SearchPhase.IMMERSIVE_SEARCH
        dragProgress = 1f
    }

    /** 回到默认态（并清空拖拽进度）。 */
    fun collapse() {
        phase = SearchPhase.COLLAPSED
        dragProgress = 0f
    }

    /** 拖拽进度更新（跟手时由手势实时驱动；越界自动夹紧）。 */
    fun updateDragProgress(value: Float) {
        dragProgress = value.coerceIn(0f, 1f)
    }

    /** 置位/复位拖拽标志（手势 down → true，up/cancel → false）。 */
    fun markDragActive(active: Boolean) {
        dragActive = active
    }

    /** 折叠/展开搜索一体卡片的类型/模式行（上划折叠，下拉拉长）。 */
    fun markFilterRowExpanded(expanded: Boolean) {
        filterRowExpanded = expanded
    }

    // ==================== 镜像同步（由外层 SubjectSearchViewModel 状态派生） ====================

    /** 同步模式镜像（不触发任何网络/数据操作）。 */
    fun syncMode(isPersonSearch: Boolean) {
        mode = if (isPersonSearch) SearchMode.CHARACTERS else SearchMode.WORKS
    }

    /** 同步类型镜像。 */
    fun syncContentType(selectedType: Int?) {
        contentType = ContentType.fromBangumiType(selectedType)
    }

    /** 同步查询词镜像。 */
    fun syncQuery(value: String) {
        query = value
    }

    // ==================== 状态保存/恢复（返回导航栈时保持搜索态） ====================

    /**
     * 从保存态恢复交互阶段（navigate 覆盖 MainPager 后 pop 返回时，
     * 前端状态机会重建——若不恢复 phase，会丢搜索态回到发现页首页）。
     */
    fun restore(savedPhase: SearchPhase, savedQuery: String) {
        phase = savedPhase
        query = savedQuery
    }

    companion object {
        /**
         * 仅保存恢复"搜索态"所需的两个关键字段：phase 与 query。
         * mode / contentType / dragProgress 等由 SubjectSearchViewModel 镜像同步或瞬时交互态，无需保存。
         */
        val Saver: Saver<SearchViewModel, Map<String, String>> = Saver(
            save = { vm ->
                mapOf(
                    "phase" to vm.phase.name,
                    "query" to vm.query,
                )
            },
            restore = { saved ->
                SearchViewModel().apply {
                    restore(
                        savedPhase = runCatching { SearchPhase.valueOf(saved["phase"] ?: "COLLAPSED") }
                            .getOrDefault(SearchPhase.COLLAPSED),
                        savedQuery = saved["query"] ?: "",
                    )
                }
            },
        )
    }
}
