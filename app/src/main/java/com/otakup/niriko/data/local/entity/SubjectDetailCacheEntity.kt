package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 详情页「按来源」的缓存状态（表 subject_detail_cache）。B15 阶段 1。
 *
 * ## 为什么按 (subjectId, sourceKey) 拆行，而不是把整个 UI state 序列化成一个 JSON
 *
 * 详情页要打的是一堆**互相独立**的外部源（infobox / 权威评分 / VNDB / AniList / TMDb / 豆瓣…）。
 * 拆行以后：
 * - 某一源失败不会牵连别的源（[SubjectDetailCacheDao.markFailure] 只写失败信息、不动 payload）；
 * - 「手动绑定 / 解绑 / 换季 / 评分 / 封面修改」只需失效受影响的 sourceKey，
 *   不必整条重来（见 [SubjectDetailCacheDao.invalidate]）。
 *
 * ## 为什么 payload 是 JSON
 *
 * 各来源的远程结构差异太大，逐字段建表会得到一张几十列、绝大多数列恒为 null 的表。
 * 计划书 §7.2 允许「复杂远程结构若必须 JSON 保存」，但要求**必须带来源、版本、时间和失败状态** ——
 * 这正是 [schemaVersion] / [fetchedAt] / [expiresAt] / [errorSummary] 的作用：
 * 将来某个来源的 JSON 结构变了，按 [schemaVersion] 判定过期即可，不用写迁移。
 *
 * ## 状态语义（读的时候必须能区分这四种）
 *
 * 1. 从未取过 → 没有行；
 * 2. 取成功 → 有行且 payload != null；
 * 3. 取成功但结果为空 → 有行、payload 是「空的合法值」、errorSummary == null（与失败区分）；
 * 4. 取失败 → errorSummary != null、lastErrorAt != null，**payload 保持上一次成功的值**。
 */
@Entity(
    tableName = "subject_detail_cache",
    primaryKeys = ["subjectId", "sourceKey"],
    indices = [Index(value = ["expiresAt"])],
)
data class SubjectDetailCacheEntity(
    val subjectId: Long,
    /** 来源键：infobox / external_ratings / vndb / anilist / tmdb / douban 等（见 SourceKeys）。 */
    val sourceKey: String,
    /** 该来源 payload 的结构版本；读取时与当前期望版本不一致就当作过期。 */
    val schemaVersion: Int,
    val fetchedAt: Long,
    /** 过期时刻（`fetchedAt + TTL`）。到点后先展示旧内容、后台刷新（§7.3 步骤 3）。 */
    val expiresAt: Long,
    /** 最近一次**成功**的结果（JSON）；失败时保留旧值，绝不清空。 */
    val payload: String? = null,
    /** 最近一次失败的可读摘要；成功时清空。 */
    val errorSummary: String? = null,
    val lastErrorAt: Long? = null,
) {
    /**
     * 详情页各来源的稳定键名（写库与失效都只认这些常量，避免字符串散落）。
     *
     * 直接嵌在类里而不是放进 companion object：Kotlin 不会把 companion 内部的嵌套对象
     * 再导出到外层类名下，写成 `SubjectDetailCacheEntity.SourceKeys` 会编译不过。
     */
    object SourceKeys {
        const val INFOBOX = "infobox"
        const val EXTERNAL_RATINGS = "external_ratings"
        const val VNDB = "vndb"
        const val ANILIST = "anilist"
        const val TMDb = "tmdb"
        const val DOUBAN = "douban"
        const val STEAM = "steam"
        const val ANITABI = "anitabi"
        const val GUESS_YOU_LIKE = "guess_you_like"
        const val MANUAL_AWARDS = "manual_awards"
        const val EPISODE_RATINGS = "episode_ratings"
        const val COVER_CANDIDATES = "cover_candidates"

        /**
         * 集合类结果（角色 / Staff / 关联作品）的**元信息行**：payload 记三类各多少条，时间戳决定过期。
         *
         * 为什么需要它：`subject_relation_cache` 用「0 行」表示「这个集合是空的」，
         * 与「我们从来没取过」无法区分。元信息行把这两件事分开 —— 没有元信息行就是没取过。
         */
        const val COLLECTIONS_META = "collections_meta"

        /** 评分分布（Bangumi 各分段人数）——争议度/百分位由它在恢复路径上重算。 */
        const val RATING_DISTRIBUTION = "rating_distribution"
    }
}
