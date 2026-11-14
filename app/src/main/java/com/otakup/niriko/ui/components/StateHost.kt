package com.otakup.niriko.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.util.PHASE_REVEAL_DIM_ALPHA
import com.otakup.niriko.util.PHASE_REVEAL_DURATION_MS
import com.otakup.niriko.util.shouldPlayPhaseReveal

// 非正文态（加载 / 空 / 错误）之间的切换宿主（B-13）。
//
// 四种状态此前是同一个 when 的四个分支，切换时整块硬替换。直接用 AnimatedContent 会踩
// 与 B-7 同一条红线：过渡期间新旧两棵内容同时在组合树里，只要其中一棵是 Lazy（作品库 /
// 搜索结果的空态常常与列表同层），就是双份 item、双份图片请求。所以这里沿用批次三
// [com.otakup.niriko.util.shouldPlayPhaseReveal] 的 alpha 包络：换内容（O(1)），
// 只给外层一次 0.55→1 的淡入，观感是「呼吸式替换」而不是闪黑或闪白。
//
// 首次组合不播（previous 传 null 的判据），避免每次进页面先压暗一次——这正是 B-12 评分
// 弹跳踩过的坑：LaunchedEffect 在首次组合也会执行。

/**
 * @param stateKey 当前状态标识（如 "loading" / "empty" / "error" / 数据对象）。变化时播一次淡入。
 *   调用方给稳定值：不要每次重组新建对象（例如把列表直接当 key），否则会每帧重播。
 */
@Composable
fun StateHost(
    stateKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val alpha = remember { Animatable(1f) }
    var previous by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(stateKey, reduceMotion) {
        if (shouldPlayPhaseReveal(previous, stateKey, reduceMotion)) {
            alpha.snapTo(PHASE_REVEAL_DIM_ALPHA)
            alpha.animateTo(1f, tween(PHASE_REVEAL_DURATION_MS))
        }
        previous = stateKey
    }
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha.value }) { content() }
}
