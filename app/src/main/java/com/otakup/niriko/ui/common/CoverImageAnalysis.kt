package com.otakup.niriko.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Software analysis source shared by brightness and blur; never retain full-resolution hero images.
private val analysisBitmaps = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}
private val analysisLocks = Array(8) { Mutex() }

internal suspend fun cachedCoverAnalysisBitmap(url: String, load: suspend () -> Bitmap?): Bitmap? {
    analysisBitmaps.get(url)?.let { if (!it.isRecycled) return it }
    return analysisLocks[url.hashCode().ushr(1) % analysisLocks.size].withLock {
        analysisBitmaps.get(url)?.takeUnless { it.isRecycled } ?: load()?.also { analysisBitmaps.put(url, it) }
    }
}

suspend fun loadCoverAnalysisBitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
    cachedCoverAnalysisBitmap(url) {
        val result = Coil.imageLoader(context).execute(
            ImageRequest.Builder(context.applicationContext).data(url).size(480).allowHardware(false).build()
        )
        (result.drawable as? BitmapDrawable)?.bitmap
    }
}
