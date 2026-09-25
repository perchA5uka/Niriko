package com.otakup.niriko.plugin

import com.otakup.niriko.data.remote.AllPluginsFailedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 插件链失败记录单测（第 6 轮 F5）。
 *
 * 不变量：**部分成功照常出结果，全部失败才向上抛**。
 * 改造前链条只有 Log.w，失败与「确实没有结果」在返回值上完全一样。
 */
class PluginFailureTrackerTest {

    @Test
    fun allPluginsFailedThrowsWithReasons() {
        val tracker = PluginFailureTracker()
        tracker.record("bangumi", IllegalStateException("HTTP 401"))
        tracker.record("anilist", RuntimeException("timeout"))

        assertTrue(tracker.allFailed(attempted = 2))

        val thrown = try {
            tracker.throwIfAllFailed(attempted = 2)
            null
        } catch (e: AllPluginsFailedException) {
            e
        }
        assertNotNull("全部失败必须抛出 AllPluginsFailedException", thrown)
        val failure = thrown!!
        assertEquals(2, failure.failures.size)
        assertEquals("bangumi", failure.failures.first().pluginId)
        assertTrue(failure.message.orEmpty().contains("anilist"))
    }

    @Test
    fun partialFailureDoesNotThrow() {
        val tracker = PluginFailureTracker()
        tracker.record("bangumi", IllegalStateException("HTTP 500"))

        assertFalse(tracker.allFailed(attempted = 2))
        tracker.throwIfAllFailed(attempted = 2)
        assertEquals(1, tracker.size)
    }

    @Test
    fun noPluginAttemptedIsNotAllFailed() {
        val tracker = PluginFailureTracker()
        assertFalse(tracker.allFailed(attempted = 0))
        tracker.throwIfAllFailed(attempted = 0)
    }

    @Test
    fun blankExceptionMessageFallsBackToClassName() {
        val tracker = PluginFailureTracker()
        tracker.record("bangumi", IllegalStateException())
        assertEquals("bangumi=IllegalStateException", tracker.summary())
    }

    @Test
    fun summaryIsSingleLine() {
        val tracker = PluginFailureTracker()
        tracker.record("a", IllegalStateException("x"))
        tracker.record("b", IllegalStateException("y"))
        assertEquals("a=x; b=y", tracker.summary())
    }
}
