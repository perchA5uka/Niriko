package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeWavePolicyTest {
    @Test
    fun badgesLightInOrderInsteadOfSweepingTheWholeSection() {
        val firstPeak = EpisodeWavePolicy.PULSE_FRACTION / 2f
        assertTrue(EpisodeWavePolicy.badgeAlpha(firstPeak, 0, 4) > 0.99f)
        assertEquals(0f, EpisodeWavePolicy.badgeAlpha(firstPeak, 1, 4), 0.001f)
        assertEquals(0f, EpisodeWavePolicy.badgeAlpha(firstPeak, 3, 4), 0.001f)
        val lastPeak = 1f - firstPeak
        assertTrue(EpisodeWavePolicy.badgeAlpha(lastPeak, 3, 4) > 0.99f)
        assertEquals(0f, EpisodeWavePolicy.badgeAlpha(lastPeak, 0, 4), 0.001f)
    }

    @Test
    fun eachBadgeHasItsOwnPeakAndResetsAtCompletion() {
        for (count in listOf(1, 2, 12, 100)) {
            for (index in 0 until count) {
                val start = if (count == 1) 0f else index.toFloat() / (count - 1) * (1f - EpisodeWavePolicy.PULSE_FRACTION)
                assertTrue(EpisodeWavePolicy.badgeAlpha(start + EpisodeWavePolicy.PULSE_FRACTION / 2f, index, count) > 0.99f)
                assertEquals(0f, EpisodeWavePolicy.badgeAlpha(0f, index, count), 0.001f)
                assertEquals(0f, EpisodeWavePolicy.badgeAlpha(1f, index, count), 0.001f)
            }
        }
    }

    @Test
    fun invalidAndEmptyInputsDoNotLightBadges() {
        assertEquals(0f, EpisodeWavePolicy.badgeAlpha(0.5f, 0, 0), 0.001f)
        assertEquals(0f, EpisodeWavePolicy.badgeAlpha(0.5f, -1, 12), 0.001f)
        assertEquals(0f, EpisodeWavePolicy.badgeAlpha(Float.NaN, 0, 1), 0.001f)
    }
}
