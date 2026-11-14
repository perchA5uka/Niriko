package com.otakup.niriko.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V-14 收口后的锚定菜单契约（原 GlassAnchoredMenuTest 的继任者）。
 *
 * 菜单的统一入口是 [NirikoMenu]，定位/缩放/退场/焦点/Esc 都在 [GlassMenuOverlay]。
 * 旧壳（GlassAnchoredMenu）已删除；[glassMenuPosition] 的纯函数用例原样保留，
 * 因为定位几何本来就是「同窗口一次布局内先量后排」的核心，跟外壳叫什么无关。
 */
class GlassMenuOverlayTest {
    private val viewport = Rect(8f, 32f, 392f, 792f)
    private val menu = IntSize(240, 160)

    private fun overlaySource(): String = File("src/main/java/com/otakup/niriko/ui/components/GlassMenuOverlay.kt").readText()

    @Test fun alignsToTrailingAnchorAndOpensBelow() {
        assertEquals(IntOffset(144, 100), glassMenuPosition(Rect(336f, 48f, 384f, 96f), menu, viewport, 4, LayoutDirection.Ltr))
    }

    @Test fun rtlUsesAnchorLeadingEdgeAndClampsToSafeViewport() {
        assertEquals(IntOffset(8, 100), glassMenuPosition(Rect(8f, 48f, 56f, 96f), menu, viewport, 4, LayoutDirection.Rtl))
        assertEquals(IntOffset(152, 100), glassMenuPosition(Rect(360f, 48f, 408f, 96f), menu, viewport, 4, LayoutDirection.Rtl))
    }

    @Test fun bottomEdgeOpensAboveInsteadOfClipping() {
        assertEquals(IntOffset(144, 564), glassMenuPosition(Rect(336f, 728f, 384f, 776f), menu, viewport, 4, LayoutDirection.Ltr))
    }

    @Test fun tallMenuIsClampedBetweenInsets() {
        assertEquals(IntOffset(144, 32), glassMenuPosition(Rect(336f, 48f, 384f, 96f), IntSize(240, 760), viewport, 4, LayoutDirection.Ltr))
    }

    @Test fun hostOffsetIsRemovedExactlyOnce() {
        val localAnchor = Rect(336f, 48f, 384f, 96f)
        val hostOffset = Offset(20f, 56f)
        val rootAnchor = localAnchor.translate(hostOffset)
        assertEquals(
            glassMenuPosition(localAnchor, menu, viewport, 4, LayoutDirection.Ltr),
            glassMenuPosition(rootAnchor.translate(-hostOffset), menu, viewport, 4, LayoutDirection.Ltr)
        )
    }

    // Wiring guards, not claims of real focus/input or GPU backdrop verification.
    @Test fun overlayOwnsDismissalFocusAndEscape() {
        val code = overlaySource()
        assertFalse(code.contains("Popup("))
        assertFalse(code.contains("layerBackdrop("))
        assertTrue(code.contains("BackHandler(onBack = onDismissRequest)"))
        assertTrue(code.contains("detectTapGestures { onDismissRequest() }"))
        // 旧壳的 Esc 关闭与「展开进菜单/关闭还焦点」在统一浮层里保留（外接键盘 / 读屏）。
        assertTrue(code.contains("Key.Escape"))
        assertTrue(code.contains("focusRequester(menuFocus)"))
        assertTrue(code.contains("anchorFocusRequester?.requestFocus()"))
        assertTrue(code.contains("menuFocus.requestFocus()"))
        assertTrue(code.contains("onDismissRequest"))
    }

    @Test fun oldWrapperIsGoneAndDetailUsesTheUnifiedMenu() {
        val mainRoot = File("src/main/java/com/otakup/niriko")
        val legacyHits = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val code = file.readText()
                Regex("GlassAnchoredMenu\\(").findAll(code).map { file.relativeTo(mainRoot).invariantSeparatorsPath }
            }.toList()
        assertTrue("旧 GlassAnchoredMenu 包装器不应再被调用：$legacyHits", legacyHits.isEmpty())

        val detail = File(mainRoot, "ui/subject/SubjectDetailScreen.kt").readText()
        assertTrue("详情页两个菜单应收敛到 NirikoMenu", Regex("NirikoMenu\\(").findAll(detail).count() >= 2)
        assertTrue("分享菜单必须保留键盘焦点语义", detail.contains("anchorFocusRequester = shareFocus"))
        assertTrue("传送门菜单必须保留键盘焦点语义", detail.contains("anchorFocusRequester = portalFocus"))
        assertTrue("首帧 header 高度公式不能被改回", detail.contains("val headerHeight = statusTop + 56.dp"))
        assertFalse(detail.contains("headerHeight = with(headerDensity)"))
        assertFalse(detail.contains("DropdownMenu("))
        assertTrue(detail.contains("pageCardGlassBackdrop(menuBackdrop)"))
        assertTrue(detail.contains("restoreAnchorFocus = !portalMenuExpanded && !showCoverPicker && !showSharePreview"))
        // 5 处双行 M3 菜单行是刻意保留（NirikoMenuItem 无副标题槽），数量由 ControlLanguagePolicyTest 锁死。
        assertTrue(detail.contains("DropdownMenuItem("))
    }
}
