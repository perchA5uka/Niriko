package com.otakup.niriko.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.components.searchGlassSurface

data class TopSelectorOption(
    val key: String,
    val label: String,
    val onSelect: () -> Unit,
)

/** Current item first; all other entries keep their original order. */
fun orderedTopSelectorOptions(options: List<TopSelectorOption>, selectedKey: String): List<TopSelectorOption> =
    options.filter { it.key == selectedKey } + options.filter { it.key != selectedKey }

/** Both pages use the same fixed anchor; popup measurement never reaches the feed. */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun VerticalTopSelector(
    selectedKey: String,
    options: List<TopSelectorOption>,
    modifier: Modifier = Modifier,
    actions: List<TopSelectorOption> = emptyList(),
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.key == selectedKey }?.label ?: options.firstOrNull()?.label.orEmpty()
    val textStyle = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    BoxWithConstraints(modifier.height(56.dp)) {
        val widthLimit = maxWidth.coerceAtLeast(56.dp)
        val transition = updateTransition(currentLabel, label = "topSelector")
        val width by transition.animateDp(
            transitionSpec = { if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 400f) },
            label = "labelWidth",
        ) { label ->
            (with(density) { measurer.measure(AnnotatedString(label), textStyle).size.width.toDp() } + 52.dp)
                .coerceIn(56.dp, widthLimit)
        }
        Row(
            Modifier.width(width).height(56.dp)
                .searchGlassSurface(RoundedCornerShape(28.dp))
                .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            transition.Crossfade(modifier = Modifier.weight(1f)) { label ->
                Text(label, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val triangleColor = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(Modifier.size(10.dp, 9.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height); close()
                }
                drawPath(path, triangleColor)
            }
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) { androidx.compose.ui.unit.IntOffset(0, 60.dp.roundToPx()) },
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.width(widthLimit.coerceAtMost(280.dp)).heightIn(max = 360.dp)
                        .searchGlassSurface(RoundedCornerShape(24.dp))
                        .verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                ) {
                    orderedTopSelectorOptions(options, selectedKey).forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.RadioButton) { expanded = false; option.onSelect() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (option.key == selectedKey) Icon(Icons.Default.Check, "已选中", Modifier.size(18.dp))
                        }
                    }
                    if (actions.isNotEmpty()) {
                        androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        actions.forEach { action ->
                            Text(action.label, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { expanded = false; action.onSelect() }
                                .padding(horizontal = 16.dp, vertical = 14.dp))
                        }
                    }
                }
            }
        }
    }
}
