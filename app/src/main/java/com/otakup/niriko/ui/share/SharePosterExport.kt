package com.otakup.niriko.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批次 C · 8.5：分区海报的一键导出（预加载封面 → 分页渲染 → ACTION_SEND_MULTIPLE）。
 *
 * 失败静默：分享是锦上添花，一张封面挂了不能让库页崩掉，也不能弹错误打断用户。
 */
object SharePosterExport {

    /** 最多 4 页（48 部）；再多用户也不会在分享面板里翻。 */
    private const val MAX_COVERS = 48

    suspend fun shareFolderPoster(
        context: Context,
        folderName: String,
        coverUrls: List<String>,
        totalCount: Int,
    ): Boolean = runCatching {
        val covers = loadCovers(context, coverUrls.take(MAX_COVERS))
        val pages = ShareCardVariants.partitionPages(
            folderName = folderName,
            covers = covers,
            averageScore = null,
            yearRange = null,
        )
        if (pages.isEmpty()) return@runCatching false
        val host = ShareBitmapHost()
        val bitmaps = ShareCardVariants.renderPartitionPages(context, host, pages)
        val stamp = System.currentTimeMillis()
        val uris = bitmaps.mapIndexed { index, bitmap ->
            ShareFlow.saveAndGetUri(context, bitmap, "folder_poster_" + stamp + "_" + index + ".png")
        }
        val text = folderName + " · " + totalCount + " 部 · 来自 Niriko"
        context.startActivity(
            ShareFlow.buildChooser(ShareFlow.buildShareIntentMultiple(uris, text), "分享分区海报"),
        )
        true
    }.getOrDefault(false)

    /** 日历分享卡：渲染一张 1080×1400 的图并交给系统 Sharesheet；失败静默。 */
    suspend fun shareCalendar(context: Context, data: CalendarShareCardData): Boolean = runCatching {
        // 封面必须先预加载：卡面渲染是同步的（同分区海报的做法），不能等 Coil。
        val urls = data.days.mapNotNull { it.coverUrl }.distinct()
        val bitmaps = loadCovers(context, urls).mapIndexed { index, bitmap -> urls[index] to bitmap }.toMap()
        val withCovers = data.copy(
            days = data.days.map { day -> day.copy(cover = day.coverUrl?.let { bitmaps[it] }) },
        )
        val host = ShareBitmapHost()
        val bitmap = ShareCardVariants.renderCalendar(context, host, withCovers)
        val uri = ShareFlow.saveAndGetUri(context, bitmap, "calendar_" + System.currentTimeMillis() + ".png")
        context.startActivity(
            ShareFlow.buildChooser(
                ShareFlow.buildShareIntent(uri, data.monthLabel + " · 来自 Niriko"),
                "分享追番日历",
            ),
        )
        true
    }.getOrDefault(false)

    private suspend fun loadCovers(context: Context, urls: List<String>): List<Bitmap?> = withContext(Dispatchers.IO) {
        urls.map { url ->
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(300)
                    .allowHardware(false)
                    .build()
                (Coil.imageLoader(context).execute(request).drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }
    }
}
