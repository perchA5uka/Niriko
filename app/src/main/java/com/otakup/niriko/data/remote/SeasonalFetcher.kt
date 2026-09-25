package com.otakup.niriko.data.remote

import android.util.Log
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.util.AiringStatus
import com.otakup.niriko.util.TimeUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

private const val TAG = "SeasonalFetcher"

/**
 * 季节性/月度作品数据加载器。
 *
 * 职责：
 * 1. 从远程 API 加载指定月份的作品数据（ANIME + REAL）
 * 2. 转换为 [AiringSubject] 列表
 * 3. 通过 [SubjectRepository] 缓存作品元数据
 * 4. 无网络时返回空列表（由调用方展示离线状态）
 */
class SeasonalFetcher(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val subjectRepository: SubjectRepository,
) {

    /**
     * 加载指定月份的季节性数据。
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @return 该月的放送作品列表（ANIME + REAL 合并去重）
     */
    suspend fun fetchSeasonal(year: Int, month: Int): List<AiringSubject> {
        return try {
            val subjects = fetchSubjects(year, month)
            // 批量一次写入（避免逐条 upsert 造成的失效风暴）
            subjectRepository.upsertAll(subjects)

            val airingList = subjects.mapNotNull { subject ->
                val parsedAirDate = TimeUtils.parseDate(subject.airDate) ?: return@mapNotNull null
                val estimatedEnd = if (subject.totalEpisodes != null && subject.totalEpisodes > 0)
                    AiringStatus.getEstimatedEndDate(parsedAirDate, subject.totalEpisodes) else null
                AiringSubject(subject, parsedAirDate, estimatedEnd)
            }
            Log.d(TAG, "Seasonal data loaded for $year-$month: ${airingList.size} subjects")
            airingList
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load seasonal data for $year-$month", e)
            emptyList()
        }
    }

    private suspend fun fetchSubjects(year: Int, month: Int): List<com.otakup.niriko.data.local.entity.SubjectEntity> {
        val animeList = remoteDataSource.getSubjectsByMonth(type = 2, year = year, month = month)
        val realList = remoteDataSource.getSubjectsByMonth(type = 6, year = year, month = month)
        return (animeList + realList).distinctBy { it.subjectId }
    }

    /**
     * 加载指定日期范围内开播的放送数据（跨月延续用）。
     * 开播日在 [startDate, endDate) 区间内的 ANIME + REAL 作品。
     */
    suspend fun fetchSeasonalInRange(startDate: LocalDate, endDate: LocalDate): List<AiringSubject> {
        return try {
            val start = startDate.toString()
            val end = endDate.toString()
            val animeList = remoteDataSource.getSubjectsInDateRange(type = 2, startDate = start, endDate = end)
            val realList = remoteDataSource.getSubjectsInDateRange(type = 6, startDate = start, endDate = end)
            val subjects = (animeList + realList).distinctBy { it.subjectId }
            // 集数补齐：周播番且 totalEpisodes 缺失时拉详情补集数。
            // 改造前是**串行**逐条 getDetail（一个月几十个周播番 = 几十次串行网络请求，
            // 翻月时统计页会长时间不出内容）。现在限制并发 + 只处理前 [MAX_DETAIL_FILL] 条。
            val subjectsWithEpisodes = fillMissingEpisodeCounts(subjects)
            // 批量一次写入（避免逐条 upsert 造成的失效风暴）
            subjectRepository.upsertAll(subjectsWithEpisodes)

            subjectsWithEpisodes.mapNotNull { subject ->
                val parsedAirDate = TimeUtils.parseDate(subject.airDate) ?: return@mapNotNull null
                val estimatedEnd = if (subject.totalEpisodes != null && subject.totalEpisodes > 0)
                    AiringStatus.getEstimatedEndDate(parsedAirDate, subject.totalEpisodes) else null
                AiringSubject(subject, parsedAirDate, estimatedEnd)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load seasonal range $startDate..$endDate", e)
            emptyList()
        }
    }

    /**
     * 为缺集数的周播番补 [SubjectEntity.totalEpisodes]。
     *
     * 改造前是 `subjects.map { ... remoteDataSource.getDetail(...) }` —— **串行**执行，
     * 一个 6 个月区间的数据里有几十个缺集数的周播番时，统计页翻月要串行等几十次网络往返。
     * 现在：最多并发 [DETAIL_FILL_CONCURRENCY]、单条 [DETAIL_FILL_TIMEOUT_MS] 超时、
     * 且总量封顶 [MAX_DETAIL_FILL] 条（补集数是锦上添花，不能拖垮主流程）。
     */
    private suspend fun fillMissingEpisodeCounts(
        subjects: List<com.otakup.niriko.data.local.entity.SubjectEntity>,
    ): List<com.otakup.niriko.data.local.entity.SubjectEntity> {
        val needing = subjects.filter {
            AiringStatus.isWeeklyAnime(it) && (it.totalEpisodes == null || it.totalEpisodes <= 0)
        }
        if (needing.isEmpty()) return subjects

        val targets = needing.take(MAX_DETAIL_FILL).map { it.subjectId }.toSet()
        val semaphore = Semaphore(DETAIL_FILL_CONCURRENCY)
        val filled = coroutineScope {
            subjects.map { subject ->
                if (subject.subjectId !in targets) {
                    async { subject }
                } else {
                    async {
                        runCatching {
                            semaphore.withPermit {
                                withTimeout(DETAIL_FILL_TIMEOUT_MS) {
                                    remoteDataSource.getDetail(subject.subjectId)
                                }
                            }
                        }.map { subject.copy(totalEpisodes = it.totalEpisodes) }.getOrDefault(subject)
                    }
                }
            }.awaitAll()
        }
        return filled
    }

    private companion object {
        /** 补集数最多处理多少条（超出部分维持原样，不阻塞翻月）。 */
        const val MAX_DETAIL_FILL = 24

        /** 补集数并发上限（对 Bangumi 友好，避免瞬间几十个请求）。 */
        const val DETAIL_FILL_CONCURRENCY = 4

        /** 单条补集数超时。 */
        const val DETAIL_FILL_TIMEOUT_MS = 3_000L
    }
}
