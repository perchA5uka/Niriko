package com.otakup.niriko.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 放送提醒调度器（阶段 J）。
 *
 * 职责：
 * - 创建通知渠道（[ensureChannel]）
 * - 注册/取消每日周期的 [AiringReminderWorker]（幂等：唯一 work 名，[ExistingPeriodicWorkPolicy.KEEP] 不重置计时）
 * - 定义通知点击携带的 subjectId（EXTRA_SUBJECT_ID），由 MainActivity 处理并导航到详情页
 */
object AiringReminderScheduler {

    /** 唯一周期 work 名（注册/取消共用）。 */
    const val WORK_NAME = "airing_reminder_periodic"

    /** 放送提醒通知渠道 id。 */
    const val CHANNEL_ID = "airing_reminder"

    /** Intent extra：通知点击携带的 subjectId。 */
    const val EXTRA_SUBJECT_ID = "niriko_airing_subject_id"

    /** 建立（或复用）放送提醒通知渠道。API 26+ 才需要。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "放送提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "在看作品新一集放送当天提醒"
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** 注册每日周期的放送提醒 Worker。幂等：同 key 已存在则不覆盖（使用 KEEP 保留原计时）。 */
    fun schedule(context: Context) {
        ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<AiringReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 取消周期放送提醒 Worker（开关关闭时调用）。 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

