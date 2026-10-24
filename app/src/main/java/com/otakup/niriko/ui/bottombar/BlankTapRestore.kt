package com.otakup.niriko.ui.bottombar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.max

/**
 * B11-Step3：点**空白**恢复悬浮控件（Dock / 顶栏 / 详情收藏胶囊）。
 *
 * 实现约束（任务卡 ③）：
 * - **不是**全屏透明层：这里只在 [PointerEventPass.Final] 观察，既不消费事件也不改 zIndex，
 *   所以卡片点击 / 长按 / 搜索 / 筛选 / 弹窗全部照旧；
 * - 只有「一次 down→up 全程没有任何 change 被消费」且「位移 < touchSlop」且「不是长按」才恢复 ——
 *   点卡片时子节点（combinedClickable）在 Main pass 就消费了 down，这里自然跳过；
 * - Popup / Dialog 是独立窗口，事件根本不会落到页面根 → 不会误触发恢复，也不会被位移带走。
 *
 * 挂在页面根容器上（MainPager 的内容层、详情页根 Box）。隐藏态的控件本身已用 offset 移出
 * 可视区（命中区一起走），所以「点控件原位置」不会命中控件，而是走到这里 → 恢复。
 */
@Composable
fun Modifier.floatingBarRestoreOnBlankTap(onRestore: () -> Unit): Modifier {
    val viewConfiguration = LocalViewConfiguration.current
    return pointerInput(onRestore, viewConfiguration) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var consumed = down.isConsumed
            var maxDistance = 0f
            var lastUpTime = down.uptimeMillis
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) consumed = true
                maxDistance = max(maxDistance, (change.position - down.position).getDistance())
                lastUpTime = change.uptimeMillis
                if (change.changedToUpIgnoreConsumed() || !change.pressed) break
            }
            val isLongPress = lastUpTime - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
            if (!consumed && !isLongPress && maxDistance <= viewConfiguration.touchSlop) {
                onRestore()
            }
        }
    }
}
