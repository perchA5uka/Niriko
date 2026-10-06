package com.otakup.niriko.ui.components

import androidx.compose.ui.geometry.Offset
import com.otakup.niriko.data.model.CardMaterialState
import com.otakup.niriko.data.model.CardRarity
import com.otakup.niriko.data.model.cardMaterialFromRating
import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.ui.animation.PressTiltLighting
import com.otakup.niriko.ui.animation.pressTiltRotation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CardMaterialLightingTest {
    @Test fun brightBackgroundContrastAndEllipticalGeometryAreBounded() {
        assertEquals(0f, cardBrightContrast(0.1f), 0.0001f)
        assertEquals(1f, cardBrightContrast(1f), 0.0001f)
        assertTrue(cardBrightContrast(Float.NaN).isFinite())
        val radii = cardSpecularRadii(androidx.compose.ui.geometry.Size(200f, 280f))
        assertTrue(radii.x > radii.y * 1.5f)
        assertTrue(radii.x < 150f && radii.y < 80f)
        assertDirection(Offset(0.35f, 0.20f), cardDiffusionCenter(Offset(1f, -1f)))
        assertTrue(cardDiffusionCenter(Offset(1f, -1f)) != cardDiffusionCenter(Offset(-1f, 1f)))
    }
    @Test fun standardNeverAddsReflectionEvenWhenPressed() {
        assertEquals(0f, cardReflectionAlpha(CardRarity.STANDARD, 1f, 1f), 0.0001f)
    }

    @Test fun localizedSpecularHasItsOwnRestrainedBudgetAtRest() {
        assertEquals(0.07f, cardReflectionAlpha(CardRarity.SILVER, 0f, 0f), 0.0001f)
        assertEquals(0.07f, cardReflectionAlpha(CardRarity.GOLD, 0f, 0f), 0.0001f)
        assertEquals(0.08f, cardReflectionAlpha(CardRarity.HOLOGRAPHIC, 0f, 0f), 0.0001f)
    }

    @Test fun existingTiltIncreasesReflectionAndReleaseRestoresIt() {
        val lighting = PressTiltLighting()
        val x = mutableStateOf(0f)
        val y = mutableStateOf(7f)
        lighting.bind(x, y)
        val pressed = cardReflectionAlpha(CardRarity.GOLD,
            cardLightDirection(lighting.rotationX, lighting.rotationY).x, 0f)
        y.value = 0f
        val released = cardReflectionAlpha(CardRarity.GOLD,
            cardLightDirection(lighting.rotationX, lighting.rotationY).x, 0f)
        assertEquals(0.125f, pressed, 0.0001f)
        assertEquals(0.07f, released, 0.0001f)
    }

    @Test fun reflectionInputsAreFiniteClampedAndPerfectRemainsRestrained() {
        assertEquals(0.08f, cardReflectionAlpha(CardRarity.HOLOGRAPHIC, Float.NaN, Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(0.16f, cardReflectionAlpha(CardRarity.HOLOGRAPHIC, 5f, 5f), 0.0001f)
        assertEquals(0.08f, cardReflectionAlpha(CardRarity.HOLOGRAPHIC, -1f, -1f), 0.0001f)
    }

    @Test fun standardHasNoNewSurfaceTint() {
        assertTrue(cardSurfaceColors(CardRarity.STANDARD).isEmpty())
    }

    @Test fun surfaceTintExtendsThroughTheCenterWithoutBecomingAFill() {
        listOf(CardRarity.SILVER, CardRarity.GOLD, CardRarity.HOLOGRAPHIC).forEach { rarity ->
            val colors = cardSurfaceColors(rarity)
            assertTrue(colors.all { it.alpha in 0.01f..0.10f })
            assertTrue(colors[2].alpha in 0.01f..0.03f)
            assertTrue(colors.first().alpha > colors[2].alpha)
            assertTrue(colors.last().alpha > colors[2].alpha)
        }
    }

    @Test fun holographicSurfaceHasDistinctColorsWithoutAnOpaqueFill() {
        val colors = cardSurfaceColors(CardRarity.HOLOGRAPHIC)
        assertEquals(5, colors.size)
        colors.forEach { color ->
            val channels = listOf(color.red, color.green, color.blue)
            assertTrue(channels.max() - channels.min() < 0.50f)
        }
        assertTrue(colors.first().blue - colors.first().red > 0.30f)
        assertTrue(colors[1].green - colors[1].red > 0.30f)
        assertTrue(colors[3].red - colors[3].green > 0.25f)
        assertTrue(colors.first().blue > colors.first().red)
        assertTrue(colors.last().red > colors.last().blue)
        assertTrue(colors[2].alpha < colors[1].alpha)
        assertTrue(colors[2].alpha < colors[3].alpha)
    }

    @Test fun surfaceGradientStopsMatchColorsAndSpanTheEntireSurface() {
        listOf(CardRarity.SILVER, CardRarity.GOLD, CardRarity.HOLOGRAPHIC).forEach { rarity ->
            val stops = cardSurfaceStops(rarity)
            assertEquals(cardSurfaceColors(rarity).size, stops.size)
            assertEquals(0f, stops.first(), 0.0001f)
            assertEquals(1f, stops.last(), 0.0001f)
            assertTrue(stops.zipWithNext().all { (a, b) -> a < b })
        }
    }

    @Test fun onlyHolographicSurfaceShiftsAndNeutralTiltRestoresIt() {
        CardRarity.entries.forEach { rarity ->
            assertDirection(Offset.Zero, cardSurfaceGradientShift(rarity, Offset.Zero))
            val expected = if (rarity == CardRarity.HOLOGRAPHIC) Offset(0.08f, -0.06f) else Offset.Zero
            assertDirection(expected, cardSurfaceGradientShift(rarity, Offset(1f, -1f)))
        }
    }

    @Test fun localizedHighlightMovesWithinASmallInteriorRegion() {
        assertDirection(Offset(0.32f, 0.28f), cardReflectionCenter(Offset.Zero))
        val positive = cardReflectionCenter(cardLightDirection(-100f, 100f))
        val negative = cardReflectionCenter(cardLightDirection(100f, -100f))
        assertDirection(Offset(0.48f, 0.42f), positive)
        assertDirection(Offset(0.16f, 0.14f), negative)
        assertDirection(Offset(0.40f, 0.21f), cardReflectionCenter(Offset(0.5f, -0.5f)))
    }

    @Test fun perfectBreathingRequiresAVisiblePerfectCardAndMotionPermission() {
        val perfect = cardMaterialFromRating(10f)
        assertTrue(perfectHighlightEnabled(perfect, visible = true, reduceMotion = false))
        assertFalse(perfectHighlightEnabled(perfect, visible = true, reduceMotion = true))
        assertFalse(perfectHighlightEnabled(perfect, visible = false, reduceMotion = false))
        assertFalse(perfectHighlightEnabled(cardMaterialFromRating(9.5f), visible = true, reduceMotion = false))
        assertFalse(perfectHighlightEnabled(CardMaterialState(CardRarity.STANDARD, isPerfect = true), true, false))
    }

    @Test fun everyGlassSettingCombinationKeepsASubtleFallback() {
        GlassEffectLevel.entries.forEach { effect ->
            CardGlassLevel.entries.forEach { cards ->
                val expected = when {
                    effect == GlassEffectLevel.OFF || cards == CardGlassLevel.OFF -> 0.45f
                    effect == GlassEffectLevel.REDUCED -> 0.65f
                    else -> 1f
                }
                assertEquals(expected, cardReflectionStrength(effect, cards), 0.0001f)
            }
        }
    }

    @Test fun fingerToExistingTiltToLightKeepsTheSameCorner() {
        val rotation = pressTiltRotation(Offset.Zero, IntSize(200, 300), 7f)
        assertDirection(Offset(-1f, -1f), cardLightDirection(rotation.x, rotation.y))
    }

    @Test fun lightingReadsActualAnimationStateWithoutCopyingTargets() {
        val x = mutableStateOf(1f)
        val y = mutableStateOf(2f)
        val lighting = PressTiltLighting()
        lighting.bind(x, y)
        x.value = 3.5f
        y.value = -3.5f
        assertDirection(Offset(-0.5f, -0.5f), cardLightDirection(lighting.rotationX, lighting.rotationY))
        lighting.bind()
        assertDirection(Offset.Zero, cardLightDirection(lighting.rotationX, lighting.rotationY))
    }

    private fun assertDirection(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
    }

    @Test fun neutralTiltHasNeutralLighting() {
        assertDirection(Offset.Zero, cardLightDirection(0f, 0f))
    }

    @Test fun lightFollowsPressedEdgeNotOppositeEdge() {
        assertDirection(Offset(-1f, 0f), cardLightDirection(0f, -7f))
        assertDirection(Offset(1f, 0f), cardLightDirection(0f, 7f))
        assertDirection(Offset(0f, -1f), cardLightDirection(7f, 0f))
        assertDirection(Offset(0f, 1f), cardLightDirection(-7f, 0f))
    }

    @Test fun animatedIntermediateValuesKeepProportionalReflection() {
        assertDirection(Offset(0.5f, -0.5f), cardLightDirection(3.5f, 3.5f))
    }

    @Test fun springOvershootCannotMoveReflectionOutsideTheCard() {
        assertDirection(Offset(1f, -1f), cardLightDirection(8f, 8f))
        assertDirection(Offset(-1f, 1f), cardLightDirection(-100f, -100f))
    }

    @Test fun nonFiniteRotationFallsBackToNeutral() {
        assertDirection(Offset.Zero, cardLightDirection(Float.NaN, 1f))
        assertDirection(Offset.Zero, cardLightDirection(1f, Float.POSITIVE_INFINITY))
    }
}
