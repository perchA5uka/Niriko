package com.otakup.niriko.ui.components.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * 液态玻璃主题令牌。
 *
 * 来源：
 * - 材质语言与参数来自项目既有液态玻璃实现（GlassCard.kt）的默认参数
 *   （refractionHeight 20dp / refractionOffset -70dp / dispersion 0.5 / depthEffect 0.3）；
 * - 渲染 API 来自参考库 **Kyant0/AndroidLiquidGlass**（Apache-2.0），本项目通过
 *   `io.github.kyant0:backdrop:1.0.6` 使用其 `drawBackdrop` / `highlight` / `shadow` /
 *   `innerShadow` DSL。
 *   注：早前此处写「AndroidLiquidGlassView(QmDeve, MIT)」有误 —— QmDeve/AndroidLiquidGlassView
 *   是本项目 AGSL 着色器（app/src/main/res/raw/liquid_glass_effect.agsl）的上游（MIT），
 *   而**本文件的材质参数与 DSL 上游是 Kyant0/AndroidLiquidGlass（Apache-2.0）**。
 *
 * 设计原则：一切视觉参数收敛为 token，禁止在组件里散落魔数：
 * - 卡片玻璃（自含材质）：浅色磨砂底 + 顶部受光 + 底部沉降 + 对角边缘反射；
 * - 浮层玻璃（真折射）：背后有内容源时，API 33+ 用 AGSL 折射 / 色散，API 31-32 高斯模糊，
 *   低版本降级为 tint 玻璃；
 * - 深色模式：卡片回退 M3 surfaceContainer（保证可读性，取消玻璃层次）。
 *
 * 材质层：色令牌不再是「死令牌」——[LiquidGlassPalette] 的每一个颜色都被
 * [liquidGlassHighlight] / [liquidGlassShadow] / [liquidGlassInnerShadow] 消费，
 * 由 backdrop 的 Highlight / Shadow / InnerShadow DSL 绘制成真实材质层。
 */

/** 液态玻璃渲染参数（像素级计算由使用方按 density 转换）。 */
data class LiquidGlassConfig(
    /** 玻璃圆角（同时也是 AGSL 折射 SDF 的 cornerRadii）。 */
    val cornerRadius: Dp = 40.dp,
    /** 边缘折射高度：圆角向内多少距离内发生折射。 */
    val refractionHeight: Dp = 20.dp,
    /** 折射偏移：负值 = 向内（凹透镜）收缩采样，与参考库默认一致。 */
    val refractionOffset: Dp = (-70).dp,
    /** 深度效果：形状法线与中心方向混合比例（0..1）。 */
    val depthEffect: Float = 0.3f,
    /** 色散强度：7 色彩带采样的分离程度。 */
    val dispersion: Float = 0.5f,
    /** 内容高斯模糊半径（折射前的磨砂底）。 */
    val blurRadius: Dp = 24.dp,
    /** 内容对比度（-1..1）。 */
    val contrast: Float = 0f,
    /** 内容白点：正值拉白、负值拉黑（0 = 不调整）。 */
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

        /**
         * 竖向玻璃 dock 预设（B2 的竖向 dock 用）。
         *
         * 竖排 dock 的窄边很窄（典型 72dp 宽 ⇒ `halfSize.x ≈ 36dp`）：backdrop 内部取
         * `gradRadius = min(cornerRadius * 1.5, min(halfSize.x, halfSize.y))`，所以竖排时梯度半径
         * 实际被**窄边**钳住（例如 cornerRadius 28dp ⇒ 42dp 会被钳到 36dp）。这意味着：
         * - 圆角给再大也不会让折射带更宽，只会让 SDF 与裁剪圆角失配；
         * - refractionHeight 必须按窄边定（12dp 左右），否则折射带占满整条 dock 显得发糊。
         *
         * ⚠️ 这些是**待真机逐档调的初始值**（竖排 dock 的折射需要 B6 截图矩阵逐档比对后定稿）；
         * 调整只应发生在本文件，不要在 dock 组件里写魔数。
         */
        val VerticalDock = LiquidGlassConfig(
            cornerRadius = 26.dp,
            refractionHeight = 12.dp,
            refractionOffset = (-30).dp,
            depthEffect = 0.28f,
            dispersion = 0.22f,
            blurRadius = 16.dp,
        )
    }
}

/**
 * 玻璃材质颜色令牌。
 *
 * 取值与 GlassCard.kt 既有实装值逐一相等（收敛前这些数字散落在组件里）；改动本文件即改动全局玻璃材质。
 */
object LiquidGlassPalette {
    /** 磨砂玻璃底（浅色，明显从背景分离）。 */
    val LightGlassBase: Color = Color.White.copy(alpha = 0.85f)

    /** 浮层 tint：浅色冷灰玻璃。 */
    val LightTint: Color = Color(0xFFF2F2F7).copy(alpha = 0.55f)

