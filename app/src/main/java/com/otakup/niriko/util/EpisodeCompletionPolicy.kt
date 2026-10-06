package com.otakup.niriko.util

import com.otakup.niriko.data.model.SubjectType

/**
 * 「看完」边沿判据（F14，计划 §11.1）。
 *
 * 反馈要的是**边沿**：从「还没看完」到「刚看完」这一下。因此判据是
 * `before < total && after >= total` —— 只要这个条件成立，它就是用户**刚做到**的事，
 * 与「页面是第几次打开」无关：
 * - 打开详情页/列表不写库，因此**永远不会触发**（不是靠「只播一次」的补丁，而是压根没有边沿）；
 * - 反复保存同一个 12/12（before 已经等于 total）不会重复触发 —— 这就是「初次加载之外不重复」；
 * - 总量未知（total 为 null / 0）**不伪造**完成：宁可少一次反馈，也不要谎报「看完了」。
 *
 * 单位：书籍/漫画有总卷数时按**卷**判断（§6.4：卷与话是两个单位，不能混着算分母），
 * 其余按集。这条规则与进度条的 [ProgressBumpPolicy] 同口径。
 */
object EpisodeCompletionPolicy {

    /**
     * 一次进度写入的前后快照。
     *
     * 四个字段都可以为 null（未记录 / 来源没有这类数据）；比较时按「用哪个单位」决定看哪一对。
     */
    data class ProgressSnapshot(
        val watchedEpisodes: Int? = null,
        val watchedVolumes: Int? = null,
        val totalEpisodes: Int? = null,
        val totalVolumes: Int? = null,
    )

    /** 该作品按卷还是按集判断「看完」：有可靠总卷数（书籍/漫画）时按卷。 */
    fun usesVolumes(type: SubjectType?, snapshot: ProgressSnapshot): Boolean =
        when (type) {
            SubjectType.BOOK, SubjectType.MANGA -> (snapshot.totalVolumes ?: 0) > 0
            else -> false
        }

    /**
     * 是否构成「刚看完」的边沿。
     *
     * @param type 作品类型（决定单位）；null 一律按集
     */
    fun shouldCelebrate(
        before: ProgressSnapshot,
        after: ProgressSnapshot,
        type: SubjectType? = null,
    ): Boolean {
        val byVolume = usesVolumes(type, after)
        val total = if (byVolume) after.totalVolumes else after.totalEpisodes
        if (total == null || total <= 0) return false
        val afterValue = if (byVolume) after.watchedVolumes else after.watchedEpisodes
        val beforeValue = if (byVolume) before.watchedVolumes else before.watchedEpisodes
        val done = afterValue ?: return false
        val was = beforeValue ?: 0
        return was < total && done >= total
    }

    /** 当前进度文本（用于诊断/日志；也方便测试断言「用的是哪个单位」）。 */
    fun progressLabel(snapshot: ProgressSnapshot, type: SubjectType? = null): String {
        return if (usesVolumes(type, snapshot)) {
            "${snapshot.watchedVolumes ?: 0}/${snapshot.totalVolumes ?: 0} 卷"
        } else {
            "${snapshot.watchedEpisodes ?: 0}/${snapshot.totalEpisodes ?: 0} 集"
        }
    }
}
