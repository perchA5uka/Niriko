package com.otakup.niriko.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.LocalCardGlassLevel
import com.otakup.niriko.ui.theme.GlassChipTokens
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.otakup.niriko.ui.theme.NirikoShapes

/**
 * 设置页统一水平内距（V-05）。
 *
 * 整改前同屏有三种左边缘：组卡贴边 0dp、组标题 16dp、页面标题与搜索框 20dp——
 * 分组卡片的圆角切边和标题文字对不上，看起来像三块各自排版。这里收口到一个常量，
 * 组卡（[SettingsSplitGroup] 内部）／组标题／页面标题／搜索框／宽屏左列全部引用它，
 * 以后改一处就整页对齐，不再出现"某个页单独写 20dp"。
 */
val SettingsPageHorizontalInset: Dp = 16.dp

/**
 * 组内行的最小高度（V-07）。
 *
 * 行高此前完全由内边距和内容撑出来：单行 48dp、带描述 70dp、带尾部控件的行更矮，
 * 于是同一侧栏里 8 个分类行高 60dp、二级页开关行 48dp，节奏是乱的。
 * 统一成 M3 列表基准的 56dp（单行）／72dp（标题 + 说明两行），可点面积也顺带达标。
 */
val SettingsRowMinHeight: Dp = 56.dp

/** 带副标题的两行设置行最小高度（见 [SettingsRowMinHeight]）。 */
val SettingsRowTwoLineMinHeight: Dp = 72.dp

/** 尾部 chevron 尺寸：整改前页面用 24dp、组内行用 20dp，这里只留 20dp（V-07）。 */
val SettingsChevronSize: Dp = 20.dp

/** 分类行左侧图标容器边长（36dp 与窄屏分类行、宽屏导航行一致）。 */
val SettingsCategoryIconSize: Dp = 36.dp

/**
 * 设置行尾箭头（V-07）：整改前有三种箭头（分类行 24dp、组内行 20dp、
 * 搜索结果行又各写一遍），尺寸与透明度都不一致。统一到这一个组件，
 * 尺寸取 [SettingsChevronSize]、颜色取 onSurfaceVariant。
 *
 * @param onDark 调用点背景是深色实底时传 true：0.9 的透明度会在深底上再压暗一档，
 *   看起来像禁用态；此时改用不透明 onSurfaceVariant 保住"可点"的信号。
 */
@Composable
fun SettingsChevron(onDark: Boolean = false) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = if (onDark) tint else tint.copy(alpha = 0.9f),
        modifier = Modifier.size(SettingsChevronSize),
    )
}

/**
 * 设置行左侧图标容器（V-08 / V-07）：36dp 圆角方片 + 玻璃标签底 + 主题色图标。
 *
 * 整改前这里是 36dp 圆形 + secondaryContainer 实底——一排实心绿球压在玻璃卡上，
 * 材质比卡片本身重，抢走了标题的视觉优先级。改用与 [GlassChipTokens] 同族的半透明白片
 * （玻璃关闭时自动退化成 10% 实底）后，图标片和设置组卡是"同一块瓷"，不再像贴上去的色块。
 */
@Composable
fun SettingsRowIconChip(icon: ImageVector, modifier: Modifier = Modifier) {
    val isDark = LocalDarkTheme.current
    // 玻璃档位关闭时不能用半透明白（页面是纯背景色，白片会变成脏灰），交给令牌退化。
    val glassEnabled = GlassChipTokens.glassEnabled(LocalGlassEffect.current, LocalCardGlassLevel.current)
    Box(
        modifier = modifier
            .size(SettingsCategoryIconSize)
            .clip(NirikoShapes.ControlShape)
            .background(
                GlassChipTokens.surfaceChipBackground(
                    isDark = isDark,
                    emphatic = false,
                    glassEnabled = glassEnabled,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}
