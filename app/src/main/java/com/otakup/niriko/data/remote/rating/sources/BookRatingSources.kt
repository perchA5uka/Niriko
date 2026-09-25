package com.otakup.niriko.data.remote.rating.sources

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.remote.rating.RatingCandidate
import com.otakup.niriko.data.remote.rating.RatingHttp
import com.otakup.niriko.data.remote.rating.RatingSource
import com.otakup.niriko.data.remote.rating.RatingSourceKeys
import com.otakup.niriko.data.remote.rating.arr
import com.otakup.niriko.data.remote.rating.int
import com.otakup.niriko.data.remote.rating.obj
import com.otakup.niriko.data.remote.rating.objects
import com.otakup.niriko.data.remote.rating.ratingUrlEncode
import com.otakup.niriko.data.remote.rating.str
import com.otakup.niriko.data.remote.rating.strings

/**
 * 书籍 / 漫画评分。
 *
 * 现实：**豆瓣读书与 Goodreads 都不可用**（豆瓣 API 已封闭，实测 403 invalid_apikey；
 * Goodreads 2020-12-08 起停止发放新 key）。因此只能取有开放 API 的两家，
 * 而它们的**评分普遍稀疏**——取不到时整块静默隐藏，不做任何"猜分"。
 */
class GoogleBooksRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_GOOGLE_BOOKS
    override val label: String = "Google Books"
    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.BOOK)
    override val requiresKey: Boolean = false
    override val requiresExternalId: Set<String> = setOf(SubjectExternalIdEntity.PROVIDER_GOOGLE_BOOKS)

    override suspend fun searchCandidates(subject: SubjectEntity, keys: RatingSourceKeys): List<RatingCandidate> {
        val query = listOfNotNull(subject.titleCN, subject.title).firstOrNull()?.trim().orEmpty()
        if (query.isEmpty()) return emptyList()
        val json = RatingHttp.getJson(
            "https://www.googleapis.com/books/v1/volumes?maxResults=8&q=intitle:${ratingUrlEncode(query)}"
        ) ?: return emptyList()
        return json.arr("items").objects().mapNotNull { wrapper ->
            val info = wrapper.obj("volumeInfo") ?: return@mapNotNull null
            val id = wrapper.str("id") ?: return@mapNotNull null
            RatingCandidate(
                provider = SubjectExternalIdEntity.PROVIDER_GOOGLE_BOOKS,
                externalId = id,
                title = info.str("title") ?: id,
                subtitle = listOfNotNull(
                    info.arr("authors")?.strings()?.firstOrNull(),
                    info.str("publishedDate")?.take(4),
                ).joinToString(" · ").takeIf { it.isNotBlank() },
                imageUrl = info.obj("imageLinks")?.str("thumbnail"),
                confidence = 0f,
            )
        }
    }

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val volumeId = externalIds[SubjectExternalIdEntity.PROVIDER_GOOGLE_BOOKS] ?: return null
        val json = RatingHttp.getJson("https://www.googleapis.com/books/v1/volumes/$volumeId") ?: return null
        val info = json.obj("volumeInfo") ?: return null
        val average = info.str("averageRating")?.toFloatOrNull() ?: return null
        if (average <= 0f) return null
        return ExternalRating(
            sourceId = id,
            label = label,
            score = ExternalRating.toTenPoint(average, 5f),
            nativeScore = average,
            scoreMax = 5f,
            voteCount = info.int("ratingsCount"),
            sourceUrl = info.str("infoLink"),
        )
    }
}

/** Open Library 评分（无 key，覆盖以英文书为主，评分数常缺）。 */
class OpenLibraryRatingSource : RatingSource {

    override val id: String = ExternalRating.SOURCE_OPEN_LIBRARY
    override val label: String = "Open Library"
    override val subjectTypes: Set<SubjectType> = setOf(SubjectType.BOOK)
    override val requiresKey: Boolean = false
    override val requiresExternalId: Set<String> = setOf(SubjectExternalIdEntity.PROVIDER_OPEN_LIBRARY)

    override suspend fun searchCandidates(subject: SubjectEntity, keys: RatingSourceKeys): List<RatingCandidate> {
        val query = listOfNotNull(subject.titleCN, subject.title).firstOrNull()?.trim().orEmpty()
        if (query.isEmpty()) return emptyList()
        val json = RatingHttp.getJson(
            "https://openlibrary.org/search.json?limit=8" +
                "&fields=key,title,author_name,first_publish_year,cover_i,ratings_average,ratings_count" +
                "&q=${ratingUrlEncode(query)}"
        ) ?: return emptyList()
        return json.arr("docs").objects().mapNotNull { item ->
            val key = item.str("key") ?: return@mapNotNull null
            RatingCandidate(
                provider = SubjectExternalIdEntity.PROVIDER_OPEN_LIBRARY,
                externalId = key,
                title = item.str("title") ?: key,
                subtitle = item.int("first_publish_year")?.toString(),
                imageUrl = item.int("cover_i")?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
                confidence = 0f,
            )
        }
    }

    override suspend fun fetch(
        subject: SubjectEntity,
        externalIds: Map<String, String>,
        keys: RatingSourceKeys,
    ): ExternalRating? {
        val key = externalIds[SubjectExternalIdEntity.PROVIDER_OPEN_LIBRARY] ?: return null
        val workKey = if (key.startsWith("/")) key else "/$key"
        val json = RatingHttp.getJson("https://openlibrary.org$workKey/ratings.json") ?: return null
        val summary = json.obj("summary") ?: return null
        val average = summary.str("average")?.toFloatOrNull() ?: return null
        if (average <= 0f) return null
        return ExternalRating(
            sourceId = id,
            label = label,
            score = ExternalRating.toTenPoint(average, 5f),
            nativeScore = average,
            scoreMax = 5f,
            voteCount = summary.int("count"),
            sourceUrl = "https://openlibrary.org$workKey",
        )
    }
}
