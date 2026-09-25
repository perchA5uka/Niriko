package com.otakup.niriko.data.refresh

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 与其他 DataStore 分开：这里的键**不是用户设置**，因此既不进设置页、也不进 JSON 备份/WebDAV 同步
 * （同步一份「上次刷新时间」到别的设备没有意义，还会污染备份体积）。
 */
private val Context.refreshDataStore by preferencesDataStore(name = "niriko_refresh")

/**
 * 新鲜度 / 退避状态的读写接口。
 *
 * 抽成接口是为了让 [RefreshCoordinator] 可以在纯 JVM 单测里跑（Android DataStore 需要 Context）。
 */
interface FreshnessSource {

    /** 全部快照（UI 观察用）。 */
    val snapshots: StateFlow<Map<String, FreshnessSnapshot>>

    suspend fun snapshot(key: String): FreshnessSnapshot?

    /** 记录一次成功（清空失败计数与退避）。 */
    suspend fun recordSuccess(key: String, now: Long): FreshnessSnapshot

    /** 记录一次失败（累计失败计数并按退避梯设置 backoffUntil）。 */
    suspend fun recordFailure(key: String, now: Long, error: String?): FreshnessSnapshot

    /** 清除某个 key（例如手动「强制刷新」或数据被清空时）。 */
    suspend fun reset(key: String)

    /** 清除全部记录：下一次刷新会绕过所有新鲜度窗口与退避（诊断页「全部重置」）。 */
    suspend fun resetAll()
}

/**
 * DataStore 实现：整个 map 序列化成一个 JSON 字符串存在**单个** preference key 下。
 *
 * 之所以不是「一个 key 一条 preference」：这里的 key 是动态的（如 \`ranking:2\`），
 * Preferences 的键必须编译期常量；单 JSON 值也避免了每次写入都要读改写整个文件。
 * 键数量有界（只登记具名单例资源，见 [FreshnessSnapshot] 的说明），因此体积可控。
 */
class DataStoreFreshnessSource(private val context: Context) : FreshnessSource {

    private val dataKey = stringPreferencesKey("freshness_v1")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 显式 serializer：避免依赖 reified 扩展重载（不同 kotlinx.serialization 版本解析结果不一致）。 */
    private val mapSerializer = MapSerializer(String.serializer(), FreshnessSnapshot.serializer())

    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    private val _snapshots = MutableStateFlow<Map<String, FreshnessSnapshot>>(emptyMap())
    override val snapshots: StateFlow<Map<String, FreshnessSnapshot>> = _snapshots.asStateFlow()

    override suspend fun snapshot(key: String): FreshnessSnapshot? {
        ensureLoaded()
        return _snapshots.value[key]
    }

    override suspend fun recordSuccess(key: String, now: Long): FreshnessSnapshot =
        mutate(key) { it.afterSuccess(now) }

    override suspend fun recordFailure(key: String, now: Long, error: String?): FreshnessSnapshot =
        mutate(key) { it.afterFailure(now, error) }

    override suspend fun reset(key: String) = mutex.withLock {
        ensureLoadedLocked()
        if (!_snapshots.value.containsKey(key)) return@withLock
        val next = _snapshots.value - key
        _snapshots.value = next
        persist(next)
    }

    override suspend fun resetAll() = mutex.withLock {
        ensureLoadedLocked()
        if (_snapshots.value.isEmpty()) return@withLock
        _snapshots.value = emptyMap()
        persist(emptyMap())
    }

    private suspend fun mutate(
        key: String,
        transform: (FreshnessSnapshot) -> FreshnessSnapshot,
    ): FreshnessSnapshot = mutex.withLock {
        ensureLoadedLocked()
        val current = _snapshots.value[key] ?: FreshnessSnapshot()
        val updated = transform(current)
        val next = _snapshots.value + (key to updated)
        _snapshots.value = next
        persist(next)
        updated
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock { ensureLoadedLocked() }
    }

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        val parsed = runCatching {
            context.refreshDataStore.data.first()[dataKey]
        }.getOrNull()?.let { raw ->
            runCatching { json.decodeFromString(mapSerializer, raw) }.getOrNull()
        } ?: emptyMap()
        _snapshots.value = parsed
        loaded = true
    }

    private suspend fun persist(map: Map<String, FreshnessSnapshot>) {
        // 持久化失败不影响本次刷新（内存态已更新，退避在本次进程内仍然生效）
        runCatching {
            context.refreshDataStore.edit { prefs -> prefs[dataKey] = json.encodeToString(mapSerializer, map) }
        }
    }
}

/** 纯内存实现：供单元测试与「不想落盘」的场景使用。 */
class InMemoryFreshnessSource : FreshnessSource {

    private val mutex = Mutex()

    private val _snapshots = MutableStateFlow<Map<String, FreshnessSnapshot>>(emptyMap())
    override val snapshots: StateFlow<Map<String, FreshnessSnapshot>> = _snapshots.asStateFlow()

    override suspend fun snapshot(key: String): FreshnessSnapshot? = mutex.withLock {
        _snapshots.value[key]
    }

    override suspend fun recordSuccess(key: String, now: Long): FreshnessSnapshot = mutex.withLock {
        val updated = (_snapshots.value[key] ?: FreshnessSnapshot()).afterSuccess(now)
        _snapshots.value = _snapshots.value + (key to updated)
        updated
    }

    override suspend fun recordFailure(key: String, now: Long, error: String?): FreshnessSnapshot =
        mutex.withLock {
            val updated = (_snapshots.value[key] ?: FreshnessSnapshot()).afterFailure(now, error)
            _snapshots.value = _snapshots.value + (key to updated)
            updated
        }

    override suspend fun reset(key: String) = mutex.withLock {
        _snapshots.value = _snapshots.value - key
    }

    override suspend fun resetAll() = mutex.withLock {
        _snapshots.value = emptyMap()
    }
}
