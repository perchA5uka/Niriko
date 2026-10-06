package com.otakup.niriko.ui.adaptive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.otakup.niriko.ui.animation.AnimDurationNormal
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.animation.motionEnabled
import com.otakup.niriko.ui.animation.NirikoMotionSpecs

/**
 * 详情页并排两栏的归属列（B2b）。
 *
 * 折痕判定与窗口尺寸类别是两条**正交**的判据（计划书 §三 B2 实现要点 1）：
 * [NirikoWindowLayout] 只管竖向 dock 的出现时机，折痕只管详情页内部的分栏方式。
 */
enum class NirikoDetailPane {
    /** 概览列：封面 / 标题 / 元信息 / infobox / 各 provider 绑定卡 / 关联 / 统计 / 剧照 / 角色与制作 / 收藏按钮。 */
    OVERVIEW,

    /** 内容列：评分与评分分布 / 权威评分 / 剧集评分走势 / TMDb 绑定 / 曲目列表。 */
    CONTENT,
}

/** 折痕几何 → 分栏策略。 */
enum class NirikoPaneArrangement {
    /** 不按折痕分栏（窄屏、无折痕、或折痕无法充当分隔线）。 */
    SINGLE,

    /** 竖直折痕 → 左右并排（书本式展开：折痕当两栏的自然分隔线）。 */
    SIDE_BY_SIDE,

    /** 水平折痕 → 上下分栏（平板模式 / 帐篷模式）。 */
    TOP_BOTTOM,
}

/**
 * 折痕包围盒（[NirikoFoldStrategy] 的唯一几何输入）。
 *
 * 四个入参必须同单位，[windowWidth] 与 left/right 同单位；生产环境统一用 dp
 * （[currentNirikoFoldState] 负责把 FoldingFeature 的 px bounds 换算成 dp）。
 * 刻意不持有任何 Android / Compose 类型，纯函数才能在只有 junit 的 JVM 测试里跑。
 */
@Immutable
data class NirikoFoldBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * 折痕 → 分栏策略（**纯函数**，单测见 app/src/test/java/com/otakup/niriko/ui/adaptive/NirikoFoldStrategyTest.kt）。
 *
 * 只回答「这块折痕能不能当两栏的分隔线」，不关心 dock（那是 [NirikoWindowLayout] 的事）。
 */
object NirikoFoldStrategy {
    /** 与 [FoldingFeature.Orientation] 解耦的方向常量（纯函数不引用 Android 类型）。 */
    const val ORIENTATION_UNKNOWN = 0
    const val ORIENTATION_VERTICAL = 1
    const val ORIENTATION_HORIZONTAL = 2

    /**
     * 并排两栏所需的窗口宽度下限，与 material3 窗口尺寸类的 EXPANDED 下限同值
     * （WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND = 840dp），也与
     * [NirikoWindowLayout.from] 里 EXPANDED 的宽度门槛一致 —— 839dp 单栏、840dp 起分栏。
     */
    const val MIN_SIDE_BY_SIDE_WIDTH_DP = 840

    fun from(
        orientation: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        windowWidth: Float,
    ): NirikoPaneArrangement = from(orientation, NirikoFoldBounds(left, top, right, bottom), windowWidth)

    fun from(orientation: Int, bounds: NirikoFoldBounds, windowWidth: Float): NirikoPaneArrangement = when {
        // 空折痕（无折叠 / 退化 bounds）不分栏。
        bounds.width <= 0f && bounds.height <= 0f -> NirikoPaneArrangement.SINGLE
        // 宽度不够两栏（手机竖屏的假折痕、小平板折痕等）—— 与 dock 无关，这里只看放不放得下。
        !windowWidth.isFinite() || windowWidth < MIN_SIDE_BY_SIDE_WIDTH_DP -> NirikoPaneArrangement.SINGLE
        // 竖直折痕：书本式展开 / 双屏，左右并排。
        orientation == ORIENTATION_VERTICAL -> NirikoPaneArrangement.SIDE_BY_SIDE
        // 水平折痕：平板模式 / 帐篷模式，上下分栏（首轮不渲染，见 AdaptiveDetailScaffold 说明）。
        orientation == ORIENTATION_HORIZONTAL -> NirikoPaneArrangement.TOP_BOTTOM
        else -> NirikoPaneArrangement.SINGLE
    }

    /**
     * 竖直折痕是否真的落在两栏之间（而不是贴在窗口边缘）。
     *
     * 贴边的折痕不适合当分隔线：内容贴合边缘反而更自然，此时也不需要额外留出遮挡宽度。
     */
    fun isVerticalFoldBetweenPanes(bounds: NirikoFoldBounds, windowWidth: Float): Boolean =
        bounds.width > 0f && bounds.left > 0f && bounds.right < windowWidth
}

/**
 * 当前折痕快照。[occludedWidthDp] 是折痕真正遮挡内容时的可视宽度（无缝折痕为 0，
 * 此时由 [AdaptiveDetailScaffold] 给一条最小视觉分隔线）。
 */
@Immutable
data class NirikoFoldState(
    val arrangement: NirikoPaneArrangement,
    val occludedWidthDp: Float,
) {
    companion object {
        val None = NirikoFoldState(NirikoPaneArrangement.SINGLE, 0f)
    }
}

/**
 * 读当前折痕（material3-adaptive 的 collectFoldingFeaturesAsState → androidx.window 的
 * WindowInfoTracker 流），折算成纯函数需要的 dp 几何。
 *
 * 窗口宽度取 [LocalWindowInfo] 的 containerSize（compose-ui 1.11.1 的非实验 API），
 * 刻意不用 material3-adaptive 里带 @ExperimentalMaterial3AdaptiveApi 的 currentWindowSize()。
 */
