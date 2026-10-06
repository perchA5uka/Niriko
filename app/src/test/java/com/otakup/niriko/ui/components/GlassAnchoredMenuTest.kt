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

class GlassAnchoredMenuTest {
    private val viewport = Rect(8f, 32f, 392f, 792f)
    private val menu = IntSize(240, 160)

    @Test fun alignsToTrailingAnchorAndOpensBelow() {
        assertEquals(IntOffset(144, 100), glassMenuPosition(
            Rect(336f, 48f, 384f, 96f), menu, viewport, 4, LayoutDirection.Ltr))
    }

    @Test fun rtlUsesAnchorLeadingEdgeAndClampsToSafeViewport() {
        assertEquals(IntOffset(8, 100), glassMenuPosition(
            Rect(8f, 48f, 56f, 96f), menu, viewport, 4, LayoutDirection.Rtl))
        assertEquals(IntOffset(152, 100), glassMenuPosition(
            Rect(360f, 48f, 408f, 96f), menu, viewport, 4, LayoutDirection.Rtl))
    }

    @Test fun bottomEdgeOpensAboveInsteadOfClipping() {
        assertEquals(IntOffset(144, 564), glassMenuPosition(
            Rect(336f, 728f, 384f, 776f), menu, viewport, 4, LayoutDirection.Ltr))
    }

    @Test fun tallMenuIsClampedBetweenInsets() {
        assertEquals(IntOffset(144, 32), glassMenuPosition(
            Rect(336f, 48f, 384f, 96f), IntSize(240, 760), viewport, 4, LayoutDirection.Ltr))
    }

    @Test fun hostOffsetIsRemovedExactlyOnce() {
        val localAnchor = Rect(336f, 48f, 384f, 96f)
        val hostOffset = Offset(20f, 56f)
        val rootAnchor = localAnchor.translate(hostOffset)
        assertEquals(glassMenuPosition(localAnchor, menu, viewport, 4, LayoutDirection.Ltr),
            glassMenuPosition(rootAnchor.translate(-hostOffset), menu, viewport, 4, LayoutDirection.Ltr))
    }

    // Wiring guards, not claims of real focus/input or GPU backdrop verification.
    @Test fun overlayOwnsDismissalAndFocusWithoutIndependentWindowOrSelfCapture() {
        val code = File("src/main/java/com/otakup/niriko/ui/components/GlassAnchoredMenu.kt").readText()
        assertFalse(code.contains("Popup("))
        assertFalse(code.contains("DropdownMenu("))
        assertFalse(code.contains("layerBackdrop("))
        assertFalse(code.contains("PredictiveBackHandler"))
        assertTrue(code.contains("BackHandler(onBack = onDismissRequest)"))
        assertTrue(code.contains("detectTapGestures { onDismissRequest() }"))
        assertTrue(code.contains("Key.Escape"))
        assertTrue(code.contains("anchorFocusRequester.requestFocus()"))
        assertTrue(code.contains(".searchGlassSurface(RoundedCornerShape(24.dp), source)"))
        assertTrue(code.contains("anchorInRoot.translate(-origin)"))
        assertTrue(code.contains(".verticalScroll(rememberScrollState())"))
    }

    @Test fun detailMenusAreAfterCapturedPageAndKeepFirstFrameHeaderStable() {
        val code = File("src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt").readText()
        assertTrue(code.contains("val headerHeight = statusTop + 56.dp"))
        assertFalse(code.contains("headerHeight = with(headerDensity)"))
        assertFalse(code.contains("DropdownMenu("))
        assertTrue(code.contains("pageCardGlassBackdrop(menuBackdrop)"))
        assertTrue(code.contains("restoreAnchorFocus = !portalMenuExpanded && !showCoverPicker && !showSharePreview"))
        assertTrue(code.indexOf("GlassAnchoredMenu(") > code.indexOf("topInset = headerHeight"))
    }
}
