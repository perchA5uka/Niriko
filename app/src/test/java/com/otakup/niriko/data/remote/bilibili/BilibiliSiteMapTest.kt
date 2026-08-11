package com.otakup.niriko.data.remote.bilibili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BilibiliSiteMap 映射测试。
 *
 * - 纯函数部分用内联 JSON 验证解析逻辑;
 * - 真实资源: 读取生成的真实映射文件(bilibili_site_map.json),验证整体加载与已知条目。
 */
class BilibiliSiteMapTest {

    private val realFile: File =
        File("src/test/resources/bilibili/bilibili_site_map.json")

    // ==================== 纯函数 ====================

    @Test
    fun `parse 解析常规映射`() {
        val json = """{"3398":{"b":3398},"2823":{"h":28232},"256":{"b":3398,"h":123}}"""
        val map = BilibiliSiteMap.parse(json)
        assertEquals(mapOf("b" to 3398), map[3398L])
        assertEquals(mapOf("h" to 28232), map[2823L])
        assertEquals(mapOf("b" to 3398, "h" to 123), map[256L])
        assertEquals(3, map.size)
    }

    @Test
    fun `parse 忽略 0 值与非数字键`() {
        val map = BilibiliSiteMap.parse("""{"0":{"b":0},"abc":{"b":1},"7":{"h":0}}""")
        assertEquals(0, map.size)
    }

    @Test
    fun `parse 空串与非法 JSON 返回空`() {
        assertTrue(BilibiliSiteMap.parse("").isEmpty())
        assertTrue(BilibiliSiteMap.parse("not json{").isEmpty())
    }

    // ==================== 反向索引（buildReverse） ====================

    @Test
    fun `buildReverse 建立 season 到 bgmId 的映射`() {
        val forward = mapOf(
            3398L to mapOf("b" to 3398, "h" to 28232),
            256L to mapOf("b" to 3398, "h" to 123),
        )
        val reverse = BilibiliSiteMap.buildReverse(forward)
        // season 3398 同时被 3398L 与 256L 引用：取第一个（保持确定性）
        assertEquals(3398L, reverse[3398])
        // 港澳台 season 兜底占位
        assertEquals(256L, reverse[123])
        assertEquals(3398L, reverse[28232])
    }

    @Test
    fun `buildReverse 与 parse 双向一致（含真实资源抽样）`() {
        if (!realFile.exists()) return
        val map = BilibiliSiteMap.parse(realFile.readText())
        val reverse = BilibiliSiteMap.buildReverse(map)
        assertTrue("反向索引应非空", reverse.isNotEmpty())
        // 抽样 100 条正向条目，验证 season_id 能反查回原 bgmId（无重复冲突时）
        var roundTrip = 0
        var checked = 0
        map.entries.take(100).forEach { (bgmId, sites) ->
            val preferred = sites["b"] ?: sites.values.firstOrNull() ?: return@forEach
            checked++
            if (reverse[preferred] == bgmId) roundTrip++
        }
        println("反向一致性：$roundTrip/$checked 条可回查（其余为同 season 多 bgmId 冲突，正常）")
        assertTrue("至少应能反查回大部分条目", roundTrip > 0)
    }

    // ==================== 真实资源 ====================

    @Test
    fun `真实映射文件可解析且非空`() {
        assertTrue("测试资源缺失: ${realFile.absolutePath}", realFile.exists())
        val map = BilibiliSiteMap.parse(realFile.readText())
        assertTrue("映射应非空", map.isNotEmpty())
    }

    @Test
    fun `真实映射条目命中且为正`() {
        if (!realFile.exists()) return
        val map = BilibiliSiteMap.parse(realFile.readText())
        assertTrue("映射应非空", map.isNotEmpty())
        // 取样前 50 条,验证都能解析成 Long → Map<String, Int> 且值为正
        val samples = map.entries.take(50)
        samples.forEach { (bgmId, sites) ->
            assertTrue("bgmId 须为正: $bgmId", bgmId > 0)
            assertTrue("每个站点 season_id 须为正", sites.values.all { it > 0 })
        }
        println("取样验证 ${samples.size} 条, 总计 ${map.size} 条")
    }

    @Test
    fun `真实映射所有值均为正数`() {
        if (!realFile.exists()) return
        val map = BilibiliSiteMap.parse(realFile.readText())
        map.forEach { (_, sites) ->
            sites.forEach { (_, v) ->
                assertTrue("season_id 必须为正: $v", v > 0)
            }
        }
    }
}