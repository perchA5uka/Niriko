package com.otakup.niriko.plugin.bilibili

/**
 * bilibili 条目 → Bangumi 条目匹配器。
 *
 * 匹配策略（与 Bangumi-master bilibili-sync 一致）：
 * 1. 首选：season_id 反查 [com.otakup.niriko.data.remote.bilibili.BilibiliSiteMap]
 *    （大陆 b 优先，港澳台 h 兜底）；
 * 2. 兜底：标题规范化后与本地 subjects 的 title/titleCN 精确比对
 *    （去掉「(仅限港澳台)」类括注后缀后比对）；
 * 3. 均不中 → null（导入时标记「未匹配」并跳过）。
 *
 * 全部为纯函数，便于单元测试。
 */
object BilibiliMatcher {

    /** 标题中需要剥离的括注后缀（不影响显示语义，但会阻碍精确比对）。 */
    private val TRAILING_SUFFIXES = listOf(
        "（僅限港澳台地區）",
        "（仅限港澳台地区）",
        "（僅限港澳台）",
        "（仅限港澳台）",
        "(僅限港澳台地區)",
        "(仅限港澳台地区)",
        "（港澳台）",
        "(港澳台)",
    )

    /**
     * 标题归一化：剥离去尾括注后缀、收起空白、全角括号转半角后 trim。
     * 用于 bili 标题与本地 title/titleCN 的可比对形态。
     */
    fun normalizeTitle(raw: String): String {
        var title = raw.trim()
        var changed = true
        var guard = 0
        // 循环剥离（标题可能同时带多个后缀，如「XX（仅限港澳台）（特別篇）」）
        while (changed && guard < 8) {
            changed = false
            for (suffix in TRAILING_SUFFIXES) {
                if (title.endsWith(suffix)) {
                    title = title.removeSuffix(suffix).trim()
                    changed = true
                }
            }
            guard++
        }
        // 全角空格（U+3000，\s 不匹配）与全角括号统一为半角，然后压缩连续空白
        return title
            .replace('　', ' ')
            .replace('（', '(')
            .replace('）', ')')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 匹配单个条目。
     *
     * @param seasonId follow/list 返回的 season_id（可能为 null）
     * @param title bili 标题（原始形态）
     * @param siteMapLookup (seasonId) -> bgmId（大陆优先的 site_map 反查）
     * @param localTitles subjectId -> 标题（title 与 titleCN 都索引进去，同一条目两个键）
     * @return 匹配到的 Bangumi subjectId，未匹配为 null
     */
    fun match(
        seasonId: Int?,
        title: String,
        siteMapLookup: (Int) -> Long?,
        localTitles: Map<Long, String>,
    ): Long? {
        // 1) season_id 精确反查
        if (seasonId != null && seasonId > 0) {
            siteMapLookup(seasonId)?.let { return it }
        }

        // 2) 标题兜底：本地 title/titleCN 均归一化后精确比对
        if (title.isNotBlank() && localTitles.isNotEmpty()) {
            val normalized = normalizeTitle(title)
            if (normalized.isNotEmpty()) {
                localTitles.entries.firstOrNull { normalizeTitle(it.value) == normalized }?.key?.let {
                    return it
                }
            }
        }

        return null
    }
}