package com.otakup.niriko.data.wallpaper

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 壁纸每日轮换的周期任务（与 AiringReminderScheduler 同一套用法）。
 *
 * 调度由 NirikoApplication 跟随设置开关响应式触发，UI 层不需要 Context。
 */
object WallpaperRotationScheduler {

    const val WORK_NAME = "wallpaper_rotation_periodic"

    /** 1 天一轮；已排队的任务用 KEEP 保活，不重复入队。 */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
