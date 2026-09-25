package com.otakup.niriko.ui.components

// Liquid Glass 材质（纯 Compose，自实现）。
//
// 列表卡片（appleGlassCard）：Apple 风格 Material 模拟——材质感来自光线与层次，而非透明度。
//   Card
//   ├── Shadow（柔和弥散）
//   ├── Background glass layer
//   │    ├── translucent tint（半透明深色/冷灰玻璃底）
//   │    ├── gradient highlight（内部渐变反射：顶部亮、底部暗，像控制中心玻璃）
//   │    └── subtle border（边缘反光，对角折射）
//   └── Content layer（Cover/Text 保持锐利）
//
// 浮层（LiquidGlassSurface）：真正的背景模糊（double-draw）——把 backdrop 源画进离屏并施加
//   RenderEffect 高斯模糊，再叠 tint 与锐利内容。仅用于背后有明确内容源的悬浮层
//   （搜索建议浮层等）。Android 12+（API 31+）生效；低版本自动降级为 tint 玻璃。

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.ui.components.liquidglass.LiquidGlassConfig
import com.otakup.niriko.ui.components.liquidglass.buildFrostedRenderEffect
import com.otakup.niriko.ui.components.liquidglass.buildLiquidGlassRenderEffect
import com.otakup.niriko.ui.components.liquidglass.liquidGlassTint
import com.otakup.niriko.ui.components.liquidglass.tryLoadLiquidGlassShader
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Apple 风格磨砂玻璃卡片（列表卡片用）。
 * 绘制顺序（禁止任何 blur 作用于 Content）：
 *   Glass background（磨砂白/亮度层次）
 *   ↓ Ambient Tint Layer（环境色玻璃反射，可选）
 *   ↓ drawBehind highlight（顶部受光 → 中心透明 → 底部沉降）
 *   ↓ border reflection（对角折射，非描边）
 *   ↓ Content（锐利）
 *
 * @param tintColor 作品封面弱主色（Ambient Tint），仅作用于玻璃背景层，绝不上色内容。
 */
@Composable
fun Modifier.appleGlassCard(
    shape: Shape = RoundedCornerShape(16.dp),
    tintColor: Color? = null,
    isCollectionCard: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val isDark = LocalDarkTheme.current
    val cardBackdrop = LocalCardGlassBackdrop.current
    val glassEffect = LocalGlassEffect.current
    val glassLevel = LocalCardGlassLevel.current
    val luma = LocalGlassLuminance.current
    // 参考款：很透的磨砂玻璃 —— 低 alpha 中性 tint（浅白/深黑），让背景透过卡清晰可见。
    // 自适应亮度：亮壁纸 → 稍微加深 tint 保对比；暗壁纸 → 更透。
    val adaptive = (luma - 0.5f) * 0.10f
    val glassBaseAlpha = if (isDark) (0.30f + adaptive).coerceIn(0.22f, 0.42f)
                         else (0.20f + adaptive).coerceIn(0.14f, 0.32f)
    val glassBase = if (isDark) Color.Black.copy(alpha = glassBaseAlpha)
                    else Color.White.copy(alpha = glassBaseAlpha)
    val highlightColors = if (isDark) {
        listOf(
            Color.White.copy(alpha = 0.06f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.10f),
        )
    } else {
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.03f),
        )
    }
    // 封面 Ambient Tint：alpha 0.05，限制在卡片上 1/3 区域（余光，不是染色）
    val tintAlpha = 0.05f
    val tintBrush = remember(tintColor) {
        tintColor?.let { tint ->
            Brush.verticalGradient(
                colors = listOf(
                    tint.copy(alpha = tintAlpha),
                    tint.copy(alpha = tintAlpha * 0.25f),
                ),
            )
        }
    }
    val highlightBrush = remember(this, isDark) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to highlightColors[0],
                0.5f to highlightColors[1],
                1f to highlightColors[2],
            ),
        )
    }
    // 发光边缘：柔和、沿圆角定向衰减，Plus 混合“真发光”。
    // 交互式 Highlight：按压时 alpha 0.5 → 0.85，边缘“被按下更亮”。
    val pressed = interactionSource != null && interactionSource.collectIsPressedAsState().value
    val highlightAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 0.65f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "cardHighlight",
    )
    val highlight = {
        Highlight.Default.copy(
            width = 1.dp,
            blurRadius = 0.5.dp,
            alpha = highlightAlpha,
        )
    }
    // 方向性外投影：接触阴影下沉 + 环境弱化。
    val shadow = {
        Shadow(
            radius = 12.dp,
            offset = DpOffset(0.dp, 4.dp),
            color = Color.Black.copy(alpha = if (isDark) 0.24f else 0.10f),
        )
    }
    // 玻璃内厚度：底部一条下沉暗边。
    val innerShadow = {
        InnerShadow(
            radius = 12.dp,
            offset = DpOffset(0.dp, 2.dp),
            color = Color.Black.copy(alpha = if (isDark) 0.22f else 0.14f),
        )
    }
    val onDrawSurface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
        // 玻璃 tint 底
        drawRect(color = glassBase)
        // Ambient Tint（仅上 1/3）
        tintBrush?.let { brush ->
            drawRect(brush = brush, size = Size(size.width, size.height / 3f))
        }
        // 顶部受光 → 中心透明 → 底部沉降
        drawRect(brush = highlightBrush)
    }
    val realEnabled = cardBackdrop != null && glassEffect != GlassEffectLevel.OFF &&
        (glassLevel == CardGlassLevel.FULL || (glassLevel == CardGlassLevel.COLLECTION_ONLY && isCollectionCard))
    return if (realEnabled) {
        // 真液态玻璃：和 dock 同管线 —— vibrancy → blur → lens（折射背后壁纸），
        // 再叠加发光边缘/外投影/内厚度。这就是“卡片=玻璃”。
        this.drawBackdrop(
            backdrop = cardBackdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(12.dp.toPx())
                lens(
                    // 增大 refractionHeight/refractionAmount → 边缘折射/色散带更宽、更明显。
                    refractionHeight = 20.dp.toPx(),
                    refractionAmount = 40.dp.toPx(),
                    depthEffect = true,
                    // 色散（chromatic aberration）：与 dock 同款 7 色带折射分离。
                    chromaticAberration = true,
                )
            },
            highlight = highlight,
            shadow = shadow,
            innerShadow = innerShadow,
            onDrawSurface = onDrawSurface,
        )
    } else {
        // 静态降级（无壁纸 / 二级页 / 玻璃关闭）：不 blur/折射，仅保留发光/厚度/阴影。
        this.drawBackdrop(
            backdrop = emptyBackdrop(),
            shape = { shape },
            effects = {},
            highlight = highlight,
            shadow = shadow,
            innerShadow = innerShadow,
            onDrawSurface = onDrawSurface,
        )
    }
}

