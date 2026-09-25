package com.otakup.niriko.ui.wallpaper

import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.Coil
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.data.settings.WallpaperAtmosphere
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.otakup.niriko.util.WallpaperPage

/**
 * 全局壁纸层：置于 NavHost 之下（且在同一 layerBackdrop 捕获层内，底栏可折射壁纸）。
 *
 * 规则：
 * - 仅顶层四页（[active]=true）显示壁纸；详情等二级路由显示纯 surface；
 * - 每页可覆盖（[perPageUris]，key 见 [WallpaperPage]），空串继承 [globalUri]；
 * - 图片：Coil 加载 + 可选静态高斯柔化（RenderEffect 一次性创建）；
 * - 视频：Media3 ExoPlayer 循环静音；应用退后台（LifecycleStartEffect）或二级页面
 *   （[active]=false）时暂停解码省电；
 * - 可读性：按「壁纸消化管线」处理——色彩消化（降饱和/压暗）→ 高斯柔化 →
 *   自适应 scrim（亮壁纸加深、暗壁纸减轻）→ 结构化渐变（顶部/底部/暗角）。
 *   强度由 [atmosphere]（浓郁/均衡/素净）与玻璃特效等级共同决定。
 */
@Composable
fun WallpaperHost(
    enabled: Boolean,
    globalUri: String,
    perPageUris: Map<String, String>,
    currentPage: WallpaperPage,
    active: Boolean,
    blurDp: Int,
    atmosphere: WallpaperAtmosphere = WallpaperAtmosphere.BALANCED,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val surface = androidx.compose.material3.MaterialTheme.colorScheme.surface

    val effectiveUri = remember(perPageUris, globalUri, currentPage) {
        (perPageUris[currentPage.key] ?: "").ifBlank { globalUri }.trim()
    }
    val uri = remember(effectiveUri) { if (effectiveUri.isBlank()) null else runCatching { Uri.parse(effectiveUri) }.getOrNull() }
    val isVideo = remember(uri) {
        if (uri == null) false
        else {
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (mime != null) mime.startsWith("video/")
            else uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in
                setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v")
        }
    }
    // 图片壁纸取平均亮度（32px 缩略图、进程级缓存），用于自适应 scrim；视频回退 mid
    val luminance = if (isVideo || uri == null) null else rememberWallpaperLuminance(uri)

    Box(modifier = modifier.fillMaxSize().background(surface)) {
        val show = enabled && active && uri != null
        if (show) {
            val colorFilter = remember(atmosphere, isVideo) {
                if (isVideo) null else wallpaperColorFilter(atmosphere)
            }
            if (isVideo) {
                VideoWallpaper(uri = uri!!, active = active)
            } else {
                ImageWallpaper(uri = uri!!, blurDp = blurDp, colorFilter = colorFilter)
            }
            // ① 自适应 scrim（色彩消化第三步）：亮壁纸加深、暗壁纸减轻；同时受氛围档位影响
            val scrimAlpha = remember(luminance, atmosphere) {
                atmosphereScrimAlpha(atmosphere, luminance)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = scrimAlpha)
                        else Color.White.copy(alpha = scrimAlpha),
                    ),
            )
            // ④ 结构化渐变：顶部状态栏区加深、底部底栏区加深、四角 5% 径向暗角
            StructuredWallpaperOverlay(isDark = isDark, scrimAlpha = scrimAlpha)
        }
    }
}

/** 图片壁纸静态柔化 + 色彩消化（ColorFilter）。 */
@Composable
private fun ImageWallpaper(
    uri: Uri,
    blurDp: Int,
    colorFilter: ColorFilter?,
) {
    val density = LocalDensity.current
    val glassEffect = LocalGlassEffect.current
    val blurPx = if (glassEffect == GlassEffectLevel.OFF) 0f else with(density) { blurDp.dp.toPx() }
    val blurEffect = remember(blurPx) {
        if (blurPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(uri)
            .crossfade(true)
            .build(),
        contentDescription = null,
        colorFilter = colorFilter,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (blurEffect != null) renderEffect = blurEffect
            },
        contentScale = ContentScale.Crop,
    )
}

