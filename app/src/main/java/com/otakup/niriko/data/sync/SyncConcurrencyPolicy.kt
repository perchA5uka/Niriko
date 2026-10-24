package com.otakup.niriko.data.sync

import java.security.MessageDigest

/**
 * 上次同步时远端文件的版本标识（A-D4）。
 *
 * 存在独立 DataStore（不进 Room、不动 schema）：ETag 是「上传前比对」的唯一强证据，
 * 没有它就只能裸 PUT，另一台设备的写入会被静默覆盖。
 */
data class SyncBaseline(
    val url: String,
    val etag: String?,
    val lastModified: String?,
    val length: Long?,
    /** 本次下载/上传的 body SHA-256（诊断用：能判断「内容没变」）。 */
    val payloadHash: String?,
    val syncTime: Long,
    /** 本机随机 UUID，仅用于诊断文案区分设备，不参与校验。 */
    val deviceId: String,
)

/** 上传前的并发判定结果。 */
sealed interface SyncWriteDecision {
    /** 没有基线（首次同步 / 从旧版升级）：只能裸 PUT，本次无法检测并发。 */
    data object Overwrite : SyncWriteDecision

    /** 继续上传。[ifMatch] 非空时带 `If-Match`；null 表示裸 PUT（无强校验条件）。 */
    data class Proceed(val ifMatch: String?) : SyncWriteDecision

    /** 服务器不支持 ETag，弱校验（Last-Modified + 长度）发现远端变了：提示后照常上传。 */
    data class WarnThenProceed(val reason: String) : SyncWriteDecision

    /** 强校验发现远端已被其它设备改过：自动重合并一次或交 UI 让用户选。 */
    data class Conflict(val baselineEtag: String, val remoteEtag: String?) : SyncWriteDecision
}

/**
 * 条件写入的判定逻辑（A-D4）。
 *
 * 抽成纯函数是因为项目没有 mockwebserver 依赖（不为 8 条分支新增依赖），
 * 只能靠纯单测把「无基线 / ETag 相等 / ETag 不等 / 弱校验 / 都取不到」锁死。
 */
object SyncConcurrencyPolicy {

    /**
     * @param baseline 上次成功同步留下的基线；null = 没有。
     * @param remote HEAD 得到的远端元信息；null = 远端文件不存在或 HEAD 不可用。
     * @param etagSupported 远端是否回了 ETag（决定强校验还是弱校验）。
     */
    fun decide(
        baseline: SyncBaseline?,
        remote: RemoteMeta?,
        etagSupported: Boolean,
    ): SyncWriteDecision {
        if (baseline == null) return SyncWriteDecision.Overwrite
        // 远端文件没了：没有可冲突的对象，直接新建
        if (remote == null) return SyncWriteDecision.Proceed(ifMatch = null)

        if (etagSupported && baseline.etag != null && remote.etag != null) {
            return if (baseline.etag == remote.etag) {
                SyncWriteDecision.Proceed(ifMatch = baseline.etag)
            } else {
                SyncWriteDecision.Conflict(baselineEtag = baseline.etag, remoteEtag = remote.etag)
            }
        }

        val hasWeakBaseline = baseline.lastModified != null || baseline.length != null
        if (!hasWeakBaseline) return SyncWriteDecision.Proceed(ifMatch = null)

        val unchanged = baseline.lastModified == remote.lastModified && baseline.length == remote.length
        return if (unchanged) {
            SyncWriteDecision.Proceed(ifMatch = null)
        } else {
            SyncWriteDecision.WarnThenProceed(
                "云端在你上次同步后已变化（服务器不支持 ETag，仅按修改时间与大小做弱校验）",
            )
        }
    }
}

/** 同步载荷哈希（A-D4）：同一份 JSON 必须得到同一个值，便于诊断「内容是否真的变了」。 */
object SyncPayloadHash {

    private const val HEX = "0123456789abcdef"

    fun sha256Hex(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }
}
