package com.otakup.niriko.ui.common

/**
 * 搜索交互状态机（对齐原型 State 1→5，iOS 26 一体化玻璃表面）。
 *
 * - [COLLAPSED]        默认：56dp 圆角玻璃按钮（仅放大镜 icon）
 * - [MENU_EXPANDED]    短按按钮：表面从右上角锚点向下展开为全宽菜单
 *                       （行1 假轨道 + 行2 模式 + 行3 类型标题 + 行4 类型，z 高层盖住趋势标签）
 * - [DRAGGING]         在 MENU 态长按放大镜：假轨道内绿色圆指示器亮起，
 *                       左拖拉长（右缘固定在锚点中心、左缘跟手，轨道内伸缩，不放真 input）
 * - [IMMERSIVE_SEARCH] 拖到轨道最左阈值：指示器变暗化为输入框底色，假轨道→真 input，
 *                       放大镜滑到输入框最左，右侧从无到有分裂出 ×（细胞分裂）。
 *                       滚动结果时卡片高度收缩（类型/模式推入隐藏，仅留搜索框）。
 */
enum class SearchPhase {
    COLLAPSED,
    MENU_EXPANDED,
    DRAGGING,
    IMMERSIVE_SEARCH,
}

/** 搜索模式：作品 / 人物。 */
enum class SearchMode(val label: String) {
    WORKS("作品"),
    CHARACTERS("人物"),
}

/** 作品类型（Bangumi subjectType 的 UI 镜像）。 */
enum class ContentType(val label: String) {
    ALL("全部"),
    ANIME("动画"),
    BOOK("书籍"),
    GAME("游戏"),
    MUSIC("音乐"),
    REAL("三次元");

    /** 映射回 Bangumi type 整数（全部 = null）。 */
    val bangumiType: Int?
        get() = when (this) {
            ALL -> null
            ANIME -> 2
            BOOK -> 1
            GAME -> 4
            MUSIC -> 3
            REAL -> 6
        }

    companion object {
        /** 从 Bangumi type 整数映射（null = 全部，未识别类型也归入全部）。 */
        fun fromBangumiType(type: Int?): ContentType = when (type) {
            null -> ALL
            2 -> ANIME
            1 -> BOOK
            4 -> GAME
            3 -> MUSIC
            6 -> REAL
            else -> ALL
        }
    }
}

/**
 * 搜索前端状态快照。
 *
 * 注意：[mode]/[contentType]/[query] 只是 SubjectSearchViewModel 数据的 UI 镜像（派生），
 * 数据仍由 SubjectSearchViewModel 驱动，避免双写漂移；本状态机只额外持有
 * 前端交互状态：[phase]（三态）与 [dragProgress]（左划跟手轨道进度 0..1）。
 */
data class SearchUiState(
    val phase: SearchPhase = SearchPhase.COLLAPSED,
    val mode: SearchMode = SearchMode.WORKS,
    val contentType: ContentType = ContentType.ALL,
    val query: String = "",
    /** 轨道拖拽进度 0..1：搜索框/菜单宽度实时跟手的驱动值。 */
    val dragProgress: Float = 0f,
) {
    companion object {
        /** 从 ViewModel 状态派生前端状态（保持单向数据流）。 */
        fun from(
            phase: SearchPhase,
            isPersonSearch: Boolean,
            selectedType: Int?,
            query: String,
            dragProgress: Float = 0f,
        ): SearchUiState = SearchUiState(
            phase = phase,
            mode = if (isPersonSearch) SearchMode.CHARACTERS else SearchMode.WORKS,
            contentType = ContentType.fromBangumiType(selectedType),
            query = query,
            dragProgress = dragProgress,
        )
    }
}
