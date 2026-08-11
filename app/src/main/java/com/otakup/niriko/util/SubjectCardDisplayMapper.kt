package com.otakup.niriko.util

import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectCardDisplayModel
import com.otakup.niriko.data.model.SubjectType

/**
 * SubjectEntity → SubjectCardDisplayModel 的 Mapper。
 * 职责单一：提取展示所需数据，不包含 UI / 业务逻辑。
 */
fun SubjectEntity.toCardDisplayModel(steam: SteamGameEntity? = null): SubjectCardDisplayModel {
    val resolved = TitleResolver.resolve(titleCN, title)
    val primaryTitle = resolved.primary
    val secondaryTitle = resolved.secondary

    val typeLabel = when (type) {
        SubjectType.ANIME -> "动画"
        SubjectType.BOOK -> "书籍"
        SubjectType.MANGA -> "漫画"
        SubjectType.GAME -> "游戏"
        SubjectType.MUSIC -> "音乐"
        SubjectType.REAL -> "三次元"
        SubjectType.PERSON -> "人物"
        SubjectType.OTHER -> "其他"
    }

    val scoreText = ratingScore?.let { "%.1f".format(it) }
    val countText = ratingTotal?.let { "${it}人评价" }
    val ratingText = when {
        scoreText != null && countText != null -> "${scoreText}（${countText}）"
        scoreText != null -> scoreText
        countText != null -> countText
        else -> null
    }

    val secondaryInfo = when (type) {
        SubjectType.ANIME -> platform ?: if (totalEpisodes != null && totalEpisodes > 0) "${totalEpisodes} 集" else null
        SubjectType.GAME -> platform
        SubjectType.MUSIC -> platform
        SubjectType.BOOK, SubjectType.MANGA, SubjectType.REAL, SubjectType.PERSON, SubjectType.OTHER -> null
    }

    val description = summary?.takeIf { it.isNotBlank() }?.take(120)

    // Steam 补充信息（已绑定游戏卡）：价格 + 当前在线
    val steamInfoText = steam?.takeIf { type == SubjectType.GAME }?.let { s ->
        buildString {
            s.priceCents?.let { cents ->
                val symbol = if (s.currency == "CNY") "¥" else (s.currency ?: "")
                append(symbol).append(cents / 100)
                if (cents % 100 != 0) append(".").append((cents % 100).toString().padStart(2, '0'))
            }
            s.currentPlayers?.let { players ->
                if (isNotEmpty()) append(" · ")
                append(if (players >= 1000) "${players / 1000}K" else "$players").append(" 在线")
            }
        }.takeIf { it.isNotEmpty() }
    }

    return SubjectCardDisplayModel(
        cover = coverUrl,
        primaryTitle = primaryTitle,
        secondaryTitle = secondaryTitle,
        typeLabel = typeLabel,
        ratingText = ratingText,
        secondaryInfo = secondaryInfo,
        description = description,
        steamInfoText = steamInfoText,
    )
}
