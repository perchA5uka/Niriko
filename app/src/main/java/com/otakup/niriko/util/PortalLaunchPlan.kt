package com.otakup.niriko.util

import com.otakup.niriko.data.model.PortalAction

/**
 * 传送门「只能拉起、不能带参」时的补偿方案（纯函数，JVM 可测）。
 *
 * 背景：Kazumi 没有注册任何自定义 scheme（已读其源码确认），外部 App 无法把标题写进去；
 * 但它支持搜索，所以这里把标题复制到剪贴板并给一句提示，让用户粘贴后搜索，
 * 比「只拉起一个空白 App」有用得多。
 */
internal data class PortalLaunchPlan(
    /** 需要复制到剪贴板的文本；null 表示这次跳转不需要复制。 */
    val copyToClipboard: String?,
    /** 跳转后要提示给用户的一句话；null 表示无需提示。 */
    val hint: String?,
)

/**
 * 计算一次跳转的前置动作。
 *
 * 只有当**全部**候选动作都是 [PortalAction.LaunchPackage] 时才需要补偿：
 * 只要存在精确 scheme 或网页兜底，目标本身就能定位到条目，不打扰用户。
 */
internal fun portalLaunchPlan(actions: List<PortalAction>, title: String): PortalLaunchPlan {
    val launchOnly = actions.isNotEmpty() && actions.all { it is PortalAction.LaunchPackage }
    val trimmed = title.trim()
    if (!launchOnly || trimmed.isEmpty()) return PortalLaunchPlan(copyToClipboard = null, hint = null)
    return PortalLaunchPlan(
        copyToClipboard = trimmed,
        hint = "已复制「$trimmed」，打开后粘贴即可搜索",
    )
}
