package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.remote.steam.dto.SteamStoreSearchItemDto

/**
 * Bangumi 标题 → Steam 商店条目匹配器（纯函数，便于单测）。
 *
 * 匹配策略：
 * 1. 标题归一化（去空白/全角转半角/剥版权符号）后与 storesearch 候选 name 比对；
 * 2. 精确相等 → 1.0；
 * 3. 包含关系分两种：
 *    - 短标题**以词边界**出现在长标题中（前缀/后缀/空格分隔，如 "艾尔登法环" ∈ "艾尔登法环 黄金树幽影"）→ 0.85；
 *    - 短标题是长标题的**连续子串**（无空格边界，如 "elden" ∈ "eldenring"，或 "xx" ∈ "xx改"）→ 0.6，
 *      这是"短标题撞上长标题"误绑的主要来源，必须降权到阈值以下；
 * 4. 否则按字符重叠比例给出 0.1~0.9 的分数（上限 0.9：字符几乎全同的变体可胜过 0.85 边界包含）；
 * 5. 低于阈值 [MIN_CONFIDENCE] 视为不匹配，避免误绑。
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

        // 包含关系：区分"词边界包含"（带副标题/版本后缀，可信）与"连续子串"（短标题撞长标题，易误绑）
        if (a.contains(b) || b.contains(a)) {
            val (long, short) = if (a.length >= b.length) a to b else b to a
            if (short.length < 3) return 0.6f
            // 词边界：短标题是长标题的前缀/后缀，或两侧以空格分隔
            val atWordBoundary = long.startsWith("$short ") ||
                long.endsWith(" $short") ||
                long.contains(" $short ")
            return if (atWordBoundary) 0.85f else 0.6f
        }

        // 字符重叠比例（公共字符数 / 平均长度），处理小差异（标点/空格变体）
        val common = (a.toSet() intersect b.toSet()).size
        val avgLen = (a.length + b.length).toFloat() / 2f
        if (avgLen <= 0f) return 0f
        val overlap = common / avgLen
        // 完全无重叠 → 0.1（远低于 MIN_CONFIDENCE，保证不误绑）；有重叠按比例给分
        // 上限 0.9：字符几乎全同的变体（如空格差异）应胜过 0.85 的"带副标题包含"
        return (overlap * 0.8f + 0.1f).coerceIn(0.1f, 0.9f)
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
        val normalizedTitle = normalizeTitle(title)
        var best: MatchResult? = null
        for (c in candidates) {
            // 只匹配 app（忽略 bundle/sub/package）
            if (c.type != null && c.type != "app") continue
            val score = confidence(title, c.name)
            if (score < MIN_CONFIDENCE) continue
            val candidate = MatchResult(
                appId = c.id,
                steamName = c.name,
                confidence = score,
                priceCents = c.price?.final ?: c.price?.initial,
                currency = c.price?.currency,
                tinyImage = c.tinyImage,
            )
            if (best == null) {
                best = candidate
                continue
            }
            // 分数更高 → 替换；同分 → 选标题长度更接近的（多语言/版本候选消歧）
            if (score > best.confidence) {
                best = candidate
            } else if (score == best.confidence) {
                val bestLenDiff = kotlin.math.abs(normalizedTitle.length - normalizeTitle(best.steamName).length)
                val candLenDiff = kotlin.math.abs(normalizedTitle.length - normalizeTitle(c.name).length)
                if (candLenDiff < bestLenDiff) best = candidate
            }
        }
        return best
    }
}
