package com.otakup.niriko.data.local.entity

import androidx.room.Entity

/**
 * 作品级权威评分（表 subject_external_ratings）。
 *
 * 与 subjects 解耦：不同来源的评分标尺各异（10 分制 / 100 分制 / 5 分制 / 百分比），
 * 因此同时保存 [nativeScore]（原始分）与 [scoreMax]（标尺上限），
 * UI 负责按原始标尺展示，需要横向比较时再换算到 10 分制。
 */
@Entity(
    tableName = "subject_external_ratings",
    primaryKeys = ["subjectId", "sourceId"],
)
data class SubjectExternalRatingEntity(
    val subjectId: Long,
    /** 来源标识（tmdb / imdb / metacritic / steam_review / igdb ...）。 */
    val sourceId: String,
    /** 展示名（TMDb / IMDb / Metacritic / Steam 好评率 ...）。 */
    val label: String,
    /** 换算到 10 分制的分数（便于「权威评分对比」同屏排序）。 */
    val score: Float? = null,
    /** 来源原生分（Metacritic 87、IMDb 8.6、Steam 92%）。 */
    val nativeScore: Float? = null,
    /** 原生标尺上限（10 / 100 / 5 / 100）。 */
    val scoreMax: Float = 10f,
    /** 评分人数 / 票数 / 评测数。 */
    val voteCount: Int? = null,
    /** 来源页面 URL（点击跳转）。 */
    val sourceUrl: String? = null,
    /** 附加信息 JSON（如 Steam 的「好评如潮」、OpenCritic 的推荐率）。 */
    val extraJson: String = "{}",
    val fetchedAt: Long = 0L,
)
