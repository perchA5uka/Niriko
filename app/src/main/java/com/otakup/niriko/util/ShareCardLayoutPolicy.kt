package com.otakup.niriko.util

import kotlin.math.abs

/**
 * 分享三卡的纯布局规则（C · 8.5/8.6/8.7）。
 *
 * 渲染是 Android/Compose 的事，这里只算「分几页、每页放哪几项、标记落在刻度哪里、文案怎么写」，
 * 这样分页与文案可以 JVM 单测，不必靠真机截图。
 */
object ShareCardLayoutPolicy {

    /** 分区海报：3 列 × 4 行 = 12 部/页。 */
    const val POSTER_COLUMNS = 3
    const val POSTER_ROWS = 4
    const val POSTER_PER_PAGE = POSTER_COLUMNS * POSTER_ROWS

    /** 每个下标区间是一页（含首含尾）；空列表返回空页集合。 */
    fun pages(itemCount: Int): List<IntRange> {
        if (itemCount <= 0) return emptyList()
        return (0 until itemCount step POSTER_PER_PAGE).map { start ->
            start until minOf(start + POSTER_PER_PAGE, itemCount)
        }
    }

    fun pageCount(itemCount: Int): Int = pages(itemCount).size

    /** 评分刻度（0–10）→ 横向像素位置；越界夹住，不画到框外。 */
    fun scoreMarkerX(score: Float, widthPx: Float, maxScore: Float = 10f): Float {
        if (widthPx <= 0f || maxScore <= 0f) return 0f
        return score.coerceIn(0f, maxScore) / maxScore * widthPx
    }

    /** 「你比平均高 1.3 分」——任一缺失或相等时返回 null（不硬造结论）。 */
    fun deltaText(mine: Float?, community: Float?): String? {
        if (mine == null || community == null) return null
        val delta = mine - community
        if (abs(delta) < 0.05f) return "和平均分持平"
        val sign = if (delta > 0) "高" else "低"
        return "你比平均$sign %.1f 分".format(abs(delta))
    }

    /** 周报的空周判据：0 更新且 0 进度时不生成（避免「这周什么都没发生」的空卡）。 */
    fun shouldGenerateWeekly(updatedCount: Int, watchedEpisodes: Int, finishedCount: Int): Boolean =
        updatedCount > 0 || watchedEpisodes > 0 || finishedCount > 0
}
