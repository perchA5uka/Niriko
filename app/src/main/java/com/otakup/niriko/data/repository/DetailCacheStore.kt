package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.dao.DetailCacheDao
import com.otakup.niriko.data.local.entity.SubjectDetailCacheEntity
import com.otakup.niriko.data.local.entity.SubjectRelationCacheEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.SubjectRelationInfo
import com.otakup.niriko.data.remote.rating.ExternalRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 详情页「集合类外部结果」的落库读写（B15 阶段 2 的垂直切片）。
 *
 * ## 它解决的具体问题
 *
 * 详情页每次打开都会重拉角色 / Staff / 关联作品（用户报告：「二次进详情页会重跑外部源，白烧流量」）。
 * 进程内的 TtlCache 只在同一进程的 5 分钟内有效 —— 杀掉进程、或从另一个入口进来就白费。
 * 这里把这三类结果落到 Room，**重启后也能直接展示**。
 *
 * ## 为什么三类要么一起给、要么都不给
 *
 * 半份列表比没有更糟：只补上角色、关联作品还是空的，用户会以为「这部作品没有关联作品」。
 * 因此 [readCollections] 用一行元信息（[SubjectDetailCacheEntity.SourceKeys.COLLECTIONS_META]）判定
 * 「到底取过没有」：没有元信息行 = 从未取过 = 返回 null，调用方照常走网络；
 * 有元信息行但某类 0 行 = 那一类**确实是空的**（合法的空结果，不是缺数据）。
 *
 * ## 过期不等于不可用
 *
 * [CachedCollections.isExpired] 为 true 时**仍然返回数据**，由调用方决定：
 * 先显示旧内容、再后台刷新（§7.3 步骤 3/5：过期先显示旧内容、失败保留旧成功数据并提示过期）。
 */
