package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.otakup.niriko.util.EpisodeWavePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Subscribe at page scope, not inside a lazily composed episode section. */
@Composable
fun rememberEpisodeCompletionWave(events: Flow<Long>, subjectId: Long): State<Float> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = LocalReduceMotion.current
    val progress = remember(subjectId) { Animatable(1f) }
    LaunchedEffect(events, lifecycle, subjectId, reduceMotion) {
        progress.snapTo(1f)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                events.filter { it == subjectId }.collectLatest {
                    if (reduceMotion) return@collectLatest
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(EpisodeWavePolicy.DURATION_MS, easing = LinearEasing))
                }
            } finally {
                withContext(NonCancellable) { progress.snapTo(1f) }
            }
        }
    }
    return progress.asState()
}
