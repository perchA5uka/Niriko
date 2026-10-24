package com.otakup.niriko.data.snapshot

import android.content.Context
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 本地快照的落盘（C-3）：app 私有目录 files/snapshots 下的 .json.gz 文件。
 *
 * 刻意不进 Room（批次 C 存储决策：快照是文件，不是表），
 * 也不写外部存储——卸载即清理，符合「隐私数据留在本机」的既有约定。
 */
class SnapshotStore(private val context: Context) {

    fun dir(): File = File(context.filesDir, "snapshots")

    /** 写一个快照（gzip），返回落盘文件；失败返回 null（调用方继续原流程）。 */
    fun write(json: String, tag: String = "manual", now: Long = System.currentTimeMillis()): File? = runCatching {
        val d = dir().also { it.mkdirs() }
        val file = File(d, snapshotFileName(now, tag))
        GZIPOutputStream(file.outputStream().buffered()).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        }
        file
    }.getOrNull()

    /** 列出现有快照（按时间升序）；损坏/读不到的文件跳过。 */
    fun list(): List<SnapshotFile> = runCatching {
        dir().listFiles { f -> f.isFile && f.name.endsWith(".json.gz") }
            ?.mapNotNull { f -> fileTime(f)?.let { t -> SnapshotFile(f.name, t, f.length()) } }
            ?.sortedBy { it.createdAt }
            .orEmpty()
    }.getOrDefault(emptyList())

    fun read(name: String): String? = runCatching {
        val file = File(dir(), name)
        if (!file.isFile) return null
        GZIPInputStream(file.inputStream().buffered()).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    /** 删除策略之外的快照，返回删除个数。 */
    fun prune(): Int = runCatching {
        val files = list()
        val doomed = snapshotsToDelete(files)
        doomed.count { File(dir(), it.name).delete() }
    }.getOrDefault(0)

    fun totalBytes(): Long = list().sumOf { it.bytes }

    private fun fileTime(file: File): Long? = runCatching {
        // 文件名里的时间戳比 mtime 可靠（拷贝/恢复不会改语义时间）
        val stamp = file.name.removePrefix("snapshot_").substringBefore('_').let { _ ->
            file.name.split('_').getOrNull(1)?.plus(file.name.split('_').getOrNull(2).orEmpty())
        }
        if (stamp != null && stamp.length >= 13) {
            val d = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).parse(stamp)
            d?.time
        } else {
            file.lastModified()
        }
    }.getOrNull()
}
