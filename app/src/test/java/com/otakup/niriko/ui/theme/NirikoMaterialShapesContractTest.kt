package com.otakup.niriko.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D4：M3 [androidx.compose.material3.Shapes] 与 Niriko 形状令牌的映射契约（v1.2.2）。
 *
 * 为什么需要它：根因 R1 是"令牌双轨"——`MaterialTheme.shapes` 一直是 M3 默认 4/8/12/16/28，
 * 而 Niriko 自己的圆角是 20/16/12/24/4/999。修法是把五档逐一映射到**已有真实取值**上；
 * 这份测试挡住两类回退：
 * 1. 有人把某档改回 M3 默认（如 extraLarge 28dp）——那正是"弹层 28 / 卡片 20"的旧病；
 * 2. 有人把 Pill(999) 塞进额外档——会让 ModalBottomSheet / Badge 这类 M3 容器变成药丸。
 *
 * 取值方式：不读 NinrikoShapes 常量再断言等号（那测不到映射本身），
 * 而是从 Shape 生成的真实 Outline 里量圆角半径，等价于"屏幕上的角"。
 */
class NirikoMaterialShapesContractTest {

    /** 量一个 CornerBasedShape 的实际圆角半径（px，density = 1）。 */
    private fun radiusOf(shape: CornerBasedShape): Float {
        val outline = shape.createOutline(Size(200f, 200f), LayoutDirection.Ltr, Density(1f))
        assertTrue("期望圆角矩形，实际 " + outline::class.java.simpleName, outline is Outline.Rounded)
        return (outline as Outline.Rounded).roundRect.topLeftCornerRadius.x
    }

    private fun themeShapes() = NirikoMaterialShapes

    @Test
    fun 五档圆角逐一等于设计令牌() {
        val s = themeShapes()
        assertEquals("extraSmall 必须是 RowInner(4dp)", NirikoShapes.RowInner.value, radiusOf(s.extraSmall), 0f)
        assertEquals("small 是列表内小件(8dp)", 8f, radiusOf(s.small), 0f)
        assertEquals("medium 必须是 Control(12dp)", NirikoShapes.Control.value, radiusOf(s.medium), 0f)
        assertEquals("large 必须是 Section(16dp)", NirikoShapes.Section.value, radiusOf(s.large), 0f)
        assertEquals("extraLarge 必须是 Sheet(24dp)，不是 M3 默认 28dp", NirikoShapes.Sheet.value, radiusOf(s.extraLarge), 0f)
    }

    @Test
    fun 弹层档不能退化成胶囊() {
        // Pill = 999dp：若被塞进 extraLarge，ModalBottomSheet / Badge / DropdownMenu 会变成药丸
        val radius = radiusOf(themeShapes().extraLarge)
        assertTrue("extraLarge 半径 $radius 过大，疑似映射成了 Pill", radius < 32f)
    }

    @Test
    fun Theme真正传入了这套形状() {
        val theme = File("src/main/java/com/otakup/niriko/ui/theme/Theme.kt").readText()
        assertTrue("Theme.kt 没有传 shapes = NirikoMaterialShapes：R1 令牌双轨会复活", theme.contains("shapes = NirikoMaterialShapes"))
    }

    @Test
    fun 二级页标题档存在且不是M3默认的常规字重() {
        // V-16：headlineSmall 曾经没进 Typography，落回 M3 默认 24sp/w400，比主页更"淡"
        val style = NirikoTypography.headlineSmall
        assertEquals(24.sp, style.fontSize)
        assertEquals(FontWeight.SemiBold, style.fontWeight)
        // 与主页 headlineMedium（28sp/w600）保持同级字重，只降字号
        assertEquals(NirikoTypography.headlineMedium.fontWeight, style.fontWeight)
    }
}
