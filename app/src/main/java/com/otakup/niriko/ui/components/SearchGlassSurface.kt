package com.otakup.niriko.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.ui.theme.LocalGlassEffect

// The host supplies the current page capture, never the wallpaper-only card source.
val LocalSearchGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Dock material without its drag owner: the search state machine owns all gestures. */
@Composable
fun Modifier.searchGlassSurface(
    shape: Shape,
    source: Backdrop? = LocalSearchGlassBackdrop.current,
): Modifier {
    val backdrop = source
    val effect = LocalGlassEffect.current
    val surface = MaterialTheme.colorScheme.surfaceContainer
    if (backdrop == null || effect == GlassEffectLevel.OFF) {
        return clip(shape).background(surface.copy(alpha = 0.94f))
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            if (effect == GlassEffectLevel.FULL) lens(24.dp.toPx(), 24.dp.toPx())
        },
        onDrawSurface = { drawRect(surface.copy(alpha = 0.4f)) },
    )
}
