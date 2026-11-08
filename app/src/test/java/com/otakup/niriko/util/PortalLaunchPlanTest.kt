package com.otakup.niriko.util

import com.otakup.niriko.data.model.PortalAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 传送门补偿动作单测。
 *
 * 回归的是「Kazumi 只能拉起、进去是空白搜索页」：没有 scheme 的目标至少要把标题
 * 复制到剪贴板，否则这次跳转对用户毫无价值。
 */
class PortalLaunchPlanTest {

    @Test
    fun `仅拉起的目标会复制标题并给提示`() {
        val plan = portalLaunchPlan(
            listOf(PortalAction.LaunchPackage("com.predidit.kazumi")),
            "正相反的你和我",
        )
        assertEquals("正相反的你和我", plan.copyToClipboard)
        assertEquals("已复制「正相反的你和我」，打开后粘贴即可搜索", plan.hint)
    }

    @Test
    fun `带精确 scheme 的目标不做补偿`() {
        val plan = portalLaunchPlan(
            listOf(
                PortalAction.UriScheme("bilibili://bangumi/season/123"),
                PortalAction.WebUrl("https://www.bilibili.com/bangumi/play/ss123"),
            ),
            "正相反的你和我",
        )
        assertNull(plan.copyToClipboard)
        assertNull(plan.hint)
    }

    @Test
    fun `scheme 与拉起混排时不做补偿`() {
        val plan = portalLaunchPlan(
            listOf(
                PortalAction.UriScheme("bilibili://search?keyword=x"),
                PortalAction.LaunchPackage("com.predidit.kazumi"),
            ),
            "标题",
        )
        assertNull(plan.copyToClipboard)
    }

    @Test
    fun `空标题不复制空字符串`() {
        val plan = portalLaunchPlan(
            listOf(PortalAction.LaunchPackage("com.predidit.kazumi")),
            "   ",
        )
        assertNull(plan.copyToClipboard)
        assertNull(plan.hint)
    }

    @Test
    fun `空动作列表不做补偿`() {
        val plan = portalLaunchPlan(emptyList(), "标题")
        assertNull(plan.copyToClipboard)
    }
}
