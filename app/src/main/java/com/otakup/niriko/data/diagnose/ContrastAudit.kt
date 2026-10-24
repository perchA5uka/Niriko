package com.otakup.niriko.data.diagnose

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 深色对比度审计（10.15 的机械判据，与 GlassChipTokens 同一套 WCAG 2.x 口径）。
 *
 * 为什么单独一份：小件（标签/评分胶囊）已经有 `GlassChipTokens.contrastRatio` 与测试，
 * 这里负责**正文/表面**组合（onSurface/surface、onSurfaceVariant/surface、onPrimary/primary），
 * 让「深色下正文糊不糊」也变成可断言的数字，而不是看截图。
 */
data class ContrastCheck(val name: String, val fg: Int, val bg: Int, val min: Double)

data class ContrastFailure(val name: String, val ratio: Double, val min: Double)

/** WCAG 相对亮度（0..1）。 */
fun relativeLuminance(argb: Int): Double {
    fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    val r = channel((argb shr 16) and 0xFF)
    val g = channel((argb shr 8) and 0xFF)
    val b = channel(argb and 0xFF)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG 对比度（1..21）。 */
fun contrastRatio(fg: Int, bg: Int): Double {
    val a = relativeLuminance(fg)
    val b = relativeLuminance(bg)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/**
 * 把前景色收敛到与背景至少 [ratio]:1 的可读对比度（10.15）。
 *
 * 做法：**保持色相**地把前景朝白（背景偏暗）或朝黑（背景偏亮）插值 ——
 * HSV 色相只由通道差决定，`c' = c·(1-t) + target·t` 同时作用于三通道时色相不变，
 * 而亮度随 t 单调，因此二分出「刚好达标的最小改动量」，不做一次性过冲。
 *
 * - 已达标 → 原样返回（幂等，不会把正常配色改花）；
 * - 前景带 alpha → 先按背景合成再调整，返回不透明结果（否则调整量会在实际绘制时打折扣）；
 * - 两个方向都达不到（中灰背景）→ 取对比度更高的方向，尽量可读。
 */
fun ensureContrast(foreground: Long, background: Long, ratio: Float = 4.5f): Long {
    val fg = (foreground and 0xFFFFFFFFL).toInt()
    val bg = (background and 0xFFFFFFFFL).toInt()
    val target = ratio.toDouble().coerceIn(1.0, 21.0)
    val alpha = ((fg ushr 24) and 0xFF) / 255.0
    val base = if (alpha >= 0.999) fg else compositeArgb(alpha, fg, bg)
    if (contrastRatio(base, bg) + 1e-9 >= target) return fg.toLong() and 0xFFFFFFFFL

    // 朝白 / 朝黑各二分一次；方向偏好只决定「两者都可达时用哪个」。
    val toWhite = mixTowards(base, bg, target, towardWhite = true)
    val toBlack = mixTowards(base, bg, target, towardWhite = false)
    val preferWhite = relativeLuminance(bg) < 0.5
    val first = if (preferWhite) toWhite else toBlack
    val second = if (preferWhite) toBlack else toWhite
    val chosen = when {
        first.second + 1e-9 >= target -> first
        second.second + 1e-9 >= target -> second
        first.second >= second.second -> first
        else -> second
    }
    return 0xFF000000L or (chosen.first.toLong() and 0xFFFFFFFFL)
}

/** 把 [fg] 朝纯白/纯黑插值，返回「刚好达到 [target] 的最小插值量」对应的不透明 ARGB 与实测对比度。 */
private fun mixTowards(fg: Int, bg: Int, target: Double, towardWhite: Boolean): Pair<Int, Double> {
    fun at(t: Double): Int {
        fun channel(shift: Int): Int {
            val c = (fg ushr shift) and 0xFF
            val dest = if (towardWhite) 0xFF else 0x00
            return (c + (dest - c) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    val extreme = at(1.0)
    val extremeRatio = contrastRatio(extreme, bg)
    if (extremeRatio + 1e-9 < target) return extreme to extremeRatio
    var lo = 0.0
    var hi = 1.0
    repeat(24) {
        val mid = (lo + hi) / 2
        if (contrastRatio(at(mid), bg) + 1e-9 >= target) hi = mid else lo = mid
    }
    val result = at(hi)
    return result to contrastRatio(result, bg)
}

/** 半透明前景合成到背景上（sRGB 直插，与 Compose 默认混合一致）。 */
fun compositeArgb(alpha: Double, fg: Int, bg: Int): Int {
    val a = alpha.coerceIn(0.0, 1.0)
    fun mix(shift: Int): Int {
        val f = (fg shr shift) and 0xFF
        val b = (bg shr shift) and 0xFF
        return (f * a + b * (1 - a)).toInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}

/** 返回未达标项（正文字号下限 4.5:1，大字号 3:1）。 */
fun auditContrast(checks: List<ContrastCheck>): List<ContrastFailure> =
    checks.mapNotNull { c ->
        val ratio = contrastRatio(c.fg, c.bg)
        if (ratio + 1e-9 < c.min) ContrastFailure(c.name, ratio, c.min) else null
    }
