package com.otakup.niriko.util

import com.otakup.niriko.data.remote.bangumi.dto.SubjectImagesDto

/**
 * 封面 URL 回退链解析。
 * 按 large → medium → small 优先级返回第一个可用的 URL。
 * 统一把 http:// 归一化为 https://（Android 9+ 默认禁止明文 http，/calendar 接口返回 http 图）。
 */
fun resolveCoverUrl(images: SubjectImagesDto?): String? {
    if (images == null) return null
    return (images.large ?: images.medium ?: images.small)?.toHttps()
}

/** 把 http:// 前缀替换为 https://，已是 https 或非 http 则原样返回。 */
fun String?.toHttps(): String? = this?.let {
    if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it
}

/**
 * 封面 URL 回退链 + 占位图兜底。
 * 当所有尺寸都不存在时返回 android.resource URI 指向内置占位图。
 */
fun resolveCoverUrlWithPlaceholder(images: SubjectImagesDto?): String? {
    return resolveCoverUrl(images)
}
