package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「展开表面」的材质令牌（F03）：搜索卡片的**垫层**。
 *
 * ## 为什么需要一个单独的对象
 *
 * 搜索卡片本身就是 [com.otakup.niriko.ui.components.appleGlassCard]（真折射 / 静态玻璃按档位降级），
 * 但玻璃层之上、内容之下还有一层**垫层**：展开的菜单会压在正在滚动的列表上，
 * 没有它的话封面图会从玻璃里透上来把文字搅花。
 *
 * 改造前这层写死 `surface.copy(alpha = 0.92f)` —— 92% 不透明，等于把刚做好的液态玻璃盖掉了，
 * 而且与 F05 的口径正好相反：浅色模式下它是一块几乎不透明的白板，而不是「浅白半透明」。
 *
 * ## 判据
 *
 * 垫层的不透明度只由两件事决定：**玻璃档位**（能不能靠折射模糊兜住可读性）与**是否展开**。
 * 这两条都能在单测里断言（见 GlassSurfaceTokensTest），不靠肉眼估：
 *
 * - 收起（56dp 圆钮，背后只有页面顶部）：可以更透，玻璃感优先；
 * - 展开（压在滚动内容上）：必须更实，可读性优先；
 * - 玻璃档位关闭 / 低端降级：没有折射可用，两种状态都用接近实心的底。
 */
object GlassSurfaceTokens {

    /** 浅色：白系垫层（「浅白半透明」）。深色：黑系垫层。 */
    fun searchBackingColor(isDark: Boolean, alpha: Float = searchBackingAlpha(expanded = true)): Color =
        if (isDark) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha)

    /**
     * 搜索卡片垫层的不透明度。
     *
     * @param expanded 是否处于展开态（菜单 / 拖拽 / 沉浸搜索）
     * @param glassEnabled 玻璃档位是否允许半透明（关闭 / 低端降级时为 false）
     */
    fun searchBackingAlpha(expanded: Boolean, glassEnabled: Boolean = true): Float = when {
        !glassEnabled -> if (expanded) 0.96f else 0.90f
        expanded -> 0.72f
        else -> 0.55f
    }

    /** 低端 / 关闭玻璃时，垫层是否应该更实（可读性优先于玻璃观感）。 */
    fun backingIsOpaqueFallback(glassEnabled: Boolean): Boolean = !glassEnabled

    /**
     * 展开态下「垫层 + 玻璃」是否仍然够清楚：用垫层的不透明度作为可读性的代理指标。
     *
     * 玻璃本身会做模糊（真折射档位），因此展开态的垫层不需要接近实心；
     * 但一旦低于 [MIN_EXPANDED_BACKING_ALPHA]，滚动内容会明显透上来 —— 这条下限就是那根线。
     */
    const val MIN_EXPANDED_BACKING_ALPHA = 0.65f

    /** 展开态的垫层不透明度是否达标。 */
    fun expandedBackingIsLegible(glassEnabled: Boolean): Boolean =
        searchBackingAlpha(expanded = true, glassEnabled = glassEnabled) >= MIN_EXPANDED_BACKING_ALPHA
}
