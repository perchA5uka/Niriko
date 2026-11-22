package com.otakup.niriko.data.settings

/**
 * 玻璃强度（Niriko 27 · S1 / N1）：把「玻璃好不好看」交给用户自己调，对标 iOS 27 的
 * 「设置 → 外观 → Liquid Glass」透明度滑条：从「极致清晰」到「完全着色」连续可调。
 *
 * 只缩放各玻璃表面**底色 tint 的不透明度**，不动折射、模糊半径、边缘高光与阴影，所以：
 * - 强度越低，背后内容透得越多（清晰）；强度越高，底色越实（着色，文字对比更稳）；
 * - 默认 [Default]（0.6）对应倍率恰好 1.0，也就是**默认观感与改动前逐像素一致**，
 *   老用户升级后不会无故变样，想要更透 / 更实自己拖滑条。
 *
 * 放在 data.settings 而不是 ui.theme：[AppSettings] 要引用默认值，data 层不能反向依赖 ui 层。
 * 纯函数，不依赖 Android / Compose，可直接 JVM 单测。
 */
object GlassStrength {
    /** 滑条下限：极致清晰。 */
    const val Min: Float = 0f

    /** 滑条上限：完全着色。 */
    const val Max: Float = 1f

    /** 默认强度（计划 §12 第 1 条：着色 60%）。 */
    const val Default: Float = 0.6f

    /** 强度 [Min] 对应的 tint 倍率。 */
    const val MinMultiplier: Float = 0.4f

    /** 强度 [Max] 对应的 tint 倍率。 */
    const val MaxMultiplier: Float = 1.8f

    /**
     * 缩放后 tint 不透明度的上限：再实就是不透明色块，玻璃感与「背后可见」都没了，
     * 且会盖住封面主色。
     */
    const val MaxAlpha: Float = 0.92f

    /** 把任意输入夹到 [Min]..[Max]；非有限值（NaN / 无穷）回落默认值。 */
    fun clamp(value: Float): Float = if (value.isNaN() || value.isInfinite()) Default else value.coerceIn(Min, Max)

    /**
     * 强度 → tint 倍率，以 [Default] 为拐点的分段线性：
     * [Min] → [MinMultiplier]，[Default] → 1.0，[Max] → [MaxMultiplier]。
     *
     * 分段而不是一条直线，是为了让默认值**精确**落在 1.0（一条直线从 0.4 到 1.8 时 0.6 只会是 0.82）。
     */
    fun tintMultiplier(strength: Float): Float {
        val s = clamp(strength)
        return if (s <= Default) {
            MinMultiplier + (1f - MinMultiplier) * (s / Default)
        } else {
            1f + (MaxMultiplier - 1f) * ((s - Default) / (Max - Default))
        }
    }

    /** 把某表面的基准 tint 不透明度按强度缩放，并夹在 0..[MaxAlpha]。 */
    fun scaledAlpha(baseAlpha: Float, strength: Float): Float = (baseAlpha * tintMultiplier(strength)).coerceIn(0f, MaxAlpha)

    /** 滑条旁的文字档位（只用于设置页展示）。 */
    fun label(strength: Float): String {
        val s = clamp(strength)
        return when {
            s < 0.2f -> "极致清晰"
            s < 0.5f -> "清晰"
            s < 0.7f -> "均衡"
            s < 0.9f -> "偏实"
            else -> "完全着色"
        }
    }
}
