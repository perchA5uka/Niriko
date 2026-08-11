package com.otakup.niriko.ui.components.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.LocalDarkTheme

/**
 * 液态玻璃主题令牌。
 *
 * 来源:项目既有液态玻璃材质(GlassCard.kt)的视觉语言 + 参考库
 * AndroidLiquidGlassView(QmDeve, MIT)的默认参数(refractionHeight 20dp /
 * refractionOffset -70dp / dispersion 0.5 / depthEffect 0.3)。
 *
 * 设计原则:一切视觉参数收敛为 token,禁止在组件里散落魔数:
 * - 卡片玻璃(自含材质):浅色磨砂底 + 顶部受光 + 底部沉降 + 对角边缘反射。
 * - 浮层玻璃(真折射):背后有内容源时,API 33+ 用 AGSL 折射 / 色散,
 *   API 31-32 高斯模糊,低版本降级为 tint 玻璃。
 * - 深色模式:卡片回退 M3 surfaceContainer(保证可读性,取消玻璃层次)。
 */

/** 液态玻璃渲染参数(像素级计算由使用方按 density 转换)。 */
data class LiquidGlassConfig(
    /** 玻璃圆角(同时也是 AGSL 折射 SDF 的 cornerRadii)。 */
    val cornerRadius: Dp = 40.dp,
    /** 边缘折射高度:圆角向内多少距离内发生折射。 */
    val refractionHeight: Dp = 20.dp,
    /** 折射偏移:负值 = 向内(凹透镜)收缩采样,与参考库默认一致。 */
    val refractionOffset: Dp = (-70).dp,
    /** 深度效果:形状法线与中心方向混合比例(0..1)。 */
    val depthEffect: Float = 0.3f,
    /** 色散强度:7 色彩带采样的分离程度。 */
    val dispersion: Float = 0.5f,
    /** 内容高斯模糊半径(折射前的磨砂底)。 */
    val blurRadius: Dp = 24.dp,
    /** 内容对比度(-1..1)。 */
    val contrast: Float = 0f,
    /** 内容白点:正值拉白、负值拉黑(0 = 不调整)。 */
    val whitePoint: Float = 0f,
    /** 内容饱和度倍率。 */
    val chromaMultiplier: Float = 1f,
) {
    companion object {
        /** 与参考库 demo 一致的默认值（浮层）。 */
        val Default = LiquidGlassConfig()

        /** 卡片推荐参数：小圆角、更克制的折射与色散（卡片尺寸小，过强会糊）。 */
        val Card = LiquidGlassConfig(
            cornerRadius = 20.dp,
            refractionHeight = 16.dp,
            refractionOffset = (-48).dp,
            depthEffect = 0.3f,
            dispersion = 0.35f,
            blurRadius = 20.dp,
        )
    }
}

/** 玻璃材质颜色令牌(浅色卡片玻璃,与 GlassCard.kt 既有视觉一致)。 */
object LiquidGlassPalette {
    /** 磨砂玻璃底(浅色,明显从背景分离)。 */
    val LightGlassBase: Color = Color.White.copy(alpha = 0.85f)

    /** 浮层 tint:浅色冷灰玻璃。 */
    val LightTint: Color = Color(0xFFF2F2F7).copy(alpha = 0.55f)

    /** 浮层 tint:深色微量白。 */
    val DarkTint: Color = Color.White.copy(alpha = 0.10f)

    /** 顶部受光。 */
    val HighlightTop: Color = Color.White.copy(alpha = 0.22f)

    /** 中心透明 → 底部沉降。 */
    val HighlightBottom: Color = Color.Black.copy(alpha = 0.05f)

    /** 边缘反射:左上高光。 */
    val BorderLight: Color = Color.White.copy(alpha = 0.8f)

    /** 边缘反射:右下暗部。 */
    val BorderDark: Color = Color.Black.copy(alpha = 0.07f)

    /** 卡片浮起阴影。 */
    val CardShadowSpot: Color = Color.Black.copy(alpha = 0.10f)
    val CardShadowAmbient: Color = Color.Black.copy(alpha = 0.04f)
}

/** 当前主题下的浮层 tint 色（由 NirikoTheme 的 LocalDarkTheme 决定，非系统值）。 */
@Composable
fun liquidGlassTint(): Color =
    if (LocalDarkTheme.current) LiquidGlassPalette.DarkTint else LiquidGlassPalette.LightTint