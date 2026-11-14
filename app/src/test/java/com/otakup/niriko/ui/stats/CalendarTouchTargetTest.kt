package com.otakup.niriko.ui.stats

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F12：日历日期条的触控目标。
 *
 * 审计证据：日期条每格视觉宽 38dp，且当时**38dp 同时是命中区**——横滑连点时手指只要偏
 * 出 5dp 就会点错日期。修法不是把格子画大（7 天窗口会显得臃肿），而是把「视觉宽」与
 * 「命中宽」拆开：视觉仍 38dp，外层点击盒 48dp。
 *
 * 这里锁三件事：常量本身就是 48dp、点击盒真的用了命中常量、38dp 不再以字面量形式出现
 * （否则"常量还在、调用点退回字面量"这种回退在源码里看起来一样）。
 */
class CalendarTouchTargetTest {

    private val calendarCard = File("src/main/java/com/otakup/niriko/ui/stats/CalendarCard.kt").readText()

    @Test
    fun `日期条命中盒达到48dp`() {
        assertEquals("F12 的命中宽固定为 Material 最小可点目标 48dp", 48.dp, DATE_STRIP_TOUCH_WIDTH)
        assertTrue(DATE_STRIP_TOUCH_HEIGHT >= 48.dp)
        assertTrue("视觉宽必须小于命中宽，否则只是把格子整体放大", DATE_STRIP_VISUAL_WIDTH < DATE_STRIP_TOUCH_WIDTH)
    }

    @Test
    fun `日期条点击盒使用命中常量`() {
        assertTrue(calendarCard.contains(".width(DATE_STRIP_TOUCH_WIDTH)"))
        assertTrue(calendarCard.contains(".heightIn(min = DATE_STRIP_TOUCH_HEIGHT)"))
        assertTrue(calendarCard.contains(".clickable { onPickDate(date) }"))
    }

    @Test
    fun `不再回退成38dp裸字面量`() {
        assertFalse(calendarCard.contains("width(38.dp)"))
    }

    @Test
    fun `修好后的日期条不再用6dp固定间距补命中区`() {
        // 命中盒 48dp 时若仍留 6dp 间距，pitch 变成 54dp、31 天窗口会明显变稀；
        // 现在间距并入命中盒（左右各 5dp），pitch 恰好 48dp。
        assertTrue(calendarCard.contains("horizontalArrangement = Arrangement.spacedBy(0.dp)"))
    }
}
