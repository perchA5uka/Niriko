package com.otakup.niriko.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Pager 手势锁：搜索交互（IosStyleSearchComponent）在按下/拖拽期间置 true，
 * MainPager 据此设置 HorizontalPager(userScrollEnabled = !locked)，从根上杜绝
 * 「左划展开搜索框」与「Pager 切页」的手势冲突。
 *
 * 用法：
 * - 提供：MainActivity 中 `CompositionLocalProvider(LocalSearchGestureLock provides lock)`
 * - 写入：IosStyleSearchComponent 锚点手势 down → true，try/finally 收尾 → false
 * - 读取：MainPager `userScrollEnabled = !LocalSearchGestureLock.current.value`
 */
val LocalSearchGestureLock = staticCompositionLocalOf<MutableState<Boolean>> {
    error("LocalSearchGestureLock 未提供：请在 MainActivity 顶层提供")
}

/** 在 MainActivity 顶层调用，创建 Pager 手势锁。 */
@Composable
fun rememberSearchGestureLock(): MutableState<Boolean> = remember { mutableStateOf(false) }
