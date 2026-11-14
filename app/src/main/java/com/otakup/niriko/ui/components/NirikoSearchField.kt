package com.otakup.niriko.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.animation.orSnap
import com.otakup.niriko.ui.theme.GlassChipTokens
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.otakup.niriko.ui.theme.NirikoShapes

// 站内唯一的「输入型搜索框」外观（review P2-1）。
//
// 改造前搜索框有三套写法：设置页自绘 BasicTextField（Pill + 玻璃底，无 focus 态）、
// 作品库顶栏 IosStyleSearchComponent（28dp 玻璃面、会展开成整页搜索卡）、
// 搜索页工具栏（999dp 胶囊）。后两者是「搜索卡」而不是「输入框」，本轮不合并；
// 真正重复的是「一个 pill 里放放大镜 + placeholder + 输入 + 清除」这件事。
//
// 所以这里只抽输入框本体：玻璃胶囊 + 放大镜 + placeholder + BasicTextField + 清除钮，
// 并把 focus 态补上（此前设置页点进去只有光标闪，没有任何边界变化）。
// 需要搜索卡语义（展开成整页）的场景继续用 IosStyleSearchComponent，不要用本组件硬凑。

/**
 * @param value 当前文本
 * @param onValueChange 文本变化
 * @param placeholder 空文本时的提示（纯展示，不参与无障碍标签）
 * @param modifier 外部修饰符（宽度 / 对齐 / 外边距）
 * @param minHeight 视觉最小高度（设置页行基准 56dp）
 * @param imeAction 软键盘动作；默认 Search
 * @param onSearch 软键盘「搜索」动作；不传则只收起焦点
 * @param onClear 清除按钮回调；不传则只有文本非空时也不显示清除钮
 */
@Composable
fun NirikoSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 56.dp,
    leadingIcon: ImageVector? = null,
    imeAction: ImeAction = ImeAction.Search,
    onSearch: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = LocalDarkTheme.current
    val glassEnabled = GlassChipTokens.glassEnabled(LocalGlassEffect.current, LocalCardGlassLevel.current)
    val reduceMotion = LocalReduceMotion.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // focus 描边：颜色过渡走 effects 档（它是「状态切换」不是形体变化），reduceMotion 下瞬切。
    val borderColor by animateColorAsState(
        targetValue = if (focused) colorScheme.primary else GlassChipTokens.chipBorder(isDark),
        animationSpec = NirikoMotionSpecs.effectsFast<androidx.compose.ui.graphics.Color>().orSnap(reduceMotion),
        label = "searchFieldBorder",
    )
    val shape = NirikoShapes.PillShape
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(
                GlassChipTokens.surfaceChipBackground(
                    isDark = isDark,
                    emphatic = false,
                    glassEnabled = glassEnabled,
                ),
            )
            // 描边始终占 1dp 宽（未聚焦时是极淡边框）：否则聚焦瞬间内部可用宽度变化，文字会抖一下。
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = leadingIcon ?: Icons.Outlined.Search,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                cursorBrush = SolidColor(colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (onSearch != null) onSearch() else focusManager.clearFocus()
                    },
                ),
            )
        }
        if (onClear != null && value.isNotEmpty()) {
            // IconButton 自带 48dp 命中区与无障碍角色。
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "清除搜索",
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
