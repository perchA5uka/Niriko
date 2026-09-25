# 网络层迁移草案：Retrofit/OkHttp → Ktor Client

> 这些是**迁移草案**，不是最终代码。真正落地时需要在 KMP 工程中编译验证。
> 目标：把所有网络客户端放入 `commonMain`，Android 用 OkHttp/Android 引擎，iOS 用 Darwin 引擎。

## 1. 通用迁移映射

| OkHttp/Retrofit | Ktor |
|---|---|
| `OkHttpClient.Builder()` | `HttpClient(engine)` |
| `Interceptor` | `HttpRequestInterceptor` / `HttpSend` / Plugin |
| `Retrofit.Builder().baseUrl()` | `defaultRequest { url(...) }` |
| Retrofit Service interface | 手写 suspend 请求或保留接口风格封装 |
| `@GET` / `@POST` / `@Query` | `client.get(url) { parameter(...) }` |
| `RequestBody` | `setBody(TextContent(...))` / `ByteArrayContent` |
| `response.body?.string()` | `response.bodyAsText()` |
| `response.code` | `response.status.value` |
| Basic Auth | `basicAuth` 插件或手动 `header("Authorization", ...)` |
| Logging | `Logging` 插件 |
| Timeout | `HttpTimeout` 插件 |

## 2. 平台引擎工厂

```kotlin
// commonMain
expect fun createHttpClient(): HttpClient

// androidMain
actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    // 复用现有 OkHttp 行为
    install(HttpTimeout) {
        connectTimeoutMillis = 8_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
}

// iosMain
actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        connectTimeoutMillis = 8_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
}
```

## 3. 公共 Json 配置

当前各客户端各自创建 `Json`，建议收敛为公共配置：

```kotlin
val NirikoJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
```

## 4. BangumiClient 草案

```kotlin
// commonMain
object BangumiClient {

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    @Volatile
    var authTokenProvider: (() -> AuthToken?)? = null

    val client: HttpClient by lazy {
        createHttpClient().config {
            // 对应 OkHttp userAgentInterceptor
            install(UserAgent) {
                agent = "Niriko/1.0.0 (KMP)"
            }
            // 对应 OkHttp authInterceptor / baseUrlInterceptor
            install(DefaultRequest) {
                url(baseUrl)
                authTokenProvider?.invoke()?.let { token ->
                    header("Authorization", "${token.tokenType.ifBlank { "Bearer" }} ${token.accessToken}")
                }
            }
        }
    }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): String {
        val url = buildUrl(path, query)
        return client.get(url).bodyAsText()
    }
}
```

> 具体动态 baseUrl 重写、官方域/auth 专用客户端等，需要在 Ktor 中通过请求拦截器实现，保持现有行为。

## 5. WebDavClient 草案

```kotlin
class WebDavClient(
    private val client: HttpClient = createHttpClient(),
) {
    private fun authHeader(user: String, pass: String): String =
        "Basic " + encodeBase64("$user:$pass".encodeToByteArray())

    suspend fun put(url: String, content: String, user: String, pass: String): Boolean =
        request(url, user, pass, "PUT", content) { it.value in 200..299 }

    suspend fun get(url: String, user: String, pass: String): String? =
        try {
            client.get(url) {
                header("Authorization", authHeader(user, pass))
            }.bodyAsText()
        } catch (e: Exception) {
            null
        }

    suspend fun mkcol(url: String, user: String, pass: String): Boolean =
        request(url, user, pass, "MKCOL", null) { it.value in 200..299 || it.value == 405 }

    suspend fun delete(url: String, user: String, pass: String): Boolean =
        request(url, user, pass, "DELETE", null) { it.value in 200..299 || it.value == 404 }

    private suspend fun request(
        url: String,
        user: String,
        pass: String,
        method: String,
        body: String?,
        isSuccess: (HttpStatusCode) -> Boolean,
    ): Boolean = try {
        val response = client.request(url) {
            this.method = HttpMethod(method)
            header("Authorization", authHeader(user, pass))
            if (body != null) setBody(TextContent(body, ContentType.Application.Json))
        }
        isSuccess(response.status)
    } catch (e: Exception) {
        false
    }
}
```

> 需要额外引入 Base64 工具（Kotlin/Native 没有 `java.util.Base64`），可用 `io.ktor.util.encodeBase64()` 或自定义实现。

## 6. SteamOpenIdClient 草案

当前使用 `java.net.URLEncoder/URLDecoder`，Kotlin/Native 不支持，需要替换。

```kotlin
// 草案：用简单自定义 percent-encoding 替代 URLEncoder
private fun encodeUrlComponent(value: String): String =
    buildString {
        value.forEach { c ->
            when {
                c.isLetterOrDigit() -> append(c)
                c == '-' || c == '_' || c == '.' || c == '~' -> append(c)
                else -> append('%').append(c.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

private fun decodeUrlComponent(value: String): String {
    // 建议用 Ktor 的 decodeURLQueryComponent，或实现严格解码
    return value.replace('+', ' ')
}
```

## 7. 迁移验收点

- [ ] 所有网络请求在 Android 上跑现有单元测试
- [ ] 动态 baseUrl 切换后请求路径正确
- [ ] Bangumi 官方域 auth 与反代域请求隔离正确
- [ ] Steam/VNDB/AniList/Bilibili 请求与响应解析一致
- [ ] WebDAV 同步在双端行为一致
