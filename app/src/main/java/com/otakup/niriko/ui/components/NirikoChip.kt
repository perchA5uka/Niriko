package com.otakup.niriko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.GlassChipTokens
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.otakup.niriko.ui.theme.NirikoShapes

// 胶囊三档（V-12 收口）。
// 改造前「标签」这件事有五种脸：M3 FilterChip（8dp 小圆角、选中实心 primaryContainer）、
// M3 AssistChip、玻璃 TagChip、详情页选集 chip（8dp 方角 + 绿描边）、以及纯绿文字按钮
// （「抛弃 2」「搁置 7」「上次更新」「刷新」）。用户分不清哪些能点。
//
// 现在按**语义**分三档，形状一律胶囊（NirikoShapes.ChipShape）：
// 1. [NirikoTagPill]：信息，不可点（stats 计数、状态、类型）；
// 2. [NirikoFilterPill]：可点的开关（筛选行），选中主色淡底 + 主色描边；
// 3. [NirikoSelectionPill]：选集网格，等宽、可长按。
//
// 三档共用同一套 GlassChipTokens：玻璃底 + 极淡描边，未选中时文字按实际背景走
// ensureReadable 保证 4.5:1 —— 所以它们看起来才像同一枚玻璃片，而不是三种组件。

/**
 * 信息胶囊：**不可点**。
 *
 * 用在「180 部作品」「均分 3.5」「在看 2」这类纯展示上。刻意做成没有手势的样子，
 * 让「哪些能点」一眼可辨；需要可点的场景请用 [NirikoFilterPill]。
 */
@Composable
fun NirikoTagPill(
    text: String,
    modifier: Modifier = Modifier,
    emphatic: Boolean = false,
) {
    val colors = chipColors(selected = false, emphatic = emphatic)
    Box(
        modifier = modifier
            .heightIn(min = CHIP_TAG_MIN_HEIGHT)
            .clip(NirikoShapes.ChipShape)
            .background(colors.background, NirikoShapes.ChipShape)
            .border(width = 0.5.dp, color = colors.border, shape = NirikoShapes.ChipShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.label,
            maxLines = 1,
        )
    }
}

/**
 * 筛选胶囊：可点的开关（筛选行 / 排序 / 视图切换）。
 *
 * 选中态是「主色 20% 淡底 + 1dp 主色描边」，而不是 M3 FilterChip 的实心 primaryContainer：
 * 一行里常有 3-6 个可切换项，实心色块会让整行变成色块墙，也抢走底栏当前 tab 的「实心 = 唯一」语义。
 */
@Composable
fun NirikoFilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = chipColors(selected = selected)
    // 触控壳：视觉胶囊仍是 34dp，但命中区补到 Material 的 48dp 下限。
    // 手势挂在外壳上而不是胶囊上 —— 否则外壳只是占了 48dp 的布局，手指点边缘依然没反应。
    // 副作用：一行筛选胶囊的行高从 34dp 变 48dp，行距略松；换来的是不用瞄准也能点到。
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = CHIP_MIN_HEIGHT)
                .clip(NirikoShapes.ChipShape)
                .background(colors.background, NirikoShapes.ChipShape)
                // 描边宽度跟着选中态变（1dp / 0.5dp）：筛选件是列表旁的「开关」，选中需要更清楚的边界。
                .border(width = if (selected) 1.dp else 0.5.dp, color = colors.border, shape = NirikoShapes.ChipShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 选集胶囊：详情页「选集」网格里的一格（等宽、胶囊、可长按）。
 *
 * 手势必须是 `combinedClickable`：R3 记录的「收藏面板长按单集无反应」根因就是
 * FilterChip 内部 Surface 消费了长按。这里自绘背景，手势只由自己消费。
 *
 * @param onLongClick 长按回调；为 null 时长按不触发（但仍会吞掉长按，不回退到单击）
 */
@Composable
fun NirikoSelectionPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = chipColors(selected = selected)
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .heightIn(min = CHIP_SELECTION_MIN_HEIGHT)
            .clip(NirikoShapes.ChipShape)
            .background(colors.background, NirikoShapes.ChipShape)
            .border(width = if (selected) 1.dp else 0.5.dp, color = colors.border, shape = NirikoShapes.ChipShape)
            .combinedClickable(
                onClick = onClick,
                // F-07：长按选集胶囊（打开单集详情）此前没有任何触感，用户只能靠浮层出现才知道长按生效。
                // LongPress 是全站长按入口的既有语义（Screens.kt 的长按浮层同语义），不新造触感类型。
                // 传 null 时不注册长按（combinedClickable 的 null 即「不注册」），行为与改造前一致。
                onLongClick = onLongClick?.let { callback ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        callback()
                    }
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.label,
            maxLines = 1,
        )
    }
}

