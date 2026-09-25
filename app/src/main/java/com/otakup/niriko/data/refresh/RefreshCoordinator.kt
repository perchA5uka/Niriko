package com.otakup.niriko.data.refresh

import com.otakup.niriko.util.AsyncSingleFlight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 一次刷新请求的结果。 */
sealed interface RefreshOutcome<out T> {

    /**
     * 没有发起请求：缓存仍然新鲜，或正处于失败退避窗口。
     * 调用方应当直接使用自己手上的缓存。
     */
    data class Skipped(val decision: RefreshDecision) : RefreshOutcome<Nothing>

    /** 刷新成功，[value] 是刚拉到的数据（已记入新鲜度）。 */
    data class Success<T>(val value: T) : RefreshOutcome<T>

    /** 刷新失败（已记入退避梯）。调用方应回退缓存；[error] 供 UI 展示原因。 */
    data class Failure(val error: Throwable) : RefreshOutcome<Nothing>
}

/**
 * 刷新编排器 —— 本项目所有「要不要刷新 / 刷新几次 / 失败了怎么办」的唯一裁决处。
 *
 * 改造前各处的做法是「一个裸 Boolean + 一段 runCatching」：不知道缓存新不新鲜、
 * 并发触发几次就打几次接口、失败后下次触发立刻重打、失败原因全部吞掉。
 * 这里把四件事收口：
 *
 * 1. **新鲜度**（[FreshnessDecider] + [FreshnessStore]）——命中软 TTL 直接跳过，
 *    硬过期才强制重拉；状态**持久化**，冷启动不再每次都全量重跑。
 * 2. **去重**（[AsyncSingleFlight]）——同 key 的并发请求合并成一次，
 *    后来者搭车（并在执行中又收到请求时补跑一轮）。
 * 3. **退避**（[RetryLadder]）——失败后 30s/60s/120s/300s 内不再自动重试。
 * 4. **可观测**（[runningKeys] / [snapshots]）——UI 能显示「刷新中 / 上次更新 / 失败可重试」。
 *
 * 不负责的事：HTTP、缓存内容本身、UI。它只回答「现在该不该发这次请求」并保证发得不多不少。
 *
 * @param clock 可注入时钟（单测用）
 */
class RefreshCoordinator(
    private val store: FreshnessSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val singleFlight = AsyncSingleFlight()

    /** 所有具名资源的持久化新鲜度/退避状态。 */
    val snapshots: StateFlow<Map<String, FreshnessSnapshot>> get() = store.snapshots

    // region 运行中状态（UI 观察）

    private val runningMutex = Mutex()
    private val runningCounts = mutableMapOf<String, Int>()
    private val _runningKeys = MutableStateFlow<Set<String>>(emptySet())

    /** 当前正在刷新的资源 key 集合（供下拉指示器 / 诊断）。 */
    val runningKeys: StateFlow<Set<String>> = _runningKeys.asStateFlow()

    fun isRunning(key: String): Boolean = key in _runningKeys.value

    private suspend fun enter(key: String) = runningMutex.withLock {
        val next = (runningCounts[key] ?: 0) + 1
        runningCounts[key] = next
        if (next == 1) _runningKeys.value = _runningKeys.value + key
    }

    private suspend fun exit(key: String) = runningMutex.withLock {
        val next = (runningCounts[key] ?: 1) - 1
        if (next <= 0) {
            runningCounts.remove(key)
            _runningKeys.value = _runningKeys.value - key
        } else {
            runningCounts[key] = next
        }
    }

    // endregion

    // region 判定与状态

    /** 只判定，不发请求。 */
    suspend fun decide(
        key: String,
        policy: RefreshResource,
        force: Boolean = false,
    ): RefreshDecision = FreshnessDecider.decide(policy, store.snapshot(key), clock(), force)

    suspend fun snapshot(key: String): FreshnessSnapshot? = store.snapshot(key)

    /** 清除某 key 的新鲜度（强制下次必刷；手动「强制刷新」入口用）。 */
    suspend fun reset(key: String) = store.reset(key)

    /** 清除全部新鲜度与退避记录：下一次刷新会重新拉取所有资源。 */
    suspend fun resetAll() = store.resetAll()

    /** 供「不经过 [refresh] 的成功路径」（如手动同步成功）登记时间。 */
    suspend fun recordSuccess(key: String, now: Long = clock()): FreshnessSnapshot =
        store.recordSuccess(key, now)

    /** 供「不经过 [refresh] 的失败路径」登记退避。 */
    suspend fun recordFailure(
        key: String,
        error: String?,
        now: Long = clock(),
    ): FreshnessSnapshot = store.recordFailure(key, now, error)

    // endregion

    /**
     * 受控刷新。
     *
     * @param force 用户显式要求（下拉刷新 / 手动重试）：绕过 TTL 与退避。
     *              用户主动操作时「什么都不做」是最差反馈，因此即便处于退避窗口也会真的打一次接口。
     * @return [RefreshOutcome.Skipped] 表示不该发请求（调用方直接用缓存）；
     *         [RefreshOutcome.Success] 返回新数据；[RefreshOutcome.Failure] 表示失败并已记退避。
     */
    suspend fun <T : Any> refresh(
        key: String,
        policy: RefreshResource,
        force: Boolean = false,
        block: suspend () -> T,
    ): RefreshOutcome<T> {
        val decision = FreshnessDecider.decide(policy, store.snapshot(key), clock(), force)
        if (decision == RefreshDecision.FRESH || decision == RefreshDecision.BACKOFF) {
            return RefreshOutcome.Skipped(decision)
        }

        enter(key)
        try {
            val box = singleFlight.run(key) {
                // 搭车者进来时 owner 可能刚好刷完 → 重新判定，避免紧接着又刷一遍
                val inner = FreshnessDecider.decide(policy, store.snapshot(key), clock(), force)
                if (inner == RefreshDecision.FRESH || inner == RefreshDecision.BACKOFF) {
                    Box<T>(skipped = inner)
                } else {
                    try {
                        val value = block()
                        store.recordSuccess(key, clock())
                        Box(value = value)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        store.recordFailure(key, clock(), e.message ?: e::class.java.simpleName)
                        Box(error = e)
                    }
                }
            }
            return when {
                box.skipped != null -> RefreshOutcome.Skipped(box.skipped)
                box.error != null -> RefreshOutcome.Failure(box.error)
                else -> RefreshOutcome.Success(box.value as T)
            }
        } finally {
            // 协程被取消时也要归还计数，否则该 key 会永远显示「刷新中」
            withContext(NonCancellable) { exit(key) }
        }
    }

    /** [refresh] 的便捷形态：跳过与失败都折叠成 null，调用方直接回退缓存。 */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> refreshOrNull(
        key: String,
        policy: RefreshResource,
        force: Boolean = false,
        block: suspend () -> T,
    ): T? = (refresh(key, policy, force, block) as? RefreshOutcome.Success<T>)?.value

    private class Box<T>(
        val value: T? = null,
        val error: Throwable? = null,
        val skipped: RefreshDecision? = null,
    )
}
