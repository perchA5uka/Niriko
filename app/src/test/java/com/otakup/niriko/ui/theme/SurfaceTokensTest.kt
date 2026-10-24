package com.otakup.niriko.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B7a：新增的两个「表面」令牌。
 *
 * 这两个值不是拍脑袋：Sheet 对齐 NirikoNonModalSheet 的既有顶部圆角（24dp），
 * RowInner 对齐 SettingsGroup 的既有行内圆角（4dp）。B7a 只建 token、不替换调用点，
 * 所以本测试只钉取值，不改变任何界面观感。
 */
class SurfaceTokensTest {

    @Test
    fun sheetTokenMatchesNonModalSheetBaseline() {
        assertEquals(24.dp, NirikoShapes.Sheet)
    }

    @Test
    fun rowInnerTokenMatchesSettingsRowBaseline() {
        assertEquals(4.dp, NirikoShapes.RowInner)
        // 行内圆角必须小于组容器圆角，否则「组 > 行」的层级在视觉上消失
        assertTrue(NirikoShapes.RowInner < NirikoShapes.Card)
    }
}
