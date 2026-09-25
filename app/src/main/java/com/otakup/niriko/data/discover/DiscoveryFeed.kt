package com.otakup.niriko.data.discover

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType

/**
 * 发现页统一排序核心（第 6 轮 §3.3 的「v1 规则版」）。
 *
 * ## 它解决什么
 *
 * 改造前的「全部」是「每类各取 20 条 → 按类型轮流交错 → take(20)」：
 * 固定配额（每类必然一样多）、跨类没有质量可比性、且只有 20 条且不可翻页。
 * 这里换成一条可解释的流水线：
 *
 * 各类型候选（已按类内热度取）→ 合并去重 → 类内归一化位置 → 质量门槛
 * → 类型多样性重排 → take(目标条数)
 *
 * 同一套函数同时服务「发现页 · 全部」（5 类候选池）与「当季热门」（动画/三次元候选池），
 * 见规格 §3.4。
 *
 * ## 为什么是「类内归一 + 多样性重排」而不是跨类加权总分
 *
 * 规格 §3.2 已确认：v0 搜索响应拿不到收藏数（要拿只能逐条打详情，125 条候选 = 125 次请求），
 * 而 rank / rating.total 都是**类内**量纲（动画的评分人数普遍是音乐的十倍以上），
 * 直接跨类加权会把类别偏置带进来。因此把「量纲归一」交给**类内取前若干**，
 * 本对象只做「按类内位置排序 + 多样性约束」（v1）；加权版（v1.5）本轮不做。
 *
 * ## 确定性
 *
 * 无随机、无时钟、不读环境：同一输入必然得到同一输出。所有阈值都在 [Params] 里显式传入，
 * 常量集中在 [DiscoveryFeed] 的伴生对象里，便于一处调整（规格 §11 的风险缓解）。
 *
 * ## 不填充
 *
 * 候选（去重 + 过质量门槛后）不足目标条数时，返回的就是实际条数，绝不补齐
 * （规格 §2.3 步骤 ⑤ / §3.5）。
 */
object DiscoveryFeed {

    /** 质量门槛：评分人数下限。规格 §2.3 步骤 ③（官方 filter 支持 rating_count，本地同样用这个阈值）。 */
    const val MIN_RATING_TOTAL = 100

    /** 多样性滑动窗口大小（条）。规格 §2.3 步骤 ④。 */
    const val WINDOW_SIZE = 10

    /** 滑动窗口内同类型上限（条）。规格 §2.3 步骤 ④ / §3.3。 */
    const val MAX_SAME_TYPE_IN_WINDOW = 3

    /** 连续同类型上限（条）。规格 §2.3 步骤 ④ / §3.3。 */
    const val MAX_CONSECUTIVE_SAME_TYPE = 2

    /** 小类保底条数（若该小类候选够）。规格 §2.3 步骤 ④。 */
    const val MIN_PER_MINOR_TYPE = 3

    /** 目标条数（原 300，规格 §2.3 步骤 ⑤）。 */
    const val TARGET_SIZE = 30

    /**
     * 「小类」判定：规格 §2.3 点名 三次元 / 音乐 / 书籍 各至少保留 3 条。
     *
     * 动画与游戏是候选大户，不给保底 —— 它们是多样性约束的**压制**对象。
     */
    val DEFAULT_MINOR_TYPES: Set<SubjectType> =
        setOf(SubjectType.REAL, SubjectType.MUSIC, SubjectType.BOOK)

