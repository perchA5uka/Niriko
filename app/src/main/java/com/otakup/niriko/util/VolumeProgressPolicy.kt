package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType

/**
 * 「单本」判定：这本书到底有没有「卷」这个概念。
 *
 * 为什么需要它：详情页原来有两套独立判据 —— 编辑弹层只要类型是 BOOK/MANGA 就渲染「卷进度」，
 * 而进度区只看 `series == false`。对同一本单本书，两处会给出相反答案：进度区知道它是单本、
 * 已经藏起来了，弹层却还给一个卷数输入框，填进去的值还会被当成话数写库。
 *
 * 这里收成唯一判定源：
 * - 弹层问 [showVolumeField]：BOOK / MANGA 的卷数 ≤1 就是单本，一律不显示卷输入；
 * - 进度区问 [showProgressSection]：历史判据 `series == false` 归到这里，不再各写一套。
 *
 * 注意 Bangumi 的 `type=1` 全部映射成 [SubjectType.BOOK]（见 `SubjectType.fromBangumiType`），
 * 但本地手工条目可能是 [SubjectType.MANGA]，两者按同一规则处理（产品口径：不区分漫画 / 文字书）。
 *
 * `volumes` 为 null/0 时表示「总卷数未知」，同样按单本处理：未知总卷时卷进度本身没有意义。
 */
object VolumeProgressPolicy {

    /**
     * 是否单本。
     *
     * @param series 主源给的「是否系列」；`false` 是改造前就在用的强判据，优先采信。
     */
    fun isSingleVolume(type: SubjectType?, volumes: Int?, series: Boolean?): Boolean = when {
        series == false -> true
        type == SubjectType.BOOK || type == SubjectType.MANGA -> (volumes ?: 0) <= 1
        else -> false
    }

    /** 编辑弹层是否显示「卷进度」输入框。 */
    fun showVolumeField(type: SubjectType?, volumes: Int?): Boolean = when (type) {
        SubjectType.BOOK, SubjectType.MANGA -> (volumes ?: 0) > 1
        else -> false
    }

    /** 详情页「卷/话」进度区是否显示。 */
    fun showProgressSection(type: SubjectType?, volumes: Int?, series: Boolean?): Boolean =
        (type == SubjectType.BOOK || type == SubjectType.MANGA) && !isSingleVolume(type, volumes, series)
}
