package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 我的每集评分（表 episode_my_ratings）。纯本地用户数据，跟随备份 / WebDAV 同步。
 * 与权威评分分表存放：这是"我的"评价，不是"他人的"评分。
 */
@Entity(
    tableName = "episode_my_ratings",
    indices = [Index(value = ["subjectId"])],
)
data class EpisodeMyRatingEntity(
    /** Bangumi 章节 id。 */
    @PrimaryKey
    val epId: Long,
    val subjectId: Long,
    /** 0-10 分。 */
    val score: Float,
    /**
     * 本集短评（单集二级页的「我的评价」）。
     * 阶段 B 新增：此前每集只能存一个分数，无法写下任何文字。
     */
    val comment: String? = null,
    /** 二刷标记。 */
    val rewatch: Boolean = false,
    val ratedAt: Long = 0L,
)
