package com.otakup.niriko.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.NirikoShapes

/**
 * 全站唯一的文本输入框形态（V-10 收口）。
 *
 * 改造前全仓有 **37 处** `OutlinedTextField` 直接裸用 M3 默认值：4dp 小圆角 + 1dp 描边，
 * 而站内卡片是 20dp / 控件 12dp 的圆角语言——输入框在设置页、详情页、数据源表单里
 * 都像一个「从别的 App 贴进来」的直角矩形。这里把它换成：
 *
 * - 圆角走 [NirikoShapes.ControlShape]（12dp），与搜索框、分段控件同一档；
 * - 容器给一层极淡的 surfaceContainerHighest（4 成不透明），读起来像卡面上「挖下去的一格」，
 *   而不是压在玻璃上的一张白纸；
 * - 描边只在聚焦/报错时明显（primary/error 7 成不透明），未聚焦时几乎不可见（3 成），
 *   于是「一排输入框」不会再被 1dp 灰线切成一格格表格。
 *
 * 参数刻意保持与 [OutlinedTextField] 同名同义，调用点只换函数名即可，行为不动：
 * 默认 [singleLine] 为 false（多数调用点是多行感想/备注，默认单行会改变换行行为）。
 */
@Composable
fun NirikoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val fieldShape = NirikoShapes.ControlShape
    // 不用 Color.Transparent 做容器：全透明会让输入框只剩一条描边，落在玻璃卡上像没渲染完；
    // 4 成 surfaceContainerHighest 既保留「凹槽」的层次，又不会盖住卡片本身的材质。
    val unfocusedContainer = colorScheme.surfaceContainerHighest.copy(alpha = 0.34f)
    val focusedContainer = colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = fieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = focusedContainer,
            unfocusedContainerColor = unfocusedContainer,
            disabledContainerColor = unfocusedContainer,
            errorContainerColor = colorScheme.errorContainer.copy(alpha = 0.3f),
            focusedBorderColor = colorScheme.primary.copy(alpha = 0.7f),
            unfocusedBorderColor = colorScheme.outline.copy(alpha = 0.3f),
            disabledBorderColor = colorScheme.outline.copy(alpha = 0.16f),
            errorBorderColor = colorScheme.error.copy(alpha = 0.75f),
            cursorColor = colorScheme.primary,
            focusedPlaceholderColor = colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = colorScheme.onSurfaceVariant,
            disabledPlaceholderColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            errorPlaceholderColor = colorScheme.onSurfaceVariant,
        ),
    )
}
