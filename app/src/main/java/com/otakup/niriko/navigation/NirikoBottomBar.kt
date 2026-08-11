package com.otakup.niriko.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.ui.animation.AnimDurationShort
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.components.InnerShadow
import com.otakup.niriko.ui.components.innerShadow
import com.otakup.niriko.ui.components.liquidglass.LIQUID_LENS_SHADER
import com.otakup.niriko.ui.theme.LocalDarkTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.drawBackdrop
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * 悬浮胶囊底部导航（对齐 SukiSU-Ultra：与 HorizontalPager 联动）。
 *
 * - 指示器 offset = (currentPage + currentPageOffsetFraction) × tabWidthPx —— **连续跟手**，
 *   手势滑动页面时指示器实时跟随，无独立动画延迟
 * - tab 点击 = pagerState.animateScrollToPage（Pager 弹簧滚动）
 * - 翻页到位（currentPage 变化）播放"放大 → 回收"弹性动画；首次组合不播放
 * - tab 宽度基准来自容器实测宽度（onGloballyPositioned），指示器与 tab 共用 → 中心精确对齐
 * - 液态玻璃（SukiSU 同款 miuix-blur）：胶囊真折射下方页面内容——
 *   [backdrop] 由 MainActivity 的 rememberLayerBackdrop 捕获页面层，
 *   drawBackdrop 施加高斯模糊 + AGSL 折射 + 饱和度增强；低版本自动降级静态背景
 */
@Composable
fun NirikoBottomBar(
    pagerState: PagerState,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop,
    modifier: Modifier = Modifier,
) {
    val isInDark = LocalDarkTheme.current
    val pillShape = CircleShape
    val accentColor = MaterialTheme.colorScheme.primary
    val tabs = TopLevelDestination.entries
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // 玻璃 tint 底（@Composable 读取，供 drawBackdrop onDrawSurface 的 DrawScope 使用）
    val glassContainerColor = MaterialTheme.colorScheme.surfaceContainer
    // AGSL 液态透镜 shader：组合期编译一次（android RuntimeShader 编译昂贵，勿在绘制期重建）
    val lensShader = remember { android.graphics.RuntimeShader(LIQUID_LENS_SHADER) }

    // 容器实测宽度 → 统一 tab 宽度基准（px）
    var containerWidthPx by remember { mutableStateOf(0f) }
    val horizontalPadPx = with(density) { 4.dp.toPx() }
    val tabWidthPx = if (containerWidthPx > 0f)
        (containerWidthPx - horizontalPadPx * 2f) / tabs.size
    else with(density) { 80.dp.toPx() }

    // 翻页到位放大动画（currentPage 变化时播放；首次组合不播放）
    val indicatorScale = remember { Animatable(1f) }
    val firstComposition = remember { mutableStateOf(true) }
    LaunchedEffect(pagerState.currentPage) {
        if (firstComposition.value) {
            firstComposition.value = false
            return@LaunchedEffect
        }
        indicatorScale.animateTo(
            targetValue = 1.15f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessHigh),
        )
        indicatorScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = AnimEasingDefault),
        )
    }

    Box(
        modifier = modifier
            .width(IntrinsicSize.Min)
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 胶囊容器
        Box(
            modifier = Modifier
                .height(64.dp)
                .onGloballyPositioned { coords ->
                    containerWidthPx = coords.size.width.toFloat()
                }
                .shadow(
                    elevation = 16.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = if (isInDark) 0.5f else 0.25f),
                    spotColor = Color.Black.copy(alpha = if (isInDark) 0.5f else 0.25f),
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        // 液态玻璃链（android RenderEffect 直构，避免 miuix 0.9.0 无 blur 辅助）：
                        //   内容 → 高斯模糊（磨砂底）→ AGSL 折射/色散 → 饱和度增强
                        colorControls(brightness = 0f, contrast = 1f, saturation = 1.2f)
                        val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                            with(density) { 12.dp.toPx() },
                            with(density) { 12.dp.toPx() },
                            android.graphics.Shader.TileMode.CLAMP,
                        ).asComposeRenderEffect()
                        val lensEffect = lensShader.let { shader ->
                            val w = size.width.coerceAtLeast(1f)
                            val h = size.height.coerceAtLeast(1f)
                            shader.setFloatUniform("size", w, h)
                            shader.setFloatUniform("offset", 0f, 0f)
                            shader.setFloatUniform("cornerRadii", w / 2f, w / 2f, w / 2f, w / 2f)
                            shader.setFloatUniform("refractionHeight", with(density) { 18.dp.toPx() })
                            shader.setFloatUniform("refractionAmount", -with(density) { 24.dp.toPx() })
                            shader.setFloatUniform("depthEffect", 0.3f)
                            shader.setFloatUniform("chromaticAberration", 0.35f)
                            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                        }
                        // android 原生链：blur(inner) → lens(outer)
                        renderEffect = android.graphics.RenderEffect
                            .createChainEffect(blurEffect.asAndroidRenderEffect(), lensEffect)
                            .asComposeRenderEffect()
                    },
                    onDrawSurface = {
                        // 玻璃 tint 底（SukiSU containerColor：半透明 surfaceContainer）
                        drawRect(
                            color = glassContainerColor,
                            alpha = if (isInDark) 0.5f else 0.55f,
                        )
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (isInDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.6f),
                    shape = pillShape,
                )
                .innerShadow(shape = pillShape) {
                    InnerShadow(
                        radius = 12.dp,
                        color = Color.Black.copy(alpha = 0.18f),
                        alpha = 1f,
                    )
                }
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            // 滑动选中指示器（底层）：连续跟手 offset = (currentPage + offsetFraction) × tabWidthPx
            val indicatorEdge = with(density) { 2.dp.toPx() }
            val indicatorWidthDp = with(density) { (tabWidthPx - 4.dp.toPx()).toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        val x = (pagerState.currentPage + pagerState.currentPageOffsetFraction) * tabWidthPx + indicatorEdge
                        IntOffset(x.roundToInt(), 0)
                    }
                    .width(indicatorWidthDp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = indicatorScale.value
                        scaleY = indicatorScale.value
                    }
                    .clip(pillShape)
                    .background(accentColor.copy(alpha = 0.15f), pillShape),
            )
            // tabs（上层，透明背景；点击 = Pager 翻页；无 spacing，位置 = tabWidthPx * index）
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                tabs.forEach { destination ->
                    val selected = pagerState.currentPage == destination.ordinal
                    FloatingTabItem(
                        destination = destination,
                        selected = selected,
                        tabWidthPx = tabWidthPx,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(destination.ordinal) }
                        },
                    )
                }
            }
        }
    }
}

/** 胶囊内单个 tab：透明背景（选中由底层指示器表达），图标 + 文字。 */
@Composable
private fun FloatingTabItem(
    destination: TopLevelDestination,
    selected: Boolean,
    tabWidthPx: Float,
    onClick: () -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val iconScale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            iconScale.animateTo(1.18f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            iconScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }
    Column(
        modifier = Modifier
            .width(with(density) { tabWidthPx.toDp() })
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(animationSpec = tween(AnimDurationShort)) togetherWith
                    fadeOut(animationSpec = tween(AnimDurationShort))
            },
            label = "navIcon",
        ) { isSelected ->
            Icon(
                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = stringResource(destination.titleRes),
                tint = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    },
            )
        }
        Text(
            text = stringResource(destination.titleRes),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

