package com.otakup.niriko.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.liquidglass.LIQUID_LENS_SHADER
import com.otakup.niriko.ui.theme.LocalDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop

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
    content: @Composable () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val density = LocalDensity.current
    // 玻璃 tint 底（@Composable 读取，供 drawBackdrop onDrawSurface 的 DrawScope 使用）
    val glassContainerColor = MaterialTheme.colorScheme.surfaceContainer

    if (backdrop == null) {
        // 降级：静态玻璃（零 blur，无 backdrop 源）
        Box(
            modifier = modifier.appleGlassCard(shape = shape),
        ) {
            Box(modifier = Modifier.padding(16.dp)) { content() }
        }
        return
    }

    // AGSL 液态透镜 shader：组合期编译一次（编译昂贵，勿在绘制期重建）
    val lensShader = remember { android.graphics.RuntimeShader(LIQUID_LENS_SHADER) }
    // 节流状态：记录上次更新 uniform 的时刻
    var lastUniformUpdate by remember { mutableLongStateOf(0L) }

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
                    val refresh = now - lastUniformUpdate >= interval
                    if (refresh) {
                        lastUniformUpdate = now
                    }
                    // 磨砂底 + 折射/色散 + 饱和度增强
                    colorControls(brightness = 0f, contrast = 1f, saturation = 1.2f)
                    val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                        with(density) { 12.dp.toPx() },
                        with(density) { 12.dp.toPx() },
                        android.graphics.Shader.TileMode.CLAMP,
                    ).asComposeRenderEffect()
                    val lensEffect = lensShader.let { shader ->
                        val w = size.width.coerceAtLeast(1f)
                        val h = size.height.coerceAtLeast(1f)
                        // 仅节流窗口内更新 uniform（窗口外保持上次值 = 输出稳定）
                        if (refresh) {
                            shader.setFloatUniform("size", w, h)
                            shader.setFloatUniform("offset", 0f, 0f)
                            shader.setFloatUniform("cornerRadii", w / 2f, w / 2f, w / 2f, w / 2f)
                            shader.setFloatUniform("refractionHeight", with(density) { 12.dp.toPx() })
                            shader.setFloatUniform("refractionAmount", -with(density) { 16.dp.toPx() })
                            shader.setFloatUniform("depthEffect", 0.3f)
                            shader.setFloatUniform("chromaticAberration", 0.25f)
                        }
                        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                    }
                    renderEffect = android.graphics.RenderEffect
                        .createChainEffect(blurEffect.asAndroidRenderEffect(), lensEffect)
                        .asComposeRenderEffect()
                },
                onDrawSurface = {
                    // 半透明 tint 底：保证内容可读性（浅色亮底 / 深色暗底）
                    drawRect(
                        color = glassContainerColor,
                        alpha = if (isDark) 0.55f else 0.65f,
                    )
                },
            ),
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** 毛玻璃 uniform 更新最小间隔（滚动时 20Hz = 50ms）。 */
private const val GLASS_UPDATE_INTERVAL_MS = 50L

/** 静止时 uniform 更新间隔（500ms ≈ 冻结,GPU 几乎不重算折射/色散）。 */
private const val GLASS_IDLE_INTERVAL_MS = 500L
