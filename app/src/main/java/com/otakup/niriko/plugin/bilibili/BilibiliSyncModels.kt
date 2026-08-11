package com.otakup.niriko.plugin.bilibili

import com.otakup.niriko.data.local.entity.BilibiliSyncItemEntity
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.model.WatchStatus

/**
 * 哔哩哔哩导入桥接模型。
 *
 * 桥接 DTO 为 WebView 注入 JS postMessage 回传 JSON 的结构约定；
 * [BilibiliSyncPreview] 为预览/导入行模型（bili 原始数据 + 本地收藏对照）。
 */

// ==================== 桥接消息类型 ====================

/** 检查登录态（nav 接口）。 */
const val BILI_MSG_CHECK_LOGIN = "CHECK_LOGIN"

/** 已获取追番列表（follow/list 分页全量）。 */
const val BILI_MSG_GET_LIST = "GET_LIST"

/** 已获取全部用户评分/短评（pgc/review/user 逐个拉取）。 */
const val BILI_MSG_GET_REVIEW = "GET_REVIEW"

/** 评分/短评拉取进度（{done, total}，每完成一批上报一次）。 */
const val BILI_MSG_REVIEW_PROGRESS = "REVIEW_PROGRESS"

/** 注入脚本内部异常上报（data 为错误文本，用于排查拉取链断裂）。 */
const val BILI_MSG_DEBUG_ERROR = "DEBUG_ERROR"

// ==================== 追番列表条目 ====================

/**
 * follow/list 返回的单条追番记录（注入 JS 里精简后的字段）。
 * @param mediaId bilibili media_id（追番列表主键）
 * @param seasonId 季 ID（用于 site_map 反查 Bangumi 条目）
 * @param followStatus 1 想看 / 2 在看 / 3 看过
 * @param progress 「看到第 N 话」文本（如 "看到第1话 0:01"），由 JS 提取 N
 */
data class BilibiliFollowItem(
    val mediaId: Long,
    val seasonId: Int? = null,
    val title: String,
    val cover: String? = null,
    val followStatus: Int,
    val progress: Int? = null,
    val totalEpisodes: Int? = null,
)

/** pgc/review/user 返回的单条用户短评（short_review）。 */
data class BilibiliReview(
    val score: Float? = null,
    val content: String? = null,
)

/** 一次桥接完成后的全量结果（List + Reviews 合并视图）。 */
data class BilibiliBridgeResult(
    val loginName: String? = null,
    val items: List<BilibiliFollowItem> = emptyList(),
    /** media_id → 用户短评。 */
    val reviews: Map<Long, BilibiliReview> = emptyMap(),
)

// ==================== 预览行 ====================

/**
 * 导入预览行：bili 原始数据 + 本地对照 + 勾选态。
 * [bgmSubjectId] 为匹配结果（未匹配为 null，导入时跳过）。
 */
data class BilibiliSyncPreview(
    val mediaId: Long,
    val seasonId: Int? = null,
    val bgmSubjectId: Long? = null,
    val title: String,
    val cover: String? = null,
    val followStatus: Int,
    val progress: Int? = null,
    val totalEpisodes: Int? = null,
    val biliScore: Float? = null,
    val biliComment: String? = null,
    /** 本地已存在收藏（null 表示尚未收藏，可新建）。 */
    val localCollection: CollectionEntity? = null,
    /** 本地条目已匹配的标题（供 UI 展示）。 */
    val localSubjectTitle: String? = null,
    /** 勾选态（默认勾选已匹配项，UI 可改）。 */
    val selected: Boolean = false,
) {
    /** 未匹配 Ban's Ban'gumi 条目。 */
    val isMatched: Boolean get() = bgmSubjectId != null

    fun toEntity(importTime: Long = System.currentTimeMillis()): BilibiliSyncItemEntity =
        BilibiliSyncItemEntity(
            mediaId = mediaId.toLong(),
            seasonId = seasonId,
            bgmSubjectId = bgmSubjectId,
            title = title,
            cover = cover,
            followStatus = followStatus,
            progress = progress,
            totalEpisodes = totalEpisodes,
            biliScore = biliScore,
            biliComment = biliComment,
            imported = false,
            importTime = importTime,
        )
}

// ==================== 状态映射 ====================

/**
 * bilibili follow_status → Niriko WatchStatus。
 * 1 想看 → PLAN_TO_WATCH，2 在看 → WATCHING，3 看过 → COMPLETED；其余兜底想看。
 */
fun biliFollowStatusToWatchStatus(followStatus: Int): WatchStatus = when (followStatus) {
    1 -> WatchStatus.PLAN_TO_WATCH
    2 -> WatchStatus.WATCHING
    3 -> WatchStatus.COMPLETED
    else -> WatchStatus.PLAN_TO_WATCH
}