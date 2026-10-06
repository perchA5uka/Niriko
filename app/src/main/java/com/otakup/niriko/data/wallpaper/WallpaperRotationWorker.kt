package com.otakup.niriko.data.wallpaper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.otakup.niriko.NirikoApplication
import kotlinx.coroutines.flow.first

/**
 * 每日轮换：从壁纸库挑下一张，写进全局壁纸槽位。
 *
 * 轮换只动全局槽位，不动四个页面的覆盖（可预期，也不会静默清掉用户设置）；
 * 全程 runCatching 静默失败，与 AiringReminderWorker 一致。
 */
class WallpaperRotationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? NirikoApplication ?: return Result.success()
        return runCatching {
            val settings = app.settingsDataStore.settings.first()
            if (!settings.wallpaperRotationEnabled) return@runCatching Result.success()
            val next = WallpaperRotation.next(
                entries = settings.wallpaperLibraryEntries,
                currentUri = settings.wallpaperUri,
                favoritesOnly = settings.wallpaperRotationFavoritesOnly,
            ) ?: return@runCatching Result.success()
            app.settingsDataStore.setWallpaperUri(next.uri)
            app.settingsDataStore.setWallpaperEnabled(true)
            Result.success()
        }.getOrDefault(Result.success())
    }
}
