package com.otakup.niriko.ui.settings

/**
 * 设置搜索（10.17）：把「页面/分组/条目」拉平成可过滤的索引。
 *
 * 为什么是纯数据 + 纯函数而不是在 Compose 里现搜：
 * 1. 现有设置页是手写 Compose（不是 Preference 框架），没有自描述能力，
 *    只能维护一张显式索引表——那就必须让它能被单测钉住（新增设置项忘了进索引会被测试抓）；
 * 2. 高亮需要「命中的字符区间」，这属于字符串算法，放 Composable 里既难测也难复用。
 */
enum class SearchField { TITLE, KEYWORDS, GROUP }

data class SettingsSearchEntry(
    /** 稳定 id（路由 + 条目 key）。 */
    val id: String,
    val pageTitle: String,
    val group: String,
    val title: String,
    val keywords: List<String> = emptyList(),
    /** 跳转路由（与 navigation 的 SETTINGS_* 常量同源）。 */
    val route: String,
)

data class SettingsSearchHit(
    val entry: SettingsSearchEntry,
    val matchedField: SearchField,
    /** 命中区间（用于标题高亮）；命中说明/关键词时为空。 */
    val ranges: List<IntRange>,
)

/** 查询清洗：去空白、小写（中文不受影响，英文/数字忽略大小写）。 */
fun normalizeQuery(raw: String): String = raw.trim().lowercase()

/**
 * 过滤索引。排序优先级：标题命中 > 关键词命中 > 分组命中；同级按索引顺序。
 * 空查询返回空列表（设置页未输入时不展示结果，避免一次渲染全部条目）。
 */
fun searchSettings(entries: List<SettingsSearchEntry>, rawQuery: String): List<SettingsSearchHit> {
    val q = normalizeQuery(rawQuery)
    if (q.isEmpty()) return emptyList()
    val hits = mutableListOf<Pair<Int, SettingsSearchHit>>()
    entries.forEachIndexed { index, entry ->
        val titleRanges = matchRanges(entry.title, q)
        when {
            titleRanges.isNotEmpty() -> hits += 0 to SettingsSearchHit(entry, SearchField.TITLE, titleRanges)
            entry.keywords.any { it.lowercase().contains(q) } ->
                hits += 1 to SettingsSearchHit(entry, SearchField.KEYWORDS, emptyList())
            entry.group.lowercase().contains(q) ->
                hits += 2 to SettingsSearchHit(entry, SearchField.GROUP, emptyList())
        }
    }
    return hits.sortedBy { it.first }.map { it.second }
}

/**
 * 设置主页的默认索引：8 个分类的标题/说明/分组 + 常见同义词。
 *
 * 同义词是「用户会搜的词」与「我们写标题的词」之间的差：例如分类叫「外观」，
 * 但用户会搜「壁纸 / 玻璃 / 字号」，所以这些词必须进 keywords，否则搜不到。
 */
fun defaultSettingsIndex(): List<SettingsSearchEntry> =
    SettingsCategory.entries.map { category ->
        SettingsSearchEntry(
            id = category.name,
            pageTitle = category.title,
            group = category.group.title,
            title = category.title,
            keywords = categoryKeywords(category) + listOf(category.subtitle),
            route = category.route,
        )
    }

private fun categoryKeywords(category: SettingsCategory): List<String> = when (category) {
    SettingsCategory.APPEARANCE -> listOf("主题", "配色", "玻璃", "壁纸", "图标", "开屏", "形状", "字号", "对比度")
    SettingsCategory.LIBRARY -> listOf("卡片", "视图", "统计", "启动页", "排序", "进度")
    SettingsCategory.SEARCH -> listOf("r18", "nsfw", "搜索建议", "过滤", "隐藏")
    SettingsCategory.DATASOURCE -> listOf("bangumi", "steam", "anilist", "token", "api key", "导入", "自检")
    SettingsCategory.SYNC -> listOf("webdav", "备份", "恢复", "快照", "同步", "导出")
    SettingsCategory.REFRESH -> listOf("刷新", "缓存", "诊断", "体检", "占用", "更新")
    SettingsCategory.ABOUT -> listOf("版本", "更新", "开源", "许可", "隐私")
    SettingsCategory.DONATE -> listOf("捐赠", "支持")
}

/** 全部命中区间（不区分大小写；中文按字符直配）。 */
fun matchRanges(text: String, rawQuery: String): List<IntRange> {
    val q = normalizeQuery(rawQuery)
    if (q.isEmpty() || text.isEmpty()) return emptyList()
    val hay = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val at = hay.indexOf(q, from)
        if (at < 0) break
        ranges += at until (at + q.length)
        from = at + q.length
    }
    return ranges
}
