package com.otakup.niriko.ui.share

/**
 * 作品信息分享卡（1080×1400，重做版）的版式规则（纯函数，JVM 可测）。
 *
 * 卡面空间有限：标签最多 4 个、短评最多 56 字、进度条 clamp 到 0..1。
 * 放在纯函数里，是为了让「标签挤爆」「进度超过 100%」这类问题有单测兜底，
 * 而不是等真机生成一张文字溢出的图。
 */
object ShareShowcasePolicy {

    /** 最多展示 4 个标签（原型 07 的版式）。 */
    const val MAX_TAGS = 4

    /** 短评最多 56 字，超出截断并补省略号。 */
    const val MAX_IMPRESSION_CHARS = 56

    /** 标签截断：去空、去重、保留顺序。 */
    fun tagsToShow(tags: List<String>): List<String> = tags.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_TAGS).toList()

    /** 短评截断：空串视为没有短评。 */
    fun clampImpression(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (trimmed.length <= MAX_IMPRESSION_CHARS) trimmed else trimmed.take(MAX_IMPRESSION_CHARS) + "…"
    }

    /** 评分文案：没有分数时显示占位而不是 0.0。 */
    fun scoreText(score: Float?): String = score?.let { String.format("%.1f", it) } ?: "—"

    /** 进度比例：缺数据或总数为 0 时返回 0，超过 1 时收敛到 1。 */
    fun progressFraction(watched: Int?, total: Int?): Float {
        if (watched == null || total == null || total <= 0) return 0f
        return (watched.toFloat() / total).coerceIn(0f, 1f)
    }

    /** 进度文案：「8 / 12 集」；缺总数时只报已看。 */
    fun progressLabel(watched: Int?, total: Int?): String? = when {
        watched == null -> null
        total != null && total > 0 -> "$watched / $total 集"
        else -> "已看 $watched 集"
    }
}
