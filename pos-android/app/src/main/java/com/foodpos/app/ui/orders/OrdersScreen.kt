package com.foodpos.app.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodpos.app.core.Dates
import com.foodpos.app.core.Money
import com.foodpos.app.data.local.OrderStatus
import com.foodpos.app.data.local.OrderType
import com.foodpos.app.data.local.OrderWithItems
import com.foodpos.app.ui.ShowMessages
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.components.EmptyState
import com.foodpos.app.ui.components.InfoRow
import com.foodpos.app.ui.components.PillRow
import com.foodpos.app.ui.components.ReceiptPreview
import com.foodpos.app.ui.components.StatusPill
import com.foodpos.app.ui.components.rememberBluetoothPermission
import com.foodpos.app.ui.theme.LocalStatusColors

@Composable
fun OrdersScreen(isOwner: Boolean) {
    val vm = appViewModel { OrdersViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = state.orders.firstOrNull { it.order.id == selectedId }
    ShowMessages(vm)

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Queue no., customer or item") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PillRow(
            options = OrderRange.entries,
            selected = state.range,
            label = { it.label },
            onSelect = vm::setRange,
            contentPadding = PaddingValues(horizontal = 16.dp),
        )
        Text(
            "${state.completedCount} completed · ${Money.format(state.salesCents, settings.currencySymbol)} sales",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.orders.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, "No orders", "Orders placed in this period appear here.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.orders, key = { it.order.id }) { order ->
                    OrderRow(order, settings.currencySymbol) { selectedId = order.order.id }
                }
            }
        }
    }

    if (selected != null) {
        val receipt by vm.receipt.collectAsStateWithLifecycle()
        val printing by vm.printing.collectAsStateWithLifecycle()
        LaunchedEffect(selected.order.id, selected.order.status) { vm.loadReceipt(selected) }
        OrderDetailDialog(
            order = selected,
            receiptPreview = { receipt?.let { ReceiptPreview(it, settings.charsPerLine) } },
            canPrint = settings.printerAddress != null,
            printing = printing,
            canVoid = isOwner && selected.order.status == OrderStatus.COMPLETED,
            onPrint = { vm.reprint(selected) },
            onVoid = { reason -> vm.voidOrder(selected, reason) },
            onDismiss = { selectedId = null },
        )
    }
}

@Composable
private fun OrderRow(order: OrderWithItems, symbol: String, onClick: () -> Unit) {
    val o = order.order
    val colors = LocalStatusColors.current
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                o.queueNumber,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    order.items.joinToString { "${it.quantity}× ${it.productName}" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${Dates.formatDateTime(o.createdAt)} · ${o.paymentMethod.label}" +
                        if (o.orderType == OrderType.DELIVERY) " · Delivery" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(o.totalCents, symbol), style = MaterialTheme.typography.titleMedium)
                if (o.status == OrderStatus.VOIDED) StatusPill("Voided", colors.danger, colors.dangerContainer)
            }
        }
    }
}

@Composable
private fun OrderDetailDialog(
    order: OrderWithItems,
    receiptPreview: @Composable () -> Unit,
    canPrint: Boolean,
    printing: Boolean,
    canVoid: Boolean,
    onPrint: () -> Unit,
    onVoid: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmVoid by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val withPermission = rememberBluetoothPermission()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Order ${order.order.queueNumber}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Cashier", order.order.cashierName)
                InfoRow("Status", if (order.order.status == OrderStatus.VOIDED) "Voided" else "Completed")
                HorizontalDivider()
                receiptPreview()
                if (confirmVoid) {
                    Text("Void this order? Its items go back into stock and it no longer counts as revenue.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (canVoid) {
                    TextButton(onClick = {
                        if (confirmVoid) {
                            onVoid(reason)
                            onDismiss()
                        } else {
                            confirmVoid = true
                        }
                    }) { Text(if (confirmVoid) "Confirm void" else "Void", color = MaterialTheme.colorScheme.error) }
                }
                if (canPrint) {
                    TextButton(onClick = { withPermission(onPrint) }, enabled = !printing) {
                        if (printing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Reprint")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
