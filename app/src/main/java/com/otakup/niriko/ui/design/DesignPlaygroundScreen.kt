package com.otakup.niriko.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.GlassSlider
import com.otakup.niriko.ui.components.GlassToggle
import com.otakup.niriko.ui.components.NirikoActionPill
import com.otakup.niriko.ui.components.NirikoFilterPill
import com.otakup.niriko.ui.components.NirikoPillButton
import com.otakup.niriko.ui.components.NirikoSegmentedControl
import com.otakup.niriko.ui.components.NirikoTagPill
import com.otakup.niriko.ui.components.NirikoTextField
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.theme.NirikoShapes
import com.otakup.niriko.ui.theme.NirikoSpacing
import com.otakup.niriko.ui.theme.SurfaceLevel

/**
 * 设计对照页（Niriko 27 · S0，仅 debug 构建可进入，入口在「关于」页底部）。
 *
 * 作用：把令牌与「现状组件」摆在同一屏，S1 起每批改动前后都在这里对照，
 * 评审只看这一页，不必满 App 找页面（计划 §7 第 4 条）。
 *
 * 刻意只摆现状：Material 残留、AlertDialog 等都是存量写法的标本，不是推荐用法。
 * 材质（玻璃折射）要在带壁纸或封面背景的页面里才看得出来，本页主要对照形状、间距、
 * 字阶与控件。
 */
@Composable
fun DesignPlaygroundScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDetailScaffold(title = "设计对照", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = NirikoSpacing.L),
            verticalArrangement = Arrangement.spacedBy(NirikoSpacing.Xxl),
        ) {
            SpacingSection()
            ShapeSection()
            SurfaceLevelSection()
            TypographySection()
            NirikoControlsSection()
            MaterialLeftoversSection()
        }
    }
}

@Composable
private fun PlaygroundSection(
    title: String,
    hint: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NirikoSpacing.S)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .appleGlassCard()
                .padding(NirikoSpacing.L),
            verticalArrangement = Arrangement.spacedBy(NirikoSpacing.M),
            content = content,
        )
    }
}

@Composable
private fun SpacingSection() {
    PlaygroundSection(
        title = "间距栅格",
        hint = "2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 56。存量里 10dp 与 6dp 不在栅格上（见 DesignDebtPolicyTest）。",
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NirikoSpacing.M),
            verticalAlignment = Alignment.Bottom,
        ) {
            NirikoSpacing.gridDp.forEach { value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(value.dp)
                            .height(NirikoSpacing.L)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(text = value.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ShapeSection() {
    PlaygroundSection(
        title = "圆角：线性 vs 平滑",
        hint = "每档左边是现在全站在用的线性圆角，右边是已写好但还没人调用的平滑圆角（squircle）。",
    ) {
        listOf(
            "Control" to NirikoShapes.Control,
            "Section" to NirikoShapes.Section,
            "Card" to NirikoShapes.Card,
            "Sheet" to NirikoShapes.Sheet,
        ).forEach { (name, radius) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(NirikoSpacing.L),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeSwatch(NirikoShapes.rounded(radius))
                ShapeSwatch(NirikoShapes.squircle(radius))
                Text(
                    text = name + " · " + radius.value.toInt() + "dp",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ShapeSwatch(shape: androidx.compose.ui.graphics.Shape) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun SurfaceLevelSection() {
    PlaygroundSection(
        title = "材质层级（起始值）",
        hint = "SurfaceLevel 的参数表；S1 把四套现有表面接到这里之前，只是数据，界面上还没有对应的实物。",
    ) {
        SurfaceLevel.entries.forEach { level ->
            val shadow = level.shadow?.let { "投影 " + it.offsetY.value.toInt() + "/" + it.blur.value.toInt() + "dp α" + it.alpha } ?: "无投影"
            Text(
                text = level.name + " · blur " + level.blur.value.toInt() + "dp · tint " +
                    level.tintAlphaLight + "/" + level.tintAlphaDark + " · 受光 " + level.edgeAlpha + " · " + shadow,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TypographySection() {
    val typography = MaterialTheme.typography
    val styles: List<Pair<String, TextStyle>> = listOf(
        "headlineLarge" to typography.headlineLarge,
        "headlineMedium" to typography.headlineMedium,
        "headlineSmall" to typography.headlineSmall,
        "titleLarge" to typography.titleLarge,
        "titleMedium" to typography.titleMedium,
        "titleSmall" to typography.titleSmall,
        "bodyLarge" to typography.bodyLarge,
        "bodyMedium" to typography.bodyMedium,
        "bodySmall" to typography.bodySmall,
        "labelLarge" to typography.labelLarge,
        "labelMedium" to typography.labelMedium,
        "labelSmall" to typography.labelSmall,
    )
    PlaygroundSection(
        title = "字阶",
        hint = "titleMedium(16) 与 titleSmall(15)、bodyMedium(14) 与 labelLarge(14) / bodySmall(13) 肉眼几乎分不出，是 S4 要合并的对象。",
    ) {
        styles.forEach { (name, style) ->
            Text(text = name + " · 作品 Aa 123", style = style)
        }
    }
}

@Composable
private fun NirikoControlsSection() {
    var segment by remember { mutableIntStateOf(0) }
    var filterOn by remember { mutableStateOf(true) }
    var toggleOn by remember { mutableStateOf(true) }
    var slider by remember { mutableFloatStateOf(6f) }
    var text by remember { mutableStateOf("") }
    PlaygroundSection(
        title = "Niriko 自研控件（现状）",
        hint = "S3 会把这些与 Material 残留一起收进 NirikoButton / NirikoField 等母版。",
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NirikoSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NirikoPillButton(text = "主要操作", onClick = {})
            NirikoActionPill(text = "动作", onClick = {})
            NirikoFilterPill(text = "筛选", selected = filterOn, onClick = { filterOn = !filterOn })
            NirikoTagPill(text = "标签")
            NirikoTagPill(text = "强调", emphatic = true)
        }
        NirikoSegmentedControl(
            options = listOf("想看", "在看", "看过"),
            selectedIndex = segment,
            onSelect = { segment = it },
            modifier = Modifier.fillMaxWidth(),
        )
        NirikoTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("文本框") },
            singleLine = true,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(NirikoSpacing.L),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToggle(checked = toggleOn, onCheckedChange = { toggleOn = it })
            GlassSlider(value = slider, onValueChange = { slider = it }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MaterialLeftoversSection() {
    var showDialog by remember { mutableStateOf(false) }
    PlaygroundSection(
        title = "Material 残留（待替换）",
        hint = "全站仍有 TextButton 134 处、OutlinedButton 19 处、AlertDialog 36 处等。对话框是玻璃界面里最扎眼的一处。",
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(NirikoSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = {}) { Text("Button") }
            OutlinedButton(onClick = {}) { Text("OutlinedButton") }
            TextButton(onClick = {}) { Text("TextButton") }
            IconButton(onClick = {}) { Icon(Icons.Outlined.Info, contentDescription = "IconButton") }
        }
        OutlinedButton(onClick = { showDialog = true }) { Text("打开 AlertDialog") }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("AlertDialog 现状") },
            text = { Text("白底、28dp 圆角、Material 按钮：和下面的玻璃界面不是同一套语言。") },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("知道了") } },
        )
    }
}
