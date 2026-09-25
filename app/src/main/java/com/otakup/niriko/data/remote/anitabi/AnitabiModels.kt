package com.otakup.niriko.data.remote.anitabi

import kotlinx.serialization.Serializable

/** Anitabi.cn 取景地标响应（阶段 K）。 */
@Serializable
data class AnitabiResponse(
    val id: Long? = null,
    val city: String = "",
    val title: String? = null,
    val cn: String? = null,
    val color: String? = null,
    val cover: String? = null,
    val imagesLength: Int = 0,
    val pointsLength: Int = 0,
    val zoom: Float? = null,
    val litePoints: List<AnitabiLitePoint> = emptyList(),
)

/** 单个地标（截图 + 信息）。 */
@Serializable
data class AnitabiLitePoint(
    val id: String? = null,
    val cn: String? = null,
    val name: String? = null,
    val ep: Int? = null,
    /** 代表 ep 中的秒。 */
    val s: Long? = null,
    val image: String? = null,
)
