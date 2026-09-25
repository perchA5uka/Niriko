package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.dao.AnitabiDao
import com.otakup.niriko.data.local.entity.AnitabiPointEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.anitabi.AnitabiClient
import com.otakup.niriko.data.remote.anitabi.AnitabiLitePoint
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 圣地巡礼（Anitabi）取景地标仓储（阶段 K）。
 * 仅动画且非 NSFW 条目；D7 快照缓存；远端失败回退缓存；静默不崩溃。
 */
class AnitabiRepository(
    private val dao: AnitabiDao,
    private val client: AnitabiClient = AnitabiClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val pointsSerializer = ListSerializer(AnitabiLitePoint.serializer())

    suspend fun load(subjectId: Long, type: SubjectType, nsfw: Boolean): AnitabiPointEntity? {
        if (type != SubjectType.ANIME || nsfw) return null
        val cached = dao.getBySubjectId(subjectId)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < D7) return cached
        val response = client.fetch(subjectId) ?: return cached
        if (response.litePoints.isEmpty()) return cached
        val entity = AnitabiPointEntity(
            subjectId = subjectId,
            city = response.city,
            pointsLength = response.pointsLength,
            imagesLength = response.imagesLength,
            litePointsJson = json.encodeToString(pointsSerializer, response.litePoints),
            fetchedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity
    }

    fun decodePoints(entity: AnitabiPointEntity?): List<AnitabiLitePoint> {
        if (entity == null) return emptyList()
        return runCatching { json.decodeFromString(pointsSerializer, entity.litePointsJson) }.getOrDefault(emptyList())
    }

    companion object {
        /** 7 天快照缓存。 */
        private const val D7 = 7L * 24 * 60 * 60 * 1000
    }
}
