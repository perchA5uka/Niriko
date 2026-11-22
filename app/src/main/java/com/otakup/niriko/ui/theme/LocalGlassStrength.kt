package com.otakup.niriko.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.otakup.niriko.data.settings.GlassStrength

/**
 * 当前玻璃强度（Niriko 27 · N1），由 MainActivity 按 [com.otakup.niriko.data.settings.AppSettings.glassStrength] 提供。
 *
 * 默认值 [GlassStrength.Default]：预览 / 测试里不提供时，等价于「不缩放」。
 * 用 static 版本：强度极少变化，变化时整棵树重组一次即可，换来读取点零开销（同 LocalGlassEffect）。
 */
val LocalGlassStrength = staticCompositionLocalOf { GlassStrength.Default }
