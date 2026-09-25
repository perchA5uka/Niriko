package com.otakup.niriko.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 单次飞行（single-flight）：同一 key 的并发调用共享同一次执行的结果。
 *
 * 参考 Kazumi 的 `AsyncSingleFlight`（`WebDav._historySyncSingleFlight`）。
 * 刷新不是「加锁后各跑一遍」，而是「后来者搭车」——发现页下拉、重试按钮、切类型、
 * 进前台可能在同一瞬间触发同一次刷新；没有它时它们会各自打一遍远端接口
 * （本项目「刷新风暴」的成因之一）。
 *
 * **与 AniShelf `SyncGate` 的差异（有意为之）**：`SyncGate` 在「执行期间又来请求」时会补跑一轮
 * （`consumeRerunRequest`）。那是因为它同步的是**本地写**——一轮 pass 从旧快照开始，
 * pass 期间的新修改必须补一轮才不会丢。本项目这里只做**只读刷新**：并发请求要的是
 * 「最新的远端数据」，正在飞的那一次返回的就是最新数据，补跑只会让网络开销翻倍。
 * 因此这里只合并、不补跑。
 *
 * 取消语义：owner 被取消时（例如它所属的页面退出了），搭车者**不会**跟着失败，
 * 而是接管成为新的 owner 重跑一次，避免「A 页面退出把 B 页面的刷新一起带崩」。
 */
class AsyncSingleFlight {

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Result<Any?>>>()

    /** 当前正在执行的 key 集合（诊断用）。 */
    suspend fun runningKeys(): Set<String> = mutex.withLock { inFlight.keys.toSet() }

    /** 同一 key 是否正在执行中。 */
    suspend fun isRunning(key: String): Boolean = mutex.withLock { inFlight.containsKey(key) }

    /**
     * 执行 [block]，同一 [key] 的并发调用合并为一次。
     *
     * @param maxTakeovers owner 被取消时允许搭车者接管重跑的最大次数（防极端活锁）
     */
    suspend fun <T> run(key: String, maxTakeovers: Int = 3, block: suspend () -> T): T {
        var takeovers = 0
        while (true) {
            val (cell, isOwner) = mutex.withLock {
                val existing = inFlight[key]
                if (existing != null) {
                    existing to false
                } else {
                    CompletableDeferred<Result<Any?>>().also { inFlight[key] = it } to true
                }
            }

            if (!isOwner) {
                val outcome = cell.await()
                val error = outcome.exceptionOrNull()
                if (error is CancellationException && takeovers < maxTakeovers) {
                    // owner 被取消 → 自己接管重跑，不让搭车者跟着失败
                    takeovers++
                    continue
                }
                @Suppress("UNCHECKED_CAST")
                return outcome.getOrThrow() as T
            }

            var outcome: Result<Any?>? = null
            try {
                val value = block()
                outcome = Result.success(value)
                @Suppress("UNCHECKED_CAST")
                return value as T
            } catch (e: Throwable) {
                // 含 CancellationException：也要登记给搭车者，让它们能接管重跑
                outcome = Result.failure(e)
                throw e
            } finally {
                // 即使本协程已被取消也要清理登记，否则该 key 会被永久卡住
                withContext(NonCancellable) {
                    mutex.withLock { if (inFlight[key] === cell) inFlight.remove(key) }
                }
                outcome?.let { cell.complete(it) }
            }
        }
    }
}
