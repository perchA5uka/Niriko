package com.otakup.niriko.viewmodel

import com.otakup.niriko.data.model.WatchStatus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Ephemeral feedback for successful local actions, never for observed records. */
internal class CompletionEvents {
    private val mutableEvents = MutableSharedFlow<Long>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = mutableEvents.asSharedFlow()

    /**
     * F14：**进度写满**的独立通道（波浪点亮用）。
     *
     * 与 [events]（状态改为看过 → 全屏庆祝）分开：两者的视觉完全不同，
     * 而且一次操作可能同时满足两者（把最后一集标为看过、状态也从「在看」变「看过」），
     * 分开通道才能让它们各自决定播不播，而不会互相顶掉（SharedFlow 的 extraBufferCapacity 只有 1）。
     */
    private val mutableProgressEvents = MutableSharedFlow<Long>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val progressEvents = mutableProgressEvents.asSharedFlow()

    fun onStatusWritten(subjectId: Long, before: WatchStatus, after: WatchStatus, succeeded: Boolean) {
        if (succeeded && before != WatchStatus.COMPLETED && after == WatchStatus.COMPLETED) {
            mutableEvents.tryEmit(subjectId)
        }
    }

    /**
     * 一次进度写入之后的边沿判定（F14）。
     *
     * 判据在 [com.otakup.niriko.util.EpisodeCompletionPolicy]（纯函数、有单测）：
     * 只有「这次写入**跨过**了总量」才发事件，因此初次加载、重复保存同一份进度都不会触发。
     */
    fun onProgressWritten(
        subjectId: Long,
        before: com.otakup.niriko.util.EpisodeCompletionPolicy.ProgressSnapshot,
        after: com.otakup.niriko.util.EpisodeCompletionPolicy.ProgressSnapshot,
        type: com.otakup.niriko.data.model.SubjectType? = null,
        succeeded: Boolean = true,
    ) {
        if (!succeeded) return
        if (com.otakup.niriko.util.EpisodeCompletionPolicy.shouldCelebrate(before, after, type)) {
            mutableProgressEvents.tryEmit(subjectId)
        }
    }
}
