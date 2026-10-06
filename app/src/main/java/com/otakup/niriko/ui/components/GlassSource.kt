package com.otakup.niriko.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * 玻璃输入源（R2 · 显式化）。
 *
 * ## 为什么需要它
 * 本项目的玻璃有两条互不相通的渲染管线，各自带一个 Backdrop 类型：
 * - **卡片**（[appleGlassCard]，kyant com.kyant.backdrop.Backdrop）：来源是
 *   [LocalCardGlassBackdrop]（顶层四页由 MainActivity 注入「壁纸层」捕获）。
 * - **分区卡**（[GlassSectionCard]，miuix top.yukonga.miuix.kmp.blur.Backdrop）：
 *   来源是调用方显式传参（详情页把封面背景墙捕获后逐卡传入）。
 *
 * 结果是二级页的卡片只能折射「壁纸层」这一间接来源：没有壁纸、或壁纸被页面自身
 * 背景墙盖住时，卡片没有可折射的源，只能退化成静态 tint。R2 的修法不是换依赖
 * （haze 2.x 与 miuix 一样有 Kotlin/AGP 版本门槛），而是把「输入源」显式化：
 * 页面自己拥有的背景墙直接声明成页内卡片的玻璃源。
 *
 * ## 统一规则（材质统一表）
 * - 卡片 [appleGlassCard] / [BlurredGlassCard]：kyant Backdrop，读 [LocalCardGlassBackdrop]，
 *   可被 [ProvideCardGlassBackdrop] 就近覆盖；
 * - 分区卡 [GlassSectionCard]：miuix Backdrop，由调用方显式传参；
 * - 悬浮胶囊底栏：kyant Backdrop，由 MainActivity 提供窗口层捕获。
 *
 * ## 用法
 * 1. [rememberPageCardGlassBackdrop] 捕获页面自己的背景墙（一层离屏录制）；
 * 2. [Modifier.pageCardGlassBackdrop] 把这一层注册成捕获源；
 * 3. [ProvideCardGlassBackdrop] 把同一 Backdrop 就近注入页内卡片。
 *
 * 只在确定要开启真玻璃的档位（GlassEffectLevel.FULL）捕获：捕获本身是一层离屏录制，
 * REDUCED/OFF 档不应付出这份开销，由调用方的 enabled 参数控制。
 */
@Composable
fun rememberPageCardGlassBackdrop(enabled: Boolean): LayerBackdrop? {
    val surface = MaterialTheme.colorScheme.surface
    return if (enabled) {
        rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }
    } else {
        null
    }
}

/**
 * 把页面自己的背景墙图层注册为 [backdrop] 的捕获源；[backdrop] 为 null 时原样返回，
 * 调用方因此可以无条件串在 Modifier 链上。
 */
fun Modifier.pageCardGlassBackdrop(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

/**
 * 把页内卡片的玻璃源就近覆盖为 [backdrop]（null 时什么都不做，卡片回到全局来源）。
 * 只覆盖 [LocalCardGlassBackdrop]：分区卡走 miuix 显式传参，不受影响。
 */
@Composable
fun ProvideCardGlassBackdrop(backdrop: Backdrop?, content: @Composable () -> Unit) {
    // A source update must never change the composition slot holding shared nodes.
    val effectiveBackdrop = backdrop ?: LocalCardGlassBackdrop.current
    CompositionLocalProvider(LocalCardGlassBackdrop provides effectiveBackdrop, content = content)
}