package com.otakup.niriko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import com.otakup.niriko.ui.animation.clampAmbientLight
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.otakup.niriko.data.model.CardMaterialState
import com.otakup.niriko.data.model.CardRarity
import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.PressTiltLighting
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.min

/** Reuses the actual animated tilt, including its spring return, rather than pointer targets. */
internal fun cardLightDirection(rotationX: Float, rotationY: Float): Offset {
    if (!rotationX.isFinite() || !rotationY.isFinite()) return Offset.Zero
    return Offset((rotationY / 7f).coerceIn(-1f, 1f), (-rotationX / 7f).coerceIn(-1f, 1f))
}

internal fun cardReflectionStrength(effect: GlassEffectLevel, cards: CardGlassLevel): Float = when {
    effect == GlassEffectLevel.OFF || cards == CardGlassLevel.OFF -> 0.45f
    effect == GlassEffectLevel.REDUCED -> 0.65f
    else -> 1f
}

internal fun cardReflectionAlpha(rarity: CardRarity, engagement: Float, breathing: Float): Float {
    if (rarity == CardRarity.STANDARD) return 0f
    val press = engagement.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val breath = breathing.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val resting = if (rarity == CardRarity.HOLOGRAPHIC) 0.08f else 0.07f
    return resting + press * 0.055f + breath * 0.025f
}

/** Static surface budget is separate from the smaller moving specular highlight. */
internal fun cardSurfaceColors(rarity: CardRarity): List<Color> = when (rarity) {
    CardRarity.STANDARD -> emptyList()
    CardRarity.SILVER -> listOf(
        Color(0xFFAAC6DF).copy(alpha = 0.08f),
        Color(0xFFD9E6F2).copy(alpha = 0.045f),
        Color(0xFFD1DEEB).copy(alpha = 0.025f),
        Color(0xFF8DABC8).copy(alpha = 0.085f),
    )
    CardRarity.GOLD -> listOf(
        Color(0xFFD9AA4B).copy(alpha = 0.09f),
        Color(0xFFF2D797).copy(alpha = 0.05f),
        Color(0xFFEDD8AF).copy(alpha = 0.028f),
        Color(0xFFC89738).copy(alpha = 0.095f),
    )
    CardRarity.HOLOGRAPHIC -> listOf(
        Color(0xFF8DA3E6).copy(alpha = 0.09f),
        Color(0xFF76D4D1).copy(alpha = 0.065f),
        Color(0xFFE0DDE8).copy(alpha = 0.028f),
        Color(0xFFE59ABF).copy(alpha = 0.075f),
        Color(0xFFE6BF71).copy(alpha = 0.085f),
    )
}

internal fun cardReflectionCenter(direction: Offset): Offset = Offset(
    0.32f + direction.x * 0.16f,
    0.28f + direction.y * 0.14f,
)

internal fun cardSurfaceGradientShift(rarity: CardRarity, direction: Offset): Offset =
    if (rarity == CardRarity.HOLOGRAPHIC) Offset(direction.x * 0.08f, direction.y * 0.06f)
    else Offset.Zero

internal fun cardSurfaceStops(rarity: CardRarity): List<Float> =
    if (rarity == CardRarity.HOLOGRAPHIC) listOf(0f, 0.28f, 0.5f, 0.76f, 1f)
    else listOf(0f, 0.26f, 0.52f, 1f)

internal fun perfectHighlightEnabled(material: CardMaterialState, visible: Boolean, reduceMotion: Boolean): Boolean =
    material.rarity == CardRarity.HOLOGRAPHIC && material.isPerfect && visible && !reduceMotion

internal fun cardOutlinePath(outline: Outline): Path = Path().apply {
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}

internal fun DrawScope.drawClippedCardSurface(shape: Shape, content: DrawScope.() -> Unit) {
    clipPath(cardOutlinePath(shape.createOutline(size, layoutDirection, this))) {
        content()
    }
}

internal fun cardBrightContrast(luminance: Float): Float =
    ((luminance.takeIf { it.isFinite() } ?: 0.5f) - 0.45f).div(0.55f).coerceIn(0f, 1f)

internal fun cardDiffusionCenter(direction: Offset): Offset {
    val safe = clampAmbientLight(direction)
    return Offset(0.26f + safe.x * 0.09f, 0.28f + safe.y * 0.08f)
}

