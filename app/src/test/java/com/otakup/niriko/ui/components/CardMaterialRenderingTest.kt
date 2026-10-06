package com.otakup.niriko.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.CardRarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardMaterialRenderingTest {
    @Test fun glassBaseAndMaterialShareRoundedCornersAtDifferentDensities() {
        listOf(1f, 2f).forEach { density ->
            listOf(8, 20, 36).forEach { radius ->
                CardRarity.entries.forEach { rarity ->
                    val bitmap = render(rarity, RoundedCornerShape(radius.dp), density = density)
                    assertEquals("$rarity/$radius/$density top left", 0, bitmap.getPixel(0, 0))
                    assertEquals(0, bitmap.getPixel(199, 0))
                    assertEquals(0, bitmap.getPixel(0, 279))
                    assertEquals(0, bitmap.getPixel(199, 279))
                    assertTrue(android.graphics.Color.alpha(bitmap.getPixel(100, 140)) > 0)
                }
            }
        }
    }

    @Test fun asymmetricCornersRespectLayoutDirectionForBothLayers() {
        val shape = RoundedCornerShape(topStart = 40.dp, topEnd = 8.dp,
            bottomEnd = 40.dp, bottomStart = 8.dp)
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layout ->
            val bitmap = render(CardRarity.GOLD, shape, layout = layout)
            val largeX = if (layout == LayoutDirection.Ltr) 5 else 194
            val smallX = if (layout == LayoutDirection.Ltr) 194 else 5
            assertEquals(0, bitmap.getPixel(largeX, 5))
            assertTrue(android.graphics.Color.alpha(bitmap.getPixel(smallX, 5)) > 0)
        }
    }

    @Test fun genericShapeClipsSurfaceAndStrokeWithoutRectangularLeakage() {
        val shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                Outline.Generic(Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width / 2f, size.height)
                    lineTo(0f, size.height / 2f)
                    close()
                })
        }
        val bitmap = render(CardRarity.HOLOGRAPHIC, shape)
        assertEquals(0, bitmap.getPixel(20, 20))
        assertEquals(0, bitmap.getPixel(180, 260))
        assertTrue(android.graphics.Color.alpha(bitmap.getPixel(100, 140)) > 0)
    }

    @Test fun restingDiffusionChangesInteriorPixelsInLightDarkAndFallbackSettings() {
        listOf(Color(0xFFF0F0F0), Color(0xFF202020)).forEach { base ->
            listOf(1f, 0.65f, 0.45f).forEach { strength ->
                val standard = render(CardRarity.STANDARD, base = base, strength = strength)
                listOf(CardRarity.SILVER, CardRarity.GOLD, CardRarity.HOLOGRAPHIC).forEach { rarity ->
                    val tinted = render(rarity, base = base, strength = strength)
                    val delta = channelDelta(standard.getPixel(32, 56), tinted.getPixel(32, 56))
                    assertTrue("$rarity/$base/$strength interior delta=$delta", delta >= 3)
                    assertEquals(0, tinted.getPixel(0, 0))
                }
            }
        }
    }

    @Test fun standardPainterAddsNoPixelsToPosterContent() {
        val bitmap = render(CardRarity.STANDARD, base = Color(0xFF636363))
        assertEquals(android.graphics.Color.rgb(99, 99, 99), bitmap.getPixel(100, 140))
    }

    @Test fun raritySurfaceIsBelowSharpForegroundPixels() {
        val bitmap = render(CardRarity.HOLOGRAPHIC, foreground = true)
        assertEquals(android.graphics.Color.MAGENTA, bitmap.getPixel(100, 140))
    }

    @Test fun everyRarityMovesOnDarkGrayWhiteAndColoredBackgrounds() {
        val backgrounds = listOf(Color(0xFF202020), Color(0xFF888888), Color.White, Color(0xFF438E8D))
        for (base in backgrounds) for (rarity in CardRarity.entries) {
            val left = render(rarity, base = base, direction = Offset(-1f, -1f))
            val right = render(rarity, base = base, direction = Offset(1f, 1f))
            var maxDelta = 0
            for (y in 35..170 step 5) for (x in 25..165 step 5) {
                maxDelta = maxOf(maxDelta, channelDelta(left.getPixel(x, y), right.getPixel(x, y)))
            }
            if (rarity == CardRarity.STANDARD) assertEquals(0, maxDelta)
            else assertTrue("$rarity/$base moving delta=$maxDelta", maxDelta >= 3)
        }
    }

    @Test fun whiteBackgroundHasLocalContrastWithoutADarkOrColoredFill() {
        val holo = render(CardRarity.HOLOGRAPHIC, base = Color.White)
        val standard = render(CardRarity.STANDARD, base = Color.White)
        var localDelta = 0
        for (y in 35..140) for (x in 30..150) localDelta = maxOf(localDelta, channelDelta(holo.getPixel(x, y), standard.getPixel(x, y)))
        assertTrue(localDelta >= 3)
        assertTrue(channelDelta(holo.getPixel(100, 240), standard.getPixel(100, 240)) <= 7)
    }

    @Test fun standardIsPixelIdenticalForAllDirectionsAndBreathing() {
        val a = render(CardRarity.STANDARD, direction = Offset(-1f, 1f), breath = 1f)
        val b = render(CardRarity.STANDARD, direction = Offset(1f, -1f))
        for (y in 0 until 280) for (x in 0 until 200) assertEquals(a.getPixel(x, y), b.getPixel(x, y))
    }

    @Test fun rarityBordersAreClearlyVisibleOnLightAndDarkBackgrounds() {
        for (base in listOf(Color.White, Color(0xFF202020))) {
            val standard = render(CardRarity.STANDARD, base = base)
            for (rarity in listOf(CardRarity.SILVER, CardRarity.GOLD, CardRarity.HOLOGRAPHIC)) {
                val bitmap = render(rarity, base = base)
                val delta = channelDelta(standard.getPixel(199, 140), bitmap.getPixel(199, 140))
                assertTrue("$rarity/$base border contrast=$delta", delta >= 15)
                assertEquals(0, bitmap.getPixel(0, 0))
            }
        }
    }

    private fun render(
        rarity: CardRarity,
        shape: Shape = RoundedCornerShape(20.dp),
        density: Float = 1f,
        layout: LayoutDirection = LayoutDirection.Ltr,
        base: Color = Color(0xFF202020),
        strength: Float = 1f,
        foreground: Boolean = false,
        direction: Offset = Offset.Zero,
        breath: Float = 0f,
    ): Bitmap {
        val size = Size(200f, 280f)
        val bitmap = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888)
        val drawDensity = Density(density)
        val painter = cardRarityPainter(rarity, shape.createOutline(size, layout, drawDensity),
            size, strength, dark = base.red < 0.5f, strokeWidth = 2.25f * density)
        CanvasDrawScope().draw(drawDensity, layout, Canvas(bitmap.asImageBitmap()), size) {
            drawClippedCardSurface(shape) {
                drawRect(base)
                painter(this, direction, breath)
            }
            if (foreground) drawRect(Color.Magenta, topLeft = Offset(90f, 130f), size = Size(20f, 20f))
        }
        return bitmap
    }

    private fun channelDelta(a: Int, b: Int): Int = maxOf(
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
        abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
        abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
    )
}
