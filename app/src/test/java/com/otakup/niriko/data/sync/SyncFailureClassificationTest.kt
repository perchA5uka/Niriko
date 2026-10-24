package com.otakup.niriko.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A-D5：HTTP 状态码 / 异常 → 失败种类的映射。
 *
 * 改造前这些信息全被折成 null 或 false，单测只能靠连真实服务器；现在分类是纯函数。
 */
class SyncFailureClassificationTest {

    @Test
    fun classify_authCovers401And403() {
        assertEquals(SyncFailureKind.AUTH, SyncFailureClassifier.classify(401))
        assertEquals(SyncFailureKind.AUTH, SyncFailureClassifier.classify(403))
    }

    @Test
    fun classify_notFound() {
        assertEquals(SyncFailureKind.NOT_FOUND, SyncFailureClassifier.classify(404))
    }

    @Test
    fun classify_other4xx() {
        assertEquals(SyncFailureKind.HTTP_4XX, SyncFailureClassifier.classify(409))
        // 412 的「冲突」语义由 WebDavClient.put 单独给出；兜底分类仍是 4xx
        assertEquals(SyncFailureKind.HTTP_4XX, SyncFailureClassifier.classify(412))
        assertEquals(SyncFailureKind.HTTP_4XX, SyncFailureClassifier.classify(423))
    }

    @Test
    fun classify_5xx() {
        assertEquals(SyncFailureKind.HTTP_5XX, SyncFailureClassifier.classify(500))
        assertEquals(SyncFailureKind.HTTP_5XX, SyncFailureClassifier.classify(503))
    }

    @Test
    fun classify_successIsNull() {
        // 成功码必须显式白名单：200/201/204/207(WebDAV Multi-Status)
        assertNull(SyncFailureClassifier.classify(200))
        assertNull(SyncFailureClassifier.classify(201))
        assertNull(SyncFailureClassifier.classify(204))
        assertNull(SyncFailureClassifier.classify(207))
    }

    @Test
    fun classifyException_timeouts() {
        assertEquals(SyncFailureKind.TIMEOUT, SyncFailureClassifier.classifyException(SocketTimeoutException("read timed out")))
        assertEquals(SyncFailureKind.TIMEOUT, SyncFailureClassifier.classifyException(InterruptedIOException("interrupted")))
    }

    @Test
    fun classifyException_network() {
        assertEquals(SyncFailureKind.NETWORK, SyncFailureClassifier.classifyException(UnknownHostException("dav.example.com")))
        assertEquals(SyncFailureKind.NETWORK, SyncFailureClassifier.classifyException(IOException("connection reset")))
    }

    @Test
    fun classifyException_unknown() {
        assertEquals(SyncFailureKind.UNKNOWN, SyncFailureClassifier.classifyException(IllegalStateException("boom")))
    }

    @Test
    fun mkcolTreats405AsExistingDirectory() {
        // 405 = 目录已存在：必须当成功，否则第二次上传会一直失败
        assertTrue(WebDavStatusPolicy.mkcolOk(405))
        assertTrue(WebDavStatusPolicy.mkcolOk(201))
        assertFalse(WebDavStatusPolicy.mkcolOk(401))
        assertFalse(WebDavStatusPolicy.mkcolOk(500))
    }

    @Test
    fun putAndDeleteWhitelists() {
        assertTrue(WebDavStatusPolicy.putOk(201))
        assertFalse(WebDavStatusPolicy.putOk(412))
        assertTrue(WebDavStatusPolicy.deleteOk(204))
        assertTrue(WebDavStatusPolicy.deleteOk(404))
        assertFalse(WebDavStatusPolicy.deleteOk(500))
    }
}
