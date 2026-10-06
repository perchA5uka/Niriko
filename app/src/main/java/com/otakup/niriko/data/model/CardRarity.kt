package com.otakup.niriko.data.model

/** Display-only material; never persisted with the collection. */
enum class CardRarity {
    STANDARD,
    SILVER,
    GOLD,
    HOLOGRAPHIC,
}

data class CardMaterialState(
    val rarity: CardRarity = CardRarity.STANDARD,
    val isPerfect: Boolean = false,
)

fun cardRarityFromRating(rating: Float?): CardRarity = when {
    rating == null || !rating.isFinite() || rating !in 0f..10f -> CardRarity.STANDARD
    rating >= 9f -> CardRarity.HOLOGRAPHIC
    rating >= 8f -> CardRarity.GOLD
    rating >= 7f -> CardRarity.SILVER
    else -> CardRarity.STANDARD
}

fun cardMaterialFromRating(rating: Float?): CardMaterialState = CardMaterialState(
    rarity = cardRarityFromRating(rating),
    isPerfect = rating == 10f,
)
