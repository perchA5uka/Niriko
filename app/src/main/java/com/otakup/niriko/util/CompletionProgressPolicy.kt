package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus

/**
 * 「标记为看过」时该往哪个进度字段写。
 *
 * 为什么需要它：改造前的 `updateStatus` 对书籍/漫画取 `volumes ?: totalEpisodes` 当分母，
 * 然后把同一个数字写进 `watchedEpisodes`。一本 24 卷的书标「看过」后，`watchedEpisodes` 变成 24，
 * 统计侧把它当「已看 24 集」—— 两个字段的单位被混在一起（统计页因此虚高）。
 *
 * 这里的规则只有一条：**有卷就写卷，没有卷才写话**。
 * 有卷时不去改 `watchedEpisodes`（用户此前按话记过的进度不该被卷数覆盖，也不该被清空）；
 * 没有卷时保持改造前的两处旧行为（ANIME/REAL/BOOK/MANGA 写总集数，其他类型清空）。
 */
object CompletionProgressPolicy {

    /** 一次状态变更要落库的两个进度值（null 表示清空该字段）。 */
    data class Target(val watchedEpisodes: Int?, val watchedVolumes: Int?)

    /**
     * 计算状态变更后的进度。
     *
     * @param currentEpisodes / [currentVolumes] 写入前的值：非完成态与「有卷」分支都要原样保留。
     */
    fun targetFor(
        type: SubjectType?,
        status: WatchStatus,
        totalEpisodes: Int?,
        volumes: Int?,
        currentEpisodes: Int?,
        currentVolumes: Int?,
    ): Target = when (status) {
        // 想看等于「清空进度」，但卷数不动：用户可能只是把状态改回来。
        WatchStatus.PLAN_TO_WATCH -> Target(null, currentVolumes)

        WatchStatus.COMPLETED -> {
            val volumeTarget = volumes?.takeIf { it > 0 && isVolumeTracked(type) }
            if (volumeTarget != null) {
                Target(currentEpisodes, volumeTarget)
            } else {
                Target(completedEpisodes(type, totalEpisodes, volumes), currentVolumes)
            }
        }

        else -> Target(currentEpisodes, currentVolumes)
    }

    /** 卷进度是书籍/漫画的专属概念。 */
    private fun isVolumeTracked(type: SubjectType?): Boolean =
        type == SubjectType.BOOK || type == SubjectType.MANGA

    /** 改造前就有的「完成 = 写总集数」分支，保持在无卷时不变。 */
    private fun completedEpisodes(type: SubjectType?, totalEpisodes: Int?, volumes: Int?): Int? {
        val total = when (type) {
            SubjectType.ANIME, SubjectType.REAL -> totalEpisodes
            SubjectType.BOOK, SubjectType.MANGA -> volumes ?: totalEpisodes
            else -> null
        }
        return total?.takeIf { it > 0 }
    }
}
