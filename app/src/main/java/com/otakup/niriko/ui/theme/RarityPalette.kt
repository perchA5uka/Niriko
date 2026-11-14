package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color

// 材质派生色令牌（V-02 收口，v1.2.2）。
//
// 这些色值不是主题色：稀有度材质的银/金/全息是「卡面物理效果」的一部分，
// 搜索指示器的绿条是「手势进度」的专属语义，都不能从 MaterialTheme 派生。
// 但它们此前是散落在组件里的 Color(0x…) 字面量——换主题/种子色时不会跟着变，
// 也无法被离线断言（谁跟谁同源、哪个值是刻意取的，只有代码知道）。
//
// 本批只做**等值搬迁**（零像素变化）：下面每个值都能在改造前的字面量里逐一对应；
// 为什么放在 ui/theme 域：ThemeColorAuditTest 把「颜色定义域」限定在 theme/share/
// LiquidGlassTokens，令牌必须落在定义域里，否则下一次扫描会把它当越界色值报回来。

/**
 * 卡面稀有度材质色。
 *
 * 三组色各司其职（见 [com.otakup.niriko.ui.components.cardRarityPainter]）：
 * - Surface*：卡面静态渐变（自上而下的 4~5 段，带极低 alpha）；
 * - Edge/Specular/Deep：描边渐变与高光反射的三段（不透明，用的时候再乘强度 alpha）；
 * - 银/金/全息之间刻意不复用同一个色：全息的金尾段与金卡的主色差一档，是刻意的色温区分。
 */
object RarityPalette {

    // ── 银 ────────────────────────────────────────────────────────────────
    val SilverSurfaceTop = Color(0xFFAAC6DF)
    val SilverSurfaceUpper = Color(0xFFD9E6F2)
    val SilverSurfaceLower = Color(0xFFD1DEEB)
    val SilverSurfaceBottom = Color(0xFF8DABC8)
    val SilverEdge = Color(0xFF94B4CE)
    val SilverSpecular = Color(0xFFF1F7FF)
    val SilverEdgeDeep = Color(0xFF7597B5)

    // ── 金 ────────────────────────────────────────────────────────────────
    val GoldSurfaceTop = Color(0xFFD9AA4B)
    val GoldSurfaceUpper = Color(0xFFF2D797)
    val GoldSurfaceLower = Color(0xFFEDD8AF)
    val GoldSurfaceBottom = Color(0xFFC89738)
    val GoldEdge = Color(0xFFD5A342)
    val GoldSpecular = Color(0xFFFFE3A0)
    val GoldEdgeDeep = Color(0xFFAF7B29)

    // ── 全息 ──────────────────────────────────────────────────────────────
    val HoloSurfaceTop = Color(0xFF8DA3E6)
    val HoloSurfaceUpper = Color(0xFF76D4D1)
    val HoloSurfaceLower = Color(0xFFE0DDE8)
    val HoloSurfaceBottom = Color(0xFFE59ABF)
    val HoloSurfaceTail = Color(0xFFE6BF71)
    val HoloEdge = Color(0xFF65C9CF)
    val HoloSpecular = Color(0xFFE8A0D0)
    val HoloEdgeDeep = Color(0xFFDAB358)

    /**
     * 中性「肩线」：白底卡面上给稀有度反射留的一层亮度对比（不带色相）。
     *
     * 刻意不用主题的 onSurfaceVariant：肩线要的是固定明度偏移，跟着主题色走会在深色下消失。
     */
    val NeutralShoulder = Color(0xFF43464B)
}

/**
 * 搜索指示器（绿色进度条）的两种形态（V-02：此前是 IosStyleSearchComponent 里的两个私有常量）。
 *
 * 为什么放 theme 域而不是留在组件里：两个值都是「手势进度」语义的材质色，
 * 与 [RarityPalette] 同批从组件迁出，落在这里才能被 ThemeColorAuditTest 认可。
 */
object SearchIndicatorAccents {

    /** 拖拽中：亮绿。 */
    val Bright = Color(0xFF43A047)

    /**
     * 沉浸态：品牌绿的 16% 底（原型 rgba(46,125,50,.16)）。
     *
     * 用 [NirikoAccents.PeopleGreen] + 精确 alpha 表达，保证与改造前的 0x292E7D32 逐位相同
     * （0x29 / 255 = 0.16078…，不是手写的 0.16）。
     */
    val ImmersiveDim = NirikoAccents.PeopleGreen.copy(alpha = 0x29 / 255f)
}
