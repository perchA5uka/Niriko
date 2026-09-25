package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.dao.ExternalIdDao
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity

/**
 * 作品外部身份绑定仓储（TMDb / IMDb / IGDB / RAWG / Discogs / MusicBrainz / Google Books / Open Library）。
 *
 * 通用化的意义：以后新增数据源**不需要再新建一张 xxx_bindings 表**（steam / vndb / anilist 时代
 * 每加一个源就要一次数据库迁移）。新源只需要新增一个 provider 常量。
 *
 * 与项目既有的保守匹配约定一致：标题匹配只产出候选，**只有用户确认才写库**。
 */
class ExternalIdRepository(
    private val dao: ExternalIdDao,
) {

    /** 该作品的全部绑定（provider → externalId）。 */
    suspend fun bindingsOf(subjectId: Long): Map<String, String> =
        runCatching { dao.getBySubject(subjectId).associate { it.provider to it.externalId } }
            .getOrDefault(emptyMap())

    suspend fun entitiesOf(subjectId: Long): List<SubjectExternalIdEntity> =
        runCatching { dao.getBySubject(subjectId) }.getOrDefault(emptyList())

    suspend fun get(subjectId: Long, provider: String): SubjectExternalIdEntity? =
        runCatching { dao.get(subjectId, provider) }.getOrNull()

    suspend fun bind(
        subjectId: Long,
        provider: String,
        externalId: String,
        titleSnapshot: String? = null,
        confidence: Float = 0f,
        bindMethod: String = SubjectExternalIdEntity.METHOD_MANUAL,
        subKey: String? = null,
    ) {
        runCatching {
            dao.upsert(
                SubjectExternalIdEntity(
                    subjectId = subjectId,
                    provider = provider,
                    externalId = externalId,
                    titleSnapshot = titleSnapshot,
                    confidence = confidence,
                    bindMethod = bindMethod,
                    subKey = subKey,
                    boundAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun unbind(subjectId: Long, provider: String) {
        runCatching { dao.delete(subjectId, provider) }
    }

    /** 备份导出 / 导入用。 */
    suspend fun all(): List<SubjectExternalIdEntity> = runCatching { dao.getAll() }.getOrDefault(emptyList())

    suspend fun insertAll(list: List<SubjectExternalIdEntity>) {
        if (list.isEmpty()) return
        runCatching { dao.insertAll(list) }
    }
}
