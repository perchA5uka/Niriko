package com.otakup.niriko.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 材质层级（Niriko 27 · S0 空壳，见 docs/design-language-plan-2026-11-niriko27.md §3.1）。
 *
 * 目标：把现在散在 [com.otakup.niriko.ui.components.liquidglass.LiquidGlassPalette]、GlassCard、
 * GlassSectionCard、BlurredGlassSurface 里的「一层 85% 白罩」换成按层级给的四层材质
 * （折射 / tint / 受光 / 阴影），每一层的参数只在这里出现一次。
 *
 * S0 只建数据与不变量测试，**没有任何调用点**：界面观感不变。S1 才会把四套表面接到
 * 这里，并在真机上调参；表里的数值是起始值，调完后同步改本文件与 SurfaceLevelsTest。
 *
 * 与既有令牌的关系：[GlassSurfaceTokens] 只管搜索卡片的垫层，LiquidGlassTokens 管 kyant
 * 渲染参数，二者都不是层级表；本文件是层级表。名字刻意不叫 SurfaceTokens，避免和
 * SurfaceTokensTest（B7a 圆角令牌的测试）混淆。
 */
enum class SurfaceLevel(
    /** 背后内容的模糊半径；0 表示不是玻璃（页面底）。 */
    val blur: Dp,
    /** 浅色主题下的 tint 不透明度（0..1）。 */
    val tintAlphaLight: Float,
    /** 深色主题下的 tint 不透明度（0..1）。 */
    val tintAlphaDark: Float,
    /** 1dp 边缘受光的不透明度（0..1）。 */
    val edgeAlpha: Float,
    /** 投影；null 表示贴着底面、不浮起。 */
    val shadow: SurfaceShadow?,
) {
    /** L0 页面底：壁纸或 surface，不做玻璃。 */
    Canvas(blur = 0.dp, tintAlphaLight = 0f, tintAlphaDark = 0f, edgeAlpha = 0f, shadow = null),

    /**
     * L1 全幅区块、设置分组：最薄的一层，只分区不浮起。
     * blur 比 Card 还宽（20 > 16）是有意的：面积大，窄模糊会显出颗粒（见 SurfaceLevelsTest）。
     */
    Section(blur = 20.dp, tintAlphaLight = 0.06f, tintAlphaDark = 0.10f, edgeAlpha = 0.12f, shadow = null),

    /** L2 列表卡、统计卡：主力材质。 */
    Card(
        blur = 16.dp,
        tintAlphaLight = 0.10f,
        tintAlphaDark = 0.14f,
        edgeAlpha = 0.16f,
        shadow = SurfaceShadow(offsetY = 4.dp, blur = 12.dp, alpha = 0.08f),
    ),

    /** L3 底栏、dock、浮动按钮：悬在内容之上。 */
    Floating(
        blur = 24.dp,
        tintAlphaLight = 0.14f,
        tintAlphaDark = 0.18f,
        edgeAlpha = 0.22f,
        shadow = SurfaceShadow(offsetY = 8.dp, blur = 24.dp, alpha = 0.12f),
    ),

    /** L4 sheet、对话框、菜单：最高层，压住下面的一切。 */
    Modal(
        blur = 28.dp,
        tintAlphaLight = 0.20f,
        tintAlphaDark = 0.24f,
        edgeAlpha = 0.26f,
        shadow = SurfaceShadow(offsetY = 16.dp, blur = 40.dp, alpha = 0.18f),
    ),
    ;

    /** 是否玻璃层（有模糊）。页面底不是。 */
    val isGlass: Boolean get() = blur > 0.dp

    /** 按主题取 tint 不透明度。 */
    fun tintAlpha(isDark: Boolean): Float = if (isDark) tintAlphaDark else tintAlphaLight
}

/** 单层投影：只有垂直偏移，水平恒为 0（光从上方来）。 */
data class SurfaceShadow(
    val offsetY: Dp,
    val blur: Dp,
    /** 投影不透明度（0..1）。 */
    val alpha: Float,
)
