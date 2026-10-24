package com.otakup.niriko.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-D4：上传前的并发判定（纯函数，项目没有 mockwebserver，不为此加依赖）。
 *
 * 旧实现是裸 PUT，这里锁定「无基线 / ETag 相等 / ETag 不等 / 弱校验一致 / 弱校验变了 /
 * 元信息全缺」六条分支，避免把它们写成一坨 if 之后没人能改。
 */
class SyncConcurrencyPolicyTest {

    private fun baseline(
        etag: String? = "etag-1",
        lastModified: String? = null,
        length: Long? = null,
    ) = SyncBaseline(
        url = "https://dav.example.com/niriko/sync.json",
        etag = etag,
        lastModified = lastModified,
        length = length,
        payloadHash = "payload-hash",
        syncTime = 1_000L,
        deviceId = "device-1",
    )

    @Test
    fun noBaseline_fallsBackToPlainOverwrite() {
        val decision = SyncConcurrencyPolicy.decide(
            baseline = null,
            remote = RemoteMeta(etag = "server-etag", lastModified = "Sat, 19 Oct 2026 10:00:00 GMT", length = 100),
            etagSupported = true,
        )
        assertEquals(SyncWriteDecision.Overwrite, decision)
    }

    @Test
    fun equalEtag_sendsIfMatch() {
        val decision = SyncConcurrencyPolicy.decide(
            baseline = baseline(etag = "etag-1"),
            remote = RemoteMeta(etag = "etag-1"),
            etagSupported = true,
        )
        assertEquals(SyncWriteDecision.Proceed(ifMatch = "etag-1"), decision)
    }

    @Test
    fun differentEtag_isConflict() {
        val decision = SyncConcurrencyPolicy.decide(
            baseline = baseline(etag = "etag-1"),
            remote = RemoteMeta(etag = "etag-2"),
            etagSupported = true,
        )
        assertEquals(SyncWriteDecision.Conflict("etag-1", "etag-2"), decision)
    }

    @Test
    fun noEtagAndWeakMetadataUnchanged_proceedsWithoutIfMatch() {
        val decision = SyncConcurrencyPolicy.decide(
            baseline = baseline(etag = null, lastModified = "Sat, 19 Oct 2026 10:00:00 GMT", length = 100),
            remote = RemoteMeta(etag = null, lastModified = "Sat, 19 Oct 2026 10:00:00 GMT", length = 100),
            etagSupported = false,
        )
        assertEquals(SyncWriteDecision.Proceed(ifMatch = null), decision)
    }

    @Test
    fun noEtagAndLastModifiedChanged_warnsThenProceeds() {
        val decision = SyncConcurrencyPolicy.decide(
            baseline = baseline(etag = null, lastModified = "Sat, 19 Oct 2026 10:00:00 GMT", length = 100),
            remote = RemoteMeta(etag = null, lastModified = "Sat, 19 Oct 2026 11:00:00 GMT", length = 100),
            etagSupported = false,
        )
        assertTrue(decision is SyncWriteDecision.WarnThenProceed)
        assertTrue((decision as SyncWriteDecision.WarnThenProceed).reason.isNotBlank())
    }

    @Test
    fun noMetadataAtAll_proceedsWithPlainPut() {
        val decision = SyncConcurrencyPolicy.decide(
            baseline = baseline(etag = null, lastModified = null, length = null),
            remote = RemoteMeta(etag = null, lastModified = null, length = null),
            etagSupported = false,
        )
        assertEquals(SyncWriteDecision.Proceed(ifMatch = null), decision)
    }

    @Test
    fun remoteFileMissing_proceedsWithoutIfMatch() {
        val decision = SyncConcurrencyPolicy.decide(
            baseline = baseline(etag = "etag-1"),
            remote = null,
            etagSupported = true,
        )
        assertEquals(SyncWriteDecision.Proceed(ifMatch = null), decision)
    }

    @Test
    fun baselineRoundTripsThroughJson() {
        val original = baseline(
            etag = "etag-1",
            lastModified = "Sat, 19 Oct 2026 10:00:00 GMT",
            length = 100,
        )
        val decoded = SyncBaselineCodec.decode(SyncBaselineCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun baselineDecodeToleratesGarbage() {
        assertNull(SyncBaselineCodec.decode(null))
        assertNull(SyncBaselineCodec.decode(""))
        assertNull(SyncBaselineCodec.decode("not json"))
        // 缺 url 的 JSON 不能当成有效基线
        assertNull(SyncBaselineCodec.decode("{\"deviceId\":\"d\"}"))
    }

    @Test
    fun payloadHashIsStableAndContentSensitive() {
        // 空串的 SHA-256 是固定值，用来锁死实现（十六进制小写、无前缀）
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            SyncPayloadHash.sha256Hex(""),
        )
        val payloadA = "{\"a\":1}"
        val payloadA2 = "{\"a\":1}"
        val payloadB = "{\"a\":2}"
        assertEquals(SyncPayloadHash.sha256Hex(payloadA), SyncPayloadHash.sha256Hex(payloadA2))
        assertNotEquals(SyncPayloadHash.sha256Hex(payloadA), SyncPayloadHash.sha256Hex(payloadB))
        assertEquals(64, SyncPayloadHash.sha256Hex("niriko").length)
        assertNotNull(SyncBaselineCodec.decode(SyncBaselineCodec.encode(baseline())))
    }
}
