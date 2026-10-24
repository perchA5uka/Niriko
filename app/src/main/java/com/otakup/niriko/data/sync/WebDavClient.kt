package com.otakup.niriko.data.sync

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

private const val TAG = "WebDavClient"
private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * 远端文件元信息（A-D4）。
 *
 * 服务器支持并发检测时以 [etag] 为准；轻量 WebDAV（如部分坚果云接口）不回 ETag 时
 * 退化为 [lastModified] + [length] 的弱校验。
 */
data class RemoteMeta(
    val etag: String? = null,
    val lastModified: String? = null,
    val length: Long? = null,
)

/** GET 的响应体与元信息（A-D4 起随结果一起返回，避免再发一次 HEAD）。 */
data class RemoteContent(
    val body: String,
    val meta: RemoteMeta,
)

/**
 * 基于 OkHttp 的轻量 WebDAV 客户端。
 * 支持 MKCOL / PUT / HEAD / GET / DELETE，全部返回 [WebDavResult]（A-D5）。
 *
 * 改造前 get 把 401/5xx/超时一律折成 null，mkcol 的返回值被调用方丢弃，
 * 导致「登录失效」和「远端没有数据」不可区分；现在失败被分类而不是被吞。
 * 零第三方依赖（复用项目已有的 OkHttp）。
 */
class WebDavClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 创建远程目录。已存在（405）视为成功。 */
    suspend fun mkcol(url: String, user: String, pass: String): WebDavResult<Unit> =
        withResponse(url, user, pass, "MKCOL", null) { response ->
            // 405 = 目录已存在，也算成功（白名单在 WebDavStatusPolicy 里，有纯单测）
            if (WebDavStatusPolicy.mkcolOk(response.code)) WebDavResult.Ok(Unit) else failure(response)
        }

    /**
     * PUT 上传字符串内容。
     *
     * @param ifMatch 非空时带 `If-Match`，服务器发现远端已变会回 412，
     *                由 [SyncManager] 决定自动重合并还是报冲突。
     */
    suspend fun put(
        url: String,
        content: String,
        user: String,
        pass: String,
        ifMatch: String? = null,
    ): WebDavResult<RemoteMeta> {
        val body = content.toRequestBody(JSON_MEDIA)
        return withResponse(url, user, pass, "PUT", body, ifMatch) { response ->
            when {
                // 412 是「并发冲突」，语义上不是普通 4xx：不能降级成 HTTP_4XX，
                // 否则上层会把「远端已更新」当成「请求被拒」，自动合并逻辑无处触发。
                response.code == 412 -> WebDavResult.Failure(SyncFailureKind.CONFLICT, 412)
                WebDavStatusPolicy.putOk(response.code) -> WebDavResult.Ok(response.meta())
                else -> failure(response)
            }
        }
    }

    /** GET 下载文件内容与元信息。404 返回 [WebDavResult.NotFound]（不是失败）。 */
    suspend fun get(url: String, user: String, pass: String): WebDavResult<RemoteContent> =
        withResponse(url, user, pass, "GET", null) { response ->
            when {
                response.code == 404 -> WebDavResult.NotFound
                response.code in 200..299 -> {
                    // meta 要在读 body 前取：string() 会关闭 body
                    val meta = response.meta()
                    WebDavResult.Ok(RemoteContent(response.body?.string().orEmpty(), meta))
                }
                else -> failure(response)
            }
        }

    /** DELETE 删除远程文件。不存在也视为成功。 */
    suspend fun delete(url: String, user: String, pass: String): WebDavResult<Unit> =
        withResponse(url, user, pass, "DELETE", null) { response ->
            when {
                response.code == 404 -> WebDavResult.NotFound
                WebDavStatusPolicy.deleteOk(response.code) -> WebDavResult.Ok(Unit)
                else -> failure(response)
            }
        }

    /**
     * 检查远程文件是否存在并取元信息（HEAD，避免下载整个文件）。
     *
     * 改造前这个方法全仓 0 调用；A-D4 用它拿 ETag / Last-Modified 做条件写入判断。
     */
    suspend fun exists(url: String, user: String, pass: String): WebDavResult<RemoteMeta> =
        withResponse(url, user, pass, "HEAD", null) { response ->
            when {
                response.code == 404 -> WebDavResult.NotFound
                response.code in 200..299 -> WebDavResult.Ok(response.meta())
                else -> failure(response)
            }
        }

    /** 发送带 Basic 认证的请求：只负责「发出 + 关闭 + 异常分类」，状态码语义交给调用方。 */
    private suspend fun <T> withResponse(
        url: String,
        user: String,
        pass: String,
        method: String,
        body: RequestBody?,
        ifMatch: String? = null,
        block: (Response) -> WebDavResult<T>,
    ): WebDavResult<T> {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(user, pass))
            if (ifMatch != null) builder.header("If-Match", ifMatch)
            if (body != null) builder.method(method, body) else builder.method(method, null)
            client.newCall(builder.build()).execute().use { response -> block(response) }
        } catch (e: Exception) {
            // 不再把异常吞成 false：超时 / 断网 / 未知异常各自归类，上层据此给不同文案
            Log.e(TAG, "$method failed: $url", e)
            WebDavResult.Failure(
                kind = SyncFailureClassifier.classifyException(e),
                code = null,
                detail = e.message?.take(120),
            )
        }
    }

    private fun failure(response: Response): WebDavResult.Failure = WebDavResult.Failure(
        kind = SyncFailureClassifier.classify(response.code) ?: SyncFailureKind.UNKNOWN,
        code = response.code,
        detail = response.message.take(120),
    )

    private fun Response.meta(): RemoteMeta = RemoteMeta(
        etag = header("ETag"),
        lastModified = header("Last-Modified"),
        length = header("Content-Length")?.toLongOrNull(),
    )
}