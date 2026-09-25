package com.otakup.niriko.data.sync

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "WebDavClient"
private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * 基于 OkHttp 的轻量 WebDAV 客户端。
 * 支持 MKCOL / PUT / GET / DELETE。
 * 零第三方依赖（复用项目已有的 OkHttp）。
 */
class WebDavClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 创建远程目录。返回 true 如果已存在或创建成功。 */
    suspend fun mkcol(url: String, user: String, pass: String): Boolean {
        return execute(url, user, pass, "MKCOL", null) { code ->
            code in 200..299 || code == 405 // 405 = 目录已存在
        }
    }

    /** PUT 上传字符串内容。 */
    suspend fun put(url: String, content: String, user: String, pass: String): Boolean {
        val body = content.toRequestBody(JSON_MEDIA)
        return execute(url, user, pass, "PUT", body) { code ->
            code in 200..299
        }
    }

    /** GET 下载文件内容。文件不存在或失败返回 null。 */
    suspend fun get(url: String, user: String, pass: String): String? {
        return try {
            val auth = Credentials.basic(user, pass)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", auth)
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            Log.e(TAG, "GET failed: $url", e)
            null
        }
    }

    /** DELETE 删除远程文件。不存在也视为成功。 */
    suspend fun delete(url: String, user: String, pass: String): Boolean {
        return execute(url, user, pass, "DELETE", null) { code ->
            code in 200..299 || code == 404
        }
    }

    /** 检查远程文件是否存在（用 HEAD 避免下载整个文件）。 */
    suspend fun exists(url: String, user: String, pass: String): Boolean {
        return execute(url, user, pass, "HEAD", null) { code ->
            code in 200..299
        }
    }

    /** 发送带 Basic 认证的 HTTP 请求，根据状态码判断成功与否。 */
    private suspend fun execute(
        url: String,
        user: String,
        pass: String,
        method: String,
        body: okhttp3.RequestBody?,
        isSuccess: (code: Int) -> Boolean,
    ): Boolean {
        return try {
            val auth = Credentials.basic(user, pass)
            var builder = Request.Builder()
                .url(url)
                .header("Authorization", auth)
            builder = when {
                body != null -> builder.method(method, body)
                method == "MKCOL" || method == "DELETE" -> builder.method(method, null)
                else -> builder.method(method, null)
            }
            val response = client.newCall(builder.build()).execute()
            isSuccess(response.code)
        } catch (e: Exception) {
            Log.e(TAG, "$method failed: $url", e)
            false
        }
    }
}
