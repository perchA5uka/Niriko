package com.otakup.niriko.ui.animation

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PredictiveDismissTest {
    @Test fun completedGestureCommitsOnceAndResets() = runBlocking {
        val state = BackPreviewState()
        var commits = 0
        collectPredictiveDismiss(flowOf(0.2f, 0.8f), state) {
            assertEquals(0.8f, state.progress, 0f)
            commits++
        }
        assertEquals(1, commits)
        assertEquals(0f, state.progress, 0f)
    }

    @Test fun cancelledGesturePreservesStateAndResetsPreview() = runBlocking {
        val state = BackPreviewState()
        var commits = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            collectPredictiveDismiss(flow { emit(0.4f); awaitCancellation() }, state) { commits++ }
        }
        assertEquals(0.4f, state.progress, 0f)
        job.cancelAndJoin()
        assertEquals(0, commits)
        assertEquals(0f, state.progress, 0f)
    }

    @Test fun callbackFailureDoesNotLeavePreviewActive() = runBlocking {
        val state = BackPreviewState()
        val result = runCatching { collectPredictiveDismiss(flowOf(1f), state) { error("failure") } }
        assertTrue(result.isFailure)
        assertEquals(0f, state.progress, 0f)
    }

    @Test fun buttonBackWithNoProgressStillCommits() = runBlocking {
        val state = BackPreviewState()
        var committed = false
        collectPredictiveDismiss(flowOf(), state) { committed = true }
        assertTrue(committed)
    }

    @Test fun invalidProgressIsClamped() {
        val state = BackPreviewState()
        state.update(2f)
        assertEquals(1f, state.progress, 0f)
        state.update(-1f)
        assertEquals(0f, state.progress, 0f)
        state.update(Float.NaN)
        assertEquals(0f, state.progress, 0f)
    }
}
