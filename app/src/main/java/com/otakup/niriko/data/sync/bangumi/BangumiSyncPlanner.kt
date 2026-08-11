package com.otakup.niriko.data.sync.bangumi

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.remote.bangumi.dto.UserCollectionDto
import com.otakup.niriko.data.sync.bangumi.BangumiStatusMapper.toBangumiType
import com.otakup.niriko.data.sync.bangumi.BangumiStatusMapper.toWatchStatus

/**
 * 合并计划纯函数(参照 Kazumi lib/modules/collect/collect_sync_merger.dart 的 planBangumi)。
 *
 * 规则:
 * - 本地有、远程无 → localOnly(上传);
 * - 远程有、本地无 → remoteOnly(落库,仅收录状态可映射的条目);
 * - 双方都有且状态不同 → conflict,由 [BangumiSyncPriority] 裁定方向;
 * - 状态一致 → match,无需操作。
 * 注意:本期不做删除同步(远端已删的本地条目保留),与 Kazumi 语义一致。
 */
object BangumiSyncPlanner {

    fun plan(
        local: List<CollectionEntity>,
        remote: List<UserCollectionDto>,
        priority: BangumiSyncPriority,
    ): BangumiSyncPlan {
        val localBySubjectId = local.associateBy { it.subjectId }

        // 仅收录能映射到本地状态且非空 subject 的远端条目(Kazumi 同款过滤)。
        val remoteBySubjectId = remote.mapNotNull { dto ->
            val status = dto.type.toWatchStatus() ?: return@mapNotNull null
            dto.subjectId to (dto to status)
        }.toMap()

        val localIds = localBySubjectId.keys.toSet()
        val remoteIds = remoteBySubjectId.keys.toSet()

        val localOnly = (localIds - remoteIds).mapNotNull { id ->
            localBySubjectId[id]?.let { LocalUpload(id, it.status.toBangumiType(), it) }
        }.sortedBy { it.subjectId }

        val remoteOnly = (remoteIds - localIds).mapNotNull { id ->
            val (dto, status) = remoteBySubjectId.getValue(id)
            RemoteDownload(id, dto, status)
        }.sortedBy { it.subjectId }

        var match = 0
        val conflict = mutableListOf<Conflict>()
        for (id in localIds.intersect(remoteIds)) {
            val localEntity = localBySubjectId.getValue(id)
            val (remoteDto, remoteStatus) = remoteBySubjectId.getValue(id)
            if (remoteDto.type == localEntity.status.toBangumiType()) {
                match++
            } else {
                conflict += Conflict(
                    subjectId = id,
                    local = localEntity,
                    remote = remoteDto,
                    localStatus = localEntity.status,
                    remoteStatus = remoteStatus,
                )
            }
        }
        conflict.sortBy { it.subjectId }

        return BangumiSyncPlan(
            localOnly = localOnly,
            remoteOnly = remoteOnly,
            conflict = conflict,
            match = match,
        )
    }
}