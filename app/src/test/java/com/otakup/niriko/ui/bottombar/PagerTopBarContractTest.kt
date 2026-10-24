package com.otakup.niriko.ui.bottombar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B8 / B11-Step1 的源码契约（照 ApprovedTopControlsTest 的 File.readText 写法）。
 *
 * 这里只钉「结构不能被改回去」的事实：
 * - 顶栏外壳在 pager 外层、两页内容由各自宿主提供；
 * - 页面内容里不再有第二套顶栏（防「搬一半、两套都在」）；
 * - 滚动上报只有一个派发器（不再有每容器累加器），Dock 位移用 offset 而不是 translationY。
 */
class PagerTopBarContractTest {

    private fun source(path: String): String =
        File("src/main/java/com/otakup/niriko/$path").readText()

    private val mainPager by lazy { source("navigation/MainPager.kt") }
    private val library by lazy { source("ui/screens/Screens.kt") }
    private val discover by lazy { source("ui/subject/SubjectSearchScreen.kt") }

    @Test
    fun pagerTopBarIsTheSingleShell() {
        val shell = source("ui/bottombar/PagerTopBar.kt")
        assertTrue(shell.contains("fun PagerTopBar("))
        assertTrue(shell.contains("height(56.dp)"))
        assertTrue(shell.contains("zIndex(10f)"))
        assertTrue(shell.contains("class LibraryControlsState"))
        // task-25：切页立即换内容，不做交叉淡入（Crossfade 断言在 pagerTopBarSwitchesContentWithoutCrossfade）
    }

    @Test
    fun mainPagerOwnsChromeAndHoistedState() {
        assertTrue(mainPager.contains("PagerTopBar("))
        assertTrue(mainPager.contains("LibraryTopBar("))
        assertTrue(mainPager.contains("DiscoverTopBar("))
        assertTrue(mainPager.contains("LibraryControlsState.Saver"))
        assertTrue(mainPager.contains("rememberSaveable(saver = SearchViewModel.Saver)"))
        assertTrue("切页要收起搜索态", mainPager.contains("librarySearchVm.collapse()"))
        assertTrue(mainPager.contains("discoverSearchVm.collapse()"))
    }

    @Test
    fun libraryContentNoLongerDrawsItsOwnTopBar() {
        // 顶栏现住在独立文件（避免与 1100 行的 Screens.kt 互相拖动）
        val content = library
            .substringAfter("private fun LibraryScreenContent(")
            .substringBefore("private fun LibrarySkeleton(")
        assertFalse("内容层不得再画搜索按钮（否则两套顶栏叠着走）", content.contains("IosStyleSearchComponent("))
        assertFalse("内容层不得再画分区条", content.contains("FolderNavBar("))
        val bar = source("ui/screens/LibraryTopBar.kt")
        assertTrue(bar.contains("FolderNavBar("))
        assertTrue(bar.contains("IosStyleSearchComponent("))
    }

    @Test
    fun discoverContentKeepsToolbarOnlyForTheStandaloneRoute() {
        val screen = discover
            .substringAfter("fun SubjectSearchScreen(")
            .substringBefore("fun DiscoverTopBar(")
        // 发现页（pager 内）由 DiscoverTopBar 画；独立路由 subject_search 仍要自绘，
        // 所以屏幕里保留的是 showOwnTopBar 守卫下的那一份，而不是无条件的第二套。
        assertTrue(screen.contains("if (showOwnTopBar) {"))
        assertTrue(screen.contains("SearchToolbar("))
        assertTrue(
            "MainPager 必须显式关掉页面自绘顶栏",
            mainPager.contains("showOwnTopBar = false"),
        )
        assertTrue(discover.substringAfter("fun DiscoverTopBar(").contains("SearchToolbar("))
    }

