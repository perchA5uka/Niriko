package com.otakup.niriko.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.coverOverrides by preferencesDataStore(name = "cover_overrides")

/**
 * 封面覆盖存储（阶段 E：用户更换封面）。
 * 独立 DataStore 文件（name="cover_overrides"），记录 subjectId → 覆盖 URI（上传图拷贝到
 * filesDir/covers/，或源站大图）。CoverImage 优先读取覆盖值；空则回退原封面。
 */
class CoverOverrideStore(private val context: Context) {

    private fun key(subjectId: Long) = stringPreferencesKey("subject_$subjectId")

    /** 某条目当前覆盖 URI（null=未覆盖）。 */
    fun overrideFlow(subjectId: Long): Flow<String?> =
        context.coverOverrides.data.map { prefs -> prefs[key(subjectId)] }

    /** 立即读取（仅内存快照提示用）。 */
    suspend fun overrideFor(subjectId: Long): String? =
        context.coverOverrides.data.map { prefs -> prefs[key(subjectId)] }.first()

    suspend fun setOverride(subjectId: Long, uri: String) {
        context.coverOverrides.edit { prefs -> prefs[key(subjectId)] = uri }
    }

    suspend fun clearOverride(subjectId: Long) {
        context.coverOverrides.edit { prefs -> prefs.remove(key(subjectId)) }
    }

    // ===== 阶段 7：备份导出 / 导入 =====
    // 此前封面覆盖只存在 DataStore 里，**不进备份也不进 WebDAV** ——
    // 换设备后所有自定义封面都会丢，必须重新一张张贴回去。

    /** 全部覆盖（subjectId → 引用）。 */
    suspend fun all(): Map<Long, String> = context.coverOverrides.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { (prefKey, value) ->
            val id = prefKey.name.removePrefix(PREFIX).toLongOrNull() ?: return@mapNotNull null
            val uri = value as? String ?: return@mapNotNull null
            if (uri.isBlank()) null else id to uri
        }.toMap()
    }.first()

    /** 批量写入（导入用；merge=false 时会先清空）。 */
    suspend fun putAll(overrides: Map<Long, String>, clearFirst: Boolean) {
        if (overrides.isEmpty() && !clearFirst) return
        context.coverOverrides.edit { prefs ->
            if (clearFirst) prefs.clear()
            overrides.forEach { (id, uri) -> prefs[key(id)] = uri }
        }
    }

    private companion object {
        const val PREFIX = "subject_"
    }
}
