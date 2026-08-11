package com.otakup.niriko.data.remote

import android.util.Log
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.util.AiringStatus
import com.otakup.niriko.util.TimeUtils
import java.time.DayOfWeek
import java.time.LocalDate

private const val TAG = "BroadcastFetcher"

/**
 * 放送日历加载器。
 *
 * 职责：
 * 1. 调用远程 API 获取本周放送日历
 * 2. 将结果转换为以星期分组的 [AiringSubject] 列表
 * 3. 通过 [SubjectRepository] 缓存返回的作品元数据
 * 4. 无网络时返回空数据（由调用方展示错误提示）
 */
class BroadcastFetcher(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val subjectRepository: SubjectRepository,
) {

    /**
     * 加载放送日历数据。
     *
     * @return 按星期分组的放送作品列表，以及可选的错误信息。
     */
    suspend fun fetch(): BroadcastResult {
        return try {
            val schedule = remoteDataSource.getCalendar()
            val now = LocalDate.now()

            val byDay = mutableMapOf<DayOfWeek, MutableList<AiringSubject>>()

            schedule.forEach { day ->
                day.subjects.forEach { subject ->
                    if (AiringStatus.shouldShowInWeeklyCalendar(subject, now)) {
                        val parsedAirDate = TimeUtils.parseDate(subject.airDate) ?: return@forEach
                        val estimatedEnd = if (subject.totalEpisodes != null && subject.totalEpisodes > 0) {
                            AiringStatus.getEstimatedEndDate(parsedAirDate, subject.totalEpisodes)
                        } else null
                        byDay.getOrPut(day.dayOfWeek) { mutableListOf() }.add(
                            AiringSubject(subject, parsedAirDate, estimatedEnd),
                        )
                    }
                }
            }

            // 缓存作品元数据到本地
            val nowMs = System.currentTimeMillis()
            val allSubjects = byDay.values.flatten().map { it.subject }
            allSubjects.forEach { subjectRepository.insert(it.copy(lastSyncTime = nowMs)) }

            if (byDay.isNotEmpty()) {
                Log.d(TAG, "Broadcast loaded: ${byDay.size} days, ${byDay.values.sumOf { it.size }} airing")
                BroadcastResult(byDay)
            } else {
                BroadcastResult(emptyMap(), error = "暂无连载中的作品")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load broadcast calendar", e)
            BroadcastResult(emptyMap(), error = "无法加载放送信息")
        }
    }
}

/** 放送日历加载结果。 */
data class BroadcastResult(
    val schedule: Map<DayOfWeek, List<AiringSubject>> = emptyMap(),
    val error: String? = null,
)
