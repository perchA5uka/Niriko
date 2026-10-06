package com.otakup.niriko.data.wallpaper

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 壁纸库的本地文件存储。
 *
 * 用户从系统选择器挑的图会被复制进 `filesDir/wallpapers/`，库里存的是本地 file URI：
 * 一是不再依赖 SAF 的长期读权限（换设备/清权限后仍能显示），二是轮换写在后台也能直接读。
 */
object WallpaperLibraryFiles {

    private const val DIR_NAME = "wallpapers"

    private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

    fun newId(): String = UUID.randomUUID().toString()

    /** 壁纸库目录（不存在则创建）。 */
    fun dir(context: Context): File = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 把 [source] 复制进壁纸库。成功返回入库条目（调用方负责写入设置），失败返回 null。
     * [addedAt] 由调用方给（便于测试传入固定时间）。
     */
    fun import(
        context: Context,
        source: Uri,
        name: String,
        addedAt: Long,
        id: String = newId(),
    ): WallpaperLibraryEntry? {
        val target = File(dir(context), id + "." + extensionOf(context, source))
        return runCatching {
            val input = context.contentResolver.openInputStream(source) ?: return@runCatching null
            input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
            WallpaperLibraryEntry(id = id, uri = Uri.fromFile(target).toString(), name = name, addedAt = addedAt)
        }.getOrNull()
    }

    /** 删除条目对应的本地文件（只删壁纸库目录里的，外部 URI 不动）。 */
    fun delete(context: Context, entry: WallpaperLibraryEntry) {
        runCatching {
            val uri = Uri.parse(entry.uri)
            if (uri.scheme != "file") return@runCatching
            val file = uri.path?.let { File(it) } ?: return@runCatching
            if (file.parentFile?.absolutePath == dir(context).absolutePath) file.delete()
        }
    }

    private fun extensionOf(context: Context, source: Uri): String {
        val fromName = runCatching { source.lastPathSegment?.substringAfterLast('.', "") }.getOrNull().orEmpty()
        val fromMime = runCatching { context.contentResolver.getType(source)?.substringAfter('/', "") }.getOrNull().orEmpty()
        val candidate = fromName.ifBlank { fromMime }.lowercase()
        return if (candidate in ALLOWED_EXTENSIONS) candidate else "img"
    }
}
