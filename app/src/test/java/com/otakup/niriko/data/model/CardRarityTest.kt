package com.otakup.niriko.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRarityTest {
    @Test fun requiredRatingBoundaries() {
        val cases = listOf(
            null to CardRarity.STANDARD,
            0f to CardRarity.STANDARD,
            6.9f to CardRarity.STANDARD,
            7f to CardRarity.SILVER,
            7.9f to CardRarity.SILVER,
            8f to CardRarity.GOLD,
            8.9f to CardRarity.GOLD,
            9f to CardRarity.HOLOGRAPHIC,
            9.9f to CardRarity.HOLOGRAPHIC,
            10f to CardRarity.HOLOGRAPHIC,
        )
        cases.forEach { (rating, expected) ->
            assertEquals("rating=$rating", expected, cardRarityFromRating(rating))
            assertEquals("material rating=$rating", expected, cardMaterialFromRating(rating).rarity)
        }
    }

    @Test fun perfectIsNotAFifthRarity() {
        assertEquals(4, CardRarity.entries.size)
        val material = cardMaterialFromRating(10f)
        assertEquals(CardRarity.HOLOGRAPHIC, material.rarity)
        assertTrue(material.isPerfect)
    }

    @Test fun onlyExactlyTenIsPerfect() {
        listOf(null, 0f, 7f, 8f, 9f, 9.5f, 9.9f, 9.999f, 10.001f).forEach {
            assertFalse("rating=$it", cardMaterialFromRating(it).isPerfect)
        }
    }

    @Test fun thresholdsUseActualRatingWithoutRounding() {
        assertEquals(CardRarity.STANDARD, cardRarityFromRating(6.999f))
        assertEquals(CardRarity.SILVER, cardRarityFromRating(7.999f))
        assertEquals(CardRarity.GOLD, cardRarityFromRating(8.999f))
        assertEquals(CardRarity.HOLOGRAPHIC, cardRarityFromRating(9.999f))
    }

    @Test fun halfPointRatingsRemainSupported() {
        assertEquals(CardRarity.STANDARD, cardRarityFromRating(6.5f))
        assertEquals(CardRarity.SILVER, cardRarityFromRating(7.5f))
        assertEquals(CardRarity.GOLD, cardRarityFromRating(8.5f))
        assertEquals(CardRarity.HOLOGRAPHIC, cardRarityFromRating(9.5f))
    }

    @Test fun invalidImportedRatingsFallBackWithoutBecomingPerfect() {
        listOf(-1f, -0.001f, 10.001f, 11f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals("rating=$it", CardRarity.STANDARD, cardRarityFromRating(it))
            assertEquals("material rating=$it", CardMaterialState(), cardMaterialFromRating(it))
        }
    }

    @Test fun absentRatingHasUnchangedStandardBaseline() {
        assertEquals(CardMaterialState(), cardMaterialFromRating(null))
    }
}
