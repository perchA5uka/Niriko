package com.otakup.niriko.ui.components

import android.app.Application
import android.graphics.Bitmap
import com.otakup.niriko.ui.common.cachedCoverAnalysisBitmap
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CoverAnalysisCacheTest {
    @Test fun sameUrlReusesAlreadyLoadedSoftwareBitmap() = runBlocking {
        val calls = AtomicInteger(0)
        val key = "one-" + System.nanoTime()
        val first = cachedCoverAnalysisBitmap(key) { calls.incrementAndGet(); Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) }
        val second = cachedCoverAnalysisBitmap(key) { calls.incrementAndGet(); Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) }
        assertSame(first, second)
        assertEquals(1, calls.get())
    }
    @Test fun concurrentAnalysisConsumersCoalesceIntoOneDecode() = runBlocking {
        val calls = AtomicInteger(0)
        val key = "concurrent-" + System.nanoTime()
        val results = List(8) { async {
            cachedCoverAnalysisBitmap(key) { calls.incrementAndGet(); delay(20); Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) }
        } }.awaitAll()
        assertEquals(1, calls.get())
        assertTrue(results.all { it === results.first() })
    }
    @Test fun failedAnalysisCanRetryAndCancellationIsNotCached() = runBlocking {
        val key = "retry-" + System.nanoTime()
        assertNull(cachedCoverAnalysisBitmap(key) { null })
        val cancelled = CancellationException("cancelled")
        try { cachedCoverAnalysisBitmap(key) { throw cancelled }; fail("Cancellation swallowed") }
        catch (actual: CancellationException) { assertEquals(cancelled.message, actual.message) }
        assertNotNull(cachedCoverAnalysisBitmap(key) { Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888) })
    }
    @Test fun cachedAnalysisAndBlurHaveBoundedMemoryAndNoWholesaleClear() {
        fun source(path: String) = File("src/main/java/com/otakup/niriko/" + path).readText()
        val analysis = source("ui/common/CoverImageAnalysis.kt")
        assertTrue(analysis.contains("8 * 1024 * 1024"))
        assertTrue(analysis.contains("value.allocationByteCount"))
        assertTrue(analysis.contains("size(480).allowHardware(false)"))
        val blur = source("ui/components/liquidglass/BlurredCover.kt")
        assertTrue(blur.contains("LruCache<String, Bitmap>(8)"))
        assertFalse(blur.contains("cache.clear()"))
        assertTrue(blur.contains("loadCoverAnalysisBitmap(context, url)"))
        assertTrue(source("ui/common/ImageBrightness.kt").contains("withContext(Dispatchers.Default)"))
        assertTrue(source("ui/components/GlassCard.kt").contains("loadCoverAnalysisBitmap(context, url)"))
    }
}