/**
 * 真液态玻璃浮层（分层渲染 double-draw）：
 *   底层：backdrop 源（API 33+ 施加 AGSL 折射/色散链；API 31-32 高斯模糊；26-30 tint）
 *   中层：玻璃 tint 染色（仅无 AGSL 时；shader 路径下 tint 由 AGSL 混合）
 *   顶层：锐利内容
 * 仅适用于背后有明确内容源的悬浮层（搜索建议浮层等）。
 *
 * 性能：RenderEffect 链在 remember 中缓存——仅层尺寸/参数变化时重建，内容重绘
 * （如击键刷新建议列表）不重建渲染链。
 *
 * @param config 液态玻璃渲染参数（默认参考库 demo 参数 LiquidGlassConfig.Default）。
 */
@Composable
fun LiquidGlassSurface(
    backdrop: @Composable (Modifier) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    config: LiquidGlassConfig = LiquidGlassConfig.Default,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val effectiveTint = tint ?: liquidGlassTint()
    val density = LocalDensity.current

    // —— 渲染参数（px）——（cornerRadius 直接用 config，保证 AGSL 折射 SDF 与剪裁圆角一致）
    val cornerRadiusPx = with(density) { config.cornerRadius.toPx() }
    val refractionHeightPx = with(density) { config.refractionHeight.toPx() }
    val refractionOffsetPx = with(density) { config.refractionOffset.toPx() }
    val blurRadiusPx = with(density) { config.blurRadius.toPx() }

    // 层尺寸（px）——记录后驱动 AGSL size uniform
    var layerSizePx by remember { mutableStateOf(IntSize.Zero) }

    // RuntimeShader：组合期加载一次（失败降级模糊/tint，不崩溃）
    val shader = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            tryLoadLiquidGlassShader(context)
        } else null
    }

    // 渲染链缓存：仅尺寸/参数变化时重建（内容重绘不触发）
    val effect = remember(
        layerSizePx, cornerRadiusPx, refractionHeightPx, refractionOffsetPx, blurRadiusPx,
        config.depthEffect, config.dispersion, config.contrast, config.whitePoint,
        config.chromaMultiplier, effectiveTint, shader,
    ) {
        when {
            layerSizePx.width <= 0 || layerSizePx.height <= 0 -> null
            shader != null -> buildLiquidGlassRenderEffect(
                shader = shader,
                sizePx = layerSizePx,
                cornerRadiusPx = cornerRadiusPx,
                refractionHeightPx = refractionHeightPx,
                refractionOffsetPx = refractionOffsetPx,
                depthEffect = config.depthEffect,
                dispersion = config.dispersion,
                blurRadiusPx = blurRadiusPx,
                contrast = config.contrast,
                whitePoint = config.whitePoint,
                chromaMultiplier = config.chromaMultiplier,
                tint = effectiveTint,
            )
            else -> buildFrostedRenderEffect(layerSizePx, blurRadiusPx)
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 12.dp else 16.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = if (isDark) 0.04f else 0.08f),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0f else 0.04f),
            )
            .clip(shape),
    ) {
        // 底层：backdrop 源 + 渲染链（AGSL 折射 / 模糊降级）
        Box(
            modifier = Modifier
                .matchParentSize()
                .onSizeChanged { layerSizePx = it }
                .graphicsLayer {
                    effect?.let { renderEffect = it }
                },
        ) {
            backdrop(Modifier.fillMaxSize())
        }
        // 中层：玻璃染色（shader 路径下 tint 已由 AGSL 混合，无需额外层）
        if (shader == null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(effectiveTint),
            )
        }
        // 顶层：锐利内容
        Box(modifier = Modifier.matchParentSize()) {
            content()
        }
    }
}

