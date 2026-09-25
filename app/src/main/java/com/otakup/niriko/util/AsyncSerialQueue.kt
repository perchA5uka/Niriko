package com.otakup.niriko.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 异步串行队列：所有任务按提交顺序**逐个**执行。
 *
 * 参考 Kazumi 的 `AsyncSerialQueue`（`WebDav._webDavOperationQueue`）。
 * 用途：把对同一远端资源的「读-改-写」串行化，避免两个同步/刷新同时跑导致
 * 后一次基于过期快照覆盖前一次结果（WebDAV 上传、Bangumi 同步、榜单落库等）。
 *
 * 与 [AsyncSingleFlight] 的分工：
 * - single-flight 解决「同一件事被并发请求多次」→ 合并成一次；
 * - serial queue 解决「不同的事必须排队」→ 顺序执行，不能合并。
 */
class AsyncSerialQueue {

    private val mutex = Mutex()

    /** 排队执行 [block]（FIFO：先到先得，后来者等前一个结束）。 */
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }

    /** 当前是否有任务在执行。 */
    val isBusy: Boolean get() = mutex.isLocked
}
