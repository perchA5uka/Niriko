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
    /**
     * /calendar 的共享缓存（第 5 轮 D26）。
     *
     * 与发现页「当季热门」共用同一个实例：改造前两边各自直连接口，
     * 打开统计页再打开发现页就是两次完全相同的请求。
     * 缓存还负责「接口失败时退回上次结果」，这正是弱网下日历还能显示的原因。
     */
    private val calendarCache: CalendarCache = CalendarCache(),
) {

    /**
     * 加载放送日历数据。
     *
     * @return 按星期分组的放送作品列表，以及可选的错误信息。
     */
    suspend fun fetch(): BroadcastResult {
        return try {
            val snapshot = calendarCache.load { remoteDataSource.getCalendar() }
                ?: return BroadcastResult(emptyMap(), error = "无法加载放送信息")
            val schedule = snapshot.entries
            val now = LocalDate.now()

            val byDay = mutableMapOf<DayOfWeek, MutableList<AiringSubject>>()

            schedule.forEach { day ->
                day.subjects.forEach { subject ->
                    if (AiringStatus.shouldShowInWeeklyCalendar(subject, now)) {
                        // 与本地已缓存作品合并：列表接口常缺集数/封面/评分，用本地详情补齐。
                        val cached = runCatching { subjectRepository.getById(subject.subjectId) }.getOrNull()
                        val enrichedSubject = cached?.let { c ->
                            subject.copy(
                                totalEpisodes = subject.totalEpisodes ?: c.totalEpisodes,
                                volumes = subject.volumes ?: c.volumes,
                                coverUrl = subject.coverUrl ?: c.coverUrl,
                                ratingScore = subject.ratingScore ?: c.ratingScore,
                                ratingTotal = subject.ratingTotal ?: c.ratingTotal,
                                rank = subject.rank ?: c.rank,
                                platform = subject.platform ?: c.platform,
                            )
                        } ?: subject
                        val parsedAirDate = TimeUtils.parseDate(enrichedSubject.airDate) ?: return@forEach
                        val estimatedEnd = if (enrichedSubject.totalEpisodes != null && enrichedSubject.totalEpisodes > 0) {
                            AiringStatus.getEstimatedEndDate(parsedAirDate, enrichedSubject.totalEpisodes)
                        } else null
                        byDay.getOrPut(day.dayOfWeek) { mutableListOf() }.add(
                            AiringSubject(enrichedSubject, parsedAirDate, estimatedEnd),
                        )
                    }
                }
            }

            // 缓存作品元数据到本地（upsert：允许补齐后的集数覆盖旧列表数据）。
            // 批量一次写入：改造前逐条 upsert 会触发同样多次 Room 失效通知，
            // 而作品库列表同时观察 subjects 表 → 每写一条就全量重算一次。
            val allSubjects = byDay.values.flatten().map { it.subject }
            subjectRepository.upsertAll(allSubjects)

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
