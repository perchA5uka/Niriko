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
 * - 书籍 / 漫画：已经在记卷进度时用卷（watchedVolumes，上限 = Bangumi 的 volumes 总卷数）；否则退回集
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
        /** 总卷数（书籍 / 漫画的 Bangumi volumes）；未知或非正数时不设上限。 */
        totalVolumes: Int? = null,
    ): ProgressSnapshot? = when (unitOf(type, watchedVolumes)) {
        ProgressUnit.EPISODE -> ProgressSnapshot(
            unit = ProgressUnit.EPISODE,
            value = (watchedEpisodes ?: 0).coerceAtLeast(0),
            total = totalEpisodes?.takeIf { it > 0 },
        )
        ProgressUnit.VOLUME -> ProgressSnapshot(
            unit = ProgressUnit.VOLUME,
            value = (watchedVolumes ?: 0).coerceAtLeast(0),
            total = totalVolumes?.takeIf { it > 0 },
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

    /**
     * 进度条完成度（0..1），**与进度编辑入口同口径**。
     *
     * 修复 B11：收藏页进度条改造前固定用 `watchedEpisodes / totalEpisodes`，
     * 而漫画 / 书籍的进度是按**卷**记的（「灌篮高手」记 31 卷，而 Bangumi 的
     * totalEpisodes 是 276 话）→ 拿卷数除以话数，进度条永远只有 11% 左右。
     * 现在按 [unitOf] 选单位：记卷就用卷 / 总卷数，记集就用集 / 总集数。
     * 分母未知时返回 null —— 宁可不画进度条，也不画一条骗人的。
     */
    fun barFraction(
        type: SubjectType,
        watchedEpisodes: Int?,
        watchedVolumes: Int?,
        totalEpisodes: Int?,
        totalVolumes: Int?,
    ): Float? = when (unitOf(type, watchedVolumes)) {
        ProgressUnit.EPISODE -> fraction(watchedEpisodes, totalEpisodes)
        ProgressUnit.VOLUME -> fraction(watchedVolumes, totalVolumes)
        ProgressUnit.MINUTE, ProgressUnit.UNSUPPORTED -> null
    }

    private fun fraction(value: Int?, total: Int?): Float? {
        val t = total?.takeIf { it > 0 } ?: return null
        return (value ?: 0).coerceIn(0, t).toFloat() / t.toFloat()
    }

    /** 浮层上的进度文案：`7 / 12 集` / `3 卷` / `2h30m`。 */
    fun label(snapshot: ProgressSnapshot): String = when (snapshot.unit) {
        ProgressUnit.EPISODE ->
            if (snapshot.total != null) "${snapshot.value} / ${snapshot.total} 集"
            else "${snapshot.value} 集"
        ProgressUnit.VOLUME ->
            if (snapshot.total != null) "${snapshot.value} / ${snapshot.total} 卷"
            else "${snapshot.value} 卷"
        ProgressUnit.MINUTE -> PlaytimeConverter.format(snapshot.value)
        ProgressUnit.UNSUPPORTED -> ""
    }
}
