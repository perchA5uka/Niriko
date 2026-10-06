package com.otakup.niriko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.GlassChipTokens
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect

/**
 * 详情页标签胶囊（F02：统一材质）。
 *
 * 改造前详情页有**三套**标签样式：社区标签用 `secondaryContainer`（实心色块）、
 * Steam 标签用 Material `FilterChip` 默认底色、VNDB 标签又用 `selected` 表示权重强度
 * —— 同一屏里三种胶囊，这正是 F02 要收掉的东西。
 *
 * 现在只有这一枚：半透明白底 + 极淡描边 + 该主题下可读的文字，与海报小件（[FavoriteBadge] 等）
 * 共用 [GlassChipTokens]，因此「海报上的胶囊」与「页面上的标签」看起来是同一族材质。
 *
 * @param emphatic 强调态（社区标签里的高频标签、VNDB 里权重 ≥2 的标签）——只提高底色不透明度，
 *   不换成实心主题色：实心块正是被替换掉的那个样子。
 * @param onClick null = 纯展示（详情页标签目前不可点，点击不该有涟漪——那是「可选项」的暗示）。
 */
@Composable
fun TagChip(
    label: String,
    modifier: Modifier = Modifier,
    emphatic: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    val glassEnabled = GlassChipTokens.glassEnabled(LocalGlassEffect.current, LocalCardGlassLevel.current)
    val background = GlassChipTokens.surfaceChipBackground(isDark, emphatic, glassEnabled)
    val border = GlassChipTokens.chipBorder(isDark)
    val shape = RoundedCornerShape(999.dp)
    // 页面表面的标签压在页面背景上，因此对比度按**实际页面背景**判定，
    // 而不是像海报小件那样按「压在图片上的最坏情况」。
    val pageSurface = MaterialTheme.colorScheme.background
    val effective = remember(background, pageSurface) {
        GlassChipTokens.compositeOver(background, pageSurface)
    }
    // MaterialTheme 只能在组合里读，因此先把颜色取出来再进 remember
    val preferredLabel = MaterialTheme.colorScheme.onSurface
    val labelColor = remember(effective, preferredLabel) {
        GlassChipTokens.ensureReadable(preferred = preferredLabel, background = effective)
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .border(0.5.dp, border, shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (emphatic) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
        )
    }
}
