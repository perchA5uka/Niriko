package com.otakup.niriko.ui.subject

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B10 / B9A 的源码契约测试。
 *
 * 这两张卡的验收有一半是「结构」而不是「数值」：收藏入口必须只剩**一个**外壳、
 * 必须悬浮而不是列表 item、编辑层必须把 inset 交给内容自己。M3 的参数名会随版本变，
 * 所以这里锁的是本文件里真实存在的关键字面，改坏了会在单测里先响。
 *
 * 读文件用相对路径（Gradle 单测的工作目录是 app/），与 DetailNavigationRegressionTest 同法。
 */
class CollectionActionLayoutContractTest {

    private val source = File("src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt").readText()

    private val collectionAction: String get() = source.substringAfter("private fun DetailCollectionAction(")

    @Test
    fun `收藏入口只剩一个外壳_不再有 M3 Button 或 DetailDock`() {
        assertFalse(
            "未收藏态仍在用 M3 Button：两态没有统一",
            source.contains("Button(onClick = onAddToCollection"),
        )
        assertFalse(
            "DetailDock 仍是第二条外壳：两态没有统一",
            source.contains("DetailDock("),
        )
        assertTrue(
            "缺少统一外壳的调用点",
            source.contains("DetailCollectionAction("),
        )
        assertTrue(
            "调用点没有走纯函数真值表",
            source.contains("detailCollectionActionState(state.isInCollection, state.isUpdating)"),
        )
    }

    @Test
    fun `悬浮与 B11 接口`() {
        assertTrue(
            "外壳没有暴露 visible: Boolean = true（B11 的挂点）",
            collectionAction.contains("visible: Boolean = true"),
        )
        assertTrue(
            "移动端没有把胶囊压在底部居中的窗口层",
            source.contains(".align(Alignment.BottomCenter)"),
        )
        assertTrue(
            "胶囊没有吃掉导航栏 inset",
            source.contains(".navigationBarsPadding()"),
        )
        assertTrue(
            "列表底部没有为悬浮胶囊留出空间（应 ≥96dp）",
            source.contains("bottom = 96.dp"),
        )
    }

    @Test
    fun `两态同形_28dp 圆角与不小于 56dp 的高度`() {
        assertTrue(
            "外壳没有用 28dp 玻璃胶囊圆角",
            collectionAction.contains("shape = RoundedCornerShape(28.dp)"),
        )
        assertTrue(
            "外壳没有保证两态同高（>=56dp）",
            collectionAction.contains(".heightIn(min = 56.dp)"),
        )
        assertTrue(
            "未收藏态没有复用同一个外壳渲染文案",
            collectionAction.contains("state.label"),
        )
    }

    @Test
    fun `B9A 编辑层贴顶参数_且没有假装给 M3 加动画`() {
        val sheet = source.substringAfter("ModalBottomSheet(", "")
            .substringBefore(") {", "")
        assertTrue("找不到 ModalBottomSheet 的参数块", sheet.isNotEmpty())
        assertTrue("缺少全宽参数", sheet.contains("sheetMaxWidth = Dp.Unspecified"))
        assertTrue("缺少去把手", sheet.contains("dragHandle = null"))
        // material3 的 contentWindowInsets 是 @Composable () -> WindowInsets，必须写成 lambda。
        assertTrue(
            "缺少零 inset（交给内容自己处理）",
            sheet.contains("contentWindowInsets = { WindowInsets(0, 0, 0, 0) }"),
        )
        assertTrue("缺少贴顶的零圆角", sheet.contains("shape = RoundedCornerShape(0.dp)"))
        // 只看参数行：注释里会解释「为什么没有 animationSpec」，不能把注释当成参数命中。
        val sheetParams = sheet.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertFalse(
            "material3 1.4.0 不支持 animationSpec，这里不该出现该参数",
            sheetParams.contains("animationSpec"),
        )

        val editSheet = source.substringAfter("private fun CollectionEditSheet(")
            .substringBefore("\nprivate fun ", "")
        assertTrue("编辑层内容没有撑满高度（贴顶的关键）", editSheet.contains(".fillMaxHeight()"))
        assertTrue("编辑层内容没有自己吃状态栏 inset", editSheet.contains(".statusBarsPadding()"))
        assertTrue("编辑层内容没有自己吃导航栏 inset", editSheet.contains(".navigationBarsPadding()"))
    }

    @Test
    fun `详情页渐隐与库页一致从窗口原点开始`() {
        // task-25 真机裁决：渐隐上边界必须是屏幕顶部/状态栏之上。
        // inset+56 会在顶部留一块固定遮盖，所以三页统一 hiddenHeight = 0.dp。
        assertTrue(
            "详情页渐隐没有改到窗口原点",
            source.contains("hiddenHeight = 0.dp"),
        )
        assertFalse(
            "不得再用 windowMaskHiddenDp 作为渐隐高度",
            source.contains("windowMaskHiddenDp("),
        )
        assertFalse(
            "仍在用已删除的常量",
            source.contains("WINDOW_MASK_HIDDEN_DP"),
        )
        assertFalse(
            "仍在用 topInset + 56.dp（inset 被重复叠加）",
            source.contains("hiddenHeight = topInset + 56.dp"),
        )
    }

    @Test
    fun `批次 A 的三处收层逻辑逐字保留`() {
        // X5：成功事件 → 等庆祝 → hide()，一行都不能丢，否则「庆祝中提前收层」回归
        assertTrue(source.contains("recordSavedEvents.collect"))
        assertTrue(source.contains("withFrameNanos { }"))
        assertTrue(source.contains("completionPlayback.isActive"))
        assertTrue(source.contains("snapshotFlow { completionPlayback.isActive }.first { !it }"))
        assertTrue(source.contains("runCatching { sheetState.hide() }"))
        // X4：单本判据仍由纯函数决定，B9 只换外壳
        assertTrue(source.contains("VolumeProgressPolicy.showVolumeField(subject.type, subject.volumes)"))
        assertTrue(source.contains("VolumeProgressPolicy.showProgressSection(subject.type, subject.volumes, subject.series)"))
    }
}
