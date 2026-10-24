package com.otakup.niriko.ui.settings.pages

import com.otakup.niriko.data.diagnose.Finding
import com.otakup.niriko.data.diagnose.HealthSummary
import com.otakup.niriko.data.diagnose.ProbeCell
import com.otakup.niriko.data.diagnose.ProbeStatus
import com.otakup.niriko.data.diagnose.ProbeSummary
import com.otakup.niriko.data.diagnose.Severity
import com.otakup.niriko.data.diagnose.StorageBucket
import com.otakup.niriko.data.diagnose.StorageItem
import com.otakup.niriko.data.snapshot.SnapshotFile
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C-4 诊断页 5 个 section 的纯逻辑单测：默认展开态、摘要文案、占比、版本行、tag 解析。
 *
 * 渲染本身不在 JVM 单测范围内（Compose），这里钉的是「用户看到的数字与一句话」。
 */
class DiagnosticsSectionsTest {

    @Test
    fun onlyHealthIsExpandedByDefault() {
        val mask = DiagnosticsFormat.defaultExpandedMask()
        assertTrue(DiagnosticsFormat.isExpanded(mask, DiagnosticsFormat.SECTION_HEALTH))
        assertFalse(DiagnosticsFormat.isExpanded(mask, DiagnosticsFormat.SECTION_STORAGE))
        assertFalse(DiagnosticsFormat.isExpanded(mask, DiagnosticsFormat.SECTION_SNAPSHOT))
        assertFalse(DiagnosticsFormat.isExpanded(mask, DiagnosticsFormat.SECTION_PROBE))
        assertFalse(DiagnosticsFormat.isExpanded(mask, DiagnosticsFormat.SECTION_UPDATE))
    }

    @Test
    fun toggleFlipsOnlyThatSection() {
        val mask = DiagnosticsFormat.defaultExpandedMask()
        val opened = DiagnosticsFormat.toggle(mask, DiagnosticsFormat.SECTION_STORAGE)
        assertTrue(DiagnosticsFormat.isExpanded(opened, DiagnosticsFormat.SECTION_STORAGE))
        assertTrue(DiagnosticsFormat.isExpanded(opened, DiagnosticsFormat.SECTION_HEALTH))
        assertEquals(mask, DiagnosticsFormat.toggle(opened, DiagnosticsFormat.SECTION_STORAGE))
    }

    @Test
    fun healthSummaryLineCoversLoadingEmptyAndFindings() {
        assertEquals(
            "正在采集只读数据…",
            DiagnosticsFormat.healthSummaryLine(null, loading = true, countsUnavailable = false),
        )
        assertEquals(
            "尚未运行体检",
            DiagnosticsFormat.healthSummaryLine(null, loading = false, countsUnavailable = false),
        )
        assertEquals(
            "8 项检查 · 全部正常",
            DiagnosticsFormat.healthSummaryLine(HealthSummary(8, 8, 0, 0), false, false),
        )
        assertEquals(
            "8 项检查 · 2 项发现 · 1 项错误",
            DiagnosticsFormat.healthSummaryLine(HealthSummary(8, 6, 1, 1), false, false),
        )
        assertTrue(
            DiagnosticsFormat.healthSummaryLine(HealthSummary(8, 8, 0, 0), false, true)
                .contains("计数读取失败"),
        )
    }

    @Test
    fun bucketFractionNeverDividesByZeroAndClamps() {
        assertEquals(0f, DiagnosticsFormat.bucketFraction(100, 0), 0f)
        assertEquals(0.5f, DiagnosticsFormat.bucketFraction(50, 100), 0f)
        assertEquals(1f, DiagnosticsFormat.bucketFraction(300, 100), 0f)
    }

    @Test
    fun storageSummaryPrefersBiggestBucket() {
        assertEquals("没有可统计的缓存目录", DiagnosticsFormat.storageSummaryLine(emptyList()))
        val items = listOf(
            StorageItem(StorageBucket.COVER, "image_cache", 3L * 1024 * 1024),
            StorageItem(StorageBucket.SNAPSHOT, "snapshots", 1024L * 1024),
        )
        val line = DiagnosticsFormat.storageSummaryLine(items)
        assertTrue(line.contains("共 4 MB"))
        assertTrue(line.contains("封面缓存"))
    }

