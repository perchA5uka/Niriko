package com.otakup.niriko.ui.animation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Rect
import java.io.File
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalSharedTransitionApi::class)
class SubjectCoverMotionTest {
    @Test fun flightIsSlightlyFasterAndLaunchesAheadOfLinear() {
        assertEquals(200, NirikoMotionSpecs.SUBJECT_COVER_DURATION_MS)
        val spec = NirikoMotionSpecs.subjectCoverSeek<Float>().vectorize(Float.VectorConverter)
        val start = AnimationVector1D(0f)
        val end = AnimationVector1D(100f)
        val velocity = AnimationVector1D(0f)
        assertEquals(68.75f, spec.getValueFromNanos(100_000_000L, start, end, velocity).value, 0.001f)
        assertEquals(100f, spec.getValueFromNanos(200_000_000L, start, end, velocity).value, 0.001f)
    }
    @Test fun bothDirectionsRemainMonotonicWithVisibleTravelThroughEveryQuarter() {
        for (contracting in listOf(false, true)) {
            val spec = NirikoMotionSpecs.subjectCoverSeek<Float>(contracting).vectorize(Float.VectorConverter)
            val start = AnimationVector1D(0f)
            val end = AnimationVector1D(100f)
            val velocity = AnimationVector1D(0f)
            val duration = NirikoMotionSpecs.SUBJECT_COVER_DURATION_MS * 1_000_000L
            var previous = 0f
            for (step in 1..100) {
                val value = spec.getValueFromNanos(duration * step / 100, start, end, velocity).value
                assertTrue(value > previous)
                assertTrue(value <= 100f)
                previous = value
            }
            val quarterValues = (0..4).map { spec.getValueFromNanos(duration * it / 4, start, end, velocity).value }
            assertTrue(quarterValues.zipWithNext().all { (a, b) -> b - a >= 10f })
            assertEquals(100f, previous, 0.001f)
            for (step in 100 downTo 0) {
                val fraction = step / 100f
                val curve = if (contracting) NirikoMotionSpecs.subjectCoverContractEasing else NirikoMotionSpecs.subjectCoverExpandEasing
                assertEquals(curve.transform(fraction) * 100f,
                    spec.getValueFromNanos(duration * step / 100, start, end, velocity).value, 0.001f)
            }
        }
    }
    @Test fun contractionRetracesTheExpansionExactly() {
        for (step in 0..100) {
            val t = step / 100f
            assertEquals(NirikoMotionSpecs.subjectCoverExpandEasing.transform(1f - t),
                1f - NirikoMotionSpecs.subjectCoverContractEasing.transform(t), 0.00001f)
        }
    }
    @Test fun pathBoundsUseSameDurationAsPageWithoutOvershootingGeometry() {
        val small = Rect(10f, 700f, 110f, 850f)
        val large = Rect(16f, 72f, 384f, 624f)
        for ((start, target) in listOf(small to large, large to small)) {
            val bounds = NirikoMotionSpecs.subjectCoverBounds.createAnimationSpec(start, target)
                .vectorize(Rect.VectorConverter)
            val from = Rect.VectorConverter.convertToVector(start)
            val to = Rect.VectorConverter.convertToVector(target)
            val zero = androidx.compose.animation.core.AnimationVector4D(0f, 0f, 0f, 0f)
            assertEquals(200_000_000L, bounds.getDurationNanos(from, to, zero))
            assertEquals(start, Rect.VectorConverter.convertFromVector(bounds.getValueFromNanos(0L, from, to, zero)))
            assertEquals(target, Rect.VectorConverter.convertFromVector(bounds.getValueFromNanos(200_000_000L, from, to, zero)))
        }
    }
    @Test fun endpointSizesRemainOutsideTheAnimatedSharedNodes() {
        fun source(path: String) = File("src/main/java/com/otakup/niriko/" + path).readText()
        assertTrue(source("ui/components/CoverImage.kt").contains(".aspectRatio(aspectRatio)\n            .then(sharedModifier)"))
        assertTrue(source("ui/subject/SubjectDetailScreen.kt").contains(".aspectRatio(ratio)\n                        .then(sharedModifier)"))
        assertTrue(source("ui/common/CreditSubjectCard.kt").contains(".aspectRatio(CreditSubjectCardMetrics.posterAspectRatio)\n                    .then(coverModifier)"))
        assertTrue(source("ui/subject/RelationsSection.kt").contains(".height(120.dp)\n                        .then(sharedModifier)"))
        assertTrue(source("ui/library/LibraryGalleryCard.kt").contains(".fillMaxWidth().height(340.dp).then(sharedModifier)"))
    }
}
