package com.otakup.niriko.util

/**
 * 保存收藏后的反馈与跳转策略。
 *
 * 为什么要有它：改造前屏幕层靠字符串比较 `msg == "记录已保存"` 决定「打点 + 吐司 + 返回上一页」，
 * 而 ViewModel **无条件**写这条消息 —— 写库失败也会退页并提示成功。把文案与「要不要退页」
 * 变成纯函数后，UI 只按状态渲染：失败就留在详情页让用户重试。
 *
 * 当前没有任何入口需要「保存后自动返回」（加入收藏不会自动进编辑层），所以 [shouldAutoBack]
 * 在现有调用点恒为 false；保留它是给将来 `?autoEdit=true` 那类「新建收藏 → 直接编辑 → 保存后回详情」
 * 的路径，届时只需把 `isNewCollection` 传进来，不必再回到字符串比较。
 */
object CollectionSavePolicy {

    const val SAVED_MESSAGE = "记录已保存"
    const val FAILED_MESSAGE = "保存失败，请重试"

    /** 写库结果 → 用户看到的提示。 */
    fun messageFor(written: Boolean): String = if (written) SAVED_MESSAGE else FAILED_MESSAGE

    /** 保存后是否允许离开详情页：只有「新建收藏」且写库成功才自动返回。 */
    fun shouldAutoBack(isNewCollection: Boolean, written: Boolean): Boolean =
        isNewCollection && written
}
