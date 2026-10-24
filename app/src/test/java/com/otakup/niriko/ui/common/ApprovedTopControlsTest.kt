package com.otakup.niriko.ui.common

import com.otakup.niriko.data.model.WatchStatus
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ApprovedTopControlsTest {
    @Test fun selectedItemMovesFirstAndOtherOptionsKeepTheirOrder() {
        val options = listOf("season", "history", "steam").map { TopSelectorOption(it, it) {} }
        assertEquals(listOf("history", "season", "steam"), orderedTopSelectorOptions(options, "history").map { it.key })
        assertEquals(options, orderedTopSelectorOptions(options, "unknown"))
    }
    @Test fun libraryCountsShowAllStatusesInsteadOfOnlySelectedStatus() {
        val counts = mapOf(WatchStatus.PLAN_TO_WATCH to 2, WatchStatus.WATCHING to 3, WatchStatus.COMPLETED to 4)
        assertEquals(9, librarySearchStatusCount(counts, null))
        assertEquals(3, librarySearchStatusCount(counts, WatchStatus.WATCHING))
        assertEquals(0, librarySearchStatusCount(counts, WatchStatus.DROPPED))
    }
    @Test fun sharedSearchPreservesWidthAndDiscoverWorksPeopleMode() {
        val text = source("ui/common/IosStyleSearchComponent.kt")
        assertTrue(text.contains("(screenW - 32.dp).coerceAtLeast(anchorDp)"))
        assertTrue(text.contains("SearchMode.WORKS"))
        assertTrue(text.contains("SearchMode.CHARACTERS"))
        assertTrue(text.contains("if (filters == null)"))
    }
    @Test fun feedsUseTheSamePolicyForPaddingAndStartTheirMaskAtWindowOrigin() {
        // 内距（内容避让）走 TopFadePolicy.feedTopPaddingDp / searchContentTopInsetDp；
        // 遮罩起点是**窗口原点**（屏幕顶部，含状态栏）：三处 hiddenHeight = 0.dp。
        val discover = source("ui/subject/SubjectSearchScreen.kt")
        val library = source("ui/screens/Screens.kt")
        assertTrue(discover.contains("searchContentTopInsetDp("))
        assertTrue(library.contains("TopFadePolicy.feedTopPaddingDp(windowTopInset.value).dp"))
        assertTrue(discover.contains("topContentAlphaMask(strength = { topFadeAlpha }, hiddenHeight = 0.dp)"))
        assertTrue(library.contains("topContentAlphaMask(strength = { headerFadeAlpha }, hiddenHeight = 0.dp)"))
        // 回归护栏：遮罩起点不允许再改回 inset + 控件行高的实心台
        assertFalse(discover.contains("windowMaskHiddenDp("))
        assertFalse(library.contains("windowMaskHiddenDp("))
        assertTrue(source("ui/common/SearchToolbar.kt").contains("VerticalTopSelector("))
        assertTrue(source("ui/library/FolderNavBar.kt").contains("VerticalTopSelector("))
    }

    @Test fun menuRowsOnlyExistWhileExpanded() {
        // B4：收起态不组合行 2–4，否则 56dp 卡片下方会留下不可见但可点的工具行。
        assertTrue(shouldComposeMenuRows(SearchPhase.COLLAPSED).not())
        assertTrue(shouldComposeMenuRows(SearchPhase.MENU_EXPANDED))
        assertTrue(shouldComposeMenuRows(SearchPhase.DRAGGING))
        assertTrue(shouldComposeMenuRows(SearchPhase.IMMERSIVE_SEARCH))
        // 收起动画期间仍保留（要淡出），但点击由挡板吃掉 —— 静置的收起态才是"不组合"。
        assertTrue(shouldComposeMenuRows(SearchPhase.COLLAPSED, surfaceAnimating = true))
        assertTrue(shouldEnableToolRow(isCollapsed = true, surfaceAnimating = true).not())
        val component = source("ui/common/IosStyleSearchComponent.kt")
        assertTrue(component.contains("if (shouldComposeMenuRows(phase, surfaceAnimating)) {"))
    }

    @Test fun toolRowIsNotClickableWhileCollapsedOrAnimating() {
        assertTrue(shouldEnableToolRow(isCollapsed = true, surfaceAnimating = false).not())
        assertTrue(shouldEnableToolRow(isCollapsed = false, surfaceAnimating = true).not())
        assertTrue(shouldEnableToolRow(isCollapsed = false, surfaceAnimating = false))
    }

    @Test fun discoverContentGivesWayToTheExpandedSurface() {
        // B3：收起态仍是工具栏内距；展开态按卡片实际高度让位（200dp 菜单 / 68dp 沉浸折叠）。
        assertEquals(96f, searchContentTopInsetDp(SearchPhase.COLLAPSED, 24f, 200f, 68f, false), 0f)
        assertEquals(100f, searchContentTopInsetDp(SearchPhase.IMMERSIVE_SEARCH, 24f, 200f, 68f, true), 0f)
        assertEquals(232f, searchContentTopInsetDp(SearchPhase.MENU_EXPANDED, 24f, 200f, 68f, false), 0f)
        assertEquals(232f, searchContentTopInsetDp(SearchPhase.DRAGGING, 24f, 200f, 68f, false), 0f)
        // 沉浸态但正在编辑（卡片回到 200dp）时同样让位
        assertEquals(232f, searchContentTopInsetDp(SearchPhase.IMMERSIVE_SEARCH, 24f, 200f, 68f, false), 0f)
    }

    @Test fun topSelectorMenuIsSameWindowOverlay() {
        // B5：Popup 是独立窗口 → 玻璃采样以弹窗原点为基准。选择器必须改走同窗口浮层。
        val selector = source("ui/common/VerticalTopSelector.kt")
        assertTrue(selector.contains("GlassMenuOverlay("))
        assertTrue(selector.contains("Popup(").not())
        assertTrue(selector.contains("androidx.compose.ui.window.Popup").not())
        assertTrue(selector.contains("onGloballyPositioned { buttonRect = it.boundsInRoot() }"))
    }

    @Test fun skeletonTakesTheSameTopPaddingAsTheRealList() {
        // B2：骨架三分支都接 topPadding，不再从窗口 y=4dp 开始画。
        val library = source("ui/screens/Screens.kt")
        assertTrue(library.contains("topPadding = TopFadePolicy.feedTopPaddingDp(windowTopInset.value).dp"))
        assertTrue(library.contains("top = topPadding + 4.dp"))
        assertTrue(library.contains("padding(start = 12.dp, end = 12.dp, top = 4.dp)").not())
    }
    private fun source(file: String) = File("src/main/java/com/otakup/niriko/" + file).readText()
}
