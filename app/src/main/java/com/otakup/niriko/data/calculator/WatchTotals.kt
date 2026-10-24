package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.SubjectType
import kotlin.math.roundToInt

/** 单集时长的来源：真实集时长，还是类型默认值。 */
enum class MinutesSource { EXACT, TYPE_DEFAULT }

/** 单集分钟数的估算结果（[minutes] 为 0 表示该类型不计时长）。 */
data class IntEstimate(val minutes: Int, val source: MinutesSource)

/**
 * 观看量分流结果。
 *
 * 为什么要分流：收藏表只有一个进度列 `watchedEpisodes`，但各类型往里写的东西完全不同——
 * ANIME/REAL 写集数，GAME 写**游玩分钟**（Steam 导入与详情页编辑都走这条），
 * MUSIC 写**已听曲目数**，MANGA/BOOK 主要写卷数（`watchedVolumes`）。
 * 全部塞进一个 `sumOf { watchedEpisodes }` 会让"总集数"凭空膨胀
 * （玩 3000 分钟的游戏等于 +3000 集），所以统计侧必须先按类型分桶再相加。
 */
data class WatchTotals(
    /** 视频集数（ANIME + REAL），唯一可以叫"集"的量。 */
    val videoEpisodes: Int = 0,
    /** 视频总分钟数（只由视频集数 × 单集分钟数构成）。 */
    val minutes: Int = 0,
    /** [minutes] 里用了类型默认单集时长的作品数（UI 据此决定要不要显示"约"）。 */
    val estimatedMinutesSubjects: Int = 0,
    /** 游戏游玩分钟数（GAME 的 watchedEpisodes 真实单位）。 */
    val gameMinutes: Int = 0,
    /** 阅读单位（MANGA/BOOK 优先卷数，缺失时退回 watchedEpisodes；OTHER 同）。 */
    val readUnits: Int = 0,
    /** 已听曲目数（MUSIC 的 watchedEpisodes 真实单位）。 */
    val trackCount: Int = 0,
)

/**
 * 单集分钟数：优先用集级真实时长（`type == 0` 本篇的均值），否则退回类型默认值。
 *
 * 为什么不回退到"作品级时长"：`SubjectEntity` 没有 duration 字段（只有 [EpisodeInfo] 有），
 * 编造二级回退会让"约"标记失去意义。
 */
fun minutesPerEpisode(subject: SubjectEntity, episodes: List<EpisodeInfo>): IntEstimate {
    val main = episodes.filter { it.type == 0 && it.durationSeconds > 0 }
    if (main.isNotEmpty()) {
        val avgSeconds = main.sumOf { it.durationSeconds.toLong() }.toDouble() / main.size
        return IntEstimate((avgSeconds / 60.0).roundToInt().coerceAtLeast(1), MinutesSource.EXACT)
    }
    return IntEstimate(defaultMinutesPerEpisode(subject.type), MinutesSource.TYPE_DEFAULT)
}

/** 类型默认单集分钟数：只有视频类给值，其余返回 0（0 = 不计时长，而不是"算作 0 分钟"）。 */
fun defaultMinutesPerEpisode(type: SubjectType): Int = when (type) {
    SubjectType.ANIME -> 24
    SubjectType.REAL -> 45
    else -> 0
}

/**
 * 把收藏列表按类型拆成各量纲的合计。
 *
 * [episodesBySubject] 只用于取真实单集时长；缺失时退回类型默认值并计入
 * [WatchTotals.estimatedMinutesSubjects]，调用方据此决定是否显示"约"。
 */
fun computeWatchTotals(
    items: List<CollectionWithSubject>,
    episodesBySubject: Map<Long, List<EpisodeInfo>> = emptyMap(),
): WatchTotals {
    var videoEpisodes = 0
    var minutes = 0
    var estimated = 0
    var gameMinutes = 0
    var readUnits = 0
    var trackCount = 0

    items.forEach { item ->
        val collection = item.collection
        val progress = (collection.watchedEpisodes ?: 0).coerceAtLeast(0)
        when (item.subject.type) {
            SubjectType.ANIME, SubjectType.REAL -> {
                videoEpisodes += progress
                if (progress > 0) {
                    val estimate = minutesPerEpisode(item.subject, episodesBySubject[item.subject.subjectId].orEmpty())
                    if (estimate.minutes > 0) {
                        minutes += progress * estimate.minutes
                        if (estimate.source == MinutesSource.TYPE_DEFAULT) estimated++
                    }
                }
            }
            SubjectType.GAME -> gameMinutes += progress
            SubjectType.MUSIC -> trackCount += progress
            // 书籍/漫画以卷为主单位；OTHER 没有明确量纲，按"进度单位"归到这里，
            // 宁可不算进集数，也不要把不明单位混进"看过多少集"。
            SubjectType.MANGA, SubjectType.BOOK, SubjectType.OTHER ->
                readUnits += (collection.watchedVolumes?.coerceAtLeast(0) ?: progress)
            // 人物不是可收藏媒介，不参与任何统计。
            SubjectType.PERSON -> Unit
        }
    }

    return WatchTotals(
        videoEpisodes = videoEpisodes,
        minutes = minutes,
        estimatedMinutesSubjects = estimated,
        gameMinutes = gameMinutes,
        readUnits = readUnits,
        trackCount = trackCount,
    )
}
