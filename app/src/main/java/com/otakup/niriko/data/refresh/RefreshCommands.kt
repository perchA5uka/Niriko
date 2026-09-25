package com.otakup.niriko.data.refresh

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 诊断页下发的刷新指令。
 *
 * 应用级资源（Steam 排行榜预取 / Steam 自动匹配）由 `NirikoApplication` 持有，
 * 设置页拿不到它的引用也不该拿；这里用一条指令流把「用户要求强制刷新」传过去，
 * 与 [AppForegroundSignals] 是同一套模式。
 */
object RefreshCommands {

    private val _appForceRefresh = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 请求强制刷新所有应用级资源（绕过新鲜度与退避）。 */
    val appForceRefresh: SharedFlow<Unit> = _appForceRefresh.asSharedFlow()

    fun requestAppForceRefresh() {
        _appForceRefresh.tryEmit(Unit)
    }
}
