package com.otakup.niriko.data.sync.bangumi

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.remote.bangumi.dto.UserCollectionDto

/** 冲突优先级:本地优先 / Bangumi 优先。 */
enum class BangumiSyncPriority {
    LOCAL_FIRST,
    BANGUMI_FIRST,
}

/** 本地有、远程无 → 需上传到 Bangumi。 */
data class LocalUpload(
    val subjectId: Long,
    /** Bangumi 官方 CollectionType(1-5)。 */
    val type: Int,
    val local: CollectionEntity,
)

/** 远程有、本地无 → 需落库。 */
data class RemoteDownload(
    val subjectId: Long,
    val remote: UserCollectionDto,
    /** 已换算为本地 WatchStatus(不可能为 null,见 Planner 过滤)。 */
    val status: com.otakup.niriko.data.model.WatchStatus,
)

/** 双方都有且状态不一致 → 按优先级裁定。 */
data class Conflict(
    val subjectId: Long,
    val local: CollectionEntity,
    val remote: UserCollectionDto,
    val localStatus: com.otakup.niriko.data.model.WatchStatus,
    val remoteStatus: com.otakup.niriko.data.model.WatchStatus,
)

/**
 * 合并计划(纯数据)。match=无需操作的一致条目数。
 */
data class BangumiSyncPlan(
    val localOnly: List<LocalUpload>,
    val remoteOnly: List<RemoteDownload>,
    val conflict: List<Conflict>,
    val match: Int,
) {
    val totalOperations: Int
        get() = localOnly.size + remoteOnly.size + conflict.size
}