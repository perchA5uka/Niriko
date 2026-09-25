package com.otakup.niriko.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.otakup.niriko.data.model.WatchStatus

/**
 * 状态语义色 token 化（P0a 色彩秩序）。
 *
 * 取代统计页 / 状态卡 / 徽标里的零散硬编码色。同色相分 5 个状态、
 * light/dark 两套 tonal 值，保证全站一致，并与主题系统同频。
 */
data class StatusTone(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
)

/** 当前主题下某状态的语义色。 */
@Composable
fun statusTone(status: WatchStatus): StatusTone =
    statusToneFor(status, LocalDarkTheme.current)

/** 指定明暗下的语义色（供非 Composable 上下文 / 预览使用）。 */
fun statusToneFor(status: WatchStatus, dark: Boolean): StatusTone = when (status) {
    WatchStatus.COMPLETED -> if (dark) {
        StatusTone(Color(0xFF1B5E20), Color(0xFFC8E6C9), Color(0xFF81C784))
    } else {
        StatusTone(Color(0xFFC8E6C9), Color(0xFF1B5E20), Color(0xFF166534))
    }
    WatchStatus.WATCHING -> if (dark) {
        StatusTone(Color(0xFFBF360C), Color(0xFFFFE0B2), Color(0xFFFFB74D))
    } else {
        StatusTone(Color(0xFFFFE0B2), Color(0xFF92400E), Color(0xFFE65100))
    }
    WatchStatus.PLAN_TO_WATCH -> if (dark) {
        StatusTone(Color(0xFF37474F), Color(0xFFECEFF1), Color(0xFF90A4AE))
    } else {
        StatusTone(Color(0xFFECEFF1), Color(0xFF455A64), Color(0xFF607D8B))
    }
    WatchStatus.ON_HOLD -> if (dark) {
        StatusTone(Color(0xFF4A148C), Color(0xFFE1BEE7), Color(0xFFCE93D8))
    } else {
        StatusTone(Color(0xFFEDE7F6), Color(0xFF5B21B6), Color(0xFF7C3AED))
    }
    WatchStatus.DROPPED -> if (dark) {
        StatusTone(Color(0xFFB71C1C), Color(0xFFFFCDD2), Color(0xFFEF9A9A))
    } else {
        StatusTone(Color(0xFFFFCDD2), Color(0xFFB71C1C), Color(0xFFDC2626))
    }
}
