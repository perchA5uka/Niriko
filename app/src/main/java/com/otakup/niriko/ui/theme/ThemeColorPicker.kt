package com.otakup.niriko.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 自定义主题色取色弹窗（HSV 面板 + 色相条 + HEX 输入 + 精选快捷色板）。
 *
 * 交互约定：
 * - 拖动 SV 面板 / 色相条期间仅更新本地预览（主/副/点缀色卡实时反映生成结果）；
 *   松手（drag end）才调用 [onSelect] 写入设置 → 全应用即时换色；
 * - HEX 输入合法（#RRGGBB / RRGGBB）时立即应用；
 * - 快捷色板点击即应用；
 * - 「恢复默认」清除自定义色（回退品牌绿）。
 *
 * 配色生成走 [SeedColorScheme]（HCT TONAL_SPOT），预览色卡与实际生效完全一致。
 */
@Composable
fun ThemeColorPickerDialog(
    currentSeed: Int,
    onSelect: (Int) -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 当前 HSV（自定义种子或默认绿初始化）
    val initialHsv = remember {
        val argb = if (currentSeed == SeedColorScheme.UnsetSeed) SeedColorScheme.DefaultSeed.toArgb() else currentSeed
        FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
    }
    var hsv by remember { mutableStateOf(initialHsv.copyOf()) }
    var hexText by remember { mutableStateOf("") }

    val hsvColor: Color = Color(android.graphics.Color.HSVToColor(hsv))
    // 预览：与全局生效完全一致的生成结果（浅色主色/容器/点缀）
    val previewScheme = remember(hsv[0], hsv[1], hsv[2]) { SeedColorScheme.light(hsvColor) }

    fun commit() {
        onSelect(hsvColor.toArgb())
    }

    fun applyHsv(h: Float, s: Float, v: Float) {
        hsv = floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    }

    fun applyHex(text: String): Boolean {
        val clean = text.removePrefix("#")
        if (!Regex("[0-9a-fA-F]{6}").matches(clean)) return false
        val argb = android.graphics.Color.parseColor("#$clean")
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv = hsv.copyOf()
        commit()
        return true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义主题色") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 生成结果预览（主色 / 主色容器 / 副色 / 点缀）
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PreviewSwatch("主色", previewScheme.primary, Modifier.weight(1f))
                    PreviewSwatch("容器", previewScheme.primaryContainer, Modifier.weight(1f))
                    PreviewSwatch("副色", previewScheme.secondary, Modifier.weight(1f))
                    PreviewSwatch("点缀", previewScheme.tertiary, Modifier.weight(1f))
                }

                // SV 面板：左→右 白→纯色相，上→下 亮→暗
                SatValPanel(
                    hsv = hsv,
                    onChange = { s, v -> applyHsv(hsv[0], s, v) },
                    onChangeEnd = { commit() },
                )

                // 色相条
                HueBar(
                    hue = hsv[0],
                    onChange = { h -> applyHsv(h, hsv[1], hsv[2]) },
                    onChangeEnd = { commit() },
                )

                // HEX 输入
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text
                        if (applyHex(text)) hexText = ""
                    },
                    label = { Text("十六进制（如 #2E7D32）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 精选快捷色板（莫兰迪低饱和系 + 品牌默认 + 明色系）
                Text("快捷色板", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickPalette.forEach { argb ->
                        val color = Color(argb)
                        val selected = hsvColor.toArgb() == argb
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                )
                                .pointerInput(argb) {
                                    detectTapGestures {
                                        android.graphics.Color.colorToHSV(argb, hsv)
                                        hsv = hsv.copyOf()
                                        commit()
                                    }
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = {
                onResetToDefault()
                android.graphics.Color.colorToHSV(SeedColorScheme.DefaultSeed.toArgb(), hsv)
                hsv = hsv.copyOf()
            }) { Text("恢复默认") }
        },
    )
}

/** 生成结果预览色卡。 */
@Composable
private fun PreviewSwatch(label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 饱和度/明度面板：拖动取色，松手生效。 */
@Composable
private fun SatValPanel(
    hsv: FloatArray,
    onChange: (Float, Float) -> Unit,
    onChangeEnd: () -> Unit,
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
    var panelSize by remember { mutableStateOf(IntSize.Zero) }

    fun updateFrom(offset: Offset) {
        if (panelSize.width == 0 || panelSize.height == 0) return
        val s = (offset.x / panelSize.width).coerceIn(0f, 1f)
        val v = 1f - (offset.y / panelSize.height).coerceIn(0f, 1f)
        onChange(s, v)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            .onSizeChanged { panelSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset -> updateFrom(offset); onChangeEnd() }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ -> change.consume(); updateFrom(change.position) },
                    onDragEnd = { onChangeEnd() },
                )
            },
    ) {
        // 拇指指示点
        if (panelSize.width > 0) {
            val thumbOffset = IntOffset(
                (hsv[1] * panelSize.width).roundToInt(),
                ((1f - hsv[2]) * panelSize.height).roundToInt(),
            )
            Box(
                modifier = Modifier
                    .offset { thumbOffset }
                    .offset((-9).dp, (-9).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.HSVToColor(hsv)))
                    .border(2.dp, Color.White, CircleShape)
                    .border(3.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
}

/** 色相滑条：360° 渐变，拖动取色，松手生效。 */
@Composable
private fun HueBar(
    hue: Float,
    onChange: (Float) -> Unit,
    onChangeEnd: () -> Unit,
) {
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val hueBrush = remember {
        Brush.horizontalGradient(
            (0..360 step 30).map { h ->
                Color(android.graphics.Color.HSVToColor(floatArrayOf(h.toFloat(), 1f, 1f)))
            },
        )
    }

    fun updateFrom(x: Float) {
        if (barSize.width == 0) return
        onChange((x / barSize.width).coerceIn(0f, 1f) * 360f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(hueBrush)
            .onSizeChanged { barSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset -> updateFrom(offset.x); onChangeEnd() }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, _ -> change.consume(); updateFrom(change.position.x) },
                    onDragEnd = { onChangeEnd() },
                )
            },
    ) {
        if (barSize.width > 0) {
            val thumbOffset = IntOffset(((hue / 360f) * barSize.width).roundToInt(), barSize.height / 2)
            Box(
                modifier = Modifier
                    .offset { thumbOffset }
                    .offset((-9).dp, (-9).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))
                    .border(2.dp, Color.White, CircleShape)
                    .border(3.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
}

/** 精选快捷色板（莫兰迪低饱和系 + 品牌绿 + 明色系）。 */
private val QuickPalette: List<Int> = listOf(
    0xFF2E7D32.toInt(), // 品牌绿（默认）
    0xFF7C9A82.toInt(), // 莫兰迪绿
    0xFF7A93A8.toInt(), // 莫兰迪蓝
    0xFF9687A8.toInt(), // 莫兰迪紫
    0xFFB98FA0.toInt(), // 莫兰迪粉
    0xFFC09478.toInt(), // 莫兰迪橙
    0xFF6E9E9B.toInt(), // 莫兰迪青
    0xFF1E88E5.toInt(), // 明蓝
    0xFF8E24AA.toInt(), // 明紫
    0xFFE53935.toInt(), // 明红
    0xFFFB8C00.toInt(), // 明橙
    0xFF00897B.toInt(), // 明青
)
