package com.otakup.niriko.ui.subject

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import com.otakup.niriko.navigation.LocalDetailNavigationState

/** Keep rail offsets on the parent detail entry, not the disposable LazyColumn item. */
@Composable
internal fun rememberDetailLazyRailState(key: String): LazyListState {
    val entry = LocalDetailNavigationState.current
    val indexKey = "detail_rail_" + key + "_index"
    val offsetKey = "detail_rail_" + key + "_offset"
    val state = rememberSaveable(entry, key, saver = LazyListState.Saver) {
        LazyListState(entry?.get<Int>(indexKey) ?: 0, entry?.get<Int>(offsetKey) ?: 0)
    }
    LaunchedEffect(state, entry, key) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            if (state.layoutInfo.totalItemsCount > 0) {
                entry?.set(indexKey, index); entry?.set(offsetKey, offset)
            }
        }
    }
    DisposableEffect(state, entry, key) {
        onDispose {
            if (state.layoutInfo.totalItemsCount > 0) {
                entry?.set(indexKey, state.firstVisibleItemIndex)
                entry?.set(offsetKey, state.firstVisibleItemScrollOffset)
            }
        }
    }
    return state
}

@Composable
internal fun rememberDetailPixelRailState(key: String): ScrollState {
    val entry = LocalDetailNavigationState.current
    val offsetKey = "detail_rail_" + key + "_pixels"
    val state = rememberSaveable(entry, key, saver = ScrollState.Saver) { ScrollState(entry?.get<Int>(offsetKey) ?: 0) }
    LaunchedEffect(state, entry, key) {
        snapshotFlow { state.value }.collect { offset ->
            if (state.maxValue > 0 && state.maxValue != Int.MAX_VALUE) entry?.set(offsetKey, offset)
        }
    }
    DisposableEffect(state, entry, key) {
        onDispose { if (state.maxValue > 0 && state.maxValue != Int.MAX_VALUE) entry?.set(offsetKey, state.value) }
    }
    return state
}
