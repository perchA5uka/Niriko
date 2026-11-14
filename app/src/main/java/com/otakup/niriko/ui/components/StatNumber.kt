package com.otakup.niriko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.theme.NirikoShapes

// 统计数值的三档排版（V-21）。
//
// 改造前同一个「大数字」在统计页用了 20 / 22 / 24 / 26 / 28 / 32sp 六种字号：主指标、均分、
// 完成率、状态计数各自挑一档，读者无法从大小判断哪个数字更重要（prototype-pending-changes.html ④）。
// 归并后只有三档，且语义与字号绑定：
//   关键数字 [StatNumber] = 24sp / w600 / tnum；
//   辅助标签 [StatLabel]  = 13sp（labelMedium）；
//   注释     [StatNote]   = 11.5sp（labelSmall）。
// 页面标题的 28sp（headlineMedium）不在这三档里——它属于页面层级，不是数值层级。
//
// tnum 必须只加在「数值」上：等宽数字让每次刷新时数字宽度不变，整行不左右跳；
// 标签是文字，加了反而破坏字形节奏（Type.kt 的既定口径）。

/** 关键数字的字号（sp）。单测钉住，防止又漂回五档。 */
internal const val STAT_NUMBER_SIZE_SP = 24f

/** 辅助标签的字号（sp）。 */
internal const val STAT_LABEL_SIZE_SP = 13f

/** 注释的字号（sp）。 */
internal const val STAT_NOTE_SIZE_SP = 11.5f

/** 统计磁贴的语义色薄涂浓度（P0a：accent × 0.14 叠在 surfaceContainer 上，不是实底）。 */
internal const val STAT_TILE_TINT_ALPHA = 0.14f

/** 关键数字的语气：默认跟随正文色；关键指标可用主色区分层级（原型 A 方案）。 */
enum class StatNumberTone { Default, Primary }

/**
 * 关键数字（24sp / w600 / tnum），可选单位与颜色覆盖。
 *
 * 为什么单位单独一个参数而不是拼进字符串：单位要用 13sp 的辅助档，拼进去会跟着 24sp 一起放大，
 * 而且不同调用点的单位分隔符会各写各的（此前就出现过 "180部" 与 "180 部作品" 两种）。
 */
@Composable
fun StatNumber(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    tone: StatNumberTone = StatNumberTone.Default,
    color: Color = Color.Unspecified,
) {
    val colorScheme = MaterialTheme.colorScheme
    val valueColor = when {
        color != Color.Unspecified -> color
        tone == StatNumberTone.Primary -> colorScheme.primary
        else -> colorScheme.onSurface
    }
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            // 24sp 取自 headlineSmall（Type.kt 的单一来源），这里只追加 tnum。
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            softWrap = false,
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 数值的辅助标签（13sp / onSurfaceVariant）。 */
@Composable
fun StatLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 数值的注释（11.5sp / onSurfaceVariant）；图例、口径说明等。 */
@Composable
fun StatNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 统计磁贴：图标 + 标签一行，关键数字一行。
 *
 * 状态四卡、今日卡里的小磁贴此前各自写了三种内部布局，这里只留一种：
 * 图标与标签先说明「这是什么」，数字在下方回答「多少」。
 *
 * @param containerColor 磁贴底色（默认中性 surfaceContainer）
 * @param tint 可选语义色薄涂：按 [STAT_TILE_TINT_ALPHA] 叠在底色上。状态卡用
 *   statusTone.accent 薄涂 + surfaceContainer 底，保持 P0a 定下的「语义色是薄层不是实底」；
 *   直接把 statusTone.container 当底色会把整张卡变成浅粉彩色块（P0a 之前的问题）。
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    valueColor: Color = Color.Unspecified,
    tint: Color? = null,
) {
    val shape = NirikoShapes.SectionShape
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .drawBehind {
                if (tint != null) drawRect(tint.copy(alpha = STAT_TILE_TINT_ALPHA))
            }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(6.dp))
        StatNumber(value = value, color = if (valueColor == Color.Unspecified) contentColor else valueColor)
    }
}
