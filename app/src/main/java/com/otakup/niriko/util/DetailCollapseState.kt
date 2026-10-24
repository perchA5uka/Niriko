package com.otakup.niriko.util

/**
 * 详情页「外部源区块」的折叠状态（C · 6.15）。
 *
 * 存储形态：DataStore 里的**一个**字符串，按作品分开：
 * "328609:steam,vndb;100:anilist"。
 *
 * 为什么不用布尔字段一格一个：区块数量会随版本变，键一多就到处迁移；串可以只解析认识的 key、
 * 未知的丢弃，升级不会把用户配置弄坏（与 DetailLayoutPolicy 的 hidden 同一套思路）。
 *
 * 与「隐藏」的区别：隐藏 = 整块不出现；折叠 = 留一行标题，点开才组合内容。
 * 两者独立存储，互不覆盖。
 */
object DetailCollapseState {

    private const val SUBJECT_SEP = ';'
    private const val KEY_SEP = ':'

    /** 编码：作品按 id 升序、区块按字母序，保证同一份集合永远得到同一个串。 */
    fun encode(map: Map<Long, Set<String>>): String =
        map.filterValues { it.isNotEmpty() }
            .toSortedMap()
            .entries
            .mapNotNull { (subjectId, keys) ->
                val normalized = keys.map { it.trim() }.filter { it.isNotEmpty() }.sorted().distinct()
                if (normalized.isEmpty()) null
                else "$subjectId${KEY_SEP}${normalized.joinToString(",")}"
            }
            .joinToString(SUBJECT_SEP.toString())

    /** 解码：非法行/未知格式一律跳过，绝不抛异常。 */
    fun decode(encoded: String?): Map<Long, Set<String>> {
        val raw = encoded?.trim().orEmpty()
        if (raw.isEmpty()) return emptyMap()
        val out = linkedMapOf<Long, MutableSet<String>>()
        raw.split(SUBJECT_SEP).forEach { piece ->
            val idText = piece.substringBefore(KEY_SEP, "")
            val keysText = piece.substringAfter(KEY_SEP, "")
            val subjectId = idText.trim().toLongOrNull() ?: return@forEach
            val keys = keysText.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (keys.isNotEmpty()) out.getOrPut(subjectId) { linkedSetOf() }.addAll(keys)
        }
        return out
    }

    /** 某作品当前折叠的区块 key 集合。 */
    fun collapsedSections(encoded: String?, subjectId: Long): Set<String> =
        decode(encoded)[subjectId].orEmpty()

    fun isCollapsed(encoded: String?, subjectId: Long, sectionKey: String): Boolean =
        sectionKey in collapsedSections(encoded, subjectId)

    /** 切换一个区块；返回新的编码串（未知作品自动新建，空集合自动清理）。 */
    fun toggle(encoded: String?, subjectId: Long, sectionKey: String): String {
        val map = decode(encoded).mapValues { it.value.toMutableSet() }.toMutableMap()
        val keys = map.getOrPut(subjectId) { mutableSetOf() }
        if (!keys.add(sectionKey)) keys.remove(sectionKey)
        return encode(map)
    }

    /** 某作品的所有折叠状态清空（预留：删除条目时调用）。 */
    fun clear(encoded: String?, subjectId: Long): String =
        encode(decode(encoded) - subjectId)
}
