package com.otakup.niriko.ui.stats

import com.otakup.niriko.data.model.stats.BroadcastMonthState
import com.otakup.niriko.data.model.stats.BroadcastMonthStatus
import org.junit.Assert.*
import org.junit.Test

class BroadcastMonthMessageTest {
    @Test fun selectedMonthDistinguishesLoadingEmptySuccessAndError() {
        val month = BroadcastMonthState(key = "2025-09")
        assertTrue(broadcastMonthMessage(month).contains("加载中"))
        assertTrue(broadcastMonthMessage(month.copy(status = BroadcastMonthStatus.EMPTY)).contains("暂无放送事件"))
        assertTrue(broadcastMonthMessage(month.copy(status = BroadcastMonthStatus.SUCCESS)).contains("已更新"))
        assertTrue(broadcastMonthMessage(month.copy(status = BroadcastMonthStatus.ERROR)).contains("失败"))
    }

    @Test fun partialErrorIsNotPresentedAsSuccess() {
        val message = "2025-09 部分加载失败（三次元），已保留可用内容，请重试"
        assertEquals(message, broadcastMonthMessage(BroadcastMonthState(
            key = "2025-09", status = BroadcastMonthStatus.ERROR, lastSuccessAt = 123L,
            error = message, isPartial = true, eventCount = 4,
        )))
    }
}
