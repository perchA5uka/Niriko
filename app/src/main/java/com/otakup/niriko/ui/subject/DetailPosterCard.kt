package com.otakup.niriko.ui.subject

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.otakup.niriko.ui.components.LocalCardReflectionLighting
import com.otakup.niriko.ui.components.rememberCardReflectionLighting
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.otakup.niriko.ui.animation.LocalAmbientTiltDemand
import com.otakup.niriko.data.model.CardRarity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.CardMaterialState
import com.otakup.niriko.ui.animation.AnimDurationNormal
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.PressTiltLighting
import com.otakup.niriko.ui.animation.pressTilt
import com.otakup.niriko.ui.components.cardRarityMaterial
import com.otakup.niriko.ui.components.rememberDetailCardHighlight

internal fun detailCardShowsBack(rotation: Float): Boolean = rotation.isFinite() && rotation > 90f

internal fun detailCardCounterRotation(showBack: Boolean, reduceMotion: Boolean): Float =
    if (showBack && !reduceMotion) 180f else 0f

/** One physical frame and one press source; only the detail poster can flip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailPosterCard(
    subjectId: Long,
    record: DetailCardBackModel?,
    material: CardMaterialState,
    shape: Shape,
    modifier: Modifier = Modifier,
    hasCover: Boolean = true,
    front: @Composable BoxScope.() -> Unit,
) {
    var flipped by remember(subjectId, record != null) { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val rotation = animateFloatAsState(
        targetValue = if (flipped && record != null) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(AnimDurationNormal, easing = AnimEasingDefault),
        label = "detailCardFlip",
    )
    val showBack = if (reduceMotion) flipped && record != null else detailCardShowsBack(rotation.value)
    val interactionSource = remember { MutableInteractionSource() }
    val lighting = remember { PressTiltLighting() }
    val reflection = if (material.rarity != CardRarity.STANDARD) rememberCardReflectionLighting(lighting) else null
    val highlight = rememberDetailCardHighlight(material)
    val ambientDemand = LocalAmbientTiltDemand.current
    LaunchedEffect(highlight.visible.value, material.rarity, record != null, ambientDemand) {
        ambientDemand?.visibleRarity = highlight.visible.value && record != null && material.rarity != CardRarity.STANDARD
    }
    DisposableEffect(ambientDemand) {
        onDispose { ambientDemand?.visibleRarity = false }
    }
    val flipLabel = if (flipped) "翻到海报正面" else "翻到个人收藏卡背"
    val flip = { flipped = !flipped }
    val interaction = if (record != null) {
        Modifier.pressTilt(interactionSource, lighting = lighting)
            .combinedClickable(interactionSource = interactionSource, indication = null,
                onClick = {}, onDoubleClick = flip)
            .onPreviewKeyEvent {
                if (it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar) {
                    if (it.type == KeyEventType.KeyUp) flip()
                    true
                } else false
            }
            .semantics {
                stateDescription = if (showBack) "个人收藏卡背" else "作品海报正面"
                customActions = listOf(CustomAccessibilityAction(flipLabel) { flip(); true })
            }
    } else if (hasCover) Modifier.pressTilt(lighting = lighting) else Modifier
    CompositionLocalProvider(LocalCardReflectionLighting provides reflection) {
    Box(
        modifier = modifier.then(interaction)
            .then(highlight.visibilityModifier)
            .graphicsLayer {
                rotationY = if (reduceMotion) 0f else rotation.value.coerceIn(0f, 180f)
                cameraDistance = 12f * density
            }
            .clip(shape)
            .then(if (hasCover) Modifier.shadow(10.dp, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.matchParentSize()
                .graphicsLayer {
                    // Counter-rotate the back content; it must never appear as mirrored text.
                    rotationY = detailCardCounterRotation(showBack, reduceMotion)
                    cameraDistance = 12f * density
                }
                .cardRarityMaterial(
                    if (showBack) CardMaterialState() else material, shape, lighting,
                ) { highlight.amount.value },
            contentAlignment = Alignment.Center,
        ) {
            if (showBack && record != null) {
                DetailCardBack(record, shape, material = material, lighting = lighting,
                    breathing = { highlight.amount.value })
            } else front()
        }
    }
    }
}
