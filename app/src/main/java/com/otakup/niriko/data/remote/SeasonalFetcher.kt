package com.otakup.niriko.data.remote

import android.util.Log
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.util.AiringStatus
import com.otakup.niriko.util.TimeUtils
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
            val nowMs = System.currentTimeMillis()
            subjects.forEach { subjectRepository.insert(it.copy(lastSyncTime = nowMs)) }

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
            val nowMs = System.currentTimeMillis()
            // 集数补齐：周播番且 totalEpisodes 缺失时拉详情补集数（限制数量避免过多请求）
            val subjectsWithEpisodes = subjects.map { subject ->
                if (AiringStatus.isWeeklyAnime(subject) &&
                    (subject.totalEpisodes == null || subject.totalEpisodes <= 0)) {
                    try {
                        val detail = remoteDataSource.getDetail(subject.subjectId)
                        subject.copy(totalEpisodes = detail.totalEpisodes)
                    } catch (_: Exception) { subject }
                } else subject
            }
            subjectsWithEpisodes.forEach { subjectRepository.insert(it.copy(lastSyncTime = nowMs)) }

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
}
