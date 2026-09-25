package com.otakup.niriko.ui.common

import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * 图片平均亮度（0..1）：32px 缩略图 → 亮度 p80 分位。
 * 进程级缓存 + remember(url)。失败/加载中返回 null。
 *
 * 取 p80（而非均值）是为了不被"深蓝底 + 稀疏白字"骗到——均值低但白字很跳
 * 的壁纸/封面应判为偏亮，从而加深 scrim。
 */
@Composable
fun rememberImageLuminance(url: String?): Float? {
    if (url.isNullOrBlank()) return null
    val context = LocalContext.current
    var luma by remember(url) { mutableStateOf(imageLuminanceCache[url]) }
    LaunchedEffect(url) {
        if (luma == null) {
            val extracted = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(32)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                percentileLuminance(bitmap, 0.80f)
            }.getOrNull()
            if (extracted != null) imageLuminanceCache[url] = extracted
            luma = extracted
        }
    }
    return luma
}

private val imageLuminanceCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

private fun percentileLuminance(bitmap: android.graphics.Bitmap, percentile: Float): Float {
    val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 16)
    val values = ArrayList<Float>()
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = bitmap.getPixel(x, y)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            values.add((0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f)
            x += step
        }
        y += step
    }
    if (values.isEmpty()) return 0.5f
    values.sort()
    val idx = ((values.size - 1) * percentile).toInt().coerceIn(0, values.size - 1)
    return values[idx]
}
