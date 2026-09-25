package com.otakup.niriko.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.ui.components.liquidglass.LIQUID_LENS_SHADER
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop

/** 详情页封面模糊位图共享：所有 GlassSectionCard 在 backdrop 不可用时用它做全幅毛玻璃。 */
val LocalDetailCoverFallback = staticCompositionLocalOf<Bitmap?> { null }

/**
 * 详情页毛玻璃 section 卡片（与悬浮胶囊底栏同款 miuix 液态玻璃）。
 *
 * - [backdrop] 非 null（详情页有封面背景墙）时：drawBackdrop 真折射下方背景墙
 *   （高斯模糊 + AGSL 圆角折射/色散 + 饱和度增强），内容浮在毛玻璃上；
 * - [backdrop] null（无封面 / Loading / Error / API<31）时：降级为静态玻璃
 *   （appleGlassCard 半透明 tint 底），保证可读性且零 blur 开销。
 *
 * 降功耗（用户调研结论：单组件限帧/preferredFrameRate 不可行，软件层跳帧是可行路径）：
 * - **静态冻结**：无滚动（[isScrolling]=false）时 shader uniform 只设一次，
 *   此后每帧复用同一 RenderEffect，GPU 不重复计算折射/色散；
 * - **滚动节流**：滚动时限制 uniform 更新 ≤ [GLASS_UPDATE_INTERVAL_MS]（20Hz），
 *   页面仍 120Hz 绘制，但着色器不逐帧重算，GPU 负载显著下降。
 */
@Composable
fun GlassSectionCard(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: Dp = 16.dp,
    /** 封面模糊位图：backdrop 不可用/长内容时用全幅模糊封面，避免黑底。 */
    fallbackBitmap: Bitmap? = null,
    content: @Composable () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val density = LocalDensity.current
    val glassEffect = LocalGlassEffect.current
    // 玻璃 tint 底（@Composable 读取，供 drawBackdrop onDrawSurface 的 DrawScope 使用）
    val glassContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val effectiveFallback = fallbackBitmap ?: LocalDetailCoverFallback.current

    // backdrop 不可用（无封面背景墙 / 玻璃非 FULL / 未捕获成功）时，一律走全幅模糊封面，
    // 不再有静态纯黑回退；长内容卡片也由 BlurredGlassSurface 的 Crop 绘制保证不断裂。
    val backdropUsable = backdrop != null && glassEffect == GlassEffectLevel.FULL

    if (!backdropUsable) {
        if (effectiveFallback != null) {
            // 全幅模糊封面：任何高度都不会黑/断裂，视觉上仍是毛玻璃。
            BlurredGlassSurface(
                bitmap = effectiveFallback,
                shape = shape,
                modifier = modifier,
            ) {
                Box(modifier = Modifier.padding(contentPadding)) { content() }
            }
        } else {
            // 最终静态玻璃：中性 tint 底，避免纯黑。
            Box(
                modifier = modifier.appleGlassCard(shape = shape),
            ) {
                Box(modifier = Modifier.padding(contentPadding)) { content() }
            }
        }
        return
    }

    // AGSL 液态透镜 shader：组合期编译一次（编译昂贵，勿在绘制期重建）
    val lensShader = remember { android.graphics.RuntimeShader(LIQUID_LENS_SHADER) }
    // 模糊 RenderEffect：GPU 资源分配昂贵（LiquidGlassShader.kt 注释），按半径缓存一次，
    // 链式 effect 仅在节流窗口/尺寸变化时重建（此前每帧重建 blur+chain = 每帧 GPU 分配）。
    val blurRadiusPx = with(density) { 12.dp.toPx() }
    val blurEffect = remember(blurRadiusPx) {
        android.graphics.RenderEffect.createBlurEffect(
            blurRadiusPx, blurRadiusPx, android.graphics.Shader.TileMode.CLAMP,
        )
    }
    // 缓存用普通可变对象（非 Compose state）：miuix effects block 在绘制阶段执行，
    // 写入 Compose state 会触发"绘制期写快照"→ 重组风暴/卡死/闪退。
    class GlassCacheHolder {
        var chain: android.graphics.RenderEffect? = null
        var chainW = 0f
        var chainH = 0f
        var lastUniformUpdate = 0L
    }
    val glassCache = remember { GlassCacheHolder() }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    // 降功耗（用户调研：单组件限帧/preferredFrameRate 不可行，软件层跳帧可行）：
                    //   - 滚动时：uniform 更新节流到 20Hz（GLASS_UPDATE_INTERVAL_MS=50ms）
                    //   - 静止时：间隔放大到 500ms（≈冻结,GPU 几乎不重算折射/色散）
                    // 页面仍 120Hz 绘制,但 shader 输出在节流窗口内恒定,GPU 负载显著下降。
                    val now = android.os.SystemClock.elapsedRealtime()
                    val interval = if (isScrolling) GLASS_UPDATE_INTERVAL_MS else GLASS_IDLE_INTERVAL_MS
                    val w = size.width.coerceAtLeast(1f)
                    val h = size.height.coerceAtLeast(1f)
                    val refresh = now - glassCache.lastUniformUpdate >= interval
                    if (refresh) {
                        glassCache.lastUniformUpdate = now
                    }
                    // 磨砂底 + 折射/色散 + 饱和度增强
                    colorControls(brightness = 0f, contrast = 1f, saturation = 1.45f)
                    // 链重建条件：节流窗口到达 / 尺寸变化 / 首次绘制；其余帧复用缓存链（零分配）
                    if (refresh || glassCache.chain == null || glassCache.chainW != w || glassCache.chainH != h) {
                        lensShader.setFloatUniform("size", w, h)
                        lensShader.setFloatUniform("offset", 0f, 0f)
                        lensShader.setFloatUniform("cornerRadii", w / 2f, w / 2f, w / 2f, w / 2f)
                        lensShader.setFloatUniform("refractionHeight", with(density) { 12.dp.toPx() })
                        lensShader.setFloatUniform("refractionAmount", -with(density) { 16.dp.toPx() })
                        lensShader.setFloatUniform("depthEffect", 0.3f)
                        lensShader.setFloatUniform("chromaticAberration", 0.25f)
                        glassCache.chain = android.graphics.RenderEffect.createChainEffect(
                            blurEffect,
                            android.graphics.RenderEffect.createRuntimeShaderEffect(lensShader, "content"),
                        )
                        glassCache.chainW = w
                        glassCache.chainH = h
                    }
                    renderEffect = glassCache.chain!!.asComposeRenderEffect()
                },
                onDrawSurface = {
                    // 半透明 tint 底：调浅让背后模糊封面透出来（不再纯黑）
                    drawRect(
                        color = glassContainerColor,
                        alpha = if (isDark) 0.38f else 0.50f,
                    )
                },
            ),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/** 毛玻璃 uniform 更新最小间隔（滚动时 20Hz = 50ms）。 */
private const val GLASS_UPDATE_INTERVAL_MS = 50L

/** 静止时 uniform 更新间隔（500ms ≈ 冻结,GPU 几乎不重算折射/色散）。 */
private const val GLASS_IDLE_INTERVAL_MS = 500L
