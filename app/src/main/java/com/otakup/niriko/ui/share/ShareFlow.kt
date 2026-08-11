package com.otakup.niriko.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * 分享触发：把渲染好的分享卡 Bitmap 保存到 cacheDir/share/，通过 FileProvider
 * 生成 content:// URI，交给系统 Sharesheet（ACTION_SEND）发送。
 * 纯工具函数，不持有状态。
 */
object ShareFlow {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val DIR = "share"

    /**
     * 保存 Bitmap 为 PNG 并返回 FileProvider URI。
     * @throws Exception 保存失败时抛出，由调用方兜底提示。
     */
    fun saveAndGetUri(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val dir = File(context.cacheDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
    }

    /** 构建 ACTION_SEND 分享 Intent（图片 + 文字）。 */
    fun buildShareIntent(imageUri: Uri, summaryText: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, summaryText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** 带系统选择器的启动 Intent。 */
    fun buildChooser(shareIntent: Intent, chooserTitle: String = "分享到"): Intent =
        Intent.createChooser(shareIntent, chooserTitle)
}
