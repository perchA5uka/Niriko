package com.otakup.niriko.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

private val Context.syncBaselineDataStore by preferencesDataStore(name = "sync_baseline")

/**
 * WebDAV 同步基线存储（A-D4）。
 *
 * 照 [com.otakup.niriko.data.settings.CoverOverrideStore] 的写法：独立 preferencesDataStore，
 * **不进 Room、不动表结构**。基线只服务「上传前判断远端有没有被别的设备改过」。
 */
class SyncBaselineStore(private val context: Context) {

    /** 读取上次同步基线；没有或解析失败返回 null（失败按「无基线」处理，退回裸 PUT）。 */
    suspend fun read(): SyncBaseline? {
        val raw = context.syncBaselineDataStore.data.first()[KEY_BASELINE]
        return SyncBaselineCodec.decode(raw)
    }

    suspend fun write(baseline: SyncBaseline) {
        context.syncBaselineDataStore.edit { prefs ->
            prefs[KEY_BASELINE] = SyncBaselineCodec.encode(baseline)
        }
    }

    suspend fun clear() {
        context.syncBaselineDataStore.edit { prefs -> prefs.remove(KEY_BASELINE) }
    }

    /**
     * 本机标识：随机 UUID，首次使用时生成并持久化。
     *
     * 只用于诊断文案（「另一台设备」），**不**参与并发校验——设备名/ID 不是可信凭据。
     */
    suspend fun deviceId(): String {
        val existing = context.syncBaselineDataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        context.syncBaselineDataStore.edit { prefs -> prefs[KEY_DEVICE_ID] = created }
        return created
    }

    private companion object {
        val KEY_BASELINE = stringPreferencesKey("sync_baseline_v1")
        val KEY_DEVICE_ID = stringPreferencesKey("sync_device_id")
    }
}

/**
 * 基线的 JSON 编解码（纯 object，不碰 Context）。
 *
 * 单独抽出来才能用 JVM 单测锁定往返；DataStore 只是它的持久化外壳。
 */
object SyncBaselineCodec {

    fun encode(baseline: SyncBaseline): String = buildJsonObject {
        put("url", JsonPrimitive(baseline.url))
        baseline.etag?.let { put("etag", JsonPrimitive(it)) }
        baseline.lastModified?.let { put("lastModified", JsonPrimitive(it)) }
        baseline.length?.let { put("length", JsonPrimitive(it)) }
        baseline.payloadHash?.let { put("payloadHash", JsonPrimitive(it)) }
        put("syncTime", JsonPrimitive(baseline.syncTime))
        put("deviceId", JsonPrimitive(baseline.deviceId))
    }.toString()

    fun decode(raw: String?): SyncBaseline? {
        if (raw.isNullOrBlank()) return null
        return try {
            val o = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
            val url = o["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
            SyncBaseline(
                url = url,
                etag = o["etag"]?.jsonPrimitive?.content,
                lastModified = o["lastModified"]?.jsonPrimitive?.content,
                length = o["length"]?.jsonPrimitive?.content?.toLongOrNull(),
                payloadHash = o["payloadHash"]?.jsonPrimitive?.content,
                syncTime = o["syncTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                deviceId = o["deviceId"]?.jsonPrimitive?.content.orEmpty(),
            )
        } catch (_: Exception) {
            null
        }
    }
}
