package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.game.PlaytimeConverter

/** 进度单位（按作品类型）。 */
enum class ProgressUnit(val label: String) {
    EPISODE("集"),
    VOLUME("卷"),
    MINUTE("分钟"),
    UNSUPPORTED(""),
}

/** 一次进度快照：单位 / 当前值 / 上限（null = 未知，不设上限）。 */
data class ProgressSnapshot(
    val unit: ProgressUnit,
    val value: Int,
    val total: Int?,
)

/**
 * 长按浮层进度 ±（计划 B1-2）的纯逻辑。
 *
 * 单位约定与收藏页 / 详情页一致：
 * - 动画 / 三次元：集（watchedEpisodes，上限 = totalEpisodes）
 * - 书籍 / 漫画：已经在记卷进度时用卷（watchedVolumes，无「总卷数」字段 → 不设上限）；否则退回集
 * - 游戏：分钟（watchedEpisodes 恒存分钟，见 [PlaytimeConverter]）
 * - 音乐 / 人物 / 其他：不支持快捷 ±（音乐是逐首勾选）
 *
 * 不依赖 Android，便于 JVM 单测。
 */
object ProgressBumpPolicy {

    /** 当前单位的进度快照；不支持的类型返回 null（浮层不显示进度行）。 */
    fun snapshot(
        type: SubjectType,
        watchedEpisodes: Int?,
        watchedVolumes: Int?,
        totalEpisodes: Int?,
    ): ProgressSnapshot? = when (unitOf(type, watchedVolumes)) {
        ProgressUnit.EPISODE -> ProgressSnapshot(
            unit = ProgressUnit.EPISODE,
            value = (watchedEpisodes ?: 0).coerceAtLeast(0),
            total = totalEpisodes?.takeIf { it > 0 },
        )
        ProgressUnit.VOLUME -> ProgressSnapshot(
            unit = ProgressUnit.VOLUME,
            value = (watchedVolumes ?: 0).coerceAtLeast(0),
            total = null,
        )
        ProgressUnit.MINUTE -> ProgressSnapshot(
            unit = ProgressUnit.MINUTE,
            value = (watchedEpisodes ?: 0).coerceAtLeast(0),
            total = null,
        )
        ProgressUnit.UNSUPPORTED -> null
    }

    /** 单位判定：书籍 / 漫画只有已经在记卷进度时才按卷走。 */
    fun unitOf(type: SubjectType, watchedVolumes: Int?): ProgressUnit = when (type) {
        SubjectType.ANIME, SubjectType.REAL -> ProgressUnit.EPISODE
        SubjectType.BOOK, SubjectType.MANGA ->
            if (watchedVolumes != null) ProgressUnit.VOLUME else ProgressUnit.EPISODE
        SubjectType.GAME -> ProgressUnit.MINUTE
        else -> ProgressUnit.UNSUPPORTED
    }

    /**
     * 加/减一步后的值：下限 0；上限为 [ProgressSnapshot.total]（未知则不设上限）。
     * 用户确认：加到上限**不**自动切换「看过」，只把进度写到上限。
     */
    fun next(snapshot: ProgressSnapshot, delta: Int): Int {
        val stepped = (snapshot.value + delta).coerceAtLeast(0)
        return snapshot.total?.let { stepped.coerceAtMost(it) } ?: stepped
    }

    /** 浮层上的进度文案：`7 / 12 集` / `3 卷` / `2h30m`。 */
    fun label(snapshot: ProgressSnapshot): String = when (snapshot.unit) {
        ProgressUnit.EPISODE ->
            if (snapshot.total != null) "${snapshot.value} / ${snapshot.total} 集"
            else "${snapshot.value} 集"
        ProgressUnit.VOLUME -> "${snapshot.value} 卷"
        ProgressUnit.MINUTE -> PlaytimeConverter.format(snapshot.value)
        ProgressUnit.UNSUPPORTED -> ""
    }
}
