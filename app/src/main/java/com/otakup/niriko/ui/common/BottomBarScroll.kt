package com.otakup.niriko.ui.common

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * 底栏滚动联动（P3 §6.2）：
 * 顶层页面把滚动位移写入共享 [LocalBottomBarHideFraction]，底栏据此 translateY + alpha。
 *
 * 约定：向下滚动（页面内容上移）时 hideFraction 增大；向上滚动回弹恢复。
 * [reportBottomBarScroll] 挂到各页面的 Scrollable 容器上即可。
 */
val LocalBottomBarHideFraction = staticCompositionLocalOf<MutableFloatState> { mutableFloatStateOf(0f) }

/** 归一化距离：约 1200px 的向下滚动把底栏完全隐藏。 */
private const val HIDE_PX = 1200f

/**
 * 为滚动容器上报"底栏隐藏比例"（0..1）。
 * 挂到 LazyColumn / LazyVerticalGrid / verticalScroll 的 modifier 上。
 */
@androidx.compose.runtime.Composable
fun Modifier.reportBottomBarScroll(): Modifier {
    val fraction = LocalBottomBarHideFraction.current
    val connection = remember(fraction) {
        object : NestedScrollConnection {
            private var acc = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy != 0f) {
                    acc = (acc + dy).coerceIn(-HIDE_PX * 0.4f, HIDE_PX * 1.2f)
                    fraction.floatValue = (acc / HIDE_PX).coerceIn(0f, 1f)
                }
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}

/** 供底栏读取隐藏比例（0..1）。 */
@androidx.compose.runtime.Composable
fun bottomBarHideFraction(): Float = LocalBottomBarHideFraction.current.floatValue
