package com.otakup.niriko.util

/**
 * 评分数字「弹一下」的触发判据（B-12：触发点错）。
 *
 * 改造前是 `LaunchedEffect(rating) { scale.snapTo(1.15f); ... }` —— `LaunchedEffect` 在**首次组合**
 * 时就会执行一次（key 的初值也算一次启动），于是「刚进详情页、还没打分」就自己弹了一下；
 * 而用户真的点星时反而只有数字变化。
 *
 * 这里把「该不该播」抽成纯函数，条件只有两条：
 * 1. 分值真的变了（首帧 previous == next，不播）；
 * 2. reduceMotion 关掉时一律不播（反馈类动画在「移除动画」下应静默，只保留数字更新）。
 *
 * 纯函数放在 util 而不是 Composable 里：这样单测能直接断言，不必起 UI 测试。
 */
fun shouldPlayRatingBump(previous: Float, next: Float, reduceMotion: Boolean): Boolean = !reduceMotion && previous != next
