package com.otakup.niriko.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.otakup.niriko.NirikoApplication
import kotlinx.coroutines.flow.first

/**
 * 开机恢复放送提醒（阶段 J）。
 *
 * 系统重启后 WorkManager 的任务不会自动保留，这里在收到 BOOT_COMPLETED 时
 * 若「放送提醒」开关已开启则重新注册周期 Worker。任何错误静默（补充功能）。
 */
class AiringReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? NirikoApplication ?: return

        val pendingResult = goAsync()
        Thread {
            try {
                val enabled = runCatching {
                    kotlinx.coroutines.runBlocking {
                        app.settingsDataStore.settings.first().airingReminderEnabled
                    }
                }.getOrDefault(false)
                if (enabled) {
                    AiringReminderScheduler.schedule(app)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
