package com.otakup.niriko.data.remote.bangumi.dto

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.util.resolveCoverUrl

/**
 * SubjectDto → SubjectEntity 扩展函数。
 * SubjectDetailResponseDto 字段结构与 SubjectDto 一致，直接复用。
 */
fun SubjectDto.toEntity(): SubjectEntity {
    val now = System.currentTimeMillis()
    return SubjectEntity(
        subjectId = id,
        title = name,
        titleCN = nameCn,
        type = mapSubjectType(type),
        summary = summary,
        coverUrl = resolveCoverUrl(images),
        totalEpisodes = totalEpisodes?.takeIf { it > 0 } ?: eps?.takeIf { it > 0 },
        platform = platform,
        volumes = volumes,
        airDate = date ?: airDateField,
        airWeekday = airWeekday,
        ratingScore = rating?.score?.toFloat(),
        ratingTotal = rating?.total?.takeIf { it > 0 },
        rank = rating?.rank?.takeIf { it > 0 },
        series = series,
        tags = tags?.map { it.name } ?: emptyList(),
        lastSyncTime = now,
    )
}

fun SubjectDetailResponseDto.toEntity(): SubjectEntity {
    val now = System.currentTimeMillis()
    return SubjectEntity(
        subjectId = id,
        title = name,
        titleCN = nameCn,
        type = mapSubjectType(type),
        summary = summary,
        coverUrl = resolveCoverUrl(images),
        totalEpisodes = totalEpisodes?.takeIf { it > 0 } ?: eps?.takeIf { it > 0 },
        platform = platform,
        volumes = volumes,
        airDate = date,
        airWeekday = airWeekday,
        ratingScore = rating?.score?.toFloat(),
        ratingTotal = rating?.total?.takeIf { it > 0 },
        rank = rating?.rank?.takeIf { it > 0 },
        series = series,
        tags = tags?.map { it.name } ?: emptyList(),
        lastSyncTime = now,
    )
}

/**
 * Bangumi type 数字 → SubjectType 枚举。
 * 映射规则：
 * 1 (BOOK)  → BOOK
 * 2 (ANIME) → ANIME
 * 3 (MUSIC) → MUSIC
 * 4 (GAME)  → GAME
 * 其他       → OTHER
 */
fun mapSubjectType(bangumiType: Int): SubjectType = when (bangumiType) {
    1 -> SubjectType.BOOK
    2 -> SubjectType.ANIME
    3 -> SubjectType.MUSIC
    4 -> SubjectType.GAME
    6 -> SubjectType.REAL
    else -> SubjectType.OTHER
}