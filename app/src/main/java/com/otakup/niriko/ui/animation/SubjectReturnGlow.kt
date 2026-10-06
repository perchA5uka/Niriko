package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「返回列表时那张卡片一次余光」（F17，计划 §11.1）。
 *
 * ## 为什么必须是一次性事件
 *
 * 这条反馈的语义是「你刚从这个作品退回来」—— 如果做成**状态**（例如「最近打开过的作品」），
 * 那么每次列表重组、每次切页回来、每次配置变更都会再闪一次，最后变成噪音。
 * 因此这里是一个**待消费的口令**：
 *
 * 1. 详情页**完成返回**时 [request] 写入 subjectId；
 * 2. 列表里只有 key 匹配的那张卡会看到 `pending == 自己`；
 * 3. 动画（约 300ms）跑完才 [clear] —— 立刻清除会把动画打断；
 *    因此**清除发生在动画结束**，而中途新来一次请求也不会被旧动画吃掉（[clear] 只清自己那次）。
 *
 * ## 不重放、不占位、不拦手势
 *
 * 只画一层扫光（drawWithContent 在内容**之上**补一层渐变），不改布局、不加语义、不拦点击；
 * 减少动态效果时**不画扫光**（[play] 仍然会被消费掉，不会一直挂着等下次重组）。
 */
object SubjectReturnGlow {

    /** 扫光时长（§11.1 给的量级：约 300ms）。 */
    const val DURATION_MS = 300

    private val _pending = MutableStateFlow<Long?>(null)

    /** 当前待播放的作品 id（null = 没有待播放）。卡片读它，只在匹配时播。 */
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    /** 详情页**完成返回**时调用（取消的预测性返回不调用 —— 「仅完成返回」）。 */
    fun request(subjectId: Long) {
        if (subjectId <= 0L) return
        _pending.value = subjectId
    }

    /**
     * 清除待播放口令。
     *
     * 只清除**自己那一次**（`expected` 不匹配时什么都不做）：否则「A 的动画还没跑完、
     * 用户已经进了 B 又退回来」这种快速操作会把 B 的扫光直接抹掉。
     */
    fun clear(expected: Long) {
        if (_pending.value == expected) _pending.value = null
    }

    /** 仅用于测试与诊断：当前是否有待播放口令。 */
    fun hasPending(): Boolean = _pending.value != null
}

/**
 * 给列表卡片挂上「返回余光」：只在 [subjectId] 匹配待播放口令时播放一次。
 *
 * @param reduceMotion 减少动态效果：直接消费掉口令但不画扫光（反馈不能只剩触感这条不适用于本反馈 ——
 *   它本来就只有视觉；减少动态效果时它整体关闭，这与 §11.1「全部反馈接入 reduceMotion」一致）
 */
@Composable
fun rememberReturnGlowModifier(
    subjectId: Long,
    reduceMotion: Boolean = LocalReduceMotion.current,
): Modifier {
    val pending by SubjectReturnGlow.pending.collectAsState()
    val active = pending == subjectId
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (reduceMotion) {
            // 不播动画，但**必须**消费掉口令：否则它会一直挂着，等下次有任何重组时突然闪一下
            SubjectReturnGlow.clear(subjectId)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SubjectReturnGlow.DURATION_MS, easing = LinearEasing))
        SubjectReturnGlow.clear(subjectId)
    }
    if (!active || reduceMotion) return Modifier
    return Modifier.drawWithContent {
        drawContent()
        val t = progress.value.coerceIn(0f, 1f)
        // 扫光：一条 40° 的窄带从左上掠到右下，中段最亮、两端淡出
        val bandWidth = size.width * 0.45f
        val center = -bandWidth + (size.width + bandWidth * 2f) * t
        val highlight = Color.White.copy(alpha = 0.42f * kotlin.math.sin(Math.PI.toFloat() * t))
        val leading = Color.White.copy(alpha = 0.16f * kotlin.math.sin(Math.PI.toFloat() * t))
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, leading, highlight, Color.Transparent),
                start = Offset(center - bandWidth / 2f, 0f),
                end = Offset(center + bandWidth / 2f, size.height),
            ),
            size = size,
        )
    }
}

/**
 * 观察一个导航条目：**条目真的被销毁**（出栈）时回调一次。
 *
 * 为什么不用 `DisposableEffect(onDispose)`：向前导航（进入角色页 / 单集页 / Staff 列表）时
 * 详情页的 composable 也会被移出组合，onDispose 会误触发 —— 那不是「返回列表」。
 * 导航库把**出栈**条目的生命周期推进到 `ON_DESTROY`，这才是判据；
 * 而「取消的预测性返回」不会销毁条目，也就自然不会触发（§11.1「仅完成返回」）。
 */
@Composable
fun NavEntryReturnedEffect(entry: androidx.lifecycle.LifecycleOwner, onReturned: () -> Unit) {
    val current = rememberUpdatedState(onReturned)
    DisposableEffect(entry) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) current.value()
        }
        entry.lifecycle.addObserver(observer)
        onDispose { entry.lifecycle.removeObserver(observer) }
    }
}

/** 扫光强度的纯函数形式：**两端为 0**、中段最强 —— 因此起止都不会有硬边。 */
fun returnGlowAlpha(progress: Float): Float =
    kotlin.math.sin(Math.PI.toFloat() * progress.coerceIn(0f, 1f))
