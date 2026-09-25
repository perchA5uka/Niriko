package com.otakup.niriko.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop
import com.otakup.niriko.data.settings.CardGlassLevel

/**
 * 卡片可用窗口 backdrop（kyant，只捕获壁纸层）。
 *
 * - 顶层四页（有壁纸）由 MainActivity 注入；卡片据此做“真液态玻璃”折射/blur/vibrancy。
 * - null = 无壁纸 / 二级页 / 玻璃关闭，卡片退化为静态材质。
 */
val LocalCardGlassBackdrop = staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

/** 卡片液态玻璃档位（全开 / 仅已收藏 / 关闭），由设置注入。 */
val LocalCardGlassLevel = staticCompositionLocalOf { CardGlassLevel.FULL }

/** 壁纸平均亮度（0..1），用于卡片玻璃的自适应 tint/对比（无壁纸时 0.5）。 */
val LocalGlassLuminance = staticCompositionLocalOf { 0.5f }
