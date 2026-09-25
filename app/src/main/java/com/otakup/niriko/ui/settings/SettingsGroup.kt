package com.otakup.niriko.ui.settings

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.motionEnabled
import com.otakup.niriko.ui.components.appleGlassCard

// ==================== 分组／行度量（对齐 Kazumi SplitListGroup） ====================

/** 组两端的大圆角（Kazumi tonalCardRadius 24 的视觉量级；与站内玻璃卡片一致的 20dp）。 */
private val GroupOuterRadius = 20.dp

/** 组内行的相邻小圆角。 */
private val RowInnerRadius = 4.dp

/** 行间距。 */
private val RowGap = 4.dp

/** 按压圆角 morph 时长（与 Kazumi splitListMotionDuration 一致）。 */
private const val RowMorphMillis = 250

/** 按压圆角 morph 曲线：easeInOutCubic（与 Kazumi splitListMotionCurve 一致）。 */
private val RowMorphEasing: Easing = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)

/** 行底：在玻璃分组容器上叠一层低透明度 tonal 面，按压 morph 才可见。 */
private val RowContainerAlpha = 0.55f

/**
 * 行交互源：由 [SettingsSplitGroup] 逐行提供（点击与按压 morph 共用同一实例）。
 * 组外（弹窗／底部面板等）为 null，行组件回退到自己 remember 的实例。
 */
val LocalRowInteractionSource = staticCompositionLocalOf<MutableInteractionSource?> { null }

/**
 * 取当前行的交互源：在 [SettingsSplitGroup] 内用分组提供的实例（这样按压会导致圆角 morph），
 * 组外回退到调用点自己的实例（避免多行共享同一个默认实例而一起发光）。
 */
@Composable
fun rememberRowInteractionSource(): MutableInteractionSource {
    val provided = LocalRowInteractionSource.current
    val fallback = remember { MutableInteractionSource() }
    return provided ?: fallback
}

/**
 * Kazumi 风格 M3 分组列表（SettingsSplitGroup）：
 * - 组容器仍是 Niriko 的 appleGlassCard 玻璃卡（取舍 ④B），圆角 20dp；
 * - 组内行间距 4dp、行小圆角 4dp；首行上／末行下为组外大圆角；
 * - 行被按下时该行圆角 morph 到 20dp（250ms easeInOutCubic；系统关闭动画时瞬时），
 *   以此替代分割线给出按压反馈；
 * - 行底为低透明度 surfaceContainerLow，间隔露出玻璃。
 */
@Composable
fun SettingsSplitGroup(
    modifier: Modifier = Modifier,
    content: List<@Composable () -> Unit>,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth().appleGlassCard(shape = RoundedCornerShape(GroupOuterRadius)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                content.forEachIndexed { index, row ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    // 减少动态效果／系统关闭动画：不播 morph
                    val morph: AnimationSpec<Dp> = if (motionEnabled()) {
                        tween(durationMillis = RowMorphMillis, easing = RowMorphEasing)
                    } else {
                        snap()
                    }
                    val topTarget = if (pressed || index == 0) GroupOuterRadius else RowInnerRadius
                    val bottomTarget =
                        if (pressed || index == content.lastIndex) GroupOuterRadius else RowInnerRadius
                    val topRadius by animateDpAsState(topTarget, morph, label = "settingsRowTopRadius")
                    val bottomRadius by animateDpAsState(bottomTarget, morph, label = "settingsRowBottomRadius")
                    CompositionLocalProvider(LocalRowInteractionSource provides interactionSource) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(
                                topStart = topRadius,
                                topEnd = topRadius,
                                bottomStart = bottomRadius,
                                bottomEnd = bottomRadius,
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = RowContainerAlpha),
                        ) {
                            row()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 设置二级页脚手架（Kazumi SettingsDetailScaffold 的 AppBar 形态等价物）：
 * 透明 TopAppBar（64dp、标题 headlineSmall、返回箭头）+ 可滚动内容区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            // NavHost 已在 Scaffold systemBars inset 内，此处不能再叠加状态栏 inset
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            content()
        }
    }
}

/**
 * 分组标题（Kazumi SectionHeader 等价物）：titleSmall + primary + w600，
 * 可选 description（bodyMedium 次要色），水平 16dp／上 20dp／下 8dp 内边距。
 */
@Composable
fun SettingsGroupTitle(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (description != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 通用单选弹窗（选项过多时仍用它；三选项以内的设置优先用就地 SettingsRadioRow）。
 */
@Composable
fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
