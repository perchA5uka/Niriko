package com.otakup.niriko.data.remote.tmdb

/**
 * TMDb 支持的语言（对齐 AniShelf 的 3 档：en / zh-CN / ja）。
 *
 * TMDb 的 title / overview 在部分语言下可能为 null（AniShelf 注释点名过 tv/35610 的 zh-TW），
 * 因此 DTO 里这些字段一律可空，取不到时回退到其它语言。
 */
enum class TmdbLanguage(val code: String, val label: String) {
    CHINESE("zh-CN", "简体中文"),
    JAPANESE("ja-JP", "日本語"),
    ENGLISH("en-US", "English"),
    ;

    companion object {
        val DEFAULT = CHINESE

        /** 图片语言白名单（对齐 AniShelf TMDbImageFilters，请求参数用 2 位代码）。 */
        val IMAGE_LANGUAGE_CODES = listOf("zh", "ja", "en")

        fun fromCode(code: String?): TmdbLanguage? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
