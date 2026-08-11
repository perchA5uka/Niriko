package com.otakup.niriko.data.model

/**
 * 作品卡片展示模型。
 * UI 只依赖此模型，不依赖 SubjectType / SubjectEntity。
 */
data class SubjectCardDisplayModel(
    val cover: String?,
    val primaryTitle: String,
    val secondaryTitle: String?,
    val typeLabel: String,
    val ratingText: String?,
    val secondaryInfo: String?,
    val description: String?,
    /** Steam 补充信息（已绑定游戏卡显示，如 "¥298"）。 */
    val steamInfoText: String? = null,
)
