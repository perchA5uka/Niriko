package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.remote.steam.dto.SteamStoreSearchItemDto

/**
 * Bangumi 标题 → Steam 商店条目匹配器（纯函数，便于单测）。
 *
 * 匹配策略：
 * 1. 标题归一化（去空白/全角转半角/剥版权符号）后与 storesearch 候选 name 比对；
 * 2. 精确相等 → 1.0；一方包含另一方（被包含方长度 ≥ 3）→ 0.85；
 * 3. 否则按字符重叠比例给出 0.5~0.84 的分数；
 * 4. 低于阈值 [MIN_CONFIDENCE] 视为不匹配，避免误绑。
 */
object SteamTitleMatcher {

    /** 自动绑定所需的最低置信度。 */
    const val MIN_CONFIDENCE = 0.7f

    /** 版权/商标符号与干扰字符。 */
    private val SYMBOL_REGEX = Regex("[™®©·•]")

    /**
     * 标题归一化：去版权符号、全角空格/括号转半角、剔除所有标点（保留字母/数字/汉字/空格/括号）、
     * 压缩连续空白、转小写、trim。
     * 不剥离版本后缀（如 "Definitive Edition"），交由包含关系评分处理。
     */
    fun normalizeTitle(raw: String): String {
        return raw
            .replace(SYMBOL_REGEX, "")
            .replace('　', ' ')
            .replace('（', '(')
            .replace('）', ')')
            .lowercase()
            // 全角冒号（标题分隔符）转空格，避免「Myth：Wukong」粘连成「mythwukong」
            .replace('：', ' ')
            // 剔除其余标点（全角逗号/句号等），只留字母/数字/汉字/空格/半角括号
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fff ()]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 计算 Bangumi 标题与 Steam 名称的匹配置信度（0~1）。
     * @param bangumiTitle 未归一化原始标题
     * @param steamName    未归一化 Steam 商店名称
     */
    fun confidence(bangumiTitle: String, steamName: String): Float {
        val a = normalizeTitle(bangumiTitle)
        val b = normalizeTitle(steamName)
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f

        // 包含关系（如 Bangumi「艾尔登法环」vs Steam「艾尔登法环」；或带版本后缀的长名）
        if (a.contains(b) || b.contains(a)) {
            val shorter = minOf(a.length, b.length)
            return if (shorter >= 3) 0.85f else 0.6f
        }

        // 字符重叠比例（公共字符数 / 平均长度），处理小差异（标点/空格变体）
        val common = (a.toSet() intersect b.toSet()).size
        val avgLen = (a.length + b.length).toFloat() / 2f
        if (avgLen <= 0f) return 0f
        val overlap = common / avgLen
        // 完全无重叠 → 0.1（远低于 MIN_CONFIDENCE，保证不误绑）；有重叠按比例给分
        return (overlap * 0.8f + 0.1f).coerceIn(0.1f, 0.84f)
    }

    /** 匹配结果。 */
    data class MatchResult(
        val appId: Int,
        val steamName: String,
        val confidence: Float,
        /** 搜索接口携带的现价（分），详情接口返回前可先展示。 */
        val priceCents: Int? = null,
        /** 价格币种。 */
        val currency: String? = null,
        /** 搜索接口携带的小封面（capsule_231x87）。 */
        val tinyImage: String? = null,
    )

    /**
     * 从候选列表中选择最佳匹配。
     * @param title     Bangumi 标题（原始形态）
     * @param candidates storesearch 返回的候选条目
     * @return 最佳匹配（置信度 ≥ [MIN_CONFIDENCE]），否则 null
     */
    fun bestMatch(title: String, candidates: List<SteamStoreSearchItemDto>): MatchResult? {
        if (title.isBlank() || candidates.isEmpty()) return null
        var best: MatchResult? = null
        for (c in candidates) {
            // 只匹配 app（忽略 bundle/sub/package）
            if (c.type != null && c.type != "app") continue
            val score = confidence(title, c.name)
            if (score < MIN_CONFIDENCE) continue
            if (best == null || score > best.confidence) {
                best = MatchResult(
                    appId = c.id,
                    steamName = c.name,
                    confidence = score,
                    priceCents = c.price?.final ?: c.price?.initial,
                    currency = c.price?.currency,
                    tinyImage = c.tinyImage,
                )
            }
        }
        return best
    }
}
