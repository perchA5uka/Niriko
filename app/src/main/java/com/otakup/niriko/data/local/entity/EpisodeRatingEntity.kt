package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 每集评分（表 episode_ratings）。
 *
 * 按 (epId, sourceId) 复合主键存储，因此同一集可以同时保存 TMDb 与 IMDb 两条评分；
 * 抓取失败只会丢一个源，不影响其它源。epId 与 [EpisodeEntity.epId] 对齐（Bangumi 章节 id）。
 */
@Entity(
    tableName = "episode_ratings",
    primaryKeys = ["epId", "sourceId"],
    indices = [Index(value = ["subjectId"])],
)
data class EpisodeRatingEntity(
    /** Bangumi 章节 id。 */
    val epId: Long,
    val subjectId: Long,
    /** tmdb / imdb（未来可扩 mal / douban）。 */
    val sourceId: String,
    /** 换算到 10 分制。 */
    val score: Float? = null,
    val scoreMax: Float = 10f,
    val voteCount: Int? = null,
    val fetchedAt: Long = 0L,
)
