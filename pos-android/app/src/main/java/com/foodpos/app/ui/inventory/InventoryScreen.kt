package com.foodpos.app.ui.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodpos.app.alerts.BatchStatus
import com.foodpos.app.alerts.BatchStock
import com.foodpos.app.alerts.ProductStock
import com.foodpos.app.alerts.StockLevel
import com.foodpos.app.core.Dates
import com.foodpos.app.core.Money
import com.foodpos.app.data.local.ProductEntity
import com.foodpos.app.ui.ShowMessages
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.components.ConfirmDialog
import com.foodpos.app.ui.components.EmptyState
import com.foodpos.app.ui.components.PillRow
import com.foodpos.app.ui.components.StatusPill
import com.foodpos.app.ui.theme.LocalStatusColors

/** Which dialog is open. */
private sealed interface InventoryDialog {
    data class EditProduct(val product: ProductEntity?) : InventoryDialog
    data class Receive(val productId: String?) : InventoryDialog
    data class Adjust(val item: ProductStock) : InventoryDialog
    data class WriteOff(val batch: BatchStock, val productName: String) : InventoryDialog
    data class Archive(val product: ProductEntity) : InventoryDialog
}

@Composable
fun InventoryScreen(isOwner: Boolean) {
    val vm = appViewModel { InventoryViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<InventoryDialog?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    ShowMessages(vm)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search product, SKU or batch") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PillRow(
                options = StockFilter.entries,
                selected = state.filter,
                label = { it.label },
                onSelect = vm::setFilter,
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
            when {
                state.loading -> Unit
                state.items.isEmpty() -> EmptyState(
                    Icons.Filled.Inventory2,
                    if (state.allProducts.isEmpty()) "No products yet" else "Nothing here",
                    if (state.allProducts.isEmpty()) {
                        if (isOwner) "Add your first product, then receive a delivery to put it in stock." else "Ask an owner to add products."
                    } else {
                        "No products match this search or filter."
                    },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.product.id }) { item ->
                        val isExpanded = item.product.id in expanded
                        ProductStockCard(
                            item = item,
                            symbol = settings.currencySymbol,
                            expanded = isExpanded,
                            isOwner = isOwner,
                            onToggle = { expanded = if (isExpanded) expanded - item.product.id else expanded + item.product.id },
                            onReceive = { dialog = InventoryDialog.Receive(item.product.id) },
                            onEdit = { dialog = InventoryDialog.EditProduct(item.product) },
                            onAdjust = { dialog = InventoryDialog.Adjust(item) },
                            onArchive = { dialog = InventoryDialog.Archive(item.product) },
                            onWriteOff = { batch -> dialog = InventoryDialog.WriteOff(batch, item.product.name) },
                        )
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isOwner) {
                SmallFloatingActionButton(onClick = { dialog = InventoryDialog.EditProduct(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add product")
                }
            }
            if (state.allProducts.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = InventoryDialog.Receive(null) },
                    icon = { Icon(Icons.Filled.LocalShipping, contentDescription = null) },
                    text = { Text("Receive delivery") },
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is InventoryDialog.EditProduct -> ProductEditorDialog(
            product = d.product,
            categories = state.categories,
            settings = settings,
            onSave = { draft -> vm.saveProduct(draft) { dialog = null } },
            onDismiss = { dialog = null },
        )
        is InventoryDialog.Receive -> ReceiveDeliveryDialog(
            products = state.allProducts.map { it.product },
            initialProductId = d.productId,
            suggestedBatch = remember(d) { vm.suggestBatchNumber() },
            symbol = settings.currencySymbol,
            onSave = { draft -> vm.receiveDelivery(draft) { dialog = null } },
            onDismiss = { dialog = null },
        )
        is InventoryDialog.Adjust -> AdjustStockDialog(
            item = d.item,
            onSave = { batchId, delta, reason -> vm.adjust(batchId, delta, reason) { dialog = null } },
            onDismiss = { dialog = null },
        )
        is InventoryDialog.WriteOff -> ConfirmDialog(
            title = "Write off spoiled stock?",
            message = "Removes ${d.batch.remaining} of ${d.productName} (batch ${d.batch.batch.batchNumber}) from stock and records it in the ledger as spoilage.",
            confirmLabel = "Write off",
            destructive = true,
            onConfirm = { vm.writeOff(d.batch.batch.id) },
            onDismiss = { dialog = null },
        )
        is InventoryDialog.Archive -> ConfirmDialog(
            title = "Archive ${d.product.name}?",
            message = "It disappears from the POS and inventory. Past orders and the ledger keep their records.",
            confirmLabel = "Archive",
            destructive = true,
            onConfirm = { vm.archiveProduct(d.product.id) },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun ProductStockCard(
    item: ProductStock,
    symbol: String,
    expanded: Boolean,
    isOwner: Boolean,
    onToggle: () -> Unit,
    onReceive: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onArchive: () -> Unit,
    onWriteOff: (BatchStock) -> Unit,
) {
    val p = item.product
    val colors = LocalStatusColors.current
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onToggle, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.category, p.sku.ifBlank { null }, Money.format(p.priceCents, symbol)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${item.sellable} ${p.unit}", style = MaterialTheme.typography.titleMedium)
                    when (item.level) {
                        StockLevel.OUT_OF_STOCK -> StatusPill("Out of stock", colors.danger, colors.dangerContainer)
                        StockLevel.RUNNING_LOW -> StatusPill("Running low", colors.warning, colors.warningContainer)
                        StockLevel.IN_STOCK -> StatusPill("In stock", colors.good, colors.goodContainer)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for ${p.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Receive delivery") },
                            leadingIcon = { Icon(Icons.Filled.LocalShipping, null) },
                            onClick = { menu = false; onReceive() },
                        )
                        if (isOwner) {
                            DropdownMenuItem(text = { Text("Adjust stock") }, leadingIcon = { Icon(Icons.Filled.Tune, null) }, onClick = { menu = false; onAdjust() })
                            DropdownMenuItem(text = { Text("Edit product") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Filled.Archive, null) }, onClick = { menu = false; onArchive() })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val active = item.batches.count { it.remaining > 0 }
                Text(
                    "$active active batch${if (active == 1) "" else "es"} · alert at ${p.lowStockThreshold}, spoiling soon at ${p.spoilSoonDays} day(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide batches" else "Show batches",
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 8.dp, end = 12.dp)) {
                    val batches = item.batches.filter { it.remaining != 0 }
                    if (batches.isEmpty()) {
                        Text("No stock in any batch.", style = MaterialTheme.typography.bodySmall)
                    }
                    batches.forEach { batch ->
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        BatchRow(batch, p.unit, canWriteOff = isOwner && batch.status == BatchStatus.SPOILED) { onWriteOff(batch) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRow(batch: BatchStock, unit: String, canWriteOff: Boolean, onWriteOff: () -> Unit) {
    val colors = LocalStatusColors.current
    val b = batch.batch
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Batch ${b.batchNumber}", style = MaterialTheme.typography.bodyMedium)
            val dates = listOfNotNull(
                b.manufacturedOn?.let { "Made ${Dates.formatEpochDay(it)}" },
                b.expiresOn?.let { "Expires ${Dates.formatEpochDay(it)}" } ?: "No expiry",
            )
            Text(dates.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Received ${Dates.formatDate(Dates.toLocalDate(b.receivedAt))} · ${b.receivedQuantity} $unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${batch.remaining} $unit", style = MaterialTheme.typography.titleSmall)
            when (batch.status) {
                BatchStatus.SPOILED -> StatusPill("Spoiled", colors.danger, colors.dangerContainer)
                BatchStatus.SPOILING_SOON -> StatusPill(
                    if (batch.daysLeft == 0L) "Expires today" else "${batch.daysLeft}d left",
                    colors.warning,
                    colors.warningContainer,
                )
                else -> Unit
            }
            if (canWriteOff) TextButton(onClick = onWriteOff) { Text("Write off") }
        }
    }
}
