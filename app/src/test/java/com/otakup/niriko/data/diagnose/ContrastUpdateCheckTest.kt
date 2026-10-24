package com.otakup.niriko.data.diagnose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10.15 深色对比度收敛 + 13.4 检查更新的纯逻辑测试。
 *
 * 两条都不碰 Android / 网络：对比度是算术，版本与 JSON 是字符串处理。
 * 与 [DiagnosePureTest] 分文件存放，避免同一文件被多个任务同时改。
 */
class ContrastUpdateCheckTest {

    // ── 10.15 ensureContrast：调整后达标、色相不变 ──────────────

    @Test
    fun darkGrayOnBlackIsLiftedToReadable() {
        val bg = 0xFF000000L
        val fg = 0xFF6E6E6EL                       // 约 4.1:1，略低于正文下限
        assertTrue(contrastRatio(fg.toInt(), bg.toInt()) < 4.5)

        val fixed = ensureContrast(fg, bg)

        assertTrue(
            "调整后仍未达标: " + contrastRatio(fixed.toInt(), bg.toInt()),
            contrastRatio(fixed.toInt(), bg.toInt()) + 1e-9 >= 4.5,
        )
        assertEquals(0xFFL, (fixed ushr 24) and 0xFFL)   // 输出必须不透明
        assertNeutral(fixed)                              // 灰色输入 → 仍是中性灰（无色相漂移）
    }

    @Test
    fun lightGrayOnWhiteIsDarkenedToReadable() {
        val bg = 0xFFFFFFFFL
        val fg = 0xFFB0B0B0L                       // 约 2.2:1，浅灰放在白底上
        assertTrue(contrastRatio(fg.toInt(), bg.toInt()) < 4.5)

        val fixed = ensureContrast(fg, bg)

        assertTrue(
            "调整后仍未达标: " + contrastRatio(fixed.toInt(), bg.toInt()),
            contrastRatio(fixed.toInt(), bg.toInt()) + 1e-9 >= 4.5,
        )
        assertNeutral(fixed)
    }

    @Test
    fun alreadyCompliantColorIsReturnedUnchanged() {
        assertEquals(0xFFFFFFFFL, ensureContrast(0xFFFFFFFFL, 0xFF000000L))   // 21:1
        assertEquals(0xFF000000L, ensureContrast(0xFF000000L, 0xFFFFFFFFL))   // 21:1
    }

    @Test
    fun coloredForegroundKeepsHue() {
        val bg = 0xFF000000L
        val fg = 0xFF2B4C7EL                       // 深蓝：在黑底上不达标
        assertTrue(contrastRatio(fg.toInt(), bg.toInt()) < 4.5)

        val fixed = ensureContrast(fg, bg)

        assertTrue(contrastRatio(fixed.toInt(), bg.toInt()) + 1e-9 >= 4.5)
        val before = hueOf(fg.toInt())
        val after = hueOf(fixed.toInt())
        assertTrue("色相丢了: " + before + " → " + after, before > 0f && after > 0f)
        assertTrue("色相漂移过大: " + before + " → " + after, hueDistance(before, after) < 3f)
        assertTrue("被改成了灰色: " + fixed, saturationOf(fixed.toInt()) > 0.05f)
    }

    @Test
    fun semiTransparentForegroundIsCompositedBeforeAdjusting() {
        // 50% 的深灰放在黑底上，合成后约 0x202020（1.3:1）；调整必须以「合成后的颜色」为准，
        // 且返回不透明结果 —— 否则调整量会在实际绘制时被 alpha 打折。
        val fixed = ensureContrast(0x80404040L, 0xFF000000L)
        assertEquals(0xFFL, (fixed ushr 24) and 0xFFL)
        assertTrue(contrastRatio(fixed.toInt(), 0xFF000000.toInt()) + 1e-9 >= 4.5)
    }

