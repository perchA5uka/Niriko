package com.otakup.niriko.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-D5：每种失败种类的人话文案。
 *
 * 旧实现会把 `上传失败: ${e.message}` 直接甩给用户（含异常类名/堆栈），
 * 这里锁定「非空、不出现 null、不出现异常类名或堆栈片段」。
 */
class SyncResultMessageTest {

    private val forbidden = listOf("null", "Exception", "java.", "at com.", "at java.", "Throwable")

    @Test
    fun everyKindHasHumanMessage() {
        SyncFailureKind.values().forEach { kind ->
            val message = SyncFailureMessages.forKind(kind)
            assertTrue("种类 $kind 没有文案", message.isNotBlank())
            forbidden.forEach { bad ->
                assertFalse("文案 $kind 出现 \"$bad\": $message", message.contains(bad, ignoreCase = false))
            }
        }
    }

    @Test
    fun authMessagesDistinguish401And403() {
        val unauthorized = SyncFailureMessages.forKind(SyncFailureKind.AUTH, 401)
        val forbidden403 = SyncFailureMessages.forKind(SyncFailureKind.AUTH, 403)
        assertTrue(unauthorized.contains("401"))
        assertTrue(unauthorized.contains("登录"))
        assertTrue(forbidden403.contains("403"))
        assertTrue(forbidden403.contains("权限"))
    }

    @Test
    fun codeAppearsInServerError() {
        assertTrue(SyncFailureMessages.forKind(SyncFailureKind.HTTP_5XX, 500).contains("500"))
        assertTrue(SyncFailureMessages.forKind(SyncFailureKind.HTTP_5XX, 503).contains("503"))
    }

    @Test
    fun networkAndTimeoutAreDistinct() {
        val timeout = SyncFailureMessages.forKind(SyncFailureKind.TIMEOUT)
        val network = SyncFailureMessages.forKind(SyncFailureKind.NETWORK)
        assertTrue(timeout.contains("超时"))
        assertTrue(network.contains("网络"))
        assertTrue(timeout != network)
    }

    @Test
    fun failureWithoutCodeDoesNotPrintNull() {
        val message = SyncFailureMessages.forFailure(
            WebDavResult.Failure(SyncFailureKind.UNKNOWN, code = null, detail = "java.net.SocketException: nope"),
        )
        assertFalse(message.contains("null"))
        // detail 只进日志，不进用户文案
        assertFalse(message.contains("SocketException"))
    }

    @Test
    fun mkcolMessageMentionsDirectoryAndCode() {
        val message = SyncFailureMessages.forMkcol(
            WebDavResult.Failure(SyncFailureKind.AUTH, code = 401),
        )
        assertTrue(message.contains("目录"))
        assertTrue(message.contains("401"))
    }
}