    /**
     * 算法参数。
     *
     * @property minRatingTotal 质量门槛的下限（含）。rating_total 为 null 视为「无法证明质量」，一并挡掉。
     * @property windowSize 滑动窗口大小。
     * @property maxSameTypeInWindow 窗口内同类型上限（含新放进去的那条）。
     * @property maxConsecutiveSameType 连续同类型上限。
     * @property minPerMinorType 小类保底条数；0 表示关闭保底。
     * @property targetSize 目标条数；0 表示返回空。
     * @property minorTypes 享受保底的类型集合。
     * @property relaxWhenStuck 所有剩余候选都违反约束时是否放宽约束继续取。
     *   默认 true：**约束让位于「不丢条目」** —— 否则候选只剩单一类型时（当季热门常见）
     *   结果会在连续上限处直接停住，只剩 2 条。false 为严格模式（违反即停止），供单测锁定约束行为。
     */
    data class Params(
        val minRatingTotal: Int = MIN_RATING_TOTAL,
        val windowSize: Int = WINDOW_SIZE,
        val maxSameTypeInWindow: Int = MAX_SAME_TYPE_IN_WINDOW,
        val maxConsecutiveSameType: Int = MAX_CONSECUTIVE_SAME_TYPE,
        val minPerMinorType: Int = MIN_PER_MINOR_TYPE,
        val targetSize: Int = TARGET_SIZE,
        val minorTypes: Set<SubjectType> = DEFAULT_MINOR_TYPES,
        val relaxWhenStuck: Boolean = true,
    ) {
        init {
            require(windowSize >= 1) { "windowSize 必须 >= 1" }
            require(maxSameTypeInWindow >= 1) { "maxSameTypeInWindow 必须 >= 1" }
            require(maxConsecutiveSameType >= 1) { "maxConsecutiveSameType 必须 >= 1" }
            require(targetSize >= 0) { "targetSize 必须 >= 0" }
            require(minPerMinorType >= 0) { "minPerMinorType 必须 >= 0" }
        }
    }

    /**
     * 一个类型的候选（**已按类内热度序**，见规格 §2.3 步骤 ②，通常是 sort=heat 的返回顺序）。
     *
     * 类内顺序即「该类型的候选优先级」：越靠前越热，归一化位置越小。
     */
    data class TypeCandidates(
        val type: SubjectType,
        val subjects: List<SubjectEntity>,
    )

    /** 重排后的一条结果。 */
    data class Entry(
        val subject: SubjectEntity,
        val type: SubjectType,
        /** 类内原始位置（0 起；0 = 该类最热）。 */
        val typeRank: Int,
        /**
         * 类内归一化位置（0..1）：0 = 该类第一名，1 = 该类最后一名。
         * 供当前排序与将来的加权版（v1.5）复用。
         */
        val normalizedPosition: Double,
        /** 是否为「小类保底」选中的条目（该小类候选够 3 条时才可能为 true）。 */
        val guaranteed: Boolean,
    )

    /**
     * 一次 Feed 计算的完整结果。
     *
     * @property items 最终条目（已重排 + 截断）。
     * @property candidateCount 合并去重后的候选数（质量门槛**之前**）。
     * @property duplicateCount 因 subjectId 重复被丢弃的条数。
     * @property rejectedByQuality 因质量门槛被丢弃的条数。
     * @property typeCounts 各类型实际入选条数（按传入的组顺序，含 0）。
     */
    data class Result(
        val items: List<Entry>,
        val candidateCount: Int,
        val duplicateCount: Int,
        val rejectedByQuality: Int,
        val typeCounts: Map<SubjectType, Int>,
    ) {
        /** 只要条目本体（UI 直接用这个）。 */
        val subjects: List<SubjectEntity> get() = items.map { it.subject }

        /** 实际条数。 */
        val size: Int get() = items.size

        /** 某类型入选条数。 */
        fun countOf(type: SubjectType): Int = typeCounts[type] ?: 0
    }

