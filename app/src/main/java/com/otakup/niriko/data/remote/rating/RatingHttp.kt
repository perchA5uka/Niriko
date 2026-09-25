package com.otakup.niriko.data.remote.rating

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 权威评分源的轻量 HTTP 层。
 *
 * 这些源（MusicBrainz / Discogs / Google Books / Open Library / OMDb / IGDB ...）
 * 大多只需要「取一个 JSON、读几个字段」，为此各建一套 Retrofit 接口属于过度工程，
 * 因此统一走这里：共享 OkHttpClient + kotlinx.serialization 的 JsonElement 手动取值。
 *
 * MusicBrainz 要求**必须带有效 User-Agent**；Discogs 要求 **token 查询参数**——
 * 都在各源里自行拼接。
 *
 * 所有方法都**不抛异常**：网络失败 / 非 2xx / 解析失败统一返回 null。
 */
object RatingHttp {

    private const val USER_AGENT = "Niriko/1.0.0 (https://github.com/perchA5uka/Niriko)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /** GET 并返回 JsonObject。 */
    suspend fun getJson(url: String, extraHeaders: Map<String, String> = emptyMap()): JsonObject? =
        requestRaw(url, extraHeaders, null) as? JsonObject

    /** POST 并返回 JsonObject。 */
    suspend fun postJson(
        url: String,
        body: String,
        contentType: String = "text/plain",
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonObject? = requestRaw(url, extraHeaders, body to contentType) as? JsonObject

    /** 返回 JSON 根为数组的响应（IGDB 多数端点如此）。 */
    suspend fun getArray(url: String, extraHeaders: Map<String, String> = emptyMap()): JsonArray? =
        requestRaw(url, extraHeaders, null) as? JsonArray

    suspend fun postArray(
        url: String,
        body: String,
        contentType: String = "text/plain",
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonArray? = requestRaw(url, extraHeaders, body to contentType) as? JsonArray

    private suspend fun requestRaw(
        url: String,
        extraHeaders: Map<String, String>,
        body: Pair<String, String>?,
    ): JsonElement? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
            extraHeaders.forEach { (k, v) -> builder.header(k, v) }
            if (body != null) {
                builder.post(body.first.toRequestBody(body.second.toMediaType()))
            }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                if (text.isBlank()) return@use null
                json.parseToJsonElement(text)
            }
        }.getOrNull()
    }
}

// ==================== JSON 取值小工具（顶层扩展函数，全部容错） ====================

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonObject?.str(key: String): String? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.takeIf { it.isNotBlank() && it != "null" }

fun JsonObject?.int(key: String): Int? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.content.toInt() }.getOrNull() }

fun JsonObject?.long(key: String): Long? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.content.toLong() }.getOrNull() }

fun JsonObject?.float(key: String): Float? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.content.toFloat() }.getOrNull() }

fun JsonObject?.bool(key: String): Boolean? =
    this?.get(key)?.let { runCatching { it.jsonPrimitive.content.toBoolean() }.getOrNull() }

fun JsonObject?.arr(key: String): JsonArray? =
    this?.get(key)?.let { runCatching { it.jsonArray }.getOrNull() }

fun JsonObject?.obj(key: String): JsonObject? =
    this?.get(key)?.let { runCatching { it.jsonObject }.getOrNull() }

fun JsonArray?.objects(): List<JsonObject> =
    this?.mapNotNull { runCatching { it.jsonObject }.getOrNull() } ?: emptyList()

fun JsonArray?.strings(): List<String> =
    this?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() } ?: emptyList()

/** URL 编码（搜索关键词用）。 */
fun ratingUrlEncode(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