internal fun cardSpecularRadii(size: Size): Offset {
    val radius = min(size.width, size.height).coerceAtLeast(1f) * 0.52f
    return Offset(radius * 1.22f, radius * 0.66f)
}

/** The same clipped, localized optics serve glass surfaces and poster overlays. */
internal fun cardRarityPainter(
    rarity: CardRarity,
    outline: Outline,
    size: Size,
    strength: Float,
    dark: Boolean,
    strokeWidth: Float,
    luminance: Float = if (dark) 0.15f else 0.85f,
): DrawScope.(Offset, Float) -> Unit {
    if (rarity == CardRarity.STANDARD) return { _, _ -> }
    val clip = cardOutlinePath(outline)
    val surfaceColors = cardSurfaceColors(rarity).map { it.copy(alpha = it.alpha * strength) }
    val surfaceStops = cardSurfaceStops(rarity).zip(surfaceColors).toTypedArray()
    val palette = when (rarity) {
        CardRarity.SILVER -> listOf(Color(0xFF94B4CE), Color(0xFFF1F7FF), Color(0xFF7597B5))
        CardRarity.GOLD -> listOf(Color(0xFFD5A342), Color(0xFFFFE3A0), Color(0xFFAF7B29))
        CardRarity.HOLOGRAPHIC -> listOf(Color(0xFF65C9CF), Color(0xFFE8A0D0), Color(0xFFDAB358))
        CardRarity.STANDARD -> emptyList()
    }
    val edgeAlpha = (if (dark) 0.60f else 0.48f) * strength
    val edge = Brush.linearGradient(
        listOf(palette[0].copy(alpha = edgeAlpha), palette[1].copy(alpha = edgeAlpha * 0.80f),
            palette[2].copy(alpha = edgeAlpha * 0.85f)),
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
    val surface = Brush.linearGradient(colorStops = surfaceStops,
        start = Offset.Zero, end = Offset(size.width, size.height))
    val bright = cardBrightContrast(luminance)
    val shoulderAlpha = (0.018f + bright * 0.042f) * strength
    val radius = min(size.width, size.height).coerceAtLeast(1f) * 0.52f
    val stroke = Stroke(strokeWidth)
    return { input, breath ->
        val direction = clampAmbientLight(input)
        clipPath(clip) {
            val shift = cardSurfaceGradientShift(rarity, direction)
            val tint = if (shift == Offset.Zero) surface else Brush.linearGradient(
                colorStops = surfaceStops,
                start = Offset(size.width * shift.x, size.height * shift.y),
                end = Offset(size.width * (1f + shift.x), size.height * (1f + shift.y)),
            )
            drawOutline(outline, tint)
            val diffuseCenter = cardDiffusionCenter(direction)
            val diffusion = Brush.radialGradient(
                colors = listOf(palette[0].copy(alpha = 0.09f * strength),
                    palette[2].copy(alpha = 0.045f * strength), Color.Transparent),
                center = Offset(size.width * diffuseCenter.x, size.height * diffuseCenter.y),
                radius = maxOf(size.width, size.height).coerceAtLeast(1f) * 0.60f,
            )
            drawOutline(outline, diffusion)
            val engagement = maxOf(abs(direction.x), abs(direction.y))
            val alpha = cardReflectionAlpha(rarity, engagement, breath).coerceAtMost(0.15f) * strength
            val normalizedCenter = cardReflectionCenter(direction)
            val center = Offset(size.width * normalizedCenter.x, size.height * normalizedCenter.y)
            // A neutral, local shoulder carries luminance contrast on white, without tinting the card.
            val shoulder = Brush.radialGradient(
                0f to Color.Transparent,
                0.45f to Color(0xFF43464B).copy(alpha = shoulderAlpha),
                1f to Color.Transparent,
                center = center, radius = radius * 1.16f,
            )
            val reflection = Brush.radialGradient(
                colors = listOf(palette[1].copy(alpha = alpha), palette[0].copy(alpha = alpha * 0.65f),
                    palette[2].copy(alpha = alpha * 0.25f), Color.Transparent),
                center = center, radius = radius,
            )
            // Draw the gradients in an elliptical frame, but keep clipping in card coordinates.
            scale(1.22f, 0.66f, pivot = center) {
                drawCircle(shoulder, radius * 1.16f, center)
                drawCircle(reflection, radius, center)
            }
            drawOutline(outline, edge, style = stroke)
        }
    }
}

/** Glass calls this from its surface callback, before its sharp foreground. */
@Composable
internal fun rememberCardRaritySurface(
    material: CardMaterialState,
    shape: Shape,
    lighting: PressTiltLighting?,
    breathing: () -> Float,
): (DrawScope.() -> Unit)? {
    if (material.rarity == CardRarity.STANDARD) return null
    val dark = LocalDarkTheme.current
    val luminance = LocalGlassLuminance.current
    val reflection = rememberCardReflectionLighting(lighting)
    val reduceMotion = LocalReduceMotion.current
    val strength = cardReflectionStrength(LocalGlassEffect.current, LocalCardGlassLevel.current)
    val currentBreathing by rememberUpdatedState(breathing)
    return remember(material, shape, lighting, dark, reduceMotion, strength, luminance, reflection) {
        var cachedSize = Size.Unspecified
        var cachedDensity = Float.NaN
        var cachedLayout: LayoutDirection? = null
        var painter: (DrawScope.(Offset, Float) -> Unit)? = null
        val draw: DrawScope.() -> Unit = {
            if (cachedSize != size || cachedDensity != density || cachedLayout != layoutDirection) {
                cachedSize = size
                cachedDensity = density
                cachedLayout = layoutDirection
                painter = cardRarityPainter(material.rarity, shape.createOutline(size, layoutDirection, this),
                    size, strength, dark, 2.25.dp.toPx(), luminance)
            }
            val direction = reflection.direction()
            painter?.invoke(this, direction, if (reduceMotion || !material.isPerfect) 0f else currentBreathing())
        }
        draw
    }
}

/** Optical accent only; no backdrop capture, shader, bitmap or gesture system. */
@Composable
fun Modifier.cardRarityMaterial(
    material: CardMaterialState,
    shape: Shape,
    lighting: PressTiltLighting? = null,
    behindContent: Boolean = false,
    breathing: () -> Float = { 0f },
): Modifier {
    if (material.rarity == CardRarity.STANDARD) return this
    val dark = LocalDarkTheme.current
    val luminance = LocalGlassLuminance.current
    val reflection = rememberCardReflectionLighting(lighting)
    val reduceMotion = LocalReduceMotion.current
    val strength = cardReflectionStrength(LocalGlassEffect.current, LocalCardGlassLevel.current)
    return drawWithCache {
        val painter = cardRarityPainter(material.rarity, shape.createOutline(size, layoutDirection, this),
            size, strength, dark, 2.25.dp.toPx(), luminance)
        onDrawWithContent {
            if (!behindContent) drawContent()
            val direction = reflection.direction()
            painter(this, direction, if (reduceMotion || !material.isPerfect) 0f else breathing())
            if (behindContent) drawContent()
        }
    }
}

class DetailCardHighlight internal constructor(
    val visibilityModifier: Modifier,
    val amount: State<Float>,
    val visible: State<Boolean>,
)

/** Only composed detail posters opt in; library cards never start this loop. */
@Composable
fun rememberDetailCardHighlight(material: CardMaterialState): DetailCardHighlight {
    val amount = remember { Animatable(0f) }
    val visibleState = remember { mutableStateOf(false) }
    var visible by visibleState
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = LocalReduceMotion.current
    val enabled = perfectHighlightEnabled(material, visible, reduceMotion)
    LaunchedEffect(enabled, lifecycle) {
        amount.snapTo(0f)
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            amount.snapTo(0f)
            while (isActive) {
                amount.animateTo(1f, tween(3000, easing = AnimEasingDefault))
                amount.animateTo(0f, tween(3000, easing = AnimEasingDefault))
            }
        }
    }
    val visibilityModifier = Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = bounds.width > 0f && bounds.height > 0f && bounds.right > 0f && bounds.bottom > 0f &&
            bounds.left < view.width && bounds.top < view.height
    }
    return DetailCardHighlight(visibilityModifier, amount.asState(), visibleState)
}
