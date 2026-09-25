package com.otakup.niriko.data.remote.rating.sources

/**
 * TMDb 候选打分（纯函数，可单测）。
 *
 * 复用项目既有的「标题置信度 + 年份邻近」思路，但不依赖 SteamTitleMatcher 的 Steam 归一化规则，
 * 因为 TMDb 的标题是多语言混合（中文名 / 原名 / 罗马音）。
 *
 * 打分构成（总和上限 1.0）：
 * - 标题相似度（0-1）占 **0.8 权重**：归一化后的最长公共子串占比，取候选所有标题与作品所有标题的最大值
 * - 年份邻近：同年 +0.12；±1 年 +0.06；否则 0
 * - 集数接近：完全相等 +0.08；差值 <= 2 +0.04
 *
 * 标题权重刻意留出余量：若标题直接取 1.0，年份/集数的加分会被 clamp 吃掉，
 * 导致"年份完全对得上"与"年份差 3 年"得到同样的分数（无法区分）。
 *
 * 结果 clamp 到 0..1。**仅用于排序候选，不用于自动绑定**。
 */
object TmdbMatchScorer {

    fun score(
        bangumiTitles: List<String>,
        candidateTitles: List<String>,
        bangumiYear: Int? = null,
        candidateYear: Int? = null,
        bangumiEpisodes: Int? = null,
        candidateEpisodes: Int? = null,
    ): Float {
        val titleScore = bestTitleScore(bangumiTitles, candidateTitles)
        if (titleScore <= 0f) return 0f

        var total = titleScore * 0.8f
        if (bangumiYear != null && candidateYear != null) {
            val diff = kotlin.math.abs(bangumiYear - candidateYear)
            total += when {
                diff == 0 -> 0.12f
                diff == 1 -> 0.06f
                else -> 0f
            }
        }
        if (bangumiEpisodes != null && candidateEpisodes != null && bangumiEpisodes > 0) {
            val diff = kotlin.math.abs(bangumiEpisodes - candidateEpisodes)
            total += when {
                diff == 0 -> 0.08f
                diff <= 2 -> 0.04f
                else -> 0f
            }
        }
        return total.coerceIn(0f, 1f)
    }

    private fun bestTitleScore(left: List<String>, right: List<String>): Float {
        var best = 0f
        for (a in left) {
            val na = normalize(a)
            if (na.isEmpty()) continue
            for (b in right) {
                val nb = normalize(b)
                if (nb.isEmpty()) continue
                val s = similarity(na, nb)
                if (s > best) best = s
            }
        }
        return best
    }

    /** 归一化：小写、去空白/标点、全角转半角、去常见季/期后缀。 */
    private fun normalize(raw: String): String {
        val lowered = raw.lowercase()
        val sb = StringBuilder(lowered.length)
        for (ch in lowered) {
            val c = when {
                ch.code == 0x3000 -> ' '
                ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                else -> ch
            }
            if (c.isLetterOrDigit()) sb.append(c)
        }
        return sb.toString()
            .removeSuffix("season")
            .removeSuffix("th")
            .removeSuffix("nd")
            .removeSuffix("rd")
    }

    /** 归一化后的相似度：完全相等 1.0；否则用「较短串在较长串中的最长连续公共子串占比」。 */
    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        val small = if (a.length <= b.length) a else b
        val large = if (a.length <= b.length) b else a
        if (small.isEmpty()) return 0f
        val lcs = longestCommonSubstring(small, large)
        return lcs.toFloat() / small.length
    }

    private fun longestCommonSubstring(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        var best = 0
        val prev = IntArray(b.length + 1)
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else 0
                if (cur[j] > best) best = cur[j]
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
            java.util.Arrays.fill(cur, 0)
        }
        return best
    }
}
