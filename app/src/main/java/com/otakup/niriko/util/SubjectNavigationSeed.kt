package com.otakup.niriko.util

import coil.memory.MemoryCache
import com.otakup.niriko.data.local.entity.SubjectEntity

/** Small navigation handoff: metadata only; Coil owns the bitmap lifetime. */
object SubjectNavigationSeed {
    data class Cover(val url: String, val aspectRatio: Float, val memoryCacheKey: MemoryCache.Key?)
    private val covers = LinkedHashMap<Long, Cover>()
    private var pending: SubjectEntity? = null

    @Synchronized fun rememberCover(id: Long, url: String, width: Int, height: Int, key: MemoryCache.Key?) {
        if (width <= 0 || height <= 0) return
        covers.remove(id)
        covers[id] = Cover(url, width.toFloat() / height, key)
        while (covers.size > 48) covers.remove(covers.keys.first())
    }

    @Synchronized fun coverFor(id: Long): Cover? = covers[id]
    @Synchronized fun prepare(subject: SubjectEntity?) { pending = subject }
    @Synchronized fun peekSubject(id: Long): SubjectEntity? = pending?.takeIf { it.subjectId == id }

    fun preparePreview(relation: com.otakup.niriko.data.remote.SubjectRelationInfo) {
        preparePreview(relation.subjectId, relation.title, relation.titleCN, relation.type, relation.imageUrl)
    }
    fun preparePreview(subject: com.otakup.niriko.data.remote.PersonSubjectInfo) {
        preparePreview(subject.subjectId, subject.title, subject.titleCN, subject.type, subject.imageUrl)
    }
    private fun preparePreview(id: Long, title: String, titleCN: String?, type: Int, cover: String?) {
        if (id <= 0 || type !in listOf(1, 2, 3, 4, 6) || (title.isBlank() && titleCN.isNullOrBlank())) return
        // Observed API fields only. This preview never enters Room or fabricates unavailable metadata.
        prepare(SubjectEntity(id, title, titleCN,
            com.otakup.niriko.data.model.SubjectType.fromBangumiType(type), coverUrl = cover))
    }
    @Synchronized fun takeSubject(id: Long): SubjectEntity? {
        val result = pending?.takeIf { it.subjectId == id }
        pending = null
        return result
    }
}
