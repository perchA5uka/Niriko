package com.otakup.niriko.data.remote.game

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType

/**
 * GameItem → SubjectEntity 映射器。
 *
 * 把任意游戏数据源搜到的条目转为本地作品条目（type=GAME），
 * 使"Bangumi 没有的词条、其他源有也能成为同等作品条目"。
 * sourceId 用数据源 id（如 "steam"），subjectId 用源内 id 的 Long 化
 * （Steam appid 本身就是数字；其他源用 id 哈希避免负数 hack）。
 *
 * sourceKey 约定：`{sourceId}:{sourceGameId}`，如 "steam:570"、"neodb:xxxx"。
 */
object GameItemMapper {

    /** 组装 sourceKey。 */
    fun sourceKey(sourceId: String, sourceGameId: String): String = "$sourceId:$sourceGameId"

    /**
     * GameItem → SubjectEntity（新条目）。
     * @param subjectId 本地主键：优先外部给定（迁移/已有）；否则用 sourceGameId 的数字形式或哈希
     */
    fun toSubject(
        sourceId: String,
        item: GameItem,
        subjectId: Long? = null,
    ): SubjectEntity {
        val id = subjectId ?: deriveSubjectId(sourceId, item.sourceGameId)
        return SubjectEntity(
            subjectId = id,
            title = item.title,
            titleCN = item.title,
            type = SubjectType.GAME,
            summary = item.summary,
            coverUrl = item.coverUrl,
            totalEpisodes = null,
            platform = item.platforms.takeIf { it.isNotEmpty() }?.joinToString(" / "),
            volumes = null,
            airDate = item.releaseDate,
            airWeekday = null,
            ratingScore = item.ratingScore,
            ratingTotal = item.ratingCount,
            rank = null,
            biliScore = null,
            biliRatingTotal = null,
            biliSeasonId = null,
            series = null,
            tags = item.tags,
            lastSyncTime = System.currentTimeMillis(),
            sourceId = sourceId,
        )
    }

    /**
     * 由 sourceGameId 派生本地 subjectId。
     * - 纯数字 id（如 Steam appid）直接用（正数，取代负数 hack）；
     * - 非数字 id（如 NeoDB uuid）用稳定哈希（Long 正数，避免负数/溢出）。
     */
    fun deriveSubjectId(sourceId: String, sourceGameId: String): Long {
        sourceGameId.toLongOrNull()?.let { return it }
        // 稳定哈希（FNV-1a 变体，用 ULong 乘法避免溢出），取正数
        var hash = 0x9E3779B97F4A7C15uL
        val input = "$sourceId:$sourceGameId"
        for (c in input) {
            hash = (hash xor c.code.toULong()) * 0x100000001B3uL
        }
        return (hash and Long.MAX_VALUE.toULong()).toLong()
    }
}
