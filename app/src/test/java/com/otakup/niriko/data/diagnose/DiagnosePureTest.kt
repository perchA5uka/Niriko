package com.otakup.niriko.data.diagnose

import com.otakup.niriko.data.settings.GlassEffectLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 C 设置七件的纯逻辑测试（不碰 Android / 网络 / 文件）。
 *
 * 这里钉的是「判定规则」而不是文案：id、阈值、比较方向、分类优先级。
 */
class DiagnosePureTest {

    // ── C-1 数据体检：8 项、id 稳定、阈值可解释 ────────────────

    @Test
    fun healthChecksHaveExactlyEightStableIdsInOrder() {
        assertEquals(8, HealthChecks.IDS.size)
        assertEquals(HealthChecks.IDS, HealthChecks.run(HealthInput()).map { it.id })
    }

    @Test
    fun emptyLibraryIsInfoNotError() {
        val finding = HealthChecks.run(HealthInput(collectionCount = 0))
            .first { it.id == "library.empty" }
        assertEquals(Severity.INFO, finding.severity)
    }

    @Test
    fun missingProgressWarnsOnlyAboveHalf() {
        val below = HealthChecks.missingProgress(HealthInput(collectionCount = 10, collectionWithoutProgress = 5))
        assertEquals(Severity.OK, below.severity)   // 50% 不算超阈值
        val above = HealthChecks.missingProgress(HealthInput(collectionCount = 10, collectionWithoutProgress = 6))
        assertEquals(Severity.WARN, above.severity)
    }

    @Test
    fun missingRatingIsInfoNotWarn() {
        val finding = HealthChecks.missingRating(HealthInput(collectionCount = 10, collectionWithoutRating = 10))
        assertEquals(Severity.INFO, finding.severity)
        assertTrue(finding.fix != null)
    }

    @Test
    fun snapshotCheckDistinguishesMissingStaleAndFresh() {
        val missing = HealthChecks.snapshotFreshness(HealthInput(snapshotCount = 0))
        assertEquals(Severity.WARN, missing.severity)
        val stale = HealthChecks.snapshotFreshness(HealthInput(snapshotCount = 3, lastSnapshotAgeHours = 7L * 24 + 1))
        assertEquals(Severity.WARN, stale.severity)
        val fresh = HealthChecks.snapshotFreshness(HealthInput(snapshotCount = 3, lastSnapshotAgeHours = 5L))
        assertEquals(Severity.OK, fresh.severity)
    }

    @Test
    fun cacheOverLimitOnlyWhenLimitConfiguredAndExceeded() {
        val noLimit = HealthChecks.cacheOverLimit(HealthInput(cacheBytes = 10_000L, cacheLimitBytes = 0L))
        assertEquals(Severity.OK, noLimit.severity)
        val over = HealthChecks.cacheOverLimit(HealthInput(cacheBytes = 300L, cacheLimitBytes = 200L))
        assertEquals(Severity.WARN, over.severity)
        val under = HealthChecks.cacheOverLimit(HealthInput(cacheBytes = 100L, cacheLimitBytes = 200L))
        assertEquals(Severity.OK, under.severity)
    }

    @Test
    fun summaryCountsSeverities() {
        val findings = HealthChecks.run(
            HealthInput(collectionCount = 10, snapshotCount = 1, lastSnapshotAgeHours = 1L, cacheLimitBytes = 100L, cacheBytes = 999L),
        )
        val summary = HealthChecks.summarize(findings)
        assertEquals(8, summary.checked)
        assertEquals(1, summary.warnings)
        assertTrue(summary.hasProblem)
    }

    // ── 10.1 玻璃自动档 ───────────────────────────────────────

    @Test
    fun glassTierFollowsSdkAndLowRam() {
        assertEquals(GlassTier.STATIC, resolveGlassTier(sdkInt = 30, isLowRamDevice = false))
        assertEquals(GlassTier.BLUR_ONLY, resolveGlassTier(sdkInt = 31, isLowRamDevice = true))
        assertEquals(GlassTier.FULL, resolveGlassTier(sdkInt = 34, isLowRamDevice = false))
    }

    @Test
    fun effectiveLevelIsTheMoreConservativeOne() {
        // 用户想要全效果，但设备只能静态 → OFF
        assertEquals(GlassEffectLevel.OFF, effectiveGlassLevel(GlassEffectLevel.FULL, GlassTier.STATIC))
        // 低端机：FULL 降为 REDUCED，用户本来选 REDUCED/OFF 时保持不变
        assertEquals(GlassEffectLevel.REDUCED, effectiveGlassLevel(GlassEffectLevel.FULL, GlassTier.BLUR_ONLY))
        assertEquals(GlassEffectLevel.REDUCED, effectiveGlassLevel(GlassEffectLevel.REDUCED, GlassTier.BLUR_ONLY))
        assertEquals(GlassEffectLevel.OFF, effectiveGlassLevel(GlassEffectLevel.OFF, GlassTier.BLUR_ONLY))
        // 设备全开：完全听用户
        assertEquals(GlassEffectLevel.FULL, effectiveGlassLevel(GlassEffectLevel.FULL, GlassTier.FULL))
    }

    @Test
    fun autoModeDefaultsToAutoUnlessExplicitlyManual() {
        assertTrue(GlassAutoMode.isAuto(null))
        assertTrue(GlassAutoMode.isAuto(GlassAutoMode.AUTO))
        assertFalse(GlassAutoMode.isAuto(GlassAutoMode.MANUAL))
    }

