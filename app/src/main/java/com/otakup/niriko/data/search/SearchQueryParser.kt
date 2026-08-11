package com.otakup.niriko.data.search

/**
 * 搜索查询语法解析器（对齐 Kazumi SearchParser 子集）。
 *
 * 支持的语法：
 * - `tag:异世界`          → 标签精确过滤（可多次；当前实现取第一个标签，多 tag 且关系留作后续）
 * - `sort:heat`           → 排序（match/heat/rank/score）
 * - 普通词 `异世界`        → keyword（标题/别名检索）
 * - 混合 `异世界 tag:搞笑` → keyword + 标签过滤同时生效
 *
 * 示例：
 * - `tag:异世界`           → keyword="", tags=["异世界"], sort=null
 * - `tag:异世界 tag:搞笑`  → keyword="", tags=["异世界","搞笑"], sort=null（当前取"异世界"）
 * - `异世界 sort:rank`     → keyword="异世界", tags=[], sort="rank"
 */
data class ParsedSearchQuery(
    val keyword: String = "",
    val tags: List<String> = emptyList(),
    val sort: String? = null,
    /** 是否包含任何 tag: 语法（用于决定是否走纯标签过滤路径）。 */
    val hasTagSyntax: Boolean = false,
)

object SearchQueryParser {

    private val TAG_REGEX = Regex("""(?:^|\s)tag:([^\s]+)""", RegexOption.IGNORE_CASE)
    private val SORT_REGEX = Regex("""(?:^|\s)sort:([\w-]+)""", RegexOption.IGNORE_CASE)

    fun parse(query: String): ParsedSearchQuery {
        val tags = TAG_REGEX.findAll(query)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        val sort = SORT_REGEX.find(query)?.groupValues?.get(1)?.lowercase()
        // 移除所有 tag:/sort: 语法 token，剩余为 keyword
        val keyword = query
            .replace(TAG_REGEX, " ")
            .replace(SORT_REGEX, " ")
            .trim()
        return ParsedSearchQuery(
            keyword = keyword,
            tags = tags,
            sort = sort,
            hasTagSyntax = tags.isNotEmpty(),
        )
    }
}
