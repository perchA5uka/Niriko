package com.otakup.niriko.ui.components

/**
 * 「这张封面已经揭示过」的进程级记账（P2-2）。
 *
 * 为什么需要它：封面淡入用 remember(url) { arrived } 记住「图已经到达」，但列表项滚出组合树
 * 就被销毁，滚回来时 remember 复位成 false —— 每张重新进入视口的封面都会再淡入一次。
 * 作品库/发现页快速回滚时，一屏 12~18 张卡各闪一下 150ms，观感像掉帧。
 *
 * 为什么是进程级而不是 CompositionLocal：要改列表卡调用点就得给每个 Lazy 容器 provide 一层，
 * 而「同一张封面在哪个列表里都不该再闪」本来就是全局语义；条目数量有上限
 * （[COVER_REVEAL_TRACKER_CAPACITY]），长时间浏览不会无界增长。
 *
 * 为什么不直接用 url→Boolean 的 MutableMap：需要「最早记的先进先出淘汰」，
 * [LinkedHashSet] 的插入序天生就是这个语义，命中时也不需要额外分配。
 */
internal class CoverRevealTracker(private val capacity: Int = COVER_REVEAL_TRACKER_CAPACITY) {

    private val revealed = LinkedHashSet<String>(capacity)

    /** 是否已经揭示过（url 为 null 的占位图不参与记账）。 */
    fun isRevealed(url: String?): Boolean = url != null && revealed.contains(url)

    /**
     * 记一次「已揭示」；返回这次是不是首次（调用方不必先查再记）。
     *
     * 同 url 再次命中会移到队尾：常被看到的封面不会被一次冷门浏览挤掉。
     */
    fun recordRevealed(url: String?): Boolean {
        if (url == null) return false
        if (revealed.remove(url)) {
            revealed.add(url)
            return false
        }
        revealed.add(url)
        while (revealed.size > capacity) {
            val oldest = revealed.first()
            revealed.remove(oldest)
        }
        return true
    }

    /** 当前记账条数（测试断言容量用；生产代码不需要）。 */
    fun size(): Int = revealed.size
}

/** 记账上限：一屏 12~18 张、翻十屏的封面量；再往上淘汰最旧的，内存占用可忽略。 */
internal const val COVER_REVEAL_TRACKER_CAPACITY = 512

/**
 * 进程级实例：封面组件直接用它，不改调用点。
 *
 * 刻意不做 reset：配置变更 / 页面重建后回来，同一张封面仍不应再闪。
 */
internal val ProcessCoverRevealTracker = CoverRevealTracker()
