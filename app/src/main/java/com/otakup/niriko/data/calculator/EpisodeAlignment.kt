package com.otakup.niriko.data.calculator

/**
 * Bangumi 章节 ↔ TMDb 每集的对齐（**纯函数，可单测**）。
 *
 * ## 为什么需要它
 *
 * Bangumi 的季/集划分与 TMDb 不总是 1:1：
 * - Bangumi 把两季合成一个词条（TMDb 是 season 1 + season 2）；
 * - Bangumi 词条含 SP / OVA / OP / ED，TMDb 的季里可能有 specials（season 0）；
 * - 少数条目集号从 0 开始或有缺号。
 *
 * ## 对齐策略（保守、可解释）
 *
 * 1. 只对齐**本篇**（Bangumi type == 0，TMDb episodeNumber > 0）；
 * 2. 优先按集号直接匹配（Bangumi sort 四舍五入 == TMDb episodeNumber）；
 * 3. 剩余未被匹配的按**顺序**补位（Bangumi 第 k 个未匹配 ↔ TMDb 第 k 个未匹配）；
 * 4. 数量差距过大（超过 20% 或超过 3 集）时**不做顺序补位**，只保留集号直接匹配——
 *    宁可少匹配，也不要错配出一张骗人的曲线。
 */
object EpisodeAlignment {

    /** Bangumi 侧待对齐的章节。 */
    data class BangumiEp(val epId: Long, val sort: Double)

    /** TMDb 侧单集（只保留对齐需要的字段）。 */
    data class TmdbEp(val episodeNumber: Int, val score: Float?, val voteCount: Int?, val stillUrl: String?)

    /** TMDb 单集 → Bangumi 章节的映射结果。 */
    data class Aligned(
        val epId: Long,
        val tmdb: TmdbEp,
    )

    data class Result(
        val matched: List<Aligned>,
        /** 未能对齐的 Bangumi 章节数。 */
        val unmatchedBangumi: Int,
        /** 未能对齐的 TMDb 集数。 */
        val unmatchedTmdb: Int,
    ) {
        val matchedCount: Int get() = matched.size
        val hasData: Boolean get() = matched.isNotEmpty()
    }

    fun align(bangumiEps: List<BangumiEp>, tmdbEps: List<TmdbEp>): Result {
        val bgm = bangumiEps.sortedBy { it.sort }
        val tmdb = tmdbEps.filter { it.episodeNumber > 0 }.sortedBy { it.episodeNumber }
        if (bgm.isEmpty() || tmdb.isEmpty()) {
            return Result(emptyList(), bgm.size, tmdb.size)
        }

        val usedTmdb = HashSet<Int>()   // 索引
        val matched = ArrayList<Aligned>(minOf(bgm.size, tmdb.size))
        val matchedBgmEpIds = HashSet<Long>()

        // 1) 集号直接匹配
        val byNumber = tmdb.withIndex().associate { (idx, ep) -> ep.episodeNumber to idx }
        for (ep in bgm) {
            val number = kotlin.math.round(ep.sort).toInt()
            val idx = byNumber[number] ?: continue
            if (usedTmdb.contains(idx)) continue
            usedTmdb.add(idx)
            matched.add(Aligned(ep.epId, tmdb[idx]))
            matchedBgmEpIds.add(ep.epId)
        }

        // 2) 顺序补位（仅在两侧剩余数量接近时）
        val remainingBgm = bgm.filter { it.epId !in matchedBgmEpIds }
        val remainingTmdbIdx = tmdb.indices.filter { it !in usedTmdb }
        if (remainingBgm.isNotEmpty() && remainingTmdbIdx.isNotEmpty()) {
            val diff = kotlin.math.abs(remainingBgm.size - remainingTmdbIdx.size)
            val tolerance = maxOf(3, (maxOf(remainingBgm.size, remainingTmdbIdx.size) * 0.2f).toInt())
            if (diff <= tolerance) {
                val pairCount = minOf(remainingBgm.size, remainingTmdbIdx.size)
                for (i in 0 until pairCount) {
                    matched.add(Aligned(remainingBgm[i].epId, tmdb[remainingTmdbIdx[i]]))
                    matchedBgmEpIds.add(remainingBgm[i].epId)
                    usedTmdb.add(remainingTmdbIdx[i])
                }
            }
        }

        return Result(
            matched = matched.sortedBy { it.epId },
            unmatchedBangumi = bgm.size - matchedBgmEpIds.size,
            unmatchedTmdb = tmdb.size - usedTmdb.size,
        )
    }

    /**
     * 挑选与 Bangumi 词条对应的 TMDb 季。
     *
     * 单季作品直接返回该季；多季时按「开播年份最接近」+「集数最接近」打分。
     * 找不到合适季时回退 season 1，最终回退第一个非特别篇季。
     */
    fun pickSeason(
        seasons: List<SeasonRef>,
        subjectYear: Int?,
        subjectEpisodes: Int?,
    ): Int? {
        val regular = seasons.filter { it.seasonNumber > 0 }
        if (regular.isEmpty()) return null
        if (regular.size == 1) return regular.first().seasonNumber

        var best: SeasonRef? = null
        var bestScore = Int.MIN_VALUE
        for (season in regular) {
            var score = 0
            if (subjectYear != null && season.year != null) {
                score -= kotlin.math.abs(subjectYear - season.year) * 10
            }
            if (subjectEpisodes != null && season.episodeCount != null) {
                score -= kotlin.math.abs(subjectEpisodes - season.episodeCount)
            }
            if (score > bestScore) {
                bestScore = score
                best = season
            }
        }
        return best?.seasonNumber ?: regular.first().seasonNumber
    }

    /** 季候选（TMDb seasons[] 的纯数据投影）。 */
    data class SeasonRef(
        val seasonNumber: Int,
        val year: Int?,
        val episodeCount: Int?,
        val name: String?,
    )
}
