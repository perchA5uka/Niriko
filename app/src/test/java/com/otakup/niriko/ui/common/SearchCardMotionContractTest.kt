package com.otakup.niriko.ui.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-11：搜索卡的三条收口契约（规格 / 降级 / 帧阶段）。
 *
 * 为什么只锁这三件事而不是锁观感：搜索卡是 900 行的交互状态机，真机逐帧验证是唯一的观感判据
 * （本轮拿不到设备）。能在 CI 里守的是**可被 grep 证明**的部分：
 * 1. 不再有手写 spring/tween 字面量（同一物理量四套 spec 是末段对齐抖动的根因）；
 * 2. 每条动画都有 reduceMotion 降级；
 * 3. 尺寸/位移动画的读取时机从组合期挪到布局期（否则展开期间整张卡片每帧重组）。
 *
 * 反过来说：本测试绿**不代表**真机不抖，只代表规格没有退回改造前。
 */
class SearchCardMotionContractTest {

    private val code = stripComments(
        File("src/main/java/com/otakup/niriko/ui/common/IosStyleSearchComponent.kt").readText(),
    )

    @Test
    fun 不再手写弹簧与时长字面量() {
        assertFalse("搜索卡不得再出现手写 spring(", code.contains("spring("))
        val tweenLiterals = Regex("tween(?:<[^>]*>)?\\(\\s*(?:durationMillis\\s*=\\s*)?[0-9]+").findAll(code).count()
        assertEquals("搜索卡不得再出现 tween(数字)（走 NirikoMotionSpecs 六档）", 0, tweenLiterals)
    }

    @Test
    fun 六档令牌真的接上了且每处都有降级() {
        val tokenUses = Regex("NirikoMotionSpecs\\.[a-zA-Z]+").findAll(code).count()
        assertTrue("tokenUses=" + tokenUses, tokenUses >= 8)
        val guardUses = Regex("orSnap\\(reduceMotion\\)").findAll(code).count()
        assertTrue("reduceMotion 降级点只有 " + guardUses + " 处（每处动画都要有）", guardUses >= 6)
        assertTrue("关闭钮进/退场缺少 reduceMotion 分支", code.contains("EnterTransition.None") && code.contains("ExitTransition.None"))
    }

    @Test
    fun 尺寸与锚点位移在布局期读而不是组合期() {
        // 改造前：requiredWidth(cardW) / offset(x = anchorX + …) / size(anchorSize) 全是组合期读，
        // 展开的每一帧都会重组整张卡片。现在交给布局期修饰符。
        assertTrue("卡片尺寸应走 animatedRequiredSize", code.contains("animatedRequiredSize(cardW, cardH)"))
        assertTrue("锚点尺寸应走 animatedRequiredSize", code.contains("animatedRequiredSize(anchorSize)"))
        assertTrue("锚点位移应走 offset { } lambda", code.contains(".offset {"))
        assertFalse("不得退回 requiredWidth(cardW)", code.contains("requiredWidth(cardW)"))
        assertFalse("不得退回 offset(x = anchorX", code.contains(".offset(x = anchorX"))
        assertTrue("surfaceAnimating 应用 derivedStateOf 包住", code.contains("derivedStateOf"))
        assertFalse("不得在组合期读 viewModel.dragProgress", code.contains("val progress = viewModel.dragProgress"))
    }

    @Test
    fun 搜索菜单的胶囊与类型格有反馈和选中语义() {
        // F08：改造前 indication = null 且没有替代通道 —— 按下零反馈、读屏听不出选中。
        assertTrue("模式胶囊应统一到 NirikoFilterPill", code.contains("NirikoFilterPill("))
        assertTrue("类型格补按压高亮（与列表行同值 0.08）", code.contains("NirikoPressSpecs.ROW_PRESS_ALPHA"))
        assertTrue("类型格补 selected 语义", code.contains("this.selected = selected"))
    }

    @Test
    fun 关闭钮命中区补到48dp() {
        // F12：视觉 44dp 的 X 单手够不到；外壳 48dp 承接点击，玻璃圆钮留在原位。
        assertTrue("关闭钮需要 48dp 命中区常量", code.contains("SearchCloseTouchTarget = 48.dp"))
        assertTrue("点击必须挂在外壳上", code.contains(".size(SearchCloseTouchTarget)"))
    }

    private fun stripComments(text: String): String {
        val noBlock = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == '/' && text[i + 1] == '*') {
                val end = text.indexOf("*/", i + 2)
                i = if (end < 0) text.length else end + 2
                noBlock.append('\n')
            } else {
                noBlock.append(text[i])
                i++
            }
        }
        return noBlock.lineSequence().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }
    }
}
