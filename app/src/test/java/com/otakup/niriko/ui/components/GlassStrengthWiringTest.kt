package com.otakup.niriko.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Niriko 27 · S1：玻璃强度的接线护栏。
 *
 * GlassStrengthTest 保证数学没错；这里保证**每个玻璃表面真的在用它**。否则有人重构表面时
 * 悄悄丢掉缩放，设置页的滑条就成了摆设，而单测和 CI 都看不出来。
 * 同样的「只增不减」思路见 ControlLanguagePolicyTest。
 */
class GlassStrengthWiringTest {

    private val uiRoot = File("src/main/java/com/otakup/niriko/ui")

    /** 文件 → 至少应出现几处 GlassStrength.scaledAlpha（即该表面有几处 tint 需要缩放）。 */
    private val surfaces = mapOf(
        "components/GlassCard.kt" to 1,
        "components/GlassSectionCard.kt" to 1,
        "components/BlurredGlassCard.kt" to 2,
    )

    private fun code(path: String): String = File(uiRoot, path).readLines()
        .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") || it.trimStart().startsWith("/*") }
        .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun 每个玻璃表面都按强度缩放底色() {
        surfaces.forEach { (path, minimum) ->
            val hits = Regex("GlassStrength\\.scaledAlpha\\(").findAll(code(path)).count()
            assertTrue(path + " 里 GlassStrength.scaledAlpha 只有 " + hits + " 处，应不少于 " + minimum + "：玻璃强度滑条对它失效了", hits >= minimum)
        }
    }

    @Test
    fun 每个玻璃表面都从当前主题读取强度() {
        surfaces.keys.forEach { path ->
            assertTrue(path + " 没有读取 LocalGlassStrength", code(path).contains("LocalGlassStrength.current"))
        }
    }

    @Test
    fun 主界面向整棵树提供玻璃强度() {
        val main = File("src/main/java/com/otakup/niriko/MainActivity.kt").readText()
        assertEquals(1, Regex("LocalGlassStrength provides settings\\.glassStrength").findAll(main).count())
    }

    @Test
    fun 备份对玻璃强度读写对称() {
        // settingsToJson / 解析都是 private，不值得为测它改可见性；只守「两侧都有」，防止只写不读
        val backup = File("src/main/java/com/otakup/niriko/data/backup/BackupManager.kt").readText()
        assertEquals("备份没有写出 glassStrength", 1, Regex("put\\(\"glassStrength\"").findAll(backup).count())
        assertEquals("备份没有读回 glassStrength", 1, Regex("o\\[\"glassStrength\"\\]").findAll(backup).count())
        val store = File("src/main/java/com/otakup/niriko/data/settings/SettingsDataStore.kt").readText()
        assertEquals("DataStore 的读取 / 写入 / 备份恢复三处应各引用一次 Keys.GLASS_STRENGTH（键定义不带 Keys. 前缀，不计）", 3, Regex("Keys\\.GLASS_STRENGTH").findAll(store).count())
    }

    @Test
    fun 零引用的玻璃底色令牌没有回来() {
        val tokens = File(uiRoot, "components/liquidglass/LiquidGlassTokens.kt").readText()
        assertTrue("LightGlassBase 是零引用的死令牌（α0.85 与真实卡片底色不符），已删除，不要加回来", !tokens.contains("val LightGlassBase"))
    }
}
