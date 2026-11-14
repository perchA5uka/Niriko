package com.otakup.niriko.ui.subject

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailPhaseTest {
    @Test
    fun `加载中优先于错误与内容`() {
        assertEquals(DetailPhase.Loading, detailPhaseOf(isLoading = true, hasError = true, hasSubject = true))
    }

    @Test
    fun `有旧数据且刷新失败时停在错误分支`() {
        // 分支顺序契约：error 先于 ready，否则刷新失败会突然把正文换成错误页
        assertEquals(DetailPhase.Error, detailPhaseOf(isLoading = false, hasError = true, hasSubject = true))
    }

    @Test
    fun `数据到达后进入正文`() {
        assertEquals(DetailPhase.Ready, detailPhaseOf(isLoading = false, hasError = false, hasSubject = true))
    }

    @Test
    fun `三者皆假时回退为加载态`() {
        assertEquals(DetailPhase.Loading, detailPhaseOf(isLoading = false, hasError = false, hasSubject = false))
    }
}
