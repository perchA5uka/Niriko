package com.otakup.niriko.ui.theme

import androidx.compose.ui.graphics.Color
import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [typeColorMap] 纯函数测试。
 *
 * 它是 chartTypeColor 的映射本体，也是日历圆点（CalendarCard.buildMarks /
 * CalendarDaySheet）等非 @Composable 场景取色的唯一来源；历史上日历用固定
 * CalendarTypeColors 色表、图表用主题派生色，换主题色后两者会脱节。
 */
class ChartPaletteTest {

    private val palette = listOf(
        Color(0xFF111111),
        Color(0xFF222222),
        Color(0xFF333333),
        Color(0xFF444444),
        Color(0xFF555555),
        Color(0xFF666666),
    )

    @Test
    fun `前六种类型按色板顺序一一对应`() {
        val map = typeColorMap(palette)
        assertEquals(palette[0], map[SubjectType.ANIME])
        assertEquals(palette[1], map[SubjectType.MANGA])
        assertEquals(palette[2], map[SubjectType.BOOK])
        assertEquals(palette[3], map[SubjectType.GAME])
        assertEquals(palette[4], map[SubjectType.MUSIC])
        assertEquals(palette[5], map[SubjectType.REAL])
    }

    @Test
    fun `PERSON 与 OTHER 复用色板最后一色`() {
        val map = typeColorMap(palette)
        assertEquals(palette[5], map[SubjectType.PERSON])
        assertEquals(palette[5], map[SubjectType.OTHER])
    }

    @Test
    fun `覆盖全部类型枚举且数量一致`() {
        val map = typeColorMap(palette)
        SubjectType.entries.forEach { type ->
            assertTrue("缺少类型映射: " + type.name, map.containsKey(type))
        }
        assertEquals(SubjectType.entries.size, map.size)
    }

    @Test
    fun `直接使用色板颜色不做二次派生`() {
        val map = typeColorMap(palette)
        // 不做色调/明度调整，映射结果必须就是色板原值
        assertEquals(palette[0], map[SubjectType.ANIME])
        assertTrue(palette.containsAll(map.values))
    }

    @Test
    fun `空色板不抛异常（越界由调用方保证）`() {
        // 色板长度不足时 getValue 会抛 NoSuchElementException，
        // 这里固定「映射本身不做下标校验」的既有语义。
        try {
            typeColorMap(emptyList())
            throw AssertionError("空色板应当抛出异常")
        } catch (expected: IndexOutOfBoundsException) {
            // 预期路径
        }
    }
}