    /**
     * 主入口：候选池 → 去重 → 类内归一 → 质量门槛 → 多样性重排 → 截断。
     *
     * @param groups 各类型候选；**列表顺序决定并列时的优先级**（先出现的类型优先）。
     * @param params 算法参数（阈值与多样性窗口都在这里）。
     */
    fun build(groups: List<TypeCandidates>, params: Params = Params()): Result {
        // ---- ① 合并去重：按 subjectId，先到先得；类内保持传入顺序 ----
        val seenIds = HashSet<Long>()
        var duplicateCount = 0
        val deduped = ArrayList<TypeCandidates>(groups.size)
        for (group in groups) {
            val kept = ArrayList<SubjectEntity>(group.subjects.size)
            for (subject in group.subjects) {
                if (seenIds.add(subject.subjectId)) kept.add(subject) else duplicateCount++
            }
            if (kept.isNotEmpty()) deduped.add(TypeCandidates(group.type, kept))
        }
        val candidateCount = deduped.sumOf { it.subjects.size }

        // ---- ② 类内归一化位置 + ③ 质量门槛 ----
        var rejectedByQuality = 0
        val queues = ArrayList<ArrayDeque<Cand>>(deduped.size)
        val totals = IntArray(deduped.size)
        for ((typeIndex, group) in deduped.withIndex()) {
            val classSize = group.subjects.size
            val queue = ArrayDeque<Cand>(classSize)
            for ((rank, subject) in group.subjects.withIndex()) {
                val total = subject.ratingTotal
                if (total == null || total < params.minRatingTotal) {
                    rejectedByQuality++
                    continue
                }
                queue.addLast(
                    Cand(
                        subject = subject,
                        type = group.type,
                        typeRank = rank,
                        normalizedPosition = if (classSize <= 1) {
                            0.0
                        } else {
                            rank.toDouble() / (classSize - 1).toDouble()
                        },
                        typeIndex = typeIndex,
                    ),
                )
            }
            queues.add(queue)
            totals[typeIndex] = queue.size
        }

        // ---- ④ 多样性重排（贪心）+ ⑤ 截断 ----
        val placed = ArrayList<Entry>(params.targetSize)
        val placedTypes = ArrayList<SubjectType>(params.targetSize)
        val typeCounts = LinkedHashMap<SubjectType, Int>()
        deduped.forEach { typeCounts[it.type] = 0 }

        while (placed.size < params.targetSize) {
            val pick = chooseNext(queues, placedTypes, params, totals, typeCounts) ?: break
            queues[pick.typeIndex].removeFirst()
            val guaranteed = isMinorGuaranteeSlot(pick, params, totals, typeCounts)
            placed.add(
                Entry(
                    subject = pick.subject,
                    type = pick.type,
                    typeRank = pick.typeRank,
                    normalizedPosition = pick.normalizedPosition,
                    guaranteed = guaranteed,
                ),
            )
            placedTypes.add(pick.type)
            typeCounts[pick.type] = (typeCounts[pick.type] ?: 0) + 1
        }

        return Result(
            items = placed,
            candidateCount = candidateCount,
            duplicateCount = duplicateCount,
            rejectedByQuality = rejectedByQuality,
            typeCounts = typeCounts,
        )
    }

    /** 便捷入口：按类型分组的候选（Map 的迭代顺序即优先级顺序）。 */
    fun build(byType: Map<SubjectType, List<SubjectEntity>>, params: Params = Params()): Result =
        build(byType.entries.map { TypeCandidates(it.key, it.value) }, params)

    /**
     * 按类型分组（保持首次出现顺序），便于把一维候选列表（例如 /calendar 的合并结果）
     * 交给 [build]。类内顺序**不做任何重排** —— 调用方负责先按类内热度排好。
     */
    fun groupByType(subjects: List<SubjectEntity>): List<TypeCandidates> {
        val order = ArrayList<SubjectType>()
        val byType = LinkedHashMap<SubjectType, MutableList<SubjectEntity>>()
        for (subject in subjects) {
            val list = byType.getOrPut(subject.type) {
                order.add(subject.type)
                ArrayList()
            }
            list.add(subject)
        }
        return order.map { TypeCandidates(it, byType.getValue(it)) }
    }

    // ==================== 内部实现 ====================

    private data class Cand(
        val subject: SubjectEntity,
        val type: SubjectType,
        val typeRank: Int,
        val normalizedPosition: Double,
        val typeIndex: Int,
    )

