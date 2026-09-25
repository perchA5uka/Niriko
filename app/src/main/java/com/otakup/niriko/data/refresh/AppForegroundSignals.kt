package com.otakup.niriko.data.refresh

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 应用级「回到前台」信号。
 *
 * 为什么用信号而不是让 Application 直接调 ViewModel：刷新逻辑属于各自的 ViewModel
 * （各自持有 uiState），Application 拿不到也不该拿它们的引用。
 * 这里只广播「刚回到前台」这一事实，由各自的 ViewModel 决定要不要刷 —— 且它们都会
 * 经 [RefreshCoordinator] 判定，因此「回到前台」本身不会造成重复请求。
 *
 * 用 SharedFlow（无重放）而非 StateFlow：每次回到前台都是一次独立事件。
 */
object AppForegroundSignals {

    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 冷启动完成与每次回到前台各发一次。 */
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyForeground() {
        _events.tryEmit(Unit)
    }
}
