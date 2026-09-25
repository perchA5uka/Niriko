package com.otakup.niriko.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinSearchTest {
    @Test
    fun matchesPinyin() {
        assertTrue(PinyinSearch.matches("juren", "進撃の巨人", "进击的巨人"))
        assertTrue(PinyinSearch.matches("进击的巨人", "進撃の巨人", "进击的巨人"))
        assertFalse(PinyinSearch.matches("zzzz", "進撃の巨人", "进击的巨人"))
    }

    @Test
    fun pinyinKeyContainsPinyin() {
        val key = PinyinSearch.pinyinKey("进击的巨人", "进击的巨人")
        assertTrue(key.contains("juren"))
    }
}
