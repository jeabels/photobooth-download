package com.foodpos.app.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodpos.app.core.Dates
import com.foodpos.app.data.local.MovementRow
import com.foodpos.app.data.local.MovementType
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.components.EmptyState
import com.foodpos.app.ui.theme.LocalStatusColors

/** The inventory movement ledger: every delivery, sale, adjustment, write-off and voided sale. */
@Composable
fun LedgerScreen() {
    val vm = appViewModel { LedgerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Product, batch, note or person") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            item { FilterChip(selected = state.type == null, onClick = { vm.setType(null) }, label = { Text("All") }) }
            items(MovementType.entries) { type ->
                FilterChip(
                    selected = state.type == type,
                    onClick = { vm.setType(if (state.type == type) null else type) },
                    label = { Text(type.label) },
                )
            }
        }
        if (state.rows.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.ListAlt, "No stock movements", "Deliveries, sales and adjustments are recorded here.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(state.rows, key = { it.movement.id }) { row ->
                    LedgerRow(row)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(row: MovementRow) {
    val m = row.movement
    val colors = LocalStatusColors.current
    val icon: ImageVector = when (m.type) {
        MovementType.DELIVERY -> Icons.Filled.LocalShipping
        MovementType.SALE -> Icons.Filled.PointOfSale
        MovementType.ADJUSTMENT -> Icons.Filled.Tune
        MovementType.SPOILAGE -> Icons.Filled.DeleteSweep
        MovementType.VOID_RETURN -> Icons.AutoMirrored.Filled.Undo
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.productName ?: "Unknown product", style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(m.type.label, row.batchNumber?.let { "Batch $it" }, m.note.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${Dates.formatDateTime(m.createdAt)} · ${m.userName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (m.quantity > 0) "+${m.quantity}" else m.quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (m.quantity > 0) colors.good else colors.danger,
        )
    }
}
