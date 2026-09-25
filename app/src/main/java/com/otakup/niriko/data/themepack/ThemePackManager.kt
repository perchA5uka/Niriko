package com.otakup.niriko.data.themepack

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * .nirikotheme 主题包管理器：导入 / 列出 / 删除 / 导出 / 应用载荷。
 *
 * 主题包 = ZIP：theme.json（必需）+ 壁纸文件（可选，图片/视频）+ 每页覆盖（可选）。
 * 安全约束：
 * - 包体 ≤ [MAX_PACK_BYTES]（50MB），解压后总大小同限（防 zip 炸弹）；
 * - entry 路径校验（拒绝 ".." / 绝对路径，防路径穿越）；
 * - theme.json 必须可解析，未知字段忽略。
 */
class ThemePackManager(private val context: Context) {

    companion object {
        const val MAX_PACK_BYTES = 50L * 1024 * 1024
        private const val MANIFEST_NAME = "theme.json"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val themesRoot: File
        get() = File(context.filesDir, "themes")

    /** 导入：校验 + 解压到 filesDir/themes/<id>/。 */
    suspend fun import(uri: Uri): ThemePackImportResult = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import_" + System.currentTimeMillis() + ".zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: return@withContext ThemePackImportResult.Failure("无法读取主题包文件")

            if (tmp.length() > MAX_PACK_BYTES) {
                return@withContext ThemePackImportResult.Failure("主题包超过 50MB 上限")
            }

            val manifest = parseAndValidate(tmp)
                ?: return@withContext ThemePackImportResult.Failure("theme.json 缺失或格式无效")

            val targetDir = File(themesRoot, sanitizeId(manifest.id))
            targetDir.deleteRecursively()
            targetDir.mkdirs()

            var total = 0L
            ZipInputStream(FileInputStream(tmp)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!isSafeEntry(name)) {
                        targetDir.deleteRecursively()
                        return@withContext ThemePackImportResult.Failure("主题包包含非法路径")
                    }
                    total += entry.size
                    if (total > MAX_PACK_BYTES) {
                        targetDir.deleteRecursively()
                        return@withContext ThemePackImportResult.Failure("主题包解压后超过 50MB 上限")
                    }
                    val outFile = File(targetDir, name)
                    outFile.parentFile?.mkdirs()
                    if (!entry.isDirectory) {
                        FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            tmp.delete()

            val meta = metaOf(targetDir)
            ThemePackImportResult.Success(meta)
        } catch (e: Exception) {
            ThemePackImportResult.Failure("导入失败: " + (e.message ?: "未知错误"))
        } finally {
            tmp.delete()
        }
    }

