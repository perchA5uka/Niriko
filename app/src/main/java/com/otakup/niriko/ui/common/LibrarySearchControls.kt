package com.otakup.niriko.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.WatchStatus

fun librarySearchStatusCount(counts: Map<WatchStatus, Int>, status: WatchStatus?): Int =
    if (status == null) counts.values.sum() else counts[status] ?: 0

@Composable
fun LibraryStatusSearchRow(
    counts: Map<WatchStatus, Int>,
    selectedStatus: WatchStatus?,
    onStatusSelected: (WatchStatus?) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        (listOf<WatchStatus?>(null) + WatchStatus.entries).forEach { status ->
            val isSelected = selectedStatus == status
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .semantics { selected = isSelected }
                    .clickable(role = Role.RadioButton) { onStatusSelected(status) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(librarySearchStatusCount(counts, status).toString(), style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(status?.label ?: "全部", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
