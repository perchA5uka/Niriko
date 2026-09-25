package com.otakup.niriko.util

/**
 * 极简 LRU + TTL 内存缓存。
 *
 * 用途：把「一次页面打开会并行打十几个接口」的结果按条目短时缓存，
 * 用户反复进出同一页面时不再重复拉取。参考 Kazumi 规则目录的
 * `_pluginCatalogMaxAge`（新鲜度窗口 + 显式绕过）与 AniShelf 的 actor 级 memo。
 *
 * 线程安全（synchronized），访问序 LRU（`accessOrder = true`），容量有上限。
 * 缓存是**进程内**的：进程重启即失效，这是有意的——它只用来削掉「同一次使用里的重复请求」，
 * 跨进程的长时新鲜度由各资源自己的持久化时间戳负责。
 */
class TtlCache<K : Any, V : Any>(
    private val ttlMs: Long,
    private val maxEntries: Int = 16,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val map = object : LinkedHashMap<K, Pair<V, Long>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Pair<V, Long>>): Boolean =
            size > maxEntries
    }

    /** 命中且未过期才返回；过期条目顺带清除。 */
    fun get(key: K): V? = synchronized(map) {
        val entry = map[key] ?: return null
        if (clock() - entry.second >= ttlMs) {
            map.remove(key)
            return null
        }
        entry.first
    }

    fun put(key: K, value: V) = synchronized(map) {
        map[key] = value to clock()
    }

    fun invalidate(key: K) = synchronized(map) { map.remove(key) }

    fun clear() = synchronized(map) { map.clear() }

    fun size(): Int = synchronized(map) { map.size }
}
