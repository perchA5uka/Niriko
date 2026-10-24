package com.otakup.niriko.data.diagnose

import android.content.Context
import java.io.File

/**
 * 磁盘占用扫描（C-10）：只读目录体积，归到 4 类。
 *
 * 为什么不在 UI 里递归：扫描要容错（权限/损坏文件/并发删除），
 * 放这里可以被单测用临时目录覆盖，也能在协程里 IO 线程跑。
 */
object StorageScan {

    /** 本地保留天数换算用（与快照策略同一个常量语义，但独立定义避免跨包依赖）。 */
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 只扫这些顶层目录，避免把整个私有目录遍历一遍。 */
    private val SCAN_DIRS = listOf("image_cache", "snapshots", "wallpapers", "cache", "logs")

    /** 扫描 app 私有目录，返回各类条目（目录级聚合，不逐个文件进列表）。 */
    fun scan(context: Context): List<StorageItem> {
        val roots = listOfNotNull(context.cacheDir, context.filesDir)
        return roots.flatMap { root ->
            root.listFiles().orEmpty().mapNotNull { child ->
                if (!child.isDirectory) return@mapNotNull null
                val relative = child.name
                val bytes = sizeOf(child)
                if (bytes <= 0) null else StorageItem(bucketOf(relative), relative, bytes)
            }
        }
    }

    /**
     * 清理「30 天未访问的剧照/封面缓存」——**只清图片缓存目录里的旧文件**，
     * 不碰快照、壁纸与备份（这三类是用户数据，误删不可恢复）。
     *
     * @return 清理的文件数
     */
    fun cleanupStaleImages(context: Context, olderThanDays: Int = 30, now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - olderThanDays * DAY_MS
        val dirs = listOfNotNull(context.cacheDir?.resolve("image_cache"), context.filesDir?.resolve("image_cache"))
        var removed = 0
        dirs.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.lastModified() < cutoff }.forEach { f ->
                if (runCatching { f.delete() }.getOrDefault(false)) removed++
            }
        }
        return removed
    }

    private fun sizeOf(file: File): Long =
        if (file.isFile) file.length()
        else file.listFiles().orEmpty().sumOf { sizeOf(it) }

    private fun File.resolve(child: String): File = File(this, child)
}
