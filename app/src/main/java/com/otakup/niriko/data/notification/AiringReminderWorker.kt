package com.otakup.niriko.data.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.otakup.niriko.MainActivity
import com.otakup.niriko.NirikoApplication
import com.otakup.niriko.R
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.util.AiringStatus
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 放送提醒 Worker（阶段 J）。
 *
 * 每日周期执行：读取「在看」收藏条目，判定今日放送者并发送本地通知。
 * - 开启判定：优先拉取网络放送日历（[NirikoApplication.dataSourceChain]），
 *   无网络（拉取失败）时回退到本地 subject 字段（airWeekday + airDate + AiringStatus）。
 * - 通知文案：标题「《作品名》今日更新」，正文「今日放送，记得观看」；点击跳转详情页。
 * - 未授权通知权限（API 33+ POST_NOTIFICATIONS）时不打扰，由主界面/设置页负责请求。
 * - 任一步失败静默返回 success（补充功能，不影响主流程）。
 */
class AiringReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NirikoApplication ?: return Result.success()
        val enabled = runCatching { app.settingsDataStore.settings.first().airingReminderEnabled }
            .getOrDefault(false)
        if (!enabled) return Result.success()

        // 在看收藏（status = WATCHING）
        val watchingIds = runCatching { app.database.collectionDao().getAll() }
            .getOrDefault(emptyList())
            .filter { it.status == WatchStatus.WATCHING }
            .map { it.subjectId }
            .toSet()
        if (watchingIds.isEmpty()) return Result.success()

        // 今日放送判定（网络优先，离线回退本地字段）
        val today = LocalDate.now()
        val airingToday = resolveAiringToday(app, watchingIds, today)
        if (airingToday.isEmpty()) return Result.success()

        AiringReminderScheduler.ensureChannel(applicationContext)

        // API 33+ 需 POST_NOTIFICATIONS 运行时授权；未授权则跳过，不打扰
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val manager = NotificationManagerCompat.from(applicationContext)
        for (subject in airingToday) {
            val title = "《${subject.title}》今日更新"
            // 阶段 A：有精确放送时刻时在正文带上时间
            val text = subject.airTimeMinutes?.let { "今日 ${formatMinutes(it)} 放送，记得观看" } ?: "今日放送，记得观看"
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(AiringReminderScheduler.EXTRA_SUBJECT_ID, subject.subjectId)
            }
            val pending = PendingIntent.getActivity(
                applicationContext,
                subject.subjectId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(
                applicationContext,
                AiringReminderScheduler.CHANNEL_ID,
            )
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching { manager.notify(subject.subjectId.toInt(), notification) }
        }
        return Result.success()
    }

    /**
     * 解析「今日放送」的【在看】条目。
     * - 网络日历优先：成功（非 null）即以其为权威（今日星期分组内且在看）。
     * - 无网络（getCalendar 抛异常）：回退到本地 subject 字段判断。
     */
    private suspend fun resolveAiringToday(
        app: NirikoApplication,
        watchingIds: Set<Long>,
        today: LocalDate,
    ): List<SubjectEntity> {
        val todayDOW = today.dayOfWeek
        val network = runCatching { app.dataSourceChain.getCalendar() }.getOrNull()
        if (network != null) {
            return network.filter { it.dayOfWeek == todayDOW }
                .flatMap { it.subjects }
                .filter { it.subjectId in watchingIds }
                .distinctBy { it.subjectId }
        }
        // 回退：本地字段
        val all = runCatching { app.database.subjectDao().getAll() }.getOrDefault(emptyList())
        return all.filter { it.subjectId in watchingIds && localAirsToday(it, today, todayDOW) }
    }

    /** 分钟（0-1439）→ "HH:mm"。 */
    private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** 本地字段判定某条目今天是否放送：ANIME/REAL、连载中、airWeekday==今天。 */
    private fun localAirsToday(subject: SubjectEntity, today: LocalDate, todayDOW: DayOfWeek): Boolean {
        if (subject.type != SubjectType.ANIME && subject.type != SubjectType.REAL) return false
        if (AiringStatus.getPhase(subject, today) != AiringStatus.AiringPhase.AIRING) return false
        val dow = when (val wd = subject.airWeekday) {
            null -> return false
            0 -> DayOfWeek.SUNDAY
            in 1..7 -> DayOfWeek.of(wd)
            else -> return false
        }
        return dow == todayDOW
    }
}
