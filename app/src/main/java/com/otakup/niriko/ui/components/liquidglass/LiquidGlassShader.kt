package com.otakup.niriko.ui.components.liquidglass

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.unit.IntSize
import com.otakup.niriko.R

/**
 * 进程级高斯模糊缓存（参考 AndroidLiquidGlassView 的 cachedBlurEffect + 时间窗策略）。
 *
 * createBlurEffect 是昂贵的 GPU 资源分配;同一像素半径的模糊在滚动/重绘时反复创建
 * 会有明显 GC 压力。缓存按 blurRadiusPx(整数)键存,超过 [BLUR_CACHE_MAX_ENTRIES] 或
 * 时间窗内未被使用则淘汰,防无限膨胀。
 */
private data class BlurCacheEntry(
    val effect: android.graphics.RenderEffect,
    val lastUsed: Long,
)

/** 进程级模糊缓存的单例（顶层 object，方法同步）。 */
private object BlurEffectCache {
    private val map = HashMap<Int, BlurCacheEntry>()
    private fun now() = android.os.SystemClock.elapsedRealtime()

    fun getOrCreate(radiusPx: Int): android.graphics.RenderEffect {
        val key = radiusPx.coerceAtLeast(1)
        val time = now()
        synchronized(this) {
            val hit = map[key]
            if (hit != null && time - hit.lastUsed < BLUR_CACHE_TTL_MS) {
                map[key] = hit.copy(lastUsed = time)
                return hit.effect
            }
            // 淘汰最久未用（超过时间窗）或超上限清空
            val stale = map.filter { (_, entry) -> time - entry.lastUsed > BLUR_CACHE_TTL_MS }
            if (stale.isNotEmpty()) {
                map.keys.removeAll(stale.keys.toSet())
            } else if (map.size >= BLUR_CACHE_MAX_ENTRIES) {
                map.clear()
            }
            val effect = android.graphics.RenderEffect.createBlurEffect(
                radiusPx.toFloat(), radiusPx.toFloat(), Shader.TileMode.CLAMP,
            )
            map[key] = BlurCacheEntry(effect, time)
            return effect
        }
    }
}

/** 模糊缓存:同半径超过该时长未用则视为过期(参考库 120ms)。 */
private const val BLUR_CACHE_TTL_MS = 120L

/** 模糊缓存最大条目数(不同半径数量有限,几十种就够;超限清空)。 */
private const val BLUR_CACHE_MAX_ENTRIES = 32

/**
 * AGSL 液态玻璃着色器管线(移植自 AndroidLiquidGlassView / QmDeve, MIT)。
 *
 * 与参考库完全一致的渲染链:
 *   层内内容(content) → 高斯模糊(inner) → 圆角折射 + 7 色彩带色散(outer)
 *
 * 着色器源在 app/src/main/res/raw/liquid_glass_effect.agsl,参数与上游同名
 * (size / offset / cornerRadii / refractionHeight / refractionAmount /
 *  depthEffect / chromaticAberration / contrast / whitePoint /
 *  chromaMultiplier / tintColor / tintAlpha)。
 *
 * 版本策略:
 *   API 33+  —— RuntimeShader 完整折射 + 色散(本文件);
 *   API 31-32 —— 仅高斯模糊(透出玻璃感,调用方降级);
 *   API 26-30 —— tint 玻璃(调用方降级)。
 */

