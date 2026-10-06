package com.otakup.niriko.ui.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CoverFlightPerformanceTest {

    @Test fun decodeWidthDependsOnStableEndpointInputsNotFlightConstraints() {
        assertEquals(800, coverStableDecodeWidth(1200f, null))
        assertEquals(720, coverStableDecodeWidth(1080f, null))
        assertEquals(480, coverStableDecodeWidth(1200f, 480))
        assertEquals(800, coverStableDecodeWidth(2400f, null))
        assertEquals(1, coverStableDecodeWidth(1200f, 0))
    }
    @Test fun flightImageAvoidsConstraintSubcompositionAndRetainsCachePlaceholder() {
        val cover = File("src/main/java/com/otakup/niriko/ui/components/CoverImage.kt").readText()
        assertFalse(cover.contains("BoxWithConstraints("))
        assertFalse(cover.contains("SubcomposeAsyncImage("))
        assertFalse(cover.contains("constraints.maxWidth"))
        assertTrue(cover.contains("AsyncImage("))
        assertTrue(cover.contains("placeholderMemoryCacheKey"))
        assertTrue(cover.contains("Dimension.Undefined"))
        assertTrue(cover.contains(".crossfade(false)"))
    }
    @Test fun expensiveCaptureWaitsForSharedTransitionButStaticBlurDoesNotFlashAway() {
        val detail = File("src/main/java/com/otakup/niriko/ui/subject/SubjectDetailScreen.kt").readText()
        assertTrue(detail.contains("sharedTransitionScope?.isTransitionActive"))
        assertFalse("A flight must not dispose its shared nodes", detail.contains("hasBackground && !coverFlightActive"))
        assertTrue(detail.contains("enabled = hasBackground && glassEffect"))
        assertTrue(detail.contains("if (!coverFlightActive) bgEntered = true"))
        assertTrue(detail.contains("if (blurEffect != null) renderEffect = blurEffect"))
    }
}