    @Test
    fun snapshotLineUsesFixedZoneForStableAssertions() {
        val at = Instant.parse("2026-10-18T10:45:00Z").toEpochMilli()
        assertEquals("10-18 10:45", DiagnosticsFormat.snapshotTime(at, ZoneId.of("UTC")))
        assertEquals(
            "10-18 10:45 · 2 MB",
            DiagnosticsFormat.snapshotLine(SnapshotFile("a.json.gz", at, 2L * 1024 * 1024), ZoneId.of("UTC")),
        )
    }

    @Test
    fun probeSummaryLineCountsDeclaredSupport() {
        val cells = listOf(
            ProbeCell("bangumi", "搜索", ProbeStatus.UNKNOWN),
            ProbeCell("bangumi", "日历", ProbeStatus.UNSUPPORTED),
            ProbeCell("anilist", "搜索", ProbeStatus.UNKNOWN),
        )
        val line = DiagnosticsFormat.probeSummaryLine(cells, ProbeSummary(probed = 2, failed = 0, slowestMs = 120))
        assertTrue(line.contains("3 项能力 · 声明支持 2"))
        assertTrue(line.contains("探测 2 源"))
        assertTrue(line.contains("120ms"))
    }

    @Test
    fun unsupportedLineListsAtMostFourSources() {
        assertEquals(
            "所有声明支持的能力都可自检",
            DiagnosticsFormat.unsupportedLine(emptyMap()),
        )
        val line = DiagnosticsFormat.unsupportedLine(
            mapOf("bangumi" to listOf("日历", "评分分布"), "anilist" to listOf("角色")),
        )
        assertTrue(line.contains("bangumi 缺 日历/评分分布"))
        assertTrue(line.contains("anilist 缺 角色"))
    }

    @Test
    fun updateLineExplainsEveryState() {
        assertEquals(
            "当前 v1.2.1 · 本次会话尚未检查",
            DiagnosticsFormat.updateLine("1.2.1", 0L, null, false, 0L),
        )
        assertTrue(DiagnosticsFormat.updateLine("1.2.1", 100L, null, false, 200L).contains("检查失败"))
        assertTrue(DiagnosticsFormat.updateLine("1.2.1", 100L, "v1.2.2", true, 200L).contains("有新版本 v1.2.2"))
        assertTrue(DiagnosticsFormat.updateLine("1.2.1", 100L, "v1.2.1", false, 200L).contains("已是最新"))
    }

    @Test
    fun tagNameIsParsedFromReleaseJson() {
        assertEquals(
            "v1.2.2",
            DiagnosticsFormat.extractTagName("""{"tag_name":"v1.2.2","name":"x"}"""),
        )
        assertNull(DiagnosticsFormat.extractTagName("""{"message":"Not Found"}"""))
    }

    @Test
    fun severityLabelsAreStable() {
        assertEquals("正常", DiagnosticsFormat.severityLabel(Severity.OK))
        assertEquals("提示", DiagnosticsFormat.severityLabel(Severity.INFO))
        assertEquals("注意", DiagnosticsFormat.severityLabel(Severity.WARN))
        assertEquals("错误", DiagnosticsFormat.severityLabel(Severity.ERROR))
    }

    @Test
    fun probeUrlsOnlyForKnownSources() {
        assertEquals(2, probeUrlsFor(listOf("bangumi", "anilist", "unknown")).size)
        assertTrue(probeUrlsFor(listOf("unknown")).isEmpty())
    }

    @Test
    fun findingsCarryFixSuggestionsForActionableItems() {
        // 只验证模型契约（页面按「有无 fix」决定是否显示建议行）
        val withFix = Finding("x", "t", Severity.WARN, "d", fix = "做事")
        assertEquals("做事", withFix.fix)
    }
}
