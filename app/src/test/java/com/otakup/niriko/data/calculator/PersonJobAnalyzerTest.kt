package com.otakup.niriko.data.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonJobAnalyzerTest {

    @Test
    fun `多值身份按分隔符拆开并计数`() {
        val stats = PersonJobAnalyzer.analyze(
            listOf("导演、脚本", "导演", "脚本、分镜"),
        )
        val map = stats.associate { it.job to it.count }
        assertEquals(2, map["导演"])
        assertEquals(2, map["脚本"])
        assertEquals(1, map["分镜"])
    }

    @Test
    fun `英文标点同样可拆分`() {
        val stats = PersonJobAnalyzer.analyze(listOf("原作, 系列构成; 脚本"))
        assertEquals(3, stats.size)
    }

    @Test
    fun `同一作品内重复身份只计一次`() {
        val stats = PersonJobAnalyzer.analyze(listOf("导演、导演、监督"))
        val map = stats.associate { it.job to it.count }
        assertEquals(1, map["导演"])
        assertEquals(1, map["监督"])
    }

    @Test
    fun `占位身份被过滤`() {
        val stats = PersonJobAnalyzer.analyze(listOf(null, "", "-", "其他", "协力"))
        assertEquals(0, stats.size)
    }

    @Test
    fun `结果为次数降序_同频按名称升序`() {
        val stats = PersonJobAnalyzer.analyze(listOf("B、A", "B"))
        assertEquals("B", stats.first().job)
        assertEquals(2, stats.first().count)
        assertEquals("A", stats[1].job)
    }

    @Test
    fun `空输入返回空列表`() {
        assertEquals(0, PersonJobAnalyzer.analyze(emptyList()).size)
    }

    @Test
    fun `主要身份取出现最多的一项`() {
        // 注意：同一作品内的重复身份只计一次（见上一个用例），因此这里用两个作品拉开差距
        val stats = PersonJobAnalyzer.analyze(listOf("导演", "导演、脚本"))
        assertEquals("导演", PersonJobAnalyzer.primaryJob(stats))
        assertNull(PersonJobAnalyzer.primaryJob(emptyList()))
    }
}