    /** 列出已安装主题包。 */
    fun listInstalled(): List<ThemePackMeta> = runCatching {
        themesRoot.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            runCatching { metaOf(dir) }.getOrNull()
        }?.sortedBy { it.name } ?: emptyList()
    }.getOrDefault(emptyList())

    /** 删除已安装主题包。 */
    fun delete(id: String) {
        runCatching { File(themesRoot, sanitizeId(id)).deleteRecursively() }
    }

    /** 重新打包已安装主题（原样导出，含 theme.json 与全部资源文件）。 */
    fun exportInstalledPack(id: String): File? = runCatching {
        val dir = File(themesRoot, sanitizeId(id))
        if (!dir.isDirectory) return null
        val manifest = metaOf(dir).manifest
        val safeName = sanitizeId(manifest.name.ifBlank { manifest.id }) + ".nirikotheme"
        val out = File(context.cacheDir, "share").apply { mkdirs() }
        val target = File(out, safeName)
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(dir).path.replace('\\', '/')
                zip.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        target
    }.getOrNull()

    /** 应用载荷：由设置层写入（颜色/壁纸/柔化/开屏）。 */
    fun buildApply(meta: ThemePackMeta): ThemePackApply {
        val seed = parseHexColor(meta.manifest.colors.light.primary) ?: 0xFF2E7D32.toInt()
        val packDir = File(themesRoot, sanitizeId(meta.id))
        val wallpaper = meta.manifest.wallpaper
        val wallpaperUri = wallpaper?.file?.takeIf { it.isNotBlank() }?.let { f ->
            val file = File(packDir, f)
            if (file.exists()) file.toURI().toString() else null
        }
        return ThemePackApply(
            seedColorArgb = seed,
            wallpaperUri = wallpaperUri,
            wallpaperBlurDp = wallpaper?.blurRadiusDp ?: 0,
            splashEnabled = meta.manifest.splash.enabled,
        )
    }

    /**
     * 导出当前配置为主题包。
     * @param seedColorArgb 当前主题色种子（-1=默认绿）
     * @param globalWallpaperUri 当前全局壁纸（SAF content:// 或 file:// 或空）
     * @param perPageUris 每页壁纸（key → uri，空串忽略）
     * @param blurDp 柔化半径
     * @param splashEnabled 开屏开关
     * @return 生成的 .nirikotheme 文件（cacheDir/share/）
     */
    suspend fun export(
        seedColorArgb: Int,
        globalWallpaperUri: String,
        perPageUris: Map<String, String>,
        blurDp: Int,
        splashEnabled: Boolean,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val seed = if (seedColorArgb == -1) 0xFF2E7D32.toInt() else seedColorArgb
            val seedHex = "#%06X".format(0xFFFFFF and seed)
            val name = "niriko-theme-" + System.currentTimeMillis().toString().takeLast(6) + ".nirikotheme"
            val out = File(context.cacheDir, "share").apply { mkdirs() }
            val target = File(out, name)

            val manifest = ThemePackManifest(
                id = "local.export." + System.currentTimeMillis(),
                name = "Niriko 自定义主题",
                author = "Niriko 用户",
                version = "1.0.0",
                colors = ThemePackColors(
                    light = ThemePackColorSeed(primary = seedHex),
                    dark = ThemePackColorSeed(primary = seedHex),
                ),
                wallpaper = null,
                pages = ThemePackPages(),
                splash = ThemePackSplash(enabled = splashEnabled),
            )

            ZipOutputStream(FileOutputStream(target)).use { zip ->
                val manifestJson = json.encodeToString(ThemePackManifest.serializer(), manifest)
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 复制壁纸：全局 + 每页覆盖（去重写入）
                val written = mutableSetOf<String>()
                fun writeWallpaper(uriStr: String, entryName: String, type: String) {
                    if (uriStr.isBlank() || !written.add(entryName)) return
                    val bytes = readUriBytes(uriStr) ?: return
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                if (globalWallpaperUri.isNotBlank()) {
                    writeWallpaper(globalWallpaperUri, "wallpaper" + extOf(globalWallpaperUri), "image")
                }
                perPageUris.forEach { (page, uriStr) ->
                    if (uriStr.isNotBlank()) {
                        writeWallpaper(uriStr, "wallpaper_" + page + extOf(uriStr), "image")
                    }
                }
            }
            target
        }.getOrNull()
    }

    private fun readUriBytes(uriStr: String): ByteArray? {
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            "file" -> runCatching { File(uri.path ?: "").readBytes() }.getOrNull()
            else -> null
        }
    }

    private fun extOf(uriStr: String): String {
        val path = runCatching { Uri.parse(uriStr).lastPathSegment }.getOrNull() ?: ""
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in setOf("jpg", "jpeg", "png", "webp", "mp4", "webm", "mkv", "mov", "gif")) ".$ext" else ".bin"
    }

    private fun parseAndValidate(zipFile: File): ThemePackManifest? {
        val jsonBytes = runCatching {
            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                var content: ByteArray? = null
                while (entry != null) {
                    if (entry.name == MANIFEST_NAME) {
                        content = zip.readBytes()
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                content
            }
        }.getOrNull() ?: return null
        val text = String(jsonBytes, Charsets.UTF_8)
        return runCatching {
            val manifest = json.decodeFromString(ThemePackManifest.serializer(), text)
            if (manifest.id.isBlank()) null else manifest
        }.getOrNull()
    }

    private fun metaOf(dir: File): ThemePackMeta {
        val manifest = runCatching {
            json.decodeFromString(
                ThemePackManifest.serializer(),
                File(dir, MANIFEST_NAME).readText(Charsets.UTF_8),
            )
        }.getOrThrow()
        val wallpaperType = when {
            manifest.wallpaper?.type == "video" -> "video"
            manifest.wallpaper != null -> "image"
            else -> "none"
        }
        return ThemePackMeta(
            id = manifest.id,
            name = manifest.name,
            author = manifest.author,
            version = manifest.version,
            wallpaperType = wallpaperType,
            manifest = manifest,
        )
    }

    private fun sanitizeId(id: String): String =
        id.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64)

    private fun isSafeEntry(name: String): Boolean {
        if (name.startsWith("/") || name.contains("\\")) return false
        val normalized = name.replace('\\', '/')
        return normalized.split('/').none { it == "." || it == ".." }
    }

    private fun parseHexColor(hex: String): Int? {
        val clean = hex.removePrefix("#")
        if (!Regex("[0-9a-fA-F]{6}").matches(clean)) return null
        return runCatching { 0xFF000000.toInt() or clean.toInt(16) }.getOrNull()
    }
}