    /** 浮层 tint：深色微量白。 */
    val DarkTint: Color = Color.White.copy(alpha = 0.10f)

    // ── 边缘反射 / 顶部受光 / 底部沉降（Highlight + 表面渐变）──

    /** 顶部受光（浅色，表面渐变起点）。 */
    val HighlightTop: Color = Color.White.copy(alpha = 0.22f)

    /** 顶部受光（深色）。 */
    val HighlightTopDark: Color = Color.White.copy(alpha = 0.06f)

    /** 中心透明 → 底部沉降（浅色，表面渐变终点）。 */
    val HighlightBottom: Color = Color.Black.copy(alpha = 0.03f)

    /** 中心透明 → 底部沉降（深色）。 */
    val HighlightBottomDark: Color = Color.Black.copy(alpha = 0.10f)

    /**
     * 边缘镜面反射基色（Highlight.style，Plus 混合，浅色 / 深色共用）。
     *
     * 取值等于 backdrop `HighlightStyle.Default` 的 White α0.5 —— 即收敛前 GlassCard 的实际渲染色，
     * 因此切到本令牌不改变既有边缘观感；整体调节边缘亮度只需要改这里。
     */
    val BorderLight: Color = Color.White.copy(alpha = 0.5f)

    /** 边缘暗部基色（深色模式的内沉降 / 底部厚度）。 */
    val BorderDark: Color = Color.Black.copy(alpha = 0.22f)

    // ── 阴影（Shadow / InnerShadow）──

    /** 卡片接触阴影（浅色）。 */
    val CardShadowSpot: Color = Color.Black.copy(alpha = 0.10f)

    /** 卡片环境弥散（浅色，与 [CardShadowSpot] 合成到单层 Shadow —— backdrop 只接受一层）。 */
    val CardShadowAmbient: Color = Color.Black.copy(alpha = 0.04f)

    /** 卡片接触阴影（深色，沿用既有实装值）。 */
    val CardShadowSpotDark: Color = Color.Black.copy(alpha = 0.24f)

    /** 底部内沉降（浅色，玻璃厚度感）。 */
    val CardInnerShadowLight: Color = Color.Black.copy(alpha = 0.14f)
}

/**
 * 卡片边缘镜面反射（backdrop Highlight DSL）。
 *
 * @param alpha 交互亮度（既有按压动画 0.65 → 0.95），由调用方传入
 */
fun liquidGlassHighlight(alpha: Float): Highlight = Highlight.Default.copy(
    width = 1.dp,
    blurRadius = 0.5.dp,
    alpha = alpha,
    style = HighlightStyle.Default(color = LiquidGlassPalette.BorderLight),
)

/**
 * 卡片浮起阴影（backdrop Shadow DSL）。
 *
 * backdrop 的 `drawBackdrop` 只接受**一层** Shadow，所以浅色模式把
 * [LiquidGlassPalette.CardShadowSpot]（接触阴影）与 [LiquidGlassPalette.CardShadowAmbient]
 * （环境弥散）合并到同一层的 α 上；深色模式沿用既有单值，避免改变深色观感。
 */
fun liquidGlassShadow(isDark: Boolean): Shadow = Shadow(
    radius = 12.dp,
    offset = DpOffset(0.dp, 4.dp),
    color = if (isDark) {
        LiquidGlassPalette.CardShadowSpotDark
    } else {
        Color.Black.copy(
            alpha = LiquidGlassPalette.CardShadowSpot.alpha + LiquidGlassPalette.CardShadowAmbient.alpha,
        )
    },
)

/** 卡片内厚度（backdrop InnerShadow DSL）：底部一条沉降暗边。 */
fun liquidGlassInnerShadow(isDark: Boolean): InnerShadow = InnerShadow(
    radius = 12.dp,
    offset = DpOffset(0.dp, 2.dp),
    color = if (isDark) LiquidGlassPalette.BorderDark else LiquidGlassPalette.CardInnerShadowLight,
)

/** 玻璃表面竖直渐变的三个色标（顶部受光 → 中心透明 → 底部沉降）。 */
fun liquidGlassSurfaceGradient(isDark: Boolean): List<Color> = if (isDark) {
    listOf(LiquidGlassPalette.HighlightTopDark, Color.Transparent, LiquidGlassPalette.HighlightBottomDark)
} else {
    listOf(LiquidGlassPalette.HighlightTop, Color.Transparent, LiquidGlassPalette.HighlightBottom)
}

/** 当前主题下的浮层 tint 色（由 NirikoTheme 的 LocalDarkTheme 决定，非系统值）。 */
@Composable
fun liquidGlassTint(): Color =
    if (LocalDarkTheme.current) LiquidGlassPalette.DarkTint else LiquidGlassPalette.LightTint