    @Test
    fun defaultDarkTokenAlreadyPassesSoTheTokenStaysUnchanged() {
        // 默认深色板 onSurfaceVariant(0xFFC2C9BD) on surface(0xFF121412) 约 11:1：
        // 幂等 → Theme 里这次收敛对默认色板是 no-op，只兜住动态色等低对比来源。
        assertEquals(0xFFC2C9BDL, ensureContrast(0xFFC2C9BDL, 0xFF121412L))
    }

    @Test
    fun ratioIsConfigurableForLargeText() {
        val fixed = ensureContrast(0xFF3A3A3AL, 0xFF000000L, ratio = 3f)
        assertTrue(contrastRatio(fixed.toInt(), 0xFF000000.toInt()) + 1e-9 >= 3.0)
    }

    // ── 13.4 检查更新：版本比较 + JSON 解析（固定字符串） ────────

    private val latestJson = """
        {
          "url": "https://api.github.com/repos/perchA5uka/Niriko/releases/1",
          "tag_name": "v1.3.0",
          "html_url": "https://github.com/perchA5uka/Niriko/releases/tag/v1.3.0",
          "published_at": "2026-10-01T09:00:00Z",
          "name": "v1.3.0",
          "prerelease": false
        }
    """.trimIndent()

    @Test
    fun parsesLatestReleaseFromGitHubPayload() {
        val info = parseLatestRelease(latestJson)
        assertNotNull(info)
        assertEquals("v1.3.0", info!!.tag)
        assertEquals("https://github.com/perchA5uka/Niriko/releases/tag/v1.3.0", info.htmlUrl)
        assertEquals("2026-10-01T09:00:00Z", info.publishedAt)
    }

    @Test
    fun missingHtmlUrlFallsBackToReleasesPage() {
        val info = parseLatestRelease("""{"tag_name":"v1.3.0","published_at":null}""")
        assertNotNull(info)
        assertTrue(info!!.htmlUrl.endsWith("/releases"))
        assertNull(info.publishedAt)                 // JSON null != 字符串 "null"
    }

    @Test
    fun malformedOrTaglessPayloadYieldsNull() {
        assertNull(parseLatestRelease("not json"))
        assertNull(parseLatestRelease("""{"name":"v1.3.0"}"""))
        assertNull(parseLatestRelease("""{"tag_name":null}"""))
        assertNull(parseLatestRelease("""{"tag_name":"   "}"""))
    }

    @Test
    fun updateDecisionMatchesTheScreenLogic() {
        // 屏幕上的判断就是 isNewerVersion(BuildConfig.VERSION_NAME, release.tag)
        assertTrue(isNewerVersion("1.2.0", "v1.3.0"))
        assertTrue(isNewerVersion("1.2.0", "v1.2.1"))
        assertTrue(!isNewerVersion("1.3.0", "v1.3.0"))
        assertTrue(!isNewerVersion("1.3.1", "v1.3.0"))
        assertTrue(decideUpdate("1.2.0", "v1.3.0").hasUpdate)
        assertEquals(releasesUrl(), decideUpdate("1.2.0", null).url)
    }

    @Test
    fun latestReleaseApiPointsAtThisRepository() {
        assertTrue(LATEST_RELEASE_API.contains("api.github.com"))
        assertTrue(LATEST_RELEASE_API.contains("perchA5uka/Niriko"))
        assertTrue(LATEST_RELEASE_API.endsWith("/releases/latest"))
    }

    // ── 辅助：中性灰 / 色相 ──────────────────────────────────

    private fun assertNeutral(argb: Long) {
        val r = (argb shr 16) and 0xFFL
        val g = (argb shr 8) and 0xFFL
        val b = argb and 0xFFL
        assertEquals("灰色不该被染上颜色", r, g)
        assertEquals("灰色不该被染上颜色", g, b)
    }

    /** HSV 色相（0..360）；灰/黑返回 -1。 */
    private fun hueOf(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d < 1e-4f) return -1f
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return (h * 60f + 360f) % 360f
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return minOf(d, 360f - d)
    }

    private fun saturationOf(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        if (max <= 0f) return 0f
        return (max - minOf(r, g, b)) / max
    }
}
