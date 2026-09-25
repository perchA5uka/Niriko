package com.otakup.niriko.data.remote.tmdb

/**
 * TMDb 图片 URL 解析（对齐 AniShelf 的 TMDbImageURLResolver / TMDbImagePath）。
 *
 * ## 为什么只存 file_path
 *
 * AniShelf 的经验：数据库里**只存 TMDb 的 `file_path`**（如 `/dhzbCznEzU67RXWYb53fyPe9Keb.jpg`），
 * 显示时按上下文尺寸拼 `https://image.tmdb.org/t/p/{size}{path}`。
 * 这样 TMDb 换域名/尺寸目录、或用户切换镜像时，全库封面不会失效。
 *
 * [storagePath] 负责把历史遗留的完整 URL 反解回路径（向后兼容）。
 */
object TmdbImageUrl {

    /** 官方图片域前缀。 */
    const val DEFAULT_IMAGE_BASE = "https://image.tmdb.org/t/p/"

    /** 可被镜像替换的图片基地址（由 TmdbClient 在切换端点时写入）。 */
    @Volatile
    var imageBaseUrl: String = DEFAULT_IMAGE_BASE

    // —— 尺寸档（对齐 AniShelf 的显示尺寸策略）——
    const val POSTER_TINY = "w92"
    const val POSTER_SMALL = "w185"
    const val POSTER_MEDIUM = "w342"
    const val POSTER_LARGE = "w500"
    const val POSTER_GALLERY = "w780"
    const val ORIGINAL = "original"

    const val BACKDROP_SMALL = "w300"
    const val BACKDROP_MEDIUM = "w780"
    const val BACKDROP_LARGE = "w1280"

    const val STILL_SMALL = "w92"
    const val STILL_MEDIUM = "w300"

    const val PROFILE_SMALL = "w185"

    /** `/abc.jpg` + `w500` → 完整 URL；path 为空时返回 null。 */
    fun url(path: String?, size: String = POSTER_MEDIUM): String? {
        val p = path?.trim().orEmpty()
        if (p.isEmpty()) return null
        // 已经是完整 URL：原样返回（容错）
        if (p.startsWith("http://") || p.startsWith("https://")) return p
        val normalized = if (p.startsWith("/")) p else "/$p"
        return imageBaseUrl.trimEnd('/') + "/" + size + normalized
    }

    /**
     * 完整 URL → 存储路径（含旧数据反解）。
     * `https://image.tmdb.org/t/p/w500/abc.jpg` → `/abc.jpg`；
     * 已是路径则原样规范化返回。
     */
    fun storagePath(from: String?): String? {
        val raw = from?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (!raw.startsWith("http")) {
            return if (raw.startsWith("/")) raw else "/$raw"
        }
        // 形如 {base}/{size}/{path...}，取 size 之后的全部（目录里可能还有 /）
        val marker = "/t/p/"
        val idx = raw.indexOf(marker)
        if (idx < 0) return raw.ifBlank { null }
        val afterBase = raw.substring(idx + marker.length)
        val slash = afterBase.indexOf('/')
        if (slash < 0) return null
        val rest = afterBase.substring(slash + 1)
        return if (rest.isBlank()) null else "/$rest"
    }
}
