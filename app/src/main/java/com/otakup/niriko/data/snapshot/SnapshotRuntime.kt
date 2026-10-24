package com.otakup.niriko.data.snapshot

import android.content.Context
import android.util.Log

/**
 * 快照的进程级入口（C-3 钩子）。
 *
 * 为什么用单例而不是给 SyncManager/BackupManager 加 Context 参数：
 * 这两个类已经有 5–6 个构造参数（含可空 DataStore 以便单测），再加一个 Context 会波及
 * 所有调用点与单测。这里只在 `NirikoApplication.onCreate` 装一次，
 * 破坏性操作处调一行 [preflight]；未安装时（单测/预览）静默跳过，**绝不阻断业务**。
 */
object SnapshotRuntime {

    private const val TAG = "NirikoSnapshot"

    @Volatile
    private var store: SnapshotStore? = null

    /** 进程启动时调用一次；重复安装会覆盖（幂等）。 */
    fun install(context: Context) {
        store = SnapshotStore(context.applicationContext)
    }

    /**
     * 破坏性操作（同步/导入/批量）之前调用。
     *
     * 失败只记日志：快照是「后悔药」，不能因为它写不出来就阻断用户正在做的事。
     */
    fun preflight(json: String?, tag: String) {
        val s = store ?: return
        if (json.isNullOrBlank()) return
        runCatching { s.write(json, tag) }
            .onFailure { Log.w(TAG, "快照失败（已忽略）：$tag", it) }
    }

    /** 现有快照（未安装时为空）。 */
    fun list(): List<SnapshotFile> = store?.list().orEmpty()

    /** 按策略清理；返回删除数量。 */
    fun prune(): Int = runCatching { store?.prune() ?: 0 }.getOrDefault(0)

    fun totalBytes(): Long = runCatching { store?.totalBytes() ?: 0L }.getOrDefault(0L)

    fun isInstalled(): Boolean = store != null
}