    @Test
    fun dockHidesWithOffsetAndNoSharedAccumulator() {
        val scroll = source("ui/common/BottomBarScroll.kt")
        // 注释里会保留「改造前 private var acc」的说明，断言只针对代码行。
        val scrollCode = scroll.lines()
            .filterNot { it.trim().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
            .joinToString("\n")
        assertFalse("每容器累加器必须删除（后写覆盖的根因）", scrollCode.contains("private var acc"))
        assertTrue("所有容器只派发方向", scroll.contains("controls?.dispatch("))
        val bar = source("navigation/NirikoBottomBar.kt")
        // 注释里会解释「不能用 translationY」，断言只看代码行。
        val barCode = bar.lines()
            .filterNot { it.trim().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
            .joinToString("\n")
        assertTrue("完整滑出用 offset（命中区跟着走）", barCode.contains(".offset {"))
        assertFalse("不得用 translationY（视觉走了、命中区还在）", barCode.contains("translationY"))
        assertTrue("隐藏态 alpha 归零", bar.contains("alpha = 1f - hideProgress"))
    }
    /** B11-Step2：顶栏与 Dock 共用一个隐藏进度，且隐藏用 offset（命中区一起走）。 */
    @Test
    fun topBarExitsWithTheSharedHideProgress() {
        val shell = source("ui/bottombar/PagerTopBar.kt")
        // 注释里会解释「不要用 translationY」，断言只看代码行（与 dockHides 同款处理）
        val shellCode = shell.lines()
            .filterNot { it.trim().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
            .joinToString("\n")
        assertTrue(shell.contains("hideProgress: Float"))
        assertTrue("隐藏必须改 placement", shellCode.contains(".offset {"))
        assertTrue(shell.contains("clearAndSetSemantics"))
        assertFalse("不得用 translationY（命中区会留在原位）", shellCode.contains("translationY"))
        assertTrue(mainPager.contains("hideProgress = topBarHideProgress"))
        // task-25：顶栏由同一个 FloatingControlsState.shown 驱动（不再跨 CompositionLocal 读 fraction）
        assertTrue(mainPager.contains("floatingControls?.shown"))
        assertTrue(mainPager.contains("animateFloatAsState"))
        // 协调：Dock 与顶栏的显隐都来自同一份状态（Dock 读它的动画输出）
        assertTrue(
            "Dock 必须读同一份隐藏进度",
            source("navigation/NirikoBottomBar.kt").contains("bottomBarHideFraction()"),
        )
    }

    /** task-25：切页不允许交叉淡入（两页内容差异大 → 真机闪烁）。 */
    @Test
    fun pagerTopBarSwitchesContentWithoutCrossfade() {
        val shell = source("ui/bottombar/PagerTopBar.kt")
        val shellCode = shell.lines()
            .filterNot { it.trim().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
            .joinToString("\n")
        assertFalse("不得再用 Crossfade 切两页顶栏内容", shellCode.contains("Crossfade("))
        assertTrue("必须按 slot 直接切换", shellCode.contains("TopBarSlot.LIBRARY -> libraryContent()"))
        assertTrue(
            "切页键必须是 settledPage（滑动中途不来回切）",
            mainPager.contains("topBarSlotFor(pagerState.settledPage)"),
        )
    }

    /** B11-Step2：搜索表面展开时强制显示，且 gate 会拒绝滚动派发。 */
    @Test
    fun searchExpandedForcesControlsVisible() {
        assertTrue(mainPager.contains("forceShown(searchExpanded)"))
        val state = source("ui/common/BottomBarScroll.kt")
        assertTrue(state.contains("fun forceShown("))
        assertTrue("强制期间必须拒绝滚动派发", state.contains("if (forcedShown || dyPx == 0f) return"))
    }

    /** B11-Step3：空白恢复只在 Final-pass 观察，不放全屏透明层。 */
    @Test
    fun blankTapRestoreNeverSwallowsTouches() {
        val restore = source("ui/bottombar/BlankTapRestore.kt")
        assertTrue(restore.contains("PointerEventPass.Final"))
        assertTrue(restore.contains("requireUnconsumed = false"))
        assertFalse("不得用 clickable 全屏层", restore.contains("clickable("))
        assertFalse("不得铺满全屏", restore.contains("fillMaxSize"))
        assertTrue(restore.contains("touchSlop"))
        assertTrue(restore.contains("longPressTimeoutMillis"))
        assertTrue(mainPager.contains("floatingBarRestoreOnBlankTap"))
    }

    /** Step2/3：详情胶囊由滚动状态驱动，且隐藏用 offset；详情根也有空白恢复。 */
    @Test
    fun detailPillIsScrollDrivenAndNotATouchTrap() {
        val detail = source("ui/subject/SubjectDetailScreen.kt")
        assertTrue(detail.contains("visible = detailChrome.shown"))
        // task-25：dy 由滚动容器直接派发（第一项之后也生效），不再从 scrollOffsetFor 换算
        assertTrue(detail.contains("reportFloatingScrollTo("))
        assertFalse(
            "不得再用 firstVisibleItem 换算派发（会触发 jumpGuard 只在首项有效）",
            detail.contains("detailScrollPx"),
        )
        assertTrue(detail.contains("detailChrome.forceShown(showCollectionSheet)"))
        assertTrue("胶囊隐藏用 offset", detail.contains(".offset { IntOffset(0, hiddenShiftPx.roundToInt()) }"))
        assertFalse("胶囊不得再用 translationY 隐藏", detail.contains("translationY = (1f - shown)"))
        assertTrue(detail.contains("floatingBarRestoreOnBlankTap { detailChrome.restore() }"))
        // task-25：详情顶栏与胶囊同源退出（向上位移 + alpha）
        assertTrue(detail.contains("detailHideProgress"))
        assertTrue(detail.contains("IntOffset(0, -(detailHideProgress"))
        // task-25 补充裁决：渐隐从窗口原点开始
        assertTrue(detail.contains("hiddenHeight = 0.dp"))
        assertFalse(detail.contains("windowMaskHiddenDp("))
    }
}
