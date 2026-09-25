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
 * 4. 否则按 **LCS（最长公共子序列）占比**给出 0.1~0.9 的分数。
 *    LCS 替代旧版的"字符集重叠"——后者只看公共字符个数，对
 *    "Counter-Strike 2" vs "Advance Wars 2: Black Hole Rising" 这类
 *    字符集高度重合但词序完全不同的标题会误判高分；LCS 尊重词序，能正确压低。
 *    上限 0.9：字符几乎全同的变体可胜过 0.85 边界包含；
 * 5. 低于阈值 [MIN_CONFIDENCE] 视为不匹配，避免误绑。
 *
 * 注意：跨语言匹配（Bangumi 原名 vs Steam 英文名）请配合
 * [bestConfidence] 传入多个标题（title + titleCN + latin 等）取最大分，
 * 不要依赖单一标题的字符重叠（中文名与英文名重叠几乎为 0）。
 */
object SteamTitleMatcher {

    /** 自动绑定所需的最低置信度。 */
    const val MIN_CONFIDENCE = 0.7f

    /**
     * 包含分支可信所需的最短标题长度（字节/字符数）。
     * 短标题 <4 时（如 "2"、"CS"）仅凭包含关系不得给高置信——
     * 防止 "Counter-Strike 2" 与 "Advance Wars 2: Black Hole Rising" 这类
     * 只共享一个数字/短词的弱证据误绑。
     */
    private const val MIN_STRONG_CONTAINMENT_LEN = 4

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
            // 短标题过短：包含关系不可信（"2"、"cs" 单独出现不构成证据）
            if (short.length < MIN_STRONG_CONTAINMENT_LEN) return 0.6f
            // 词边界：短标题是长标题的前缀/后缀，或两侧以空格分隔
            val atWordBoundary = long.startsWith("$short ") ||
                long.endsWith(" $short") ||
                long.contains(" $short ")
            if (!atWordBoundary) return 0.6f
            // 词序一致性：短标题的每个 token 必须按序出现在长标题的 token 序列中
            // （"Advance Wars 2" ∈ "Advance Wars 2: Black Hole Rising" ✓；
            //   共享一个数字/词的弱证据 ✗）
            return if (tokensInOrder(short, long)) 0.85f else 0.6f
        }

        // LCS（最长公共子序列）占比——尊重词序，避免"字符集重合但词序不同"的误判
        // （如 "counter strike 2" vs "advance wars 2 black hole rising"）
        val lcsLen = lcsLength(a, b)
        val maxLen = maxOf(a.length, b.length)
        if (maxLen <= 0) return 0f
        val ratio = lcsLen.toFloat() / maxLen
        // token 级惩罚：仅字符重叠但分词后公共 token 少（如只共享一个 "2"）→ 封顶 0.5
        val tokenRatio = commonTokenRatio(a, b)
        val score = (ratio * 0.8f + 0.1f).coerceIn(0.1f, 0.9f)
        return if (tokenRatio < 0.5f) minOf(score, 0.5f) else score
    }

    /**
     * [shortTitle] 的每个 token 是否按序出现在 [longTitle] 的 token 序列中。
     * 用于包含分支的强校验：防止"只共享一个数字"的弱包含被当成词边界子标题。
     */
    private fun tokensInOrder(shortTitle: String, longTitle: String): Boolean {
        val tokens = shortTitle.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        val longTokens = longTitle.split(' ').filter { it.isNotBlank() }
        // 双指针：每个短标题 token 必须在长标题 token 序列中按序出现
        var longIdx = 0
        for (token in tokens) {
            var found = -1
            for (j in longIdx until longTokens.size) {
                if (longTokens[j] == token) {
                    found = j
                    break
                }
            }
            if (found < 0) return false
            longIdx = found + 1
        }
        return true
    }

    /** 归一化标题的公共 token 比例（Jaccard 交集 / 并集）。无 token 返回 0。 */
    private fun commonTokenRatio(a: String, b: String): Float {
        val ta = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0f
        val inter = ta.intersect(tb).size
        val union = ta.union(tb).size
        return if (union == 0) 0f else inter.toFloat() / union
    }

    /** 最长公共子序列长度（O(n*m)，标题一般很短，性能无虞）。 */
    private fun lcsLength(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0 || n == 0) return 0
        var prev = IntArray(n + 1)
        for (i in 1..m) {
            val cur = IntArray(n + 1)
            for (j in 1..n) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1
                else maxOf(cur[j - 1], prev[j])
            }
            prev = cur
        }
        return prev[n]
    }

    /**
     * 多标题最佳置信度：查询方与候选方各提供多个标题（如 title / titleCN / latin），
     * 两两计算 confidence 取最大。用于跨语言匹配（中文名 vs 英文名无字符重叠，
     * 但一方原名与另一方原名可精确命中，如 "喵斯快跑" ↔ "Muse Dash"）。
     */
    fun bestConfidence(
        queryTitles: Collection<String>,
        candidateTitles: Collection<String>,
    ): Float {
        if (queryTitles.isEmpty() || candidateTitles.isEmpty()) return 0f
        return queryTitles.maxOf { q ->
            candidateTitles.maxOf { c -> confidence(q, c) }
        }
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
    fun bestMatch(title: String, candidates: List<SteamStoreSearchItemDto>): MatchResult? =
        bestMatchMulti(listOf(title), candidates)

    /**
     * 多查询标题版 [bestMatch]：查询侧提供多个标题（如 title / titleCN），
     * 每个候选取与查询标题集的最佳置信度（bestConfidence），解决跨语言
     * "喵斯快跑" ↔ "Muse Dash" 这类无字符重叠的命中（前提是其中一个查询标题
     * 与候选名有重叠/相等）。保留 type=="app" 强校验与候选长度差异惩罚。
     */
    fun bestMatchMulti(queryTitles: Collection<String>, candidates: List<SteamStoreSearchItemDto>): MatchResult? {
        val titles = queryTitles.map { normalizeTitle(it) }.filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty() || candidates.isEmpty()) return null
        var best: MatchResult? = null
        for (c in candidates) {
            // type == "app" 强校验：type 为 null（旧接口缺省）也排除，
            // 防止 bundle/sub/书籍/免费工具等非游戏 app 参与绑定
            if (c.type != "app") continue
            val candidateNorm = normalizeTitle(c.name)
            if (candidateNorm.isBlank()) continue
            // 查询侧多标题取最大置信度
            var score = titles.maxOf { confidence(normalizeTitle(it), c.name) }
            // 候选长度差异惩罚：长副标题包短名（"Advance Wars 2: Black Hole Rising" 包 "Advance Wars 2"
            // 这类歧义短标题）会被 0.85 词边界包含放大；长度差 >50% 时降权到阈值以下，
            // 除非精确命中（score==1.0）不受影响
            val bestLenMatch = titles.minByOrNull { kotlin.math.abs(it.length - candidateNorm.length) } ?: continue
            val normLenDiff = kotlin.math.abs(bestLenMatch.length - candidateNorm.length).toFloat() /
                maxOf(bestLenMatch.length, candidateNorm.length, 1)
            if (normLenDiff > 0.5f && score < 1.0f) {
                score *= 0.6f
            }
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
                val bestLenDiff = kotlin.math.abs(bestLenMatch.length - normalizeTitle(best.steamName).length)
                val candLenDiff = kotlin.math.abs(bestLenMatch.length - candidateNorm.length)
                if (candLenDiff < bestLenDiff) best = candidate
            }
        }
        return best
    }
}
