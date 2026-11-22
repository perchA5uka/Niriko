package com.otakup.niriko.ui.theme

/**
 * 触感语义（Niriko 27 · S0 空壳，见 docs/design-language-plan-2026-11-niriko27.md §3.6）。
 *
 * 现状是各处直接调 HapticFeedbackType，语义混杂。S5 会把这六个语义映射到平台触感并接入
 * 控件；S0 只定语义与「是否配套形变」这条规则，不依赖 Compose / Android，可直接单测。
 *
 * 原则（计划 §2.2 第 6 条）：可逆操作 = 触感 + 形变；不可逆操作 = 触感 + 明确的视觉代价，
 * 不靠形变安抚。
 */
enum class NirikoHaptic(
    /** 是否应配套按压形变（可逆操作为真；不可逆的破坏性操作为假）。 */
    val pairsWithMorph: Boolean,
) {
    /** 确认一次提交（保存、完成）。 */
    Confirm(pairsWithMorph = true),

    /** 分段控件 / 滑块经过刻度。 */
    SegmentTick(pairsWithMorph = true),

    /** 开关打开。 */
    ToggleOn(pairsWithMorph = true),

    /** 开关关闭。 */
    ToggleOff(pairsWithMorph = true),

    /** 手势越过触发阈值（下拉刷新、侧滑操作生效）。 */
    ThresholdActivate(pairsWithMorph = true),

    /** 不可逆的破坏性操作（删除、清空）。 */
    Destructive(pairsWithMorph = false),
}
