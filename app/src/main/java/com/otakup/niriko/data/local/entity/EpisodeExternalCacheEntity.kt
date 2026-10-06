package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 每集「外部源对齐结果」中 Episode 表没有的那部分（表 episode_external_cache）。B15 阶段 1。
 *
 * ## 刻意不重复剧照 URL
 *
 * 单集剧照已经落在 [EpisodeEntity.stillUrl]（B12 的回填链路写的就是那里），
 * 计划书 §7.2 要求「已有 Episode 表优先复用，避免重复真相」—— 所以这张表只存
 * Episode 表没有的东西：TMDb 侧对齐出来的**季号/集号**、**TMDb 标题**，以及
 * **对齐状态**（未绑定 / 未对齐 / 来源无图 / 请求失败 —— 这四种必须能区分，
 * 否则 UI 只能含糊地说「没有图片」）。
 *
 * 因此它不会与 EpisodeEntity 争同一份事实，只是为了「重启后不用重新对齐一遍」。
 */
@Entity(
    tableName = "episode_external_cache",
    primaryKeys = ["subjectId", "epId"],
    indices = [Index(value = ["expiresAt"])],
)
data class EpisodeExternalCacheEntity(
    val subjectId: Long,
    /** Bangumi 章节 id，与 [EpisodeEntity.epId] 对齐。 */
    val epId: Long,
    /** TMDb 对齐结果：季号 / 季内集号。未对齐时为 null。 */
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /** TMDb 侧标题（仅用于补空缺，不覆盖 Bangumi 已有标题）。 */
    val tmdbTitle: String? = null,
    val sourceId: String = "tmdb",
    /** 见 [AlignmentStates]：区分「来源确实没有」和「我们请求失败了」。 */
    val alignmentState: String,
    val fetchedAt: Long,
    val expiresAt: Long,
) {
    /** 对齐状态：必须能区分「来源确实没有」与「我们请求失败了」。 */
    object AlignmentStates {
        const val ALIGNED = "aligned"
        const val UNMATCHED = "unmatched"
        const val SOURCE_EMPTY = "source_empty"
        const val FAILED = "failed"
    }
}
