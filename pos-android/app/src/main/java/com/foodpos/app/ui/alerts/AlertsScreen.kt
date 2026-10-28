package com.foodpos.app.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.alerts.AlertType
import com.foodpos.app.alerts.InventoryAlert
import com.foodpos.app.ui.BaseViewModel
import com.foodpos.app.ui.ShowMessages
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.components.ConfirmDialog
import com.foodpos.app.ui.components.EmptyState
import com.foodpos.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(private val container: AppContainer) : BaseViewModel() {
    val alerts = container.inventory.observeAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun writeOff(alert: InventoryAlert) {
        val batchId = alert.batchId ?: return
        val user = container.session.currentUser ?: return
        viewModelScope.launch {
            container.inventory.writeOffBatch(batchId, user).report("${alert.productName} batch written off")
        }
    }
}

@Composable
fun AlertsScreen(isOwner: Boolean, onOpenInventory: () -> Unit) {
    val vm = appViewModel { AlertsViewModel(it) }
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<InventoryAlert?>(null) }
    ShowMessages(vm)

    val list = alerts ?: return
    if (list.isEmpty()) {
        EmptyState(Icons.Filled.CheckCircle, "All good", "Nothing is out of stock, running low, spoiled or spoiling soon.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AlertType.entries.forEach { type ->
            val group = list.filter { it.type == type }
            if (group.isNotEmpty()) {
                item(key = type.name) {
                    Text(
                        "${type.label} (${group.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(group, key = { it.key }) { alert ->
                    AlertCard(
                        alert = alert,
                        canWriteOff = isOwner && alert.type == AlertType.SPOILED,
                        onWriteOff = { confirm = alert },
                        onRestock = onOpenInventory,
                    )
                }
            }
        }
    }

    confirm?.let { alert ->
        ConfirmDialog(
            title = "Write off spoiled stock?",
            message = "Removes what is left of ${alert.productName} batch ${alert.batchNumber} from stock.",
            confirmLabel = "Write off",
            destructive = true,
            onConfirm = { vm.writeOff(alert) },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun AlertCard(alert: InventoryAlert, canWriteOff: Boolean, onWriteOff: () -> Unit, onRestock: () -> Unit) {
    val colors = LocalStatusColors.current
    val (icon, tint, container) = when (alert.type) {
        AlertType.OUT_OF_STOCK -> Triple(Icons.Filled.RemoveShoppingCart, colors.danger, colors.dangerContainer)
        AlertType.SPOILED -> Triple(Icons.Filled.Error, colors.danger, colors.dangerContainer)
        AlertType.SPOILING_SOON -> Triple(Icons.Filled.HourglassBottom, colors.warning, colors.warningContainer)
        AlertType.RUNNING_LOW -> Triple(Icons.AutoMirrored.Filled.TrendingDown, colors.warning, colors.warningContainer)
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = alert.type.label, tint = tint, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(alert.productName, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(alert.batchNumber?.let { "Batch $it" }, alert.detail).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                canWriteOff -> TextButton(onClick = onWriteOff) { Text("Write off") }
                alert.type == AlertType.OUT_OF_STOCK || alert.type == AlertType.RUNNING_LOW ->
                    TextButton(onClick = onRestock) { Text("Restock") }
            }
        }
    }
}
