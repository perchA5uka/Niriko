package com.otakup.niriko.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

// ==================== 加载中 ====================

/**
 * 全屏加载指示器（居中转圈）。
 */
@Composable
fun LoadingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

// ==================== 骨架屏（B4：compose-shimmer） ====================

/**
 * 骨架扫光修饰符（B4，来源 `com.valentinilk.shimmer:compose-shimmer`）。
 *
 * **落点规则**：加在**每个占位灰块自己身上**（骨架块 / 封面块 / 占位圆 / 骨架卡），
 * 并放在 `.clip(shape)` + `.background(...)` **之后**，这样扫光带被各自的圆角裁剪，
 * 卡片之间的页面背景空隙不会出现扫光。**不要在外层容器上再加一次**（会叠出两条扫光）。
 *
 * **降级**：[ShimmerPolicy] 判定需要降级时（应用内「减少动态效果」/ 系统关闭动画 /
 * 玻璃档位 OFF）原样返回 this，骨架退化为**静态灰块**：形状与位置不变，完全静止。
 */
@Composable
fun Modifier.skeletonShimmer(): Modifier {
    val reduceMotion = LocalReduceMotion.current
    // MainActivity.kt:184-191 已把「应用内减少动态效果」与系统 animator_duration_scale == 0
    // 取并集后交给 LocalReduceMotion（Theme.kt:252 提供），Compose 侧无法再区分二者，
    // 故合并值同时传给两个语义参数（任一为真都必须静止）。
    val animate = ShimmerPolicy.shouldAnimate(
        reduceMotion = reduceMotion,
        systemAnimatorOff = reduceMotion,
        glassEffect = LocalGlassEffect.current,
    )
    return if (animate) {
        this.shimmer(rememberShimmer(shimmerBounds = ShimmerBounds.Window))
    } else {
        this
    }
}

/** 骨架静态底色（M3 surfaceVariant 同族，半透明以适配玻璃卡片背景）。 */
@Composable
internal fun skeletonBaseColor(): Color =
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

/**
 * 通用骨架块（B4 新增）。
 *
 * @param width null = 宽度交给 [modifier]（例如 `Modifier.fillMaxWidth(0.6f)`）
 * @param height 块高
 * @param shape 圆角形状
 */
@Composable
fun SkeletonBlock(
    width: Dp? = null,
    height: Dp,
    shape: Shape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(shape)
            .background(skeletonBaseColor())
            .skeletonShimmer(),
    )
}

/**
 * 骨架屏卡片行 — 用于列表加载中的占位。
 * @param cardCount 占位卡片数量
 * @param cardHeight 卡片高度
 */
@Composable
fun SkeletonList(
    cardCount: Int = 3,
    cardHeight: Dp = 120.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(cardCount) {
            SkeletonCard(height = cardHeight)
        }
    }
}

@Composable
private fun SkeletonCard(
    height: Dp,
) {
    val shape = RoundedCornerShape(12.dp)
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(skeletonBaseColor()),
    ) {
        // 封面占位
        Box(
            modifier = Modifier
                .width(height * 0.75f)
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(lineColor.copy(alpha = 0.20f))
                .skeletonShimmer(),
        )
        // 文字占位
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonLine(fraction = 0.7f, height = 14.dp, alpha = 0.28f)
            SkeletonLine(fraction = 0.5f, height = 12.dp, alpha = 0.20f)
            SkeletonLine(fraction = 0.4f, height = 12.dp, alpha = 0.16f)
        }
    }
}

/** 骨架里的单行文字占位灰条（自带上色 + 扫光，扫光被 4.dp 圆角裁剪）。 */
@Composable
private fun SkeletonLine(
    fraction: Float,
    height: Dp,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            .skeletonShimmer(),
    )
}

/**
 * 小号骨架占位圆（用于头像/圆形图标）。
 */
@Composable
fun SkeletonCircle(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(skeletonBaseColor())
            .skeletonShimmer(),
    )
}

/**
 * 横向卡片骨架（B4 新增）：尺寸对齐 [com.otakup.niriko.ui.subject.UniversalSubjectCard]
 * （玻璃卡圆角 20.dp、内容 padding 12.dp、封面 80.dp 宽 × 3:4、右侧三行文本），
 * 供搜索建议 / 趋势榜单首屏加载使用。卡片底走 [appleGlassCard] 纯渐变（零 RenderEffect），
 * 内部每个占位灰块各自带扫光（不在卡片根上叠第二层）。
 */
@Composable
fun SkeletonSubjectCard(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .appleGlassCard(shape = shape)
            .clip(shape)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 封面：80.dp 宽 × 3:4（与 CoverImage 默认比例一致）
        SkeletonBlock(
            width = 80.dp,
            height = 80.dp * 4f / 3f,
            shape = RoundedCornerShape(12.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkeletonLine(fraction = 0.35f, height = 12.dp, alpha = 0.24f)
            SkeletonLine(fraction = 0.75f, height = 16.dp, alpha = 0.30f)
            SkeletonLine(fraction = 0.55f, height = 12.dp, alpha = 0.18f)
        }
    }
}

// ==================== 错误状态 ====================

/**
 * 带图标的错误提示卡片。
 * @param message 错误消息
 * @param onRetry 重试回调（为 null 时不显示重试按钮）
 * @param icon 错误图标，默认 CloudOff（网络错误）
 */
@Composable
fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.CloudOff,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("重试")
                }
            }
        }
    }
}

/**
 * 网络错误专用组件（WifiOff 图标）。
 */
@Composable
fun NetworkErrorContent(
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    message: String = "网络连接不可用",
) {
    ErrorContent(
        message = message,
        onRetry = onRetry,
        modifier = modifier,
        icon = Icons.Outlined.WifiOff,
    )
}

// ==================== 空状态 ====================

/**
 * 空状态占位。
 * @param icon 图标
 * @param title 标题
 * @param subtitle 副标题（可选）
 * @param action 操作按钮（可选）
 */
@Composable
fun EmptyContent(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                action()
            }
        }
    }
}

/**
 * 搜索无结果专用。
 */
@Composable
fun SearchEmptyContent(
    query: String,
    modifier: Modifier = Modifier,
) {
    EmptyContent(
        icon = Icons.Outlined.SearchOff,
        title = "未找到「${query}」相关作品",
        subtitle = "试试其他关键词或调整筛选条件",
        modifier = modifier,
    )
}

/**
 * 列表为空专用。
 */
@Composable
fun ListEmptyContent(
    title: String = "这里空空如也",
    subtitle: String? = "还没有添加任何内容",
    modifier: Modifier = Modifier,
) {
    EmptyContent(
        icon = Icons.Outlined.Inbox,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    )
}

// ==================== 轻量内联提示 ====================

/**
 * 轻量内联错误提示（用于列表中间的某一行）。
 */
@Composable
fun InlineErrorHint(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Text("重试", style = MaterialTheme.typography.bodySmall)
        }
    }
}
