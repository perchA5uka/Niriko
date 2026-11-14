package com.otakup.niriko.util

// 屏幕级「阶段替换」的淡入包络：骨架 → 正文（统计页）、列表 ↔ 网格（作品库）。
//
// 为什么不用 AnimatedContent / Crossfade：
// 这两处换掉的内容都是 LazyColumn / LazyVerticalGrid。AnimatedContent/Crossfade 在过渡期间会同时组合
// 新旧两棵内容，等于双份 item、双份图片请求、双份测量，直接违背
// docs/v1.2.2/02-motion-and-transitions.md §B-7 写下的性能红线（同屏不得同时组合两棵 Lazy）。
// 而这两处换内容的真实成本是 O(1)（只是换了一个滚动状态源 / 换了一批 item），刺眼的只是
// 「同一帧整屏变脸」——给外层容器一次 alpha 包络，就能把变脸变成呼吸式替换。

/** 列表 ↔ 网格切换时的包络起始不透明度。刻意不为 0：为 0 会露出一帧纯壁纸底，看起来像闪黑。 */
const val VIEW_TOGGLE_DIM_ALPHA: Float = 0.6f

/** 骨架 → 内容首次揭示的起始不透明度。比视图切换更暗一档：这是「从无到有」，暗一档更像淡入。 */
const val PHASE_REVEAL_DIM_ALPHA: Float = 0.55f

/** 包络回满的时长（ms）。取 180 而非 effectsFast 的弹簧：整屏发灰要尽快结束，不能有可感知的尾巴。 */
const val PHASE_REVEAL_DURATION_MS: Int = 180

/**
 * 是否播放包络动画。
 *
 * @param previous 上一次的阶段标识；**首次组合必须传 null** —— 否则每次进入页面都会先压暗一次，
 *   那是刷新感而不是过渡感（同 B-12 评分弹跳踩过的坑：LaunchedEffect 首次组合也会执行）。
 */
fun shouldPlayPhaseReveal(previous: Any?, next: Any?, reduceMotion: Boolean): Boolean = !reduceMotion && previous != null && previous != next

/**
 * 包络的起始不透明度；reduceMotion 下返回 1f（不播动画 = 直接全亮）。
 *
 * @param dimAlpha [VIEW_TOGGLE_DIM_ALPHA] 或 [PHASE_REVEAL_DIM_ALPHA]
 */
fun phaseRevealStartAlpha(dimAlpha: Float, reduceMotion: Boolean): Float = if (reduceMotion) 1f else dimAlpha
