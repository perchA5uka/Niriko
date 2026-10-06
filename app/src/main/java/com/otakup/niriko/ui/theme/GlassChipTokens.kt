package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color
import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.GlassEffectLevel
import kotlin.math.pow

/**
 * 海报小件与标签胶囊的**统一材质令牌**（F05 + F02 剩余覆盖面）。
 *
 * 改造前每个小件各写各的：状态/评分/收藏心一律 `Color.Black.copy(0.55f)`（浅色模式下也是黑底），
 * 详情页标签则直接用不透明的 `secondaryContainer`。于是同一屏里出现三种「胶囊」：
 * 黑纱、实心色块、还有 Material 默认底色 —— 这正是 F02「统一材质」要收掉的东西。
 *
 * ## 这里是纯色学，不是「看起来差不多」
 *
 * 小件压在**图片**上时，实际颜色 = 半透明底 ∘ 图片。图片亮度不可控，因此判据不能靠肉眼，
 * 而是取**两个极端的合成结果**（纯黑图 / 纯白图）都要满足对比度下限：
 *
 * - 浅色模式：底是浅白半透明（用户要求的「浅色白底」），文字取深色；
 *   最坏情况是压在**黑图**上（合成结果最暗），因此在那个合成色上要求对比度达标。
 * - 深色模式：底是半透明黑，文字取浅色；最坏情况是压在**白图**上。
 *
 * [ensureReadable] 负责把品牌色（在看橙 / 看过绿 / ★ 琥珀…）在必要时**压暗或提亮**到
 * 达标为止，保留色相、只调明度。这样「浅色白底 + 状态橙」这种在旧实现里必然不达标的组合
 * 也能成立，而且不是拍脑袋调的 —— 每个组合都有 [contrastRatio] 的机械判据，见 GlassChipTokensTest。
 *
 * 合成按 sRGB 直接插值（Compose 的默认混合空间），与 WCAG 的亮度计算分开：
 * 后者按规范做 sRGB→线性转换，所以对比度数值是标准的 WCAG 2.x 比值。
 */
object GlassChipTokens {

    /** 浅色模式的浅白底透明度：越高越不透明。 */
    const val LIGHT_BASE_ALPHA = 0.78f

    /** 深色模式的半透明黑底透明度。 */
    const val DARK_BASE_ALPHA = 0.62f

    /** 压在图片上的小件在「低端 / 关闭玻璃」档位用的更不透明底。 */
    const val OPAQUE_BASE_ALPHA = 0.92f

    /** 正文级对比度下限（WCAG AA，小字）。 */
    const val MIN_CONTRAST = 4.5f

    /**
     * 亮度自适应的**全量程**摆幅：从「压在纯黑图」到「压在纯白图」的不透明度差。
     *
     * 刻意很小（±0.08）：小件是装饰层，不该因为背后换了张图就明显变样。
     * 抽成常量是为了让「摆幅很小」这句话在单测里有唯一的数可断言，而不是两处各写一个魔数。
     */
    const val LUMINANCE_ADAPT_RANGE = 0.16f

    /**
     * 压在图片上的小件底色。
     *
     * @param isDark 深色主题
     * @param imageLuminance 背后图片/壁纸的平均亮度（0..1），null = 未知
     * @param glassEnabled 玻璃档位是否允许半透明（关闭时用更实的底，保证可读性优先）
     */
    fun onImageBackground(
        isDark: Boolean,
        imageLuminance: Float? = null,
        glassEnabled: Boolean = true,
    ): Color {
        val base = baseAlpha(isDark, imageLuminance, glassEnabled)
        return if (isDark) Color.Black.copy(alpha = base) else Color.White.copy(alpha = base)
    }

    /**
     * 底色的不透明度。
     *
     * 亮度自适应：底与图片**同向**时加一点不透明度（浅底压在亮图上、深底压在暗图上会「糊在一起」），
     * 反向时保持透明以保留玻璃观感。调整幅度刻意很小（±0.08）—— 小件是装饰层，
     * 不该因为背后换了张图就明显变样。
     */
    fun baseAlpha(isDark: Boolean, imageLuminance: Float? = null, glassEnabled: Boolean = true): Float {
        if (!glassEnabled) return OPAQUE_BASE_ALPHA
        val base = if (isDark) DARK_BASE_ALPHA else LIGHT_BASE_ALPHA
        if (imageLuminance == null) return base
        val luma = imageLuminance.coerceIn(0f, 1f)
        // 浅底：图越亮越需要一点不透明；深底：图越暗越需要
        val sameDirection = if (isDark) 1f - luma else luma
        val delta = (sameDirection - 0.5f) * LUMINANCE_ADAPT_RANGE
        return (base + delta).coerceIn(0.55f, 0.95f)
    }

