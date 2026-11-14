package com.otakup.niriko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.NirikoShapes

/**
 * 胶囊主操作按钮（v1.2.2 新增，V-24）。
 *
 * 为什么需要它：全站此前唯一的"主操作"是 M3 实心 [androidx.compose.material3.Button]
 * （错误态的重试按钮），它是**唯一**圆角 8dp 的实心方块，在满屏胶囊 + 玻璃的设计语言里
 * 像个外来件。这里给出与令牌同族的替代：胶囊形（[NirikoShapes.PillShape]）+
 * primary 低透明底 + 48dp 命中区，既保留"这是按钮"的可读性，又不再引入第四种圆角口径。
 *
 * 刻意**不用**实心 primary 填充：全站实心绿只留给"已选中状态"（分段控件、状态胶囊、
 * 底栏指示器），操作按钮一旦也用实心绿，会和"当前选中"抢语义。
 *
 * @param text 按钮文案
 * @param onClick 点击回调
 * @param modifier 外部修饰符（控制间距/对齐）
 * @param icon 可选前置图标（18dp）
 * @param enabled 禁用时整块降到 alpha 0.38 的等价观感，且不响应点击
 */
@Composable
fun NirikoPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val background = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.18f else 0.08f)
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(NirikoShapes.PillShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
        )
    }
}
