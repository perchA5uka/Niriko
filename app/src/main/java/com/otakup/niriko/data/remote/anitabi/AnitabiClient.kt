package com.otakup.niriko.data.remote.anitabi

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "AnitabiClient"

/** Anitabi.cn 取景地标客户端（阶段 K）。OkHttp GET，无需 key。 */
class AnitabiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(subjectId: Long): AnitabiResponse? {
        val url = "https://api.anitabi.cn/bangumi/" + subjectId + "/lite"
        return try {
            val body = withContext(Dispatchers.IO) {
                val resp = okHttpClient.newCall(
                    Request.Builder().url(url).header("User-Agent", "Niriko/1.0").build(),
                ).execute()
                val b = resp.body?.string() ?: ""
                if (resp.isSuccessful) b else { Log.w(TAG, "HTTP " + resp.code); "" }
            }
            if (body.isBlank()) null else json.decodeFromString<AnitabiResponse>(body)
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed subjectId=" + subjectId, e)
            null
        }
    }
}
