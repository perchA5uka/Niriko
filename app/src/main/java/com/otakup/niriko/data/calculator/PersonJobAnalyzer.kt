package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.remote.PersonJobStat

/**
 * 人物「参与职位」统计（**纯函数，可单测**）。
 *
 * 对齐 Bangumi-master 人物页的 `Jobs` 区块：把该人物所有参与作品的
 * `staff` 身份串拆开计数，得到「导演 12 · 脚本 5 · 分镜 3」这类可读统计。
 *
 * Bangumi 的 staff 字段可能是多值（"导演、脚本" / "原作, 系列构成"），
 * 且混用了中英文标点，因此统一按 [、,，/;；] 与空白切分后去重（同一作品的同一声明只计一次）。
 */
object PersonJobAnalyzer {

    /** 职位串分隔符（中英文混用是常态）。 */
    private val SEPARATORS = Regex("[、,，/;；|]+")

    /** 无意义的占位身份（Bangumi 在信息缺失时会给出这些）。 */
    private val PLACEHOLDERS = setOf("", "-", "—", "其他", "其它", "协力")

    /**
     * @param staffValues 每个参与作品的 staff 身份串（可为空）。
     * @return 按出现次数降序的职位统计；同频时按名称升序保证结果稳定（便于单测）。
     */
    fun analyze(staffValues: List<String?>): List<PersonJobStat> {
        val counter = LinkedHashMap<String, Int>()
        staffValues.forEach { raw ->
            val jobs = raw.orEmpty()
                .split(SEPARATORS)
                .map { it.trim() }
                .filter { it.isNotEmpty() && it !in PLACEHOLDERS }
                .distinct()
            jobs.forEach { job -> counter[job] = (counter[job] ?: 0) + 1 }
        }
        return counter.entries
            .map { PersonJobStat(job = it.key, count = it.value) }
            .sortedWith(compareByDescending<PersonJobStat> { it.count }.thenBy { it.job })
    }

    /** 主要身份（取出现最多的一项；无数据返回 null）。 */
    fun primaryJob(stats: List<PersonJobStat>): String? =
        stats.maxWithOrNull(compareBy({ it.count }, { it.job }))?.job
}