class DetailCacheStore(
    private val dao: DetailCacheDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = COLLECTIONS_TTL_MS,
) {

    /**
     * 本地读到的集合结果。
     *
     * @param expiresAt 三类中**最早**的过期时刻 —— 它们是一起取的，只要一类过期就该整份刷新。
     */
    data class CachedCollections(
        val characters: List<CharacterInfo>,
        val staff: List<StaffInfo>,
        val relations: List<SubjectRelationInfo>,
        val fetchedAt: Long,
        val expiresAt: Long,
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now >= expiresAt
    }

    /** 宽松模式：将来给模型加字段时，旧行仍能读出来，缺的字段走默认值。 */
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 读本地集合结果；**从未取过、或只剩元信息行与数据不一致**时返回 null（调用方会重新取）。
     *
     * ## 为什么必须核对条数
     *
     * 「元信息行说取过」与「数据行还在」是**两件事**，它们会漂移：真机实测撞到过一次 ——
     * v30→v31 迁移重建了关系表（只删数据、保留元信息行），于是缓存自认为「取过了」，
     * 三组列表全读成空，界面上角色/Staff/关联作品整块消失，而且因为「不算过期」连网络都不发，
     * **静默错到 24 小时后**。元信息行里本来就记了条数，用它核对即可让缓存自愈：
     * 对不上就当作没缓存、照常重取 —— 代价是一次请求，收益是不会静默给用户看空数据。
     */
    suspend fun readCollections(subjectId: Long): CachedCollections? = withContext(Dispatchers.IO) {
        val meta = dao.getDetail(subjectId, SubjectDetailCacheEntity.SourceKeys.COLLECTIONS_META)
            ?: return@withContext null
        val expected = expectedCounts(meta.payload) ?: return@withContext null
        val characters = decodeRows<CharacterInfo>(subjectId, KINDS.CHARACTERS).orEmpty()
        val staff = decodeRows<StaffInfo>(subjectId, KINDS.STAFF).orEmpty()
        val relations = decodeRows<SubjectRelationInfo>(subjectId, KINDS.RELATIONS).orEmpty()
        if (characters.size != expected[0] || staff.size != expected[1] || relations.size != expected[2]) {
            return@withContext null
        }
        CachedCollections(
            characters = characters,
            staff = staff,
            relations = relations,
            fetchedAt = meta.fetchedAt,
            expiresAt = meta.expiresAt,
        )
    }

    /** 从元信息行 payload 读出三类各自的条数；读不出来就当作不可用。 */
    private fun expectedCounts(payload: String?): IntArray? = runCatching {
        val obj = json.parseToJsonElement(payload.orEmpty()).jsonObject
        intArrayOf(
            obj.getValue("characters").jsonPrimitive.int,
            obj.getValue("staff").jsonPrimitive.int,
            obj.getValue("relations").jsonPrimitive.int,
        )
    }.getOrNull()

    /**
     * 写入一次**成功**的集合结果：三类整块替换 + 更新元信息行。
     * 写在同一个调用里，避免出现「角色是新的、关联作品还是旧的」这种撕裂。
     */
    suspend fun writeCollections(
        subjectId: Long,
        characters: List<CharacterInfo>,
        staff: List<StaffInfo>,
        relations: List<SubjectRelationInfo>,
    ) = withContext(Dispatchers.IO) {
        val now = clock()
        val expiresAt = now + ttlMs
        dao.replaceRelations(subjectId, KINDS.CHARACTERS, rowsOf(subjectId, KINDS.CHARACTERS, characters.map { it.id }, characters.map { json.encodeToString(it) }, now, expiresAt))
        dao.replaceRelations(subjectId, KINDS.STAFF, rowsOf(subjectId, KINDS.STAFF, staff.map { it.id }, staff.map { json.encodeToString(it) }, now, expiresAt))
        dao.replaceRelations(subjectId, KINDS.RELATIONS, rowsOf(subjectId, KINDS.RELATIONS, relations.map { it.subjectId }, relations.map { json.encodeToString(it) }, now, expiresAt))
        dao.putDetail(
            SubjectDetailCacheEntity(
                subjectId = subjectId,
                sourceKey = SubjectDetailCacheEntity.SourceKeys.COLLECTIONS_META,
                schemaVersion = COLLECTIONS_SCHEMA_VERSION,
                fetchedAt = now,
                expiresAt = expiresAt,
                payload = """{"characters":${characters.size},"staff":${staff.size},"relations":${relations.size}}""",
            ),
        )
    }

    /** 记一次**失败**：只写失败信息，**不动已经落库的成功数据**（§7.3 步骤 5）。 */
    suspend fun markCollectionsFailure(subjectId: Long, reason: String) = withContext(Dispatchers.IO) {
        dao.markFailure(subjectId, SubjectDetailCacheEntity.SourceKeys.COLLECTIONS_META, reason, clock())
        Unit
    }

    /**
     * 精准失效：只在「这些集合必然变了」时调用。它**只删缓存行**，不碰任何用户数据。
     */
    suspend fun invalidateCollections(subjectId: Long) = withContext(Dispatchers.IO) {
        dao.replaceRelations(subjectId, KINDS.CHARACTERS, emptyList())
        dao.replaceRelations(subjectId, KINDS.STAFF, emptyList())
        dao.replaceRelations(subjectId, KINDS.RELATIONS, emptyList())
        dao.invalidateDetail(subjectId, listOf(SubjectDetailCacheEntity.SourceKeys.COLLECTIONS_META))
        Unit
    }


    // ─────────────── 标量/列表类来源（infobox、权威评分、评分分布） ───────────────

    /**
     * 读一个来源的 payload。返回行本身（而不是只返回值），因为调用方需要 [SubjectDetailCacheEntity.expiresAt]
     * 来决定「先用旧内容、再后台刷新」。
     */
    suspend fun readRow(subjectId: Long, sourceKey: String): SubjectDetailCacheEntity? =
        withContext(Dispatchers.IO) { dao.getDetail(subjectId, sourceKey) }

    /** 记一次成功：整行覆盖并清空上一次的失败信息。 */
    suspend fun writeValue(subjectId: Long, sourceKey: String, payload: String) = withContext(Dispatchers.IO) {
        val now = clock()
        dao.putDetail(
            SubjectDetailCacheEntity(
                subjectId = subjectId,
                sourceKey = sourceKey,
                schemaVersion = SCALAR_SCHEMA_VERSION,
                fetchedAt = now,
                expiresAt = now + ttlMs,
                payload = payload,
            ),
        )
    }

    /** 记一次失败：只写失败信息，不动 payload（与集合类同一条铁律）。 */
    suspend fun markValueFailure(subjectId: Long, sourceKey: String, reason: String) =
        withContext(Dispatchers.IO) {
            dao.markFailure(subjectId, sourceKey, reason, clock())
            Unit
        }

    suspend fun readInfoBox(subjectId: Long): List<InfoBoxEntry>? =
        readRow(subjectId, SubjectDetailCacheEntity.SourceKeys.INFOBOX)?.let { row ->
            runCatching { json.decodeFromString<List<InfoBoxEntry>>(row.payload.orEmpty()) }.getOrNull()
        }

    suspend fun writeInfoBox(subjectId: Long, entries: List<InfoBoxEntry>) =
        writeValue(subjectId, SubjectDetailCacheEntity.SourceKeys.INFOBOX, json.encodeToString(entries))

    suspend fun readExternalRatings(subjectId: Long): List<ExternalRating>? =
        readRow(subjectId, SubjectDetailCacheEntity.SourceKeys.EXTERNAL_RATINGS)?.let { row ->
            runCatching { json.decodeFromString<List<ExternalRating>>(row.payload.orEmpty()) }.getOrNull()
        }

    suspend fun writeExternalRatings(subjectId: Long, ratings: List<ExternalRating>) =
        writeValue(subjectId, SubjectDetailCacheEntity.SourceKeys.EXTERNAL_RATINGS, json.encodeToString(ratings))

    suspend fun readRatingDistribution(subjectId: Long): Map<Int, Int>? =
        readRow(subjectId, SubjectDetailCacheEntity.SourceKeys.RATING_DISTRIBUTION)?.let { row ->
            runCatching { json.decodeFromString<Map<Int, Int>>(row.payload.orEmpty()) }.getOrNull()
        }

    suspend fun writeRatingDistribution(subjectId: Long, distribution: Map<Int, Int>) =
        writeValue(subjectId, SubjectDetailCacheEntity.SourceKeys.RATING_DISTRIBUTION, json.encodeToString(distribution))


    private inline fun <reified T> decodeRows(subjectId: Long, kind: String): List<T>? = runCatching {
        runBlocking { dao.getRelations(subjectId, kind) }
            .sortedBy { it.sortIndex }
            .map { json.decodeFromString<T>(it.payload) }
    }.getOrNull()

    private fun rowsOf(
        subjectId: Long,
        kind: String,
        itemIds: List<Long>,
        payloads: List<String>,
        now: Long,
        expiresAt: Long,
    ): List<SubjectRelationCacheEntity> = itemIds.indices.map { index ->
        SubjectRelationCacheEntity(
            subjectId = subjectId,
            kind = kind,
            itemId = itemIds[index],
            sortIndex = index,
            payload = payloads[index],
            sourceId = SOURCE_ID,
            fetchedAt = now,
            expiresAt = expiresAt,
        )
    }

    companion object {
        /**
         * 集合缓存的 TTL。
         *
         * 角色 / Staff / 关联作品很少变，24 小时是「重启后不再重拉」与「不会长期显示过期名单」的折中；
         * 同一进程内仍有一层 5 分钟内存缓存兜住「反复进出」的场景。
         */
        const val COLLECTIONS_TTL_MS = 24 * 60 * 60 * 1000L

        /** 集合类 payload 结构版本；将来给模型加字段并想强制重取时递增。 */
        const val COLLECTIONS_SCHEMA_VERSION = 1

        /** 标量类来源（infobox / 权威评分 / 评分分布）的 payload 结构版本，与集合类分开记。 */
        const val SCALAR_SCHEMA_VERSION = 1

        private const val SOURCE_ID = "bangumi"

        private val KINDS = SubjectRelationCacheEntity.RelationKinds
    }
}
