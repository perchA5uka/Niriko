package com.otakup.niriko.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.URI

/**
 * 按源限流的统一入口（计划 B4 · 4-12）。
 *
 * 全进程共享一个 [SourceRateLimiter]，所有出网点都从这里过一道，
 * 避免各客户端各自为政地打同一个源。
 */
object SourceRateLimits {

    private val limiter = SourceRateLimiter()

    /** 按 URL 限流（挂起，命中规则时等待）。 */
    suspend fun awaitUrl(url: String) {
        val host = runCatching { URI(url).host }.getOrNull() ?: return
        awaitHost(host)
    }

    /** 按 host 限流（挂起）。 */
    suspend fun awaitHost(host: String?) {
        if (host.isNullOrBlank()) return
        val wait = limiter.acquire(host, System.currentTimeMillis())
        if (wait > 0L) delay(wait)
    }

    /**
     * 同步版：给 OkHttp 拦截器这类非挂起上下文用。
     * 只在超限时阻塞调用线程（拦截器跑在 OkHttp 的 IO 线程池，不是主线程）。
     */
    fun awaitHostBlocking(host: String?) {
        if (host.isNullOrBlank()) return
        val wait = limiter.acquire(host, System.currentTimeMillis())
        if (wait > 0L) runBlocking { delay(wait) }
    }
}