    // ── C-10 存储占用 ─────────────────────────────────────────

    @Test
    fun storageBucketsMapByMostSpecificPath() {
        assertEquals(StorageBucket.SNAPSHOT, bucketOf("snapshots/snapshot_1.json.gz"))
        assertEquals(StorageBucket.WALLPAPER, bucketOf("wallpapers/a.webp"))
        assertEquals(StorageBucket.COVER, bucketOf("image_cache/abc"))
        assertEquals(StorageBucket.OTHER, bucketOf("logs/app.log"))
    }

    @Test
    fun storageSummaryAndOversizeUseTheSameInput() {
        val items = listOf(
            StorageItem(StorageBucket.COVER, "a", 10L),
            StorageItem(StorageBucket.COVER, "b", 30L),
            StorageItem(StorageBucket.SNAPSHOT, "s", 5L),
        )
        val summary = summarize(items)
        assertEquals(40L, summary.first { it.bucket == StorageBucket.COVER }.bytes)
        assertEquals(2, summary.first { it.bucket == StorageBucket.COVER }.items)
        assertEquals(0, summary.first { it.bucket == StorageBucket.WALLPAPER }.items)
        assertEquals(45L, totalBytes(items))
        assertTrue(oversizedItems(items, threshold = 20L).single().name == "b")
        assertTrue(needsCleanup(items, limit = 10L))
        assertFalse(needsCleanup(items, limit = 1000L))
    }

    @Test
    fun formatBytesIsHumanReadable() {
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1 KB", formatBytes(1024L))
        assertEquals("1.5 KB", formatBytes(1536L))
        assertEquals("2 MB", formatBytes(2L * 1024 * 1024))
    }

    // ── 13.4 检查更新 ────────────────────────────────────────

    @Test
    fun parseVersionAcceptsPrefixesAndPrerelease() {
        assertEquals(SemanticVersion(1, 2, 1, null), parseVersion("v1.2.1"))
        assertEquals(SemanticVersion(1, 2, 0, "beta01"), parseVersion("1.2.0-beta01"))
        assertEquals(SemanticVersion(1, 2, 0), parseVersion("1.2"))
        assertNull(parseVersion(""))
        assertNull(parseVersion("abc"))
    }

    @Test
    fun isNewerComparesNumericallyNotLexically() {
        assertTrue(isNewerVersion("1.2.1", "1.2.10"))   // 字符串比较会错
        assertFalse(isNewerVersion("1.2.10", "1.2.9"))
        assertTrue(isNewerVersion("1.9.9", "2.0.0"))
        assertFalse(isNewerVersion("1.2.1", "1.2.1"))
        assertTrue(isNewerVersion("1.2.1-beta01", "1.2.1"))  // 正式版覆盖预发布
        assertFalse(isNewerVersion("bad", "1.2.1"))
    }

    @Test
    fun updateDecisionAlwaysCarriesReleasesUrl() {
        val none = decideUpdate("1.2.1", "v1.2.1")
        assertFalse(none.hasUpdate)
        assertTrue(none.url.endsWith("/releases"))
        val newer = decideUpdate("1.2.1", "v1.3.0")
        assertTrue(newer.hasUpdate)
        assertFalse(decideUpdate("1.2.1", null).hasUpdate)
    }

    // ── 10.15 对比度 ────────────────────────────────────────

    @Test
    fun contrastRatioMatchesKnownWcagValues() {
        assertEquals(21.0, contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, contrastRatio(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
    }

    @Test
    fun auditReportsOnlyFailures() {
        val checks = listOf(
            ContrastCheck("ok", 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 4.5),
            ContrastCheck("bad", 0xFF777777.toInt(), 0xFF888888.toInt(), 4.5),
        )
        val failures = auditContrast(checks)
        assertEquals(1, failures.size)
        assertEquals("bad", failures.single().name)
    }

    @Test
    fun compositeBringsSemiTransparentForegroundToOpaque() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        assertEquals(white, compositeArgb(1.0, white, black))
        assertEquals(black, compositeArgb(0.0, white, black))
    }

    // ── 2.5 数据源自检计划 ───────────────────────────────────

    @Test
    fun planProbesCoversEverySourceAndCapability() {
        val sources = listOf(
            SourceCapability("bangumi", "Bangumi", mapOf("搜索" to true, "详情" to true)),
            SourceCapability("steam", "Steam", mapOf("搜索" to true, "日历" to false)),
        )
        val cells = planProbes(sources)
        assertEquals(sources.size * PROBE_CAPABILITIES.size, cells.size)
        val unsupported = unsupportedBySource(cells)
        assertEquals(listOf("日历"), unsupported["steam"]?.filter { it == "日历" })
        assertTrue(summarizePlan(cells).contains("项能力"))
    }

    @Test
    fun planProbesCanBeRestrictedToSomeSources() {
        val sources = listOf(
            SourceCapability("a", "A", emptyMap()),
            SourceCapability("b", "B", emptyMap()),
        )
        assertEquals(1, planProbes(sources, only = setOf("a")).map { it.sourceId }.distinct().size)
        assertTrue(planProbes(emptyList()).isEmpty())
        assertEquals("没有可自检的数据源", summarizePlan(emptyList()))
    }
}
