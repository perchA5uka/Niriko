package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import android.os.Build
import android.graphics.RuntimeShader
import android.graphics.RenderEffect
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.roundToInt
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.util.CrtPolicy
import kotlin.random.Random

/** 噪点数量（有限噪点：§11.2 明确要求「有限」，不是满屏雪花）。 */
private const val CRT_NOISE_POINTS = 900

/** 噪点刷新间隔：约 12fps 的颗粒感，**不跟随刷新率**（每帧随机会刺眼且费）。 */
private const val CRT_NOISE_REFRESH_MS = 80L

/**
 * CRT 老电视模式（F08，计划 §11.2）。
 *
 * ## 触发面（判据全在 [CrtPolicy]，纯函数、有单测）
 *
 * 只有**动画**、**明确年份 < 2000**、**用户自己开启**、**没开减少动态效果**四条同时成立才播；
 * 年份未知一律不触发（§16）。默认关闭。
 *
 * API 33+ uses a cached AGSL content filter for barrel distortion and RGB dispersion.
 * Shader compilation and effect creation are never performed on every scroll frame.
 * Older devices or shader failures retain the lightweight Canvas fallback.
 *

 * ## 收尾与取消
 *
 * 总时长约 [CrtPolicy.DURATION_MS] 毫秒、最后 [CrtPolicy.FADE_OUT_MS] 毫秒整体淡出，
 * 到点后 overlay 不再参与组合、页面恢复静态（不是常驻滤镜）。离开页面（返回/旋转/进程回收）
 * 连同协程一起取消 —— 「返回取消」是结构性的，不需要额外代码。
 *
 * overlay 画在内容**之上**（CRT 是罩在屏幕前的效果），但**不挂任何 pointerInput**：
 * 不消费手势，点击与滚动照常穿透。
 */
@Composable
fun CrtOverlay(
    airDate: String?,
    type: SubjectType?,
    userEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val shouldPlay = remember(airDate, type, userEnabled, reduceMotion) {
        CrtPolicy.shouldPlay(
            type = type,
            airDate = airDate,
            userEnabled = userEnabled,
            reduceMotion = reduceMotion,
        )
    }
    var playing by remember(shouldPlay) { mutableStateOf(shouldPlay) }
    val progress = remember { Animatable(0f) }
    var noiseSeed by remember { mutableIntStateOf(0) }

    LaunchedEffect(shouldPlay) {
        if (!shouldPlay) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(CrtPolicy.DURATION_MS, easing = LinearEasing))
        // 到点即收：overlay 之后不再参与组合
        playing = false
    }
    if (playing) {
        // 噪点按固定间隔刷新（不是每帧）：位置随机、数量固定
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(CRT_NOISE_REFRESH_MS)
                noiseSeed++
            }
        }
    }

    val elapsed = (progress.value * CrtPolicy.DURATION_MS).toLong()
    val envelope = if (playing) CrtPolicy.envelope(elapsed) else 0f
    val phase = CrtPolicy.scanlinePhase(elapsed)
    val noise = remember(noiseSeed) {
        val rnd = Random(noiseSeed)
        List(CRT_NOISE_POINTS) { Offset(rnd.nextFloat(), rnd.nextFloat()) }
    }
    val noiseColor = MaterialTheme.colorScheme.surface

    val distortion = remember(shouldPlay) {
        if (shouldPlay && Build.VERSION.SDK_INT >= 33) runCatching { CrtDistortionCache() }.getOrNull()
        else null
    }
    Box(modifier) {
        Box(
            modifier = Modifier.graphicsLayer {
                if (Build.VERSION.SDK_INT >= 33 && distortion != null && playing && envelope > 0f) {
                    renderEffect = distortion.effect(size.width, size.height, envelope)
                } else {
                    renderEffect = null
                }
            },
        ) {
            content()
        }
        if (!playing || envelope <= 0f) return@Box
        Box(
            modifier = Modifier
                .matchParentSize()
                // 无 pointerInput：不消费手势，点击/滚动穿透到底下内容
                .drawBehind {
                    val lineStep = 3.dp.toPx().coerceAtLeast(2f)
                    val lineWidth = 1.5.dp.toPx()
                    val lineColor = Color.Black.copy(alpha = CrtPolicy.SCANLINE_ALPHA * envelope)
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = lineWidth,
                        )
                        y += lineStep
                    }
                    // Shader mode samples RGB separately; colored edges are fallback only.
                    if (distortion == null || !distortion.isAvailable) {
                    val fringeW = 6.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Red.copy(alpha = CrtPolicy.CHROMA_ALPHA * envelope),
                                Color.Transparent,
                            ),
                        ),
                        size = androidx.compose.ui.geometry.Size(fringeW, size.height),
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Blue.copy(alpha = CrtPolicy.CHROMA_ALPHA * envelope),
                            ),
                        ),
                        topLeft = Offset(size.width - fringeW, 0f),
                        size = androidx.compose.ui.geometry.Size(fringeW, size.height),
                    )
                    }
                    // 暗角
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = kotlin.math.hypot(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = CrtPolicy.VIGNETTE_ALPHA * envelope),
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                    // 逐行扫描的亮带（静态纹理里唯一的动感）
                    val bandY = phase * size.height
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.05f * envelope),
                                Color.Transparent,
                            ),
                            startY = bandY - size.height * 0.08f,
                            endY = bandY + size.height * 0.08f,
                        ),
                        size = size,
                    )
                    // 有限噪点：一次 drawPoints 提交
                    if (noise.isNotEmpty()) {
                        drawPoints(
                            points = noise.map { Offset(it.x * size.width, it.y * size.height) },
                            pointMode = PointMode.Points,
                            color = noiseColor.copy(alpha = CrtPolicy.NOISE_ALPHA * envelope),
                            strokeWidth = 1.6.dp.toPx(),
                        )
                    }
                },
        )
    }
}

/** One shader per overlay; effects are reused between strength/size changes. */
@RequiresApi(33)
private class CrtDistortionCache {
    private val shader = RuntimeShader(CRT_DISTORTION_SHADER)
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null
    private var width = 0f
    private var height = 0f
    private var strengthBucket = -1
    private var failed = false
    val isAvailable: Boolean get() = !failed

    fun effect(w: Float, h: Float, strength: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (failed || w <= 0f || h <= 0f) return null
        val bucket = (strength.coerceIn(0f, 1f) * 60f).roundToInt()
        if (cached == null || width != w || height != h || strengthBucket != bucket) {
            try {
                shader.setFloatUniform("resolution", w, h)
                shader.setFloatUniform("strength", bucket / 60f)
                cached = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                width = w
                height = h
                strengthBucket = bucket
            } catch (_: RuntimeException) {
                failed = true
                cached = null
            }
        }
        return cached
    }
}

private const val CRT_DISTORTION_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float strength;
half4 main(float2 position) {
    float2 p = position / resolution * 2.0 - 1.0;
    float r2 = dot(p, p);
    float2 warped = p * (1.0 + 0.018 * strength * r2);
    float2 uv = (warped + 1.0) * 0.5 * resolution;
    float2 fringe = p * (0.85 * strength * r2);
    float2 lo = float2(0.5);
    float2 hi = resolution - 0.5;
    half4 center = content.eval(clamp(uv, lo, hi));
    half red = content.eval(clamp(uv + fringe, lo, hi)).r;
    half blue = content.eval(clamp(uv - fringe, lo, hi)).b;
    return half4(red, center.g, blue, center.a);
}
"""

