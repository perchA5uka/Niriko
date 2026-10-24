package com.otakup.niriko.util

import com.otakup.niriko.data.remote.tmdb.dto.TmdbImageDto

/**
 * 标题 logo 的三档显示策略（C · 13.1）。
 *
 * **默认 OFF**：logo 是「锦上添花」，不该替所有人改标题观感；开启后如果对比度不够，
 * 仍然回退文字 —— 宁可少一个 logo，也不要一行看不清的白字压在亮封面上。
 *
 * 存字符串（而不是枚举 ordinal）进设置：设置层不依赖 UI 包，[fromKey] 负责容错。
 */
enum class TitleLogoMode(val key: String) {
    OFF("off"),
    ALWAYS("always"),
    TEXT_WHEN_UNCLEAR("text_when_unclear"),
    ;

    companion object {
        fun fromKey(key: String?): TitleLogoMode =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: OFF
    }
}

/** logo 的显示框（dp）：调用方按它固定高度，加载中占位用同一个框，避免跳版。 */
data class TitleLogoBox(val widthDp: Float, val heightDp: Float)

/**
 * 标题 logo 的纯规则：选图 / 尺寸框 / 对比度 / 回退。
 *
 * 为什么全部做成纯函数：TMDb 的图片集合天然脏（svg、0 宽高、无语言、票数噪声），
 * 规则一旦散在 Compose 里就没法断言；这里每个判断都能用 JVM 单测钉住。
 */
object TitleLogoPolicy {

    /** 只接受这两种语言的字标：中文优先，其次日文。 */
    val ALLOWED_LOGO_LANGUAGES = listOf("zh", "ja")

    /** 面积恒定：面积 = AREA_FACTOR × h²（只保留规则，具体数值按标题字号在 [boxFor] 里算）。 */
    const val AREA_FACTOR = 2.19f

    /** 宽高比上限：w ≤ 3.38 × h（超宽 logo 会挤掉标题行的呼吸空间）。 */
    const val MAX_WIDTH_RATIO = 3.38f

    /** 兜底最小高度：太小的 logo 不如不要，直接回退文字。 */
    const val MIN_HEIGHT_DP = 20f

    /** 对比度下限（WCAG AA 大字号 3:1）。 */
    const val MIN_CONTRAST = 3.0f

    /**
     * 选一张 logo。
     *
     * 优先级：界面语言 → 作品原语言 → 无语言 logo；同语言内按 vote_average 再 vote_count 降序。
     * 跳过 .svg（Coil 与多数分享管线不支持）、宽或高为 0、aspect_ratio 非正的对象。
     */
    fun pick(
        logos: List<TmdbImageDto>,
        @Suppress("UNUSED_PARAMETER") uiLanguage: String = "zh",
        @Suppress("UNUSED_PARAMETER") originalLanguage: String? = null,
    ): TmdbImageDto? {
        val usable = logos.filter {
            val path = it.filePath?.trim().orEmpty()
            path.isNotEmpty() &&
                !path.endsWith(".svg", ignoreCase = true) &&
                it.width > 0 && it.height > 0 &&
                (it.aspectRatio ?: 0.0) > 0.0
        }
        if (usable.isEmpty()) return null

        // 只收中/日：中文优先，其次日文；其他语言与无语言 logo 一律不用。
        // 作品以中日动画为主，英文字标（多见于剧场版/欧美片）观感与标题体系不搭，宁缺毋滥。
        for (lang in ALLOWED_LOGO_LANGUAGES) {
            bestOf(usable.filter { it.languageCode?.equals(lang, ignoreCase = true) == true })?.let { return it }
        }
        return null
    }

    private fun bestOf(list: List<TmdbImageDto>): TmdbImageDto? =
        list.sortedWith(
            compareByDescending<TmdbImageDto> { it.voteAverage ?: 0.0 }
                .thenByDescending { it.voteCount ?: 0 }
                .thenByDescending { it.width },
        ).firstOrNull()

    /**
     * 面积恒定 + 宽高比上限的显示框。
     *
     * @param aspectRatio logo 自身宽高比（w/h）
     * @param lineHeightDp 标题行高（headlineMedium 26sp 约 34dp；调用方按实际字号传）
     * @param availableWidthDp 可用宽度（屏幕宽 - 左右边距）
     */
    fun boxFor(
        widthDp: Float,
        aspectRatio: Double,
        lineHeightDp: Float = 34f,
    ): TitleLogoBox {
        val aspect = aspectRatio.coerceAtLeast(0.01).toFloat()
        // 宽度是主导：调用方传「海报宽度的 2/3」，logo 就按这个宽度排版并水平居中。
        // 高度只做护栏：极高的 logo（aspect < 1）不能把标题区撑成一堵墙。
        val maxHeight = (lineHeightDp * 2.5f).coerceAtLeast(MIN_HEIGHT_DP)
        var width = widthDp.coerceAtLeast(1f)
        var height = width / aspect
        if (height > maxHeight) {
            height = maxHeight
            width = height * aspect
        }
        if (height < MIN_HEIGHT_DP) {
            height = MIN_HEIGHT_DP
            width = (height * aspect).coerceAtMost(widthDp.coerceAtLeast(1f))
        }
        return TitleLogoBox(widthDp = width, heightDp = height)
    }

    /** 是否回退文字标题：OFF 永远回退；ALWAYS 只在没有 logo 时回退；第三档还要看对比度。 */
    fun shouldFallbackToText(mode: TitleLogoMode, hasLogo: Boolean, contrastOk: Boolean): Boolean =
        when (mode) {
            TitleLogoMode.OFF -> true
            TitleLogoMode.ALWAYS -> !hasLogo
            TitleLogoMode.TEXT_WHEN_UNCLEAR -> !hasLogo || !contrastOk
        }

    /** WCAG 对比度。亮度由位图采样后传进来，这里保持纯函数可测。 */
    fun contrastRatio(lumaA: Float, lumaB: Float): Float {
        val a = lumaA.coerceIn(0f, 1f)
        val b = lumaB.coerceIn(0f, 1f)
        val hi = maxOf(a, b)
        val lo = minOf(a, b)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    fun isContrastOk(logoLuma: Float, backgroundLuma: Float, minRatio: Float = MIN_CONTRAST): Boolean =
        contrastRatio(logoLuma, backgroundLuma) >= minRatio
}