/**
 * 动作胶囊：可点的「做一件事」（刷新 / 上次更新 / 立即重试 / 立即导出）。
 *
 * 改造前这些入口是**裸绿文字**（「上次更新 8 分钟前 / 刷新」「抛弃 2」「搁置 7」），
 * 用户看不出哪里能点，而且和标签、按钮混在一起。做成玻璃胶囊之后：
 * 形状与标签同一族（只是文字用主色），一眼能看出「这是可点的动作」而不必靠颜色猜。
 *
 * 不用实心主色底：实心主色在全站只留给「唯一选中项」，否则一排动作会把页面点成一片绿。
 */
@Composable
fun NirikoActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = LocalDarkTheme.current
    val glassEnabled = GlassChipTokens.glassEnabled(LocalGlassEffect.current, LocalCardGlassLevel.current)
    val background = GlassChipTokens.surfaceChipBackground(isDark = isDark, glassEnabled = glassEnabled)
    val effectiveBackground = remember(background, colorScheme.background) {
        GlassChipTokens.compositeOver(background, colorScheme.background)
    }
    // 动作文字是主色：主色在半透明玻璃上不一定够 4.5:1，按实际合成底压到位（保色相只调明度）。
    val labelColor = remember(effectiveBackground, colorScheme.primary) {
        GlassChipTokens.ensureReadable(preferred = colorScheme.primary, background = effectiveBackground)
    }
    val contentColor = if (enabled) labelColor else colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    // 与筛选胶囊同一套触控壳：视觉仍是 34dp 胶囊，命中区 48dp。
    // 这样调用点也就不必再手写 heightIn(48.dp) —— 改造前「刷新」被压到 48dp、
    // 其它页的动作胶囊是 34dp，同一个组件在不同页面两种高度。
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick, enabled = enabled),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = CHIP_MIN_HEIGHT)
                .clip(NirikoShapes.ChipShape)
                .background(background, NirikoShapes.ChipShape)
                .border(width = 0.5.dp, color = GlassChipTokens.chipBorder(isDark), shape = NirikoShapes.ChipShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

/** 胶囊的三个颜色（底 / 边 / 字）。三档共用，保证「同一族材质」。 */
private class ChipColors(val background: Color, val border: Color, val label: Color)

@Composable
private fun chipColors(selected: Boolean, emphatic: Boolean = false): ChipColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = LocalDarkTheme.current
    val glassEnabled = GlassChipTokens.glassEnabled(LocalGlassEffect.current, LocalCardGlassLevel.current)
    val neutralBackground = GlassChipTokens.surfaceChipBackground(
        isDark = isDark,
        emphatic = emphatic,
        glassEnabled = glassEnabled,
    )
    // 页面上的标签压在页面背景上：按「背景 ∘ 标签底」的合成色判对比度（与 TagChip 口径一致），
    // 不按最坏情况的黑/白图 —— 标签不在图片上。
    val pageSurface = colorScheme.background
    val effectiveNeutral = remember(neutralBackground, pageSurface) {
        GlassChipTokens.compositeOver(neutralBackground, pageSurface)
    }
    val neutralLabel = remember(effectiveNeutral) {
        GlassChipTokens.ensureReadable(preferred = colorScheme.onSurface, background = effectiveNeutral)
    }
    return if (selected) {
        ChipColors(
            background = colorScheme.primary.copy(alpha = CHIP_SELECTED_BG_ALPHA),
            border = colorScheme.primary.copy(alpha = CHIP_SELECTED_BORDER_ALPHA),
            label = colorScheme.onSurface,
        )
    } else {
        ChipColors(
            background = neutralBackground,
            border = GlassChipTokens.chipBorder(isDark),
            label = neutralLabel,
        )
    }
}

/** 信息胶囊最小高度：不含触控要求（不可点），只要与筛选胶囊同一基线。 */
private val CHIP_TAG_MIN_HEIGHT = 28.dp

/**
 * 可点胶囊的**视觉**高度：34dp。
 *
 * 命中区不靠它，靠外面那层 [minimumInteractiveComponentSize] 的 48dp 触控壳 ——
 * 胶囊画得紧凑、手指够得到，两件事分开管。
 */
private val CHIP_MIN_HEIGHT = 34.dp

/**
 * 选集胶囊最小高度：40dp。
 *
 * 刻意不套 48dp 触控壳：选集是一整片网格，格子之间只有 6-8dp 间距，
 * 套壳后每行行高 +8dp、13 集就要多出一整行，而 40dp 加间隙已接近 48dp 的节距。
 */
private val CHIP_SELECTION_MIN_HEIGHT = 40.dp

/** 选中胶囊的底色透明度（与分段控件同值，保证「选中」在全站是同一个浓度）。 */
private const val CHIP_SELECTED_BG_ALPHA = 0.20f

/** 选中胶囊描边的不透明度。 */
private const val CHIP_SELECTED_BORDER_ALPHA = 0.70f