    /**
     * 贪心挑下一条。
     *
     * 三档候选（严格 → 放宽）：
     * 1. 同时满足「窗口内同类上限」与「连续同类上限」；
     * 2. 只满足窗口上限（连续约束放宽）；
     * 3. 全部放宽。
     *
     * 档内按「小类保底优先 → 类内位置越靠前越优先 → 组顺序 → subjectId」选（见 [ordering]）。
     * relaxWhenStuck = false 时第 2/3 档不参与，全被约束挡住就返回 null（停止填充）。
     */
    private fun chooseNext(
        queues: List<ArrayDeque<Cand>>,
        placedTypes: List<SubjectType>,
        params: Params,
        totals: IntArray,
        typeCounts: Map<SubjectType, Int>,
    ): Cand? {
        val heads = queues.indices.filter { queues[it].isNotEmpty() }.map { queues[it].first() }
        if (heads.isEmpty()) return null

        fun windowOk(cand: Cand): Boolean =
            countInTail(placedTypes, cand.type, params.windowSize - 1) < params.maxSameTypeInWindow

        fun consecutiveOk(cand: Cand): Boolean =
            trailingRun(placedTypes, cand.type) < params.maxConsecutiveSameType

        val strict = heads.filter { windowOk(it) && consecutiveOk(it) }
        val usable = when {
            strict.isNotEmpty() -> strict
            !params.relaxWhenStuck -> return null
            else -> heads.filter { windowOk(it) }.ifEmpty { heads }
        }
        return usable.minWithOrNull(ordering(params, totals, typeCounts))
    }

    /** 档内排序：小类保底优先，然后类内位置、类内原始位置、组顺序、subjectId（保证确定性）。 */
    private fun ordering(
        params: Params,
        totals: IntArray,
        typeCounts: Map<SubjectType, Int>,
    ): Comparator<Cand> = Comparator { a, b ->
        val ga = if (isMinorGuaranteeSlot(a, params, totals, typeCounts)) 0 else 1
        val gb = if (isMinorGuaranteeSlot(b, params, totals, typeCounts)) 0 else 1
        var result = ga.compareTo(gb)
        if (result == 0) result = a.normalizedPosition.compareTo(b.normalizedPosition)
        if (result == 0) result = a.typeRank.compareTo(b.typeRank)
        if (result == 0) result = a.typeIndex.compareTo(b.typeIndex)
        if (result == 0) result = a.subject.subjectId.compareTo(b.subject.subjectId)
        result
    }

    /** 这条候选是否算「小类保底」名额：小类 + 候选够 3 条 + 还没用满保底名额。 */
    private fun isMinorGuaranteeSlot(
        cand: Cand,
        params: Params,
        totals: IntArray,
        typeCounts: Map<SubjectType, Int>,
    ): Boolean {
        if (params.minPerMinorType <= 0) return false
        if (!params.minorTypes.contains(cand.type)) return false
        val available = totals.getOrElse(cand.typeIndex) { 0 }
        if (available < params.minPerMinorType) return false
        return (typeCounts[cand.type] ?: 0) < params.minPerMinorType
    }

    /** 已放置序列的**尾部** tailSize 条里，某类型出现几次。 */
    private fun countInTail(
        placedTypes: List<SubjectType>,
        type: SubjectType,
        tailSize: Int,
    ): Int {
        if (tailSize <= 0) return 0
        val from = (placedTypes.size - tailSize).coerceAtLeast(0)
        var count = 0
        for (i in from until placedTypes.size) {
            if (placedTypes[i] == type) count++
        }
        return count
    }

    /** 已放置序列**末尾连续**同类型的条数。 */
    private fun trailingRun(placedTypes: List<SubjectType>, type: SubjectType): Int {
        var count = 0
        for (i in placedTypes.indices.reversed()) {
            if (placedTypes[i] == type) count++ else break
        }
        return count
    }
}
