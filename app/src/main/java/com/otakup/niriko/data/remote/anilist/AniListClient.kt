package com.otakup.niriko.data.remote.anilist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "AniListClient"
private const val BASE_URL = "https://graphql.anilist.co"
private val JSON_MEDIA = "application/json".toMediaType()

/**
 * AniList GraphQL 客户端。
 * POST JSON → graphql.anilist.co → 按 dataPath 提取 JSON 节点。
 * 无需外部依赖，使用 OkHttp + kotlinx.serialization。
 */
class AniListClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 执行 GraphQL 查询。
     * @param query 查询字符串
     * @param variables 变量映射
     * @param dataPath 从响应中提取的路径，如 ["data","Page"] → response.data.Page
     */
    suspend fun query(
        query: String,
        variables: Map<String, JsonElement> = emptyMap(),
        dataPath: List<String> = listOf("data"),
    ): JsonElement {
        // 构建请求体
        val bodyObj = buildJsonObject {
            put("query", query)
            if (variables.isNotEmpty()) {
                put("variables", buildJsonObject {
                    variables.forEach { (k, v) -> put(k, v) }
                })
            }
        }
        val bodyString = json.encodeToString(JsonObject.serializer(), bodyObj)

        val request = Request.Builder()
            .url(BASE_URL)
            .post(bodyString.toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()

        val responseBody = withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(request).execute()
            val body = resp.body?.string() ?: throw Exception("Empty response body")
            if (!resp.isSuccessful) {
                Log.e(TAG, "HTTP ${resp.code}: $body")
                throw Exception("AniList API error: ${resp.code}")
            }
            body
        }

        val root = json.parseToJsonElement(responseBody).jsonObject

        // 记录 GraphQL 错误但不抛异常
        root["errors"]?.let { errors ->
            val msg = errors.jsonArray.joinToString(";") { it.jsonObject["message"]?.jsonPrimitive?.content ?: "" }
            Log.w(TAG, "GraphQL errors: $msg")
        }

        // 沿 dataPath 逐层提取
        var current: JsonElement = root
        for (key in dataPath) {
            current = (current as? JsonObject)?.get(key)
                ?: throw Exception("Missing key '$key' in AniList response")
        }
        return current
    }
}