/** 加载 AGS 着色器源码并编译为 RuntimeShader(API 33+)。 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun loadLiquidGlassShader(context: Context): RuntimeShader {
    val source = context.resources
        .openRawResource(R.raw.liquid_glass_effect)
        .bufferedReader()
        .use { it.readText() }
    return RuntimeShader(source)
}

/**
 * 尝试加载 AGSL 着色器;失败时记录原因并返回 null(调用方降级模糊/tint)。
 * 便于真机排查:logcat 过滤 LiquidGlass 可见失败原因。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun tryLoadLiquidGlassShader(context: Context): RuntimeShader? = try {
    loadLiquidGlassShader(context).also {
        Log.i(TAG, "AGSL shader 加载成功 (${it.javaClass.simpleName})")
    }
} catch (t: Throwable) {
    Log.w(TAG, "AGSL shader 加载失败,降级为模糊/tint", t)
    null
}

private const val TAG = "LiquidGlass"

/**
 * 构建"高斯模糊 + AGSL 折射/色散"合成效果。
 * @param sizePx 层尺寸(px),驱动 shader 的 size uniform。
 * @param tint  玻璃色调(0..1 分量),null 表示不着色(tintAlpha = 0)。
 * @return 可赋给 graphicsLayer.renderEffect 的 Compose 效果;尺寸未知时返回 null。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun buildLiquidGlassRenderEffect(
    shader: RuntimeShader,
    sizePx: IntSize,
    cornerRadiusPx: Float,
    refractionHeightPx: Float,
    refractionOffsetPx: Float,
    depthEffect: Float,
    dispersion: Float,
    blurRadiusPx: Float,
    contrast: Float,
    whitePoint: Float,
    chromaMultiplier: Float,
    tint: androidx.compose.ui.graphics.Color?,
): ComposeRenderEffect? {
    if (sizePx.width <= 0 || sizePx.height <= 0) return null

    shader.setFloatUniform("size", floatArrayOf(sizePx.width.toFloat(), sizePx.height.toFloat()))
    shader.setFloatUniform("offset", floatArrayOf(0f, 0f))
    shader.setFloatUniform(
        "cornerRadii",
        floatArrayOf(cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx),
    )
    shader.setFloatUniform("refractionHeight", refractionHeightPx)
    shader.setFloatUniform("refractionAmount", refractionOffsetPx)
    shader.setFloatUniform("depthEffect", depthEffect)
    shader.setFloatUniform("chromaticAberration", dispersion)
    shader.setFloatUniform("contrast", contrast)
    shader.setFloatUniform("whitePoint", whitePoint)
    shader.setFloatUniform("chromaMultiplier", chromaMultiplier)
    shader.setFloatUniform(
        "tintColor",
        floatArrayOf(tint?.red ?: 0f, tint?.green ?: 0f, tint?.blue ?: 0f),
    )
    shader.setFloatUniform("tintAlpha", tint?.alpha ?: 0f)

    val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val finalEffect: RenderEffect = if (blurRadiusPx > 0.01f) {
        // 链顺序:outer(shader) inner(blur) → 内容先磨砂,再折射/色散(与上游一致)
        // blur 子 effect 复用进程级缓存(参考库 cachedBlurEffect,避免滚动时反复分配 GPU 资源)
        val blurEffect = BlurEffectCache.getOrCreate(blurRadiusPx.toInt())
        RenderEffect.createChainEffect(shaderEffect, blurEffect)
    } else {
        shaderEffect
    }
    return finalEffect.asComposeRenderEffect()
}

/**
 * API 31-32 降级:仅高斯模糊的磨砂效果(无 AGSL 折射)。
 * API < 31 或尺寸未知返回 null(调用方保持 tint 玻璃)。
 */
fun buildFrostedRenderEffect(
    sizePx: IntSize,
    blurRadiusPx: Float,
): ComposeRenderEffect? {
    if (sizePx.width <= 0 || sizePx.height <= 0) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    if (blurRadiusPx <= 0.01f) return null
    // 复用进程级模糊缓存(与 AGSL 路径一致,避免滚动/重绘反复分配 GPU 资源)
    return BlurEffectCache.getOrCreate(blurRadiusPx.toInt()).asComposeRenderEffect()
}

/**
 * 液态玻璃透镜 AGSL（圆角折射 + 色散）。与 SukiSU-Ultra lens（Kyant0/AndroidLiquidGlass, Apache 2.0）
 * 的 ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER 同源：
 *   - 声明 uniform shader content（miuix 将下方图层绑定为 content）
 *   - 圆角 SDF 内做球面折射采样（refractionAmount 负值 = 凹透镜收缩）
 *   - 7 色彩带沿折射方向分离（chromaticAberration 控制色散强度）
 * uniform 由 drawBackdrop effects block 设置（size/offset/cornerRadii/refractionHeight/
 * refractionAmount/depthEffect/chromaticAberration）。
 */
const val LIQUID_LENS_SHADER = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""