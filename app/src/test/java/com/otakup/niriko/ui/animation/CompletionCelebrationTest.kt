package com.otakup.niriko.ui.animation

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionCelebrationTest {
    @Test fun playbackIsActiveUntilAnimationFinishes() = runBlocking {
        val state = CompletionCelebrationState()
        assertFalse(state.isActive)
        state.play { assertTrue(state.isActive) }
        assertFalse(state.isActive)
    }

    @Test fun cancelledPlaybackUnblocksReturn() = runBlocking {
        val state = CompletionCelebrationState()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            state.play { awaitCancellation() }
        }
        assertTrue(state.isActive)
        job.cancelAndJoin()
        assertFalse(state.isActive)
    }

    @Test fun overlappingPlaybackIsDroppedButNextCompletionCanPlay() = runBlocking {
        val state = CompletionCelebrationState()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            state.play { awaitCancellation() }
        }
        var played = false
        state.play { played = true }
        assertFalse(played)
        job.cancelAndJoin()
        state.play { played = true }
        assertTrue(played)
        assertFalse(state.isActive)
    }

    @Test fun failedPlaybackDoesNotLeaveReturnBlocked() = runBlocking {
        val state = CompletionCelebrationState()
        val result = runCatching { state.play { error("animation failed") } }
        assertTrue(result.isFailure)
        assertFalse(state.isActive)
    }

    @Test fun burstIsSmallAndShortLived() {
        assertTrue(CELEBRATION_DURATION_MS in 700..1500)
        assertTrue(CELEBRATION_PARTICLE_COUNT in 12..32)
    }

    @Test fun particlesDisappearAtTheEnd() {
        assertEquals(1f, celebrationAlpha(0f), 0.0001f)
        assertEquals(1f, celebrationAlpha(0.5f), 0.0001f)
        assertEquals(0.5f, celebrationAlpha(0.825f), 0.0001f)
        assertEquals(0f, celebrationAlpha(1f), 0.0001f)
        assertEquals(0f, celebrationAlpha(2f), 0.0001f)
    }

    @Test fun confirmationScaleIsSubtleAndReturnsToNeutral() {
        assertEquals(1f, celebrationScale(0f), 0.0001f)
        assertEquals(1.08f, celebrationScale(0.5f), 0.0001f)
        assertEquals(1f, celebrationScale(1f), 0.0001f)
        assertEquals(1f, celebrationScale(-1f), 0.0001f)
        assertEquals(1f, celebrationScale(2f), 0.0001f)
    }
}
