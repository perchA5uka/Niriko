package com.otakup.niriko.data.probe

import com.otakup.niriko.data.remote.douban.DoubanClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 灰色通道探针框架的纯函数单测（第 4 轮 F）。
 *
 * **不发任何真实请求**——只验证「端点清单是否完备、URL 与请求头是否按约定拼装」。
 * 真正的连通性由设置页的自检工具在真机上跑（本机不能直连外网）。
 */
class GrayChannelProbeTest {

    private val config = ProbeConfig(
        doubanImageReferer = "https://douban.com",
        doubanApiReferer = "https://m.douban.com",
        grayChannelEnabled = true,
    )

    @Test
    fun `注册表包含计划内的五个源`() {
        val ids = GrayChannelRegistry.all.map { it.id }
        assertEquals(
            listOf("douban", "anilist", "jikan", "anidb", "bangumi_legacy"),
            ids,
        )
    }

    @Test
    fun `按 id 查找可用`() {
        assertNotNull(GrayChannelRegistry.byId("douban"))
        assertEquals(null, GrayChannelRegistry.byId("does-not-exist"))
    }

    @Test
    fun `豆瓣探针给出三条链路_便于定位是哪一层挂`() {
        val probe = GrayChannelRegistry.byId("douban")!!
        val endpoints = probe.endpoints(config)
        assertEquals(3, endpoints.size)
        assertEquals("rexxar JSON", endpoints[0].name)
        assertEquals("frodo JSON", endpoints[1].name)
        assertEquals("HTML 剧照页", endpoints[2].name)
        // 三条都应给出「预期表现」的说明，否则用户看到状态码也不知道意味着什么
        assertTrue(endpoints.all { !it.note.isNullOrBlank() })
    }

    @Test
    fun `豆瓣 rexxar 端点的 Referer 按约定拼接`() {
        val probe = GrayChannelRegistry.byId("douban")!!
        val rexxar = probe.endpoints(config).first { it.name == "rexxar JSON" }
        assertEquals(
            "https://m.douban.com/movie/subject/1292052/",
            rexxar.headers["Referer"],
        )
    }

    @Test
    fun `豆瓣 HTML 端点用图片 Referer 而不是 API Referer`() {
        val probe = GrayChannelRegistry.byId("douban")!!
        val html = probe.endpoints(config).first { it.name == "HTML 剧照页" }
        assertEquals("https://douban.com", html.headers["Referer"])
    }

    @Test
    fun `豆瓣 frodo 端点的 apikey 放在 header 而不是 query`() {
        // 实测：apikey 放 query 会被拒（code 997）
        val probe = GrayChannelRegistry.byId("douban")!!
        val frodo = probe.endpoints(config).first { it.name == "frodo JSON" }
        assertEquals(DoubanClient.DEFAULT_FRODO_KEY, frodo.headers["apikey"])
        assertFalse("apikey 不应出现在 URL 里", frodo.url.contains("apikey"))
    }

    @Test
    fun `豆瓣探针不受灰色通道开关限制_必须先能自检`() {
        val probe = GrayChannelRegistry.byId("douban")!!
        assertTrue(probe.probeRegardlessOfToggle)
        // 未开启时其余源不跑（但会产出 SKIPPED 结果，见 Runner）
        assertFalse(GrayChannelRegistry.byId("jikan")!!.probeRegardlessOfToggle)
    }

    @Test
    fun `每个探针都有非空的标签与说明`() {
        GrayChannelRegistry.all.forEach { probe ->
            assertTrue("${probe.id} 缺 label", probe.label.isNotBlank())
            assertTrue("${probe.id} 缺 description", probe.description.isNotBlank())
            assertTrue("${probe.id} 端点为空", probe.endpoints(config).isNotEmpty())
        }
    }

    @Test
    fun `所有端点 URL 都是绝对 http 地址`() {
        GrayChannelRegistry.all.flatMap { it.endpoints(config) }.forEach { endpoint ->
            assertTrue(
                "端点 ${endpoint.name} 的 URL 不是绝对地址：${endpoint.url}",
                endpoint.url.startsWith("http://") || endpoint.url.startsWith("https://"),
            )
        }
    }

    @Test
    fun `豆瓣探针带合规风险提示`() {
        val note = GrayChannelRegistry.byId("douban")!!.riskNote
        assertNotNull("豆瓣必须声明合规风险", note)
        assertTrue(note!!.contains("个人"))
    }

    // ==================== Runner 的开关语义 ====================

    @Test
    fun `未开启灰色通道时仍运行自检类探针并产出跳过项`() = kotlinx.coroutines.runBlocking {
        // 用一个只返回空端点的假探针验证「命名前缀」与「跳过语义」，
        // 避免这个单测真的打外部网络。
        val results = GrayChannelRunner.runAll(
            config = config.copy(grayChannelEnabled = false),
            onlyId = "jikan",
        )
        // jikan 不是自检类且通道关闭 → 必须产出一条 SKIPPED（不能静默消失）
        assertEquals(1, results.size)
        assertEquals(ProbeState.SKIPPED, results[0].state)
        assertTrue("结果名应带源前缀", results[0].endpointName.startsWith("Jikan"))
        assertNotNull("必须说明为什么没跑", results[0].error)
    }

    @Test
    fun `未知 id 返回空列表而不是崩`() = kotlinx.coroutines.runBlocking {
        val results = GrayChannelRunner.runAll(config, onlyId = "no-such-probe")
        assertTrue(results.isEmpty())
    }

    // ==================== ProbeResult 摘要 ====================

    @Test
    fun `结果摘要包含状态码与耗时`() {
        val result = ProbeResult(
            endpointName = "x",
            url = "https://example.com",
            state = ProbeState.BLOCKED,
            httpStatus = 403,
            elapsedMs = 250,
        )
        val summary = result.summary
        assertTrue(summary.contains("被拒绝"))
        assertTrue(summary.contains("403"))
        assertTrue(summary.contains("250"))
    }

    @Test
    fun `不可达结果摘要带错误原因`() {
        val result = ProbeResult(
            endpointName = "x",
            url = "https://example.com",
            state = ProbeState.UNREACHABLE,
            error = "Unable to resolve host",
        )
        assertTrue(result.summary.contains("不可达"))
        assertTrue(result.summary.contains("Unable to resolve host"))
    }
}