/**
 * 弹窗/浮层开启宿主 window 背后模糊（iOS 弹窗效果）。
 * Android 14+（API 34）通过 WindowManager.LayoutParams.blurBehindRadius 启用整窗背后模糊；
 * 低版本静默跳过。在 ModalBottomSheet 等弹窗内容组合时调用，弹窗关闭（组合销毁）自动恢复。
 */
@Composable
fun WindowBlurBehindEffect(enabled: Boolean = true, blurRadius: Int = 48) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    LaunchedEffect(enabled, blurRadius) {
        if (Build.VERSION.SDK_INT >= 34 && enabled) {
            val window = activity?.window ?: return@LaunchedEffect
            window.attributes = window.attributes.apply {
                this.blurBehindRadius = blurRadius
            }
        }
    }
    DisposableEffect(enabled) {
        onDispose {
            if (Build.VERSION.SDK_INT >= 34) {
                val window = activity?.window ?: return@onDispose
                window.attributes = window.attributes.apply {
                    this.blurBehindRadius = 0
                }
            }
        }
    }
}

/** 递归向上查找宿主 Activity。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 进程级取色缓存：同一封面 URL 全局只提取一次（滚动时大量卡片共享）。 */
private val coverTintCache = java.util.concurrent.ConcurrentHashMap<String, Color?>()

/**
 * 从封面 URL 提取弱主色（Ambient Tint 用）。
 * - 进程级缓存 + remember(url)：同 URL 只执行一次 Coil，不随 recomposition/滚动重复
 * - 跳过近黑/近白像素，避免背景色污染
 * - 优先高饱和区域主色（提取环境色，而非平均色）
 * 异步加载（不阻塞 UI）；失败或加载中返回 null（卡片退回无 tint 玻璃）。
 */
@Composable
fun rememberCoverTint(url: String?): Color? {
    val context = LocalContext.current
    // ConcurrentHashMap 禁止 null key：url 为空时直接返回 null（无封面 → 无 tint）
    var tint by remember(url) { mutableStateOf(url?.let { coverTintCache[it] }) }
    LaunchedEffect(url) {
        if (url != null && tint == null) {
            val extracted = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(32)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let(::extractAmbientTint)
            }.getOrNull()
            // ConcurrentHashMap 禁止 null value：取色失败（null）不入缓存，仅 UI 态回退
            if (extracted != null) {
                coverTintCache[url] = extracted
            }
            tint = extracted
        }
    }
    return tint
}

/** 提取环境主色：跳过近黑/近白，优先高饱和区域。 */
private fun extractAmbientTint(bitmap: Bitmap): Color? {
    val w = bitmap.width
    val h = bitmap.height
    val step = maxOf(1, minOf(w, h) / 16)
    var sumR = 0L; var sumG = 0L; var sumB = 0L; var count = 0
    var satR = 0L; var satG = 0L; var satB = 0L; var satCount = 0
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val c = bitmap.getPixel(x, y)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val lum = (max + min) / 2
            // 跳过近黑（<12）与近白（>243），避免封面底色/文字污染
            if (lum in 12..243) {
                val sat = if (max == 0) 0f else (max - min).toFloat() / max
                sumR += r; sumG += g; sumB += b; count++
                // 高饱和像素：优先作为环境色（如 Madoka 的紫/黄）
                if (sat > 0.3f) {
                    satR += r; satG += g; satB += b; satCount++
                }
            }
            x += step
        }
        y += step
    }
    if (count == 0) return null
    // 高饱和像素足够（≥10%）→ 用高饱和区主色；否则回退非黑白平均色
    return if (satCount >= maxOf(3, count / 10)) {
        Color(satR / satCount / 255f, satG / satCount / 255f, satB / satCount / 255f)
    } else {
        Color(sumR / count / 255f, sumG / count / 255f, sumB / count / 255f)
    }
}
