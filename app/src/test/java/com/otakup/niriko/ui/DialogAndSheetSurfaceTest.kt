package com.otakup.niriko.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B7d / B7e：对话框与通用弹层的「圆角 + 材质」收敛契约。
 *
 * 这几处都是纯外观改动（组合函数没有可断言的运行时输出），所以用源码契约钉住两件事：
 * 1. 换过的 3 个对话框必须 24dp + 表面色 token，且**每个文件只换 1 个**（B7d 明确不一次全换）；
 * 2. 两个弹层（自研 / M3 ModalBottomSheet）的顶角表达式必须**逐字一致**，否则下一轮又会分叉。
 */
class DialogAndSheetSurfaceTest {

    private fun source(path: String) =
        File("src/main/java/com/otakup/niriko/" + path).readText()

    private fun count(hay: String, needle: String) = hay.split(needle).size - 1

    private val sheetRadiusCall = "shape = RoundedCornerShape(NirikoShapes.Sheet)"
    private val sheetCornerExpr = "RoundedCornerShape(topStart = NirikoShapes.Sheet, topEnd = NirikoShapes.Sheet)"

    @Test
    fun threeHighFrequencyDialogsUseSheetRadiusAndSurfaceToken() {
        val files = listOf(
            "ui/settings/pages/SyncBackupSettingsScreen.kt",
            "ui/settings/pages/DataSourceSettingsScreen.kt",
            "ui/settings/pages/WallpaperLibraryScreen.kt",
        )
        for (file in files) {
            val text = source(file)
            assertEquals("$file 只应改 1 个对话框（B7d 不一次全换）", 1, count(text, sheetRadiusCall))
            assertTrue("$file 缺少表面色 token", text.contains("containerColor = MaterialTheme.colorScheme.surfaceContainerHigh"))
            assertTrue("$file 缺少 NirikoShapes import", text.contains("import com.otakup.niriko.ui.theme.NirikoShapes"))
            assertTrue(
                "$file 缺少 RoundedCornerShape import",
                text.contains("import androidx.compose.foundation.shape.RoundedCornerShape"),
            )
        }
    }

    @Test
    fun remainingDialogsInTheSameFilesKeepMaterialDefaults() {
        // 同文件里还有其它对话框：它们必须仍是 M3 默认（只有「同步冲突」「Steam Key」「壁纸长按操作」换了）
        val sync = source("ui/settings/pages/SyncBackupSettingsScreen.kt")
        assertTrue("SyncBackup 的对话框数量变了，需同步更新本测试", count(sync, "AlertDialog(") >= 3)
        val ds = source("ui/settings/pages/DataSourceSettingsScreen.kt")
        assertTrue("DataSource 的对话框数量变了，需同步更新本测试", count(ds, "AlertDialog(") >= 3)
        assertEquals("DataSource 只换 1 个弹窗", 1, count(ds, sheetRadiusCall))
    }

    @Test
    fun nonModalSheetUsesSheetTokenInsteadOfLiteral() {
        val text = source("ui/components/NirikoNonModalSheet.kt")
        assertTrue(text.contains(sheetCornerExpr))
        assertFalse(
            "弹层圆角不得退回 24.dp 字面量",
            text.contains("RoundedCornerShape(topStart = 24.dp"),
        )
        assertTrue(text.contains("import com.otakup.niriko.ui.theme.NirikoShapes"))
    }

    @Test
    fun sharePreviewSheetMatchesNonModalSheetGeometry() {
        val text = source("ui/share/SharePreviewSheet.kt")
        assertTrue(text.contains("shape = $sheetCornerExpr"))
        assertTrue(text.contains("containerColor = MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertTrue(text.contains("import com.otakup.niriko.ui.theme.NirikoShapes"))
        // 两处弹层同源：同一句形状表达式
        assertTrue(source("ui/components/NirikoNonModalSheet.kt").contains(sheetCornerExpr))
        assertTrue(text.contains(sheetCornerExpr))
    }
}
