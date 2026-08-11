package com.otakup.niriko.plugin.kazumi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 用 Kazumi 真实导出的 collectibles.tmp（/ hive）验证 KazumiHiveReader 解析。
 *
 * 将真实文件放到本测试易读的位置即可运行；文件不存在时跳过（不损坏 CI）。
 * 用法：把 `collectibles.tmp` 复制为 `app/src/test/resources/kazumi/collectibles.tmp`。
 */
class KazumiHiveReaderRealFileTest {

    private val realFile: File =
        File("src/test/resources/kazumi/collectibles.tmp")

    @Test
    fun `真实 collectibles tmp 可被解析且仅含 anime`() {
        if (!realFile.exists()) {
            println("[SKIP] 未找到真实文件: ${realFile.absolutePath}")
            return
        }
        val bytes = realFile.readBytes()
        val entries = KazumiHiveReader.readCollectibles(bytes)

        // 打印统计（先于断言，便于失败时保留现场）
        println("解析到 ${entries.size} 条收藏")
        entries.take(5).forEach {
            println("  - [${it.collectType}] type=${it.type} (bangumiId=${it.bangumiId}) title=${it.title} nameCn=${it.nameCn}")
        }
        println("type 分布: ${entries.groupBy { it.type }.mapValues { it.value.size }}")

        assertTrue("应解析出至少 1 条收藏", entries.isNotEmpty())
        // 用户断言：Kazumi 是动画播放器，仅 anime（type==2）
        assertEquals(
            "全部条目必须是 ANIME",
            entries.size,
            entries.count { it.type == 2 },
        )
        // 每条都应有标题
        assertTrue("每条应有标题", entries.all { it.title.isNotBlank() })
    }

    @Test
    fun `解析失败时才失败 - 容错不回退`() {
        if (!realFile.exists()) return
        val bytes = realFile.readBytes()
        val entries = KazumiHiveReader.readCollectibles(bytes)
        // 若读完为 0，说明解析器把帧全跳过了（或文件不是 collectibles box）
        println("frames->entries=${entries.size}")
        if (entries.isEmpty()) {
            fail("真实文件未解析出任何条目，说明 reader 解码失败")
        }
    }
}