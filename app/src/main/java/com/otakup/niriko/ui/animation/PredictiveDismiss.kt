package com.otakup.niriko.ui.animation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

internal class BackPreviewState {
    var progress by mutableFloatStateOf(0f)
        private set

    fun update(value: Float) {
        progress = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    fun reset() { progress = 0f }
}

/** Only a normally completed gesture commits; cancellation preserves local data. */
internal suspend fun collectPredictiveDismiss(
    progress: Flow<Float>,
    state: BackPreviewState,
    onCommit: suspend () -> Unit,
) {
    try {
        progress.collect { state.update(it) }
        onCommit()
    } finally {
        state.reset()
    }
}

@Composable
internal fun PredictiveDismissHandler(
    enabled: Boolean,
    preview: BackPreviewState,
    onCommit: suspend () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { events ->
        collectPredictiveDismiss(events.map { it.progress }, preview, onCommit)
    }
}
