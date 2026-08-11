package com.otakup.niriko.ui.components.liquidglass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.Log
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程级"模糊封面 Bitmap"缓存（横向小卡共享毛玻璃背景用）。
 *
 * 用户方案：横向人物小卡不要每个都独立跑一次背景模糊（多张独立 backdropBlur = GPU
 * 重复采样，功耗暴涨）。改为**全程只模糊一次**：详情页顶层把封面加载并模糊成一张
 * Bitmap 缓存，所有小卡 drawImage 时按自身位置偏移裁剪这张图 + 主题 tint + 高光边框，
 * 零额外模糊开销。
 *
 * 模糊实现：
 * - API 31+：RenderEffect.createBlurEffect + RenderNode 离屏渲染（官方推荐）；
 * - API 26-30：退化为"缩小-放大"近似模糊（scale 0.2 → 双线性放大，视觉足够）。
 */
private object BlurredCoverCache {
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private const val MAX_ENTRIES = 8

    /** 取缓存；未命中返回 null。 */
    fun get(url: String): Bitmap? = cache[url]

    /** 放入缓存（超限清空最旧，防膨胀）。 */
    fun put(url: String, bitmap: Bitmap) {
        if (cache.size >= MAX_ENTRIES) cache.clear()
        cache[url] = bitmap
    }
}

/** 目标模糊位图尺寸（宽，px）。小图即可——模糊后细节无关紧要，省内存。 */
private const val BLURRED_TARGET_WIDTH = 480

/**
 * 加载封面并模糊为 Bitmap（进程级缓存，同 URL 只模糊一次）。
 * @return 模糊位图；加载/模糊失败返回 null（调用方降级为静态玻璃）。
 */
suspend fun loadBlurredCover(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    BlurredCoverCache.get(url)?.let { return it }

    return withContext(Dispatchers.IO) {
        runCatching {
            val req = ImageRequest.Builder(context)
                .data(url)
                .size(BLURRED_TARGET_WIDTH)
                .allowHardware(false) // 软件模糊需要软位图
                .build()
            val result = Coil.imageLoader(context).execute(req)
            val source = (result as? SuccessResult)?.drawable
                ?: return@runCatching null
            val src = (source as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: return@runCatching null

            val blurred = blurBitmap(src)
            BlurredCoverCache.put(url, blurred)
            blurred
        }.getOrNull()
    }
}

/** 跨版本 Bitmap 高斯模糊。 */
private fun blurBitmap(src: Bitmap): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blurRenderEffect(src)
    } else {
        blurScaleDown(src)
    }
}

/** API 31+：RenderEffect + RenderNode 离屏模糊。 */
@android.annotation.TargetApi(Build.VERSION_CODES.S)
private fun blurRenderEffect(src: Bitmap): Bitmap {
    val radius = 22f
    val node = RenderNode("blurCover").apply {
        setPosition(0, 0, src.width, src.height)
    }
    val canvas = node.beginRecording()
    canvas.drawBitmap(src, 0f, 0f, null)
    node.endRecording()
    node.setRenderEffect(
        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
    )
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val outCanvas = Canvas(out)
    outCanvas.drawColor(Color.TRANSPARENT)
    outCanvas.drawRenderNode(node)
    node.discardDisplayList()
    return out
}

/** API 26-30：缩小放大近似模糊（省内存且视觉接近磨砂）。 */
private fun blurScaleDown(src: Bitmap): Bitmap {
    val scale = 0.2f
    val smallW = (src.width * scale).toInt().coerceAtLeast(1)
    val smallH = (src.height * scale).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
    val out = Bitmap.createScaledBitmap(small, src.width, src.height, true)
    if (small != out) small.recycle()
    return out
}

private const val TAG = "BlurredCover"
