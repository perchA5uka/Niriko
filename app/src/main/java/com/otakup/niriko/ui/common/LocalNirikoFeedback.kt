package com.otakup.niriko.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.NirikoShapes
import kotlinx.coroutines.launch

/**
 * 全局反馈通道（F03）。
 *
 * 为什么需要它：整改前全仓只有 10 处 SnackbarHost，且都长在具体页面里；四个顶层页
 * （收藏 / 发现 / 统计 / 设置）和绝大多数二级页没有宿主。于是「批量改状态」「批量删除」
 * 「导出日历」「分享失败」这类动作只能在页面里各自造 Toast 或干脆静默——用户点完没有任何回执。
 *
 * 这里把「谁来显示」和「谁想显示」拆开：调用点只拿 [LocalNirikoFeedback] 发消息，
 * 由挂在窗口顶层的 [NirikoFeedbackHost] 统一渲染。宿主不存在时（预览、单测、某个独立
 * 二级页自己又有 SnackbarHost）调用方拿到的就是 null，直接跳过，绝不崩。
 */
data class NirikoFeedbackMessage(
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

/**
 * 反馈发送器：结构越界（比如在协程里）也能安全调用，因为 [sink] 是普通的 lambda。
 *
 * 注意：它本身不是 State，持有它不会触发重组；宿主内部用 remember 固定同一个实例。
 */
class NirikoFeedback(private val sink: (NirikoFeedbackMessage) -> Unit) {

    /** 纯文案提示（自动消失）。 */
    fun show(message: String) = sink(NirikoFeedbackMessage(message = message))

    /** 带一个操作按钮的提示（如「重试」）。 */
    fun show(message: String, actionLabel: String, onAction: () -> Unit) = sink(NirikoFeedbackMessage(message = message, actionLabel = actionLabel, action = onAction))

    /** 可撤销提示（如批量删除）。actionLabel 固定「撤销」，避免每个调用点各写一遍。 */
    fun showUndo(message: String, onUndo: () -> Unit) = show(message, "撤销", onUndo)
}

/**
 * 当前可用的反馈通道；宿主没挂时为 null。
 *
 * 用 staticCompositionLocalOf：宿主在窗口层，整棵树只提供一次，值不变；
 * 若用 compositionLocalOf 会让所有读取方跟着宿主重组一起订阅，得不偿失。
 */
val LocalNirikoFeedback = staticCompositionLocalOf<NirikoFeedback?> { null }

/**
 * Snackbar 距窗口底部的额外高度（dp）。
 *
 * 手机端底部是悬浮胶囊 dock（挂在 MAIN 路由内容里），宽屏端是竖向 dock / 横向胶囊底栏；
 * 两者都压在窗口底部这一带。96dp ≈ dock 自身高度 + 与内容之间的间隙，能保证提示条落在
 * dock 上方而不是被它盖住。
 */
val FEEDBACK_SNACKBAR_BOTTOM_INSET: Dp = 96.dp

/**
 * 全局反馈宿主（F03）：包住整棵树，在窗口层渲染唯一的 SnackbarHost。
 *
 * 必须是「z 序最上」的一层：放在内容与 NirikoNavSuite **之后**，这样二级页转场、
 * 竖向 dock 都不会把它盖住。[bottomInset] / [startInset] 由调用点按窗口尺寸类别传入。
 */
@Composable
fun NirikoFeedbackHost(
    modifier: Modifier = Modifier,
    bottomInset: Dp = FEEDBACK_SNACKBAR_BOTTOM_INSET,
    startInset: Dp = 0.dp,
    // 用 BoxScope 接收 content：宿主内部本来就有一个 Box，把作用域让出去，
    // 调用点（MainActivity 的窗口层）才能在 content 里继续调用 `BoxScope.NirikoNavSuite(...)`
    // 这类需要 Box 作用域的扩展组件，而不必自己再包一层 Box。
    content: @Composable BoxScope.() -> Unit,
) {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = remember(hostState, scope) {
        NirikoFeedback { message ->
            scope.launch {
                // 「后来的提示优先」：上一条还在显示时先收掉。否则 showSnackbar 会排队，
                // 用户连点两次会看到两条几乎一样的提示挨个弹出来。
                hostState.currentSnackbarData?.dismiss()
                val result = hostState.showSnackbar(
                    message = message.message,
                    actionLabel = message.actionLabel,
                    // 带按钮时给 Long（Undo 需要操作时间窗口）；纯文案 Short，少挡内容。
                    // 这里必须显式给 duration：M3 在 actionLabel != null 时默认 Indefinite，
                    // 那样提示会一直钉在屏幕上。
                    duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
            }
        }
    }
    CompositionLocalProvider(LocalNirikoFeedback provides feedback) {
        Box(modifier = modifier) {
            content()
            SnackbarHost(
                hostState = hostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 宿主在 Scaffold 之外（窗口层），系统导航栏的内边距要自己补
                    .navigationBarsPadding()
                    .padding(start = startInset + 16.dp, end = 16.dp, bottom = 16.dp + bottomInset),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = NirikoShapes.ControlShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
