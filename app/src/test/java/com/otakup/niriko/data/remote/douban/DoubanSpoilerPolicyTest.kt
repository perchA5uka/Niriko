package com.otakup.niriko.data.remote.douban

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 豆瓣剧照防剧透起点计算单元测试（计划 B4 · 4-13，用户开关）。
 */
class DoubanSpoilerPolicyTest {

    @Test
    fun antiSpoilerOff_startsAtZero() {
        assertEquals(0, DoubanSpoilerPolicy.photoStart(antiSpoiler = false, pageSize = 40))
    }

    @Test
    fun antiSpoilerOn_skipsFirstPage() {
        assertEquals(40, DoubanSpoilerPolicy.photoStart(antiSpoiler = true, pageSize = 40))
        assertEquals(20, DoubanSpoilerPolicy.photoStart(antiSpoiler = true, pageSize = 20))
    }

    @Test
    fun antiSpoilerOn_withZeroPageSize_staysZero() {
        // 页大小非法时不能算出负偏移
        assertEquals(0, DoubanSpoilerPolicy.photoStart(antiSpoiler = true, pageSize = 0))
        assertEquals(0, DoubanSpoilerPolicy.photoStart(antiSpoiler = true, pageSize = -5))
    }
}
