package com.kyant.shapes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置 Shapes 1.2.1 的「来源与可编译性」契约。
 *
 * 为什么需要它：这 11 个文件是**逐字内置**的上游源码（未改 API），升级上游时
 * 必须能机械核对「版本 + 来源 + 依赖面」；同时防止有人顺手引入 Maven 依赖，
 * 或让文件悄悄依赖 androidx.compose.ui 之外的东西（本项目 Kotlin 2.3.10 编译）。
 */
class VendoredShapesTest {

    private val dir = File("src/main/java/com/kyant/shapes")

    private val expected = listOf(
        "Capsule.kt",
        "ContinuousCurvatureRoundedRectangleCornerBuilder.kt",
        "Copy.kt",
        "Lerp.kt",
        "Rectangle.kt",
        "RectangleCornerRadii.kt",
        "RoundedCornerStyle.kt",
        "RoundedRectangle.kt",
        "RoundedRectangleOutline.kt",
        "RoundedRectangularShape.kt",
        "UnevenRoundedRectangle.kt",
    )

    @Test
    fun allElevenUpstreamFilesArePresent() {
        val actual = dir.listFiles { f -> f.name.endsWith(".kt") }!!.map { it.name }.sorted()
        assertEquals(expected.sorted(), actual)
    }

    @Test
    fun everyFileCarriesProvenanceHeaderAndPackage() {
        for (name in expected) {
            val lines = File(dir, name).readLines()
            assertTrue("$name 缺少 provenance 注释", lines.first().contains("Kyant0/Shapes 1.2.1"))
            assertTrue("$name 缺少 Apache-2.0 标注", lines.first().contains("Apache-2.0"))
            assertTrue("$name 的包名被改动", lines.any { it.trim() == "package com.kyant.shapes" })
        }
    }

    @Test
    fun onlyComposeUiAndKotlinStdlibAreImported() {
        val allowed = Regex("^import (androidx\\.compose\\.(runtime|ui)[A-Za-z0-9_.]*|kotlin\\.math\\.[A-Za-z]+)$")
        for (name in expected) {
            for (line in File(dir, name).readLines()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) continue
                assertTrue("$name 引入了授权范围外的依赖：$trimmed", allowed.matches(trimmed))
            }
        }
    }

    @Test
    fun publicApiSurfaceStaysTheUpstreamOne() {
        val capsule = File(dir, "Capsule.kt").readText()
        assertTrue(capsule.contains("class Capsule("))
        assertTrue(capsule.contains("RoundedCornerStyle = RoundedCornerStyle.Continuous"))
        val rect = File(dir, "RoundedRectangle.kt").readText()
        assertTrue(rect.contains("class RoundedRectangle("))
        assertTrue(rect.contains("val cornerRadius: Dp"))
        assertTrue(File(dir, "RoundedRectangularShape.kt").readText().contains("sealed interface RoundedRectangularShape : Shape"))
        assertTrue(File(dir, "RoundedCornerStyle.kt").readText().contains("enum class RoundedCornerStyle"))
    }
}