/** 视频壁纸（Media3 ExoPlayer 循环静音，不可见时暂停）。 */
@Composable
private fun VideoWallpaper(
    uri: Uri,
    active: Boolean,
) {
    val context = LocalContext.current
    // 视频壁纸色彩消化：API 31+ 用 ColorFilter RenderEffect 降饱和 + 压暗（近似图片管线）
    val colorEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cm = android.graphics.ColorMatrix().apply {
                setSaturation(0.78f)
                val scale = android.graphics.ColorMatrix().apply { setScale(0.92f, 0.92f, 0.92f, 1f) }
                postConcat(scale)
            }
            android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(cm),
            )
        } else null
    }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    val latestActive by rememberUpdatedState(active)
    LifecycleStartEffect(player) {
        if (latestActive) player.play()
        onStopOrDispose { player.pause() }
    }
    LaunchedEffect(active) {
        if (active) player.play() else player.pause()
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize().graphicsLayer {
            if (colorEffect != null) renderEffect = colorEffect.asComposeRenderEffect()
        },
    )
}

/** 结构化渐变：顶部状态栏区 24dp 加深、底部底栏区 96dp 加深、四角 5% 径向暗角。 */
@Composable
private fun StructuredWallpaperOverlay(
    isDark: Boolean,
    scrimAlpha: Float,
) {
    val baseAlpha = scrimAlpha.coerceIn(0f, 1f)
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Black.copy(alpha = if (isDark) 0.18f else 0.14f),
                    0.06f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
        ),
    )
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.88f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (isDark) 0.30f else 0.22f),
                ),
            ),
        ),
    )
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (isDark) 0.18f else 0.10f)),
                radius = 2000f,
            ),
        ),
    )
}

// ==================== 壁纸消化管线的辅助实现 ====================

private fun atmosphereScrimAlpha(atmosphere: WallpaperAtmosphere, luma: Float?): Float {
    val (low, high) = when (atmosphere) {
        WallpaperAtmosphere.RICH -> 0.58f to 0.80f
        WallpaperAtmosphere.BALANCED -> 0.50f to 0.74f
        WallpaperAtmosphere.MINIMAL -> 0.38f to 0.60f
    }
    val t = luma?.coerceIn(0f, 1f) ?: ((low + high) / 2f)
    return low + (high - low) * t
}

/** 降饱和（见氛围档位）＋ 压暗（×0.92）的色彩矩阵。 */
private fun wallpaperColorFilter(atmosphere: WallpaperAtmosphere): ColorFilter? {
    val saturation = when (atmosphere) {
        WallpaperAtmosphere.RICH -> 0.66f
        WallpaperAtmosphere.BALANCED -> 0.75f
        WallpaperAtmosphere.MINIMAL -> 0.86f
    }
    val brightness = 0.92f
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat
    val m = floatArrayOf(
        r + saturation, g, b, 0f, 0f,
        r, g + saturation, b, 0f, 0f,
        r, g, b + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    // 亮度衰减
    for (i in 0 until 3) {
        for (j in 0 until 5) {
            m[i * 5 + j] *= brightness
        }
    }
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

/** 进程级壁纸亮度缓存。 */
private val wallpaperLuminanceCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

/**
 * 从壁纸图片取平均亮度（0..1）：32px 缩略图 → 平均 Y。
 * 进程级缓存 + remember(uri)，同 URI 只采样一次；失败/加载中返回 null。
 */
@Composable
private fun rememberWallpaperLuminance(uri: Uri): Float? {
    val context = LocalContext.current
    val url = uri.toString()
    var luma by remember(url) { mutableStateOf(wallpaperLuminanceCache[url]) }
    LaunchedEffect(url) {
        if (luma == null) {
            val extracted = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(32)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                averageLuminance(bitmap)
            }.getOrNull()
            if (extracted != null) wallpaperLuminanceCache[url] = extracted
            luma = extracted
        }
    }
    return luma
}

/** 取亮度 p80 分位：均值会让"深蓝底 + 稀疏白字"被判为亮之外的低值，p80 能抓住跳白字。 */
private fun averageLuminance(bitmap: android.graphics.Bitmap): Float {
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
    val idx = ((values.size - 1) * 0.80f).toInt().coerceIn(0, values.size - 1)
    return values[idx]
}
