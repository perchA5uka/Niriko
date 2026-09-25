package com.otakup.niriko.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.otakup.niriko.data.settings.GlassEffectLevel

/**
 * 当前玻璃/特效强度（阶段 P 性能优化）。
 * 默认 FULL（全效果）；MainActivity 从设置注入，底栏 / 详情页 / 壁纸据此降级
 * 玻璃模糊与 backdrop 捕获开销。REDUCED 关 AGSL 折射，OFF 无背景捕获。
 */
val LocalGlassEffect = staticCompositionLocalOf { GlassEffectLevel.FULL }