    /** 压在图片上的小件的**最坏情况**合成底（文字要在这个颜色上达标）。 */
    fun worstCaseBackdrop(isDark: Boolean, base: Color): Color =
        compositeOver(base, if (isDark) Color.White else Color.Black)

    /**
     * 页面表面的标签胶囊底色（不压在图片上，而是压在页面背景上）。
     *
     * 与 [onImageBackground] 同一族材质，但极性与页面相反：浅色页面用「浅白半透明 + 极淡描边」，
     * 深色页面用「半透明白」——因此两种主题下都像同一枚玻璃片，而不是 Material 的实心色块。
     */
    fun surfaceChipBackground(isDark: Boolean, emphatic: Boolean = false, glassEnabled: Boolean = true): Color {
        val alpha = when {
            !glassEnabled -> 0.10f
            emphatic -> if (isDark) 0.24f else 0.30f
            isDark -> 0.14f
            else -> 0.55f
        }
        // 两个主题都用**白**：深色页面上白是「提亮」（玻璃的受光面），
        // 浅色页面上白是「压淡」（玻璃的雾面）。真正的边缘靠 surfaceChipBorder 的细描边给，
        // 所以浅色页面上这枚几乎不可见的白片也仍然「有形状」——不会看起来像没画。
        return Color.White.copy(alpha = alpha)
    }

    /**
     * 胶囊的细描边（海报小件与页面标签共用）。
     *
     * 深色下用一点点白（在黑底上勾出边缘），浅色下用一点点黑（在白底上勾出边缘）——
     * 两种主题各自都只有「一条极淡的线」，所以它们看起来才像同一枚玻璃片。
     */
    fun chipBorder(isDark: Boolean): Color =
        if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)

    /**
     * 把品牌色调整到在 [background] 上满足 [minContrast] 为止。
     *
     * 只改明度、不动色相：向黑或向白插值，步长 1/20，最多 20 步。
     * 若怎么调都到不了（例如纯中性灰底与纯灰前景），返回对比度最高的那一端。
     */
    fun ensureReadable(
        preferred: Color,
        background: Color,
        minContrast: Float = MIN_CONTRAST,
    ): Color {
        if (contrastRatio(preferred, background) >= minContrast) return preferred
        // 往「背景的反方向」调：背景偏亮 → 压暗；背景偏暗 → 提亮
        // Choose by actual contrast, not an arbitrary luminance threshold that can wash colors out.
        val target = if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) Color.Black else Color.White
        var best = preferred
        var bestContrast = contrastRatio(preferred, background)
        for (step in 1..20) {
            val t = step / 20f
            val candidate = lerpColor(preferred, target, t)
            val c = contrastRatio(candidate, background)
            if (c > bestContrast) {
                best = candidate
                bestContrast = c
            }
            if (c >= minContrast) return candidate
        }
        return best
    }

    // ==================== 色学工具（纯函数，单测直接断言） ====================

    /** sRGB 直插混合（与 Compose 默认混合空间一致）。 */
    fun compositeOver(translucent: Color, backdrop: Color): Color {
        val a = translucent.alpha.coerceIn(0f, 1f)
        return Color(
            red = translucent.red * a + backdrop.red * (1f - a),
            green = translucent.green * a + backdrop.green * (1f - a),
            blue = translucent.blue * a + backdrop.blue * (1f - a),
            alpha = 1f,
        )
    }

    /** WCAG 2.x 相对亮度（sRGB → 线性）。 */
    fun relativeLuminance(color: Color): Float {
        fun channel(v: Float): Float {
            val c = v.coerceIn(0f, 1f)
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    /** WCAG 2.x 对比度（1..21）。 */
    fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun lerpColor(from: Color, to: Color, t: Float): Color = Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f,
    )

    /** 档位 → 是否允许半透明（关闭玻璃 / 老设备降级时用实底）。 */
    fun glassEnabled(effect: GlassEffectLevel, level: CardGlassLevel): Boolean =
        effect != GlassEffectLevel.OFF && level != CardGlassLevel.OFF
}

