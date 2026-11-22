package com.otakup.niriko.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Niriko 27 · S0：触感语义。 */
class NirikoHapticsTest {

    @Test
    fun 语义恰好六个() {
        assertEquals(
            listOf("Confirm", "SegmentTick", "ToggleOn", "ToggleOff", "ThresholdActivate", "Destructive"),
            NirikoHaptic.entries.map { it.name },
        )
    }

    @Test
    fun 只有破坏性操作不配套形变() {
        assertFalse(NirikoHaptic.Destructive.pairsWithMorph)
        NirikoHaptic.entries.filter { it != NirikoHaptic.Destructive }.forEach {
            assertTrue(it.name + " 属于可逆操作，应配套形变", it.pairsWithMorph)
        }
    }
}