@Composable
fun currentNirikoFoldState(): NirikoFoldState {
    val features = collectFoldingFeaturesAsState().value
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val windowWidthDp = with(density) { windowInfo.containerSize.width.toDp().value }
    return remember(features, windowWidthDp, density) {
        // 优先取真正分隔屏幕的那条折痕（isSeparating），否则退回第一条。
        val feature = features.firstOrNull { it.isSeparating } ?: features.firstOrNull()
        if (feature == null) {
            NirikoFoldState.None
        } else {
            val bounds = NirikoFoldBounds(
                left = with(density) { feature.bounds.left.toDp().value },
                top = with(density) { feature.bounds.top.toDp().value },
                right = with(density) { feature.bounds.right.toDp().value },
                bottom = with(density) { feature.bounds.bottom.toDp().value },
            )
            val orientation = when (feature.orientation) {
                FoldingFeature.Orientation.VERTICAL -> NirikoFoldStrategy.ORIENTATION_VERTICAL
                FoldingFeature.Orientation.HORIZONTAL -> NirikoFoldStrategy.ORIENTATION_HORIZONTAL
                else -> NirikoFoldStrategy.ORIENTATION_UNKNOWN
            }
            val arrangement = NirikoFoldStrategy.from(orientation, bounds, windowWidthDp)
            val occludedWidthDp =
                if (arrangement == NirikoPaneArrangement.SIDE_BY_SIDE &&
                    NirikoFoldStrategy.isVerticalFoldBetweenPanes(bounds, windowWidthDp)
                ) {
                    bounds.width
                } else {
                    0f
                }
            NirikoFoldState(arrangement, occludedWidthDp)
        }
    }
}

/**
 * 详情页是否走并排两栏。
 *
 * 只有 EXPANDED（>=840dp 且高 >=480dp，即竖向 dock 出现的同一档）才排得下两栏；
 * 折痕给 SIDE_BY_SIDE（竖直折痕且宽度够）或没有折痕时都并排，
 * 唯独 TOP_BOTTOM（水平折痕 = 平板 / 帐篷模式）不出两栏 —— 首轮不做（计划书 §十四 Q4）。
 */
fun nirikoDetailUsesTwoPanes(
    windowLayout: NirikoWindowLayout,
    foldArrangement: NirikoPaneArrangement,
): Boolean = windowLayout == NirikoWindowLayout.EXPANDED && foldArrangement != NirikoPaneArrangement.TOP_BOTTOM

/** 折痕齐平时两栏之间的最小留白（用于没有可视遮挡宽度的无缝折痕）。 */
private val MinFoldGutter = 20.dp

/**
 * B2b 详情页自适应骨架：窄屏 / 中屏走 [single]（与升级前逐字相同的一份 item 列表），
 * EXPANDED 宽屏走 [overview] + [content] 并排两栏、各自独立滚动。
 *
 * Q1 决议：**明确不用 ListDetailPaneScaffold** —— 它会引入自己的返回栈语义，与现有
 * NavHost + NavController 冲突（计划书 §三 B2 风险 2 / R3）。这里就是一行 [Row] +
 * 两个各自持有 LazyListState 的列，滚动位置由调用方 remember 持有。
 *
 * 只有被选中的槽位会被组合，所以两栏 / 单栏来回切换不会丢滚动位置，也不会重复组合隐藏列。
 * 单栏 → 两栏的切换动画只发生在中间的分隔线宽度上，reduceMotion 时直接对齐（验收 6）。
 */
@Composable
fun AdaptiveDetailScaffold(
    single: @Composable (Modifier) -> Unit,
    overview: @Composable (Modifier) -> Unit,
    content: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    gutter: Dp = MinFoldGutter,
) {
    val windowLayout = currentNirikoWindowLayout()
    val fold = currentNirikoFoldState()
    val twoPanes = nirikoDetailUsesTwoPanes(windowLayout, fold.arrangement)
    val motion = motionEnabled()
    val specs = NirikoMotionSpecs

    // 折痕真正遮挡内容时留出它的宽度（内容不跨越折痕），否则只留一条最小视觉分隔。
    val targetGutter = if (fold.occludedWidthDp > gutter.value) Dp(fold.occludedWidthDp) else gutter
    val targetGutterDp = targetGutter.value

    // 单栏 ↔ 两栏的过渡：只有分隔线宽度会动，reduceMotion 直接 snap。
    val gutterAnim = remember { Animatable(0f) }
    LaunchedEffect(twoPanes, targetGutterDp, motion) {
        if (!twoPanes) {
            gutterAnim.snapTo(0f)
        } else if (motion) {
            gutterAnim.animateTo(targetGutterDp, specs.spatialDefault())
        } else {
            gutterAnim.snapTo(targetGutterDp)
        }
    }

    if (!twoPanes) {
        single(modifier.fillMaxSize())
        return
    }

    Row(modifier = modifier.fillMaxSize()) {
        overview(Modifier.weight(1f).fillMaxHeight())
        FoldGutter(width = gutterAnim.value.dp)
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

/** 两栏之间那条自然分隔线：折痕本身无缝时用一条极细的线补足视觉分隔。 */
@Composable
private fun FoldGutter(width: Dp) {
    Box(
        modifier = Modifier.width(width).fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        if (width > 0.dp) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
        }
    }
}
