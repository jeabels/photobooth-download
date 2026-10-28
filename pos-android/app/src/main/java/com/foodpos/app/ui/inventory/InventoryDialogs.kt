@file:OptIn(ExperimentalMaterial3Api::class)

package com.foodpos.app.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foodpos.app.alerts.BatchStatus
import com.foodpos.app.alerts.ProductStock
import com.foodpos.app.core.Dates
import com.foodpos.app.core.Money
import com.foodpos.app.data.local.ProductEntity
import com.foodpos.app.data.repository.AppSettings
import com.foodpos.app.data.repository.DeliveryDraft
import com.foodpos.app.data.repository.ProductDraft
import com.foodpos.app.ui.components.DateField
import com.foodpos.app.ui.components.MoneyField
import com.foodpos.app.ui.components.NumberField
import com.foodpos.app.ui.components.SuggestField
import java.time.LocalDate

@Composable
fun ProductEditorDialog(
    product: ProductEntity?,
    categories: List<String>,
    settings: AppSettings,
    onSave: (ProductDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var category by remember { mutableStateOf(product?.category ?: "") }
    var sku by remember { mutableStateOf(product?.sku ?: "") }
    var unit by remember { mutableStateOf(product?.unit ?: "pcs") }
    var price by remember { mutableStateOf(product?.let { Money.plain(it.priceCents) } ?: "") }
    var lowStock by remember { mutableStateOf((product?.lowStockThreshold ?: settings.defaultLowStock).toString()) }
    var spoilDays by remember { mutableStateOf((product?.spoilSoonDays ?: settings.defaultSpoilSoonDays).toString()) }
    val priceCents = Money.parse(price)
    val valid = name.isNotBlank() && priceCents != null && lowStock.toIntOrNull() != null && spoilDays.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "New product" else "Edit product") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SuggestField(category, { category = it }, "Category", (categories + listOf("Meals", "Drinks", "Snacks", "Desserts")).distinct())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyField(price, { price = it }, "Price", Modifier.weight(1f), symbol = settings.currencySymbol, isError = price.isNotBlank() && priceCents == null)
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(0.6f))
                }
                OutlinedTextField(sku, { sku = it }, label = { Text("SKU / barcode (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Alert thresholds", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(lowStock, { lowStock = it }, "Running low at", Modifier.weight(1f))
                    NumberField(spoilDays, { spoilDays = it }, "Spoiling soon (days)", Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    ProductDraft(
                        id = product?.id,
                        name = name,
                        category = category,
                        sku = sku,
                        unit = unit,
                        priceCents = priceCents ?: 0,
                        lowStockThreshold = lowStock.toIntOrNull() ?: 0,
                        spoilSoonDays = spoilDays.toIntOrNull() ?: 0,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ReceiveDeliveryDialog(
    products: List<ProductEntity>,
    initialProductId: String?,
    suggestedBatch: String,
    symbol: String,
    onSave: (DeliveryDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var productId by remember { mutableStateOf(initialProductId ?: products.firstOrNull()?.id) }
    var batchNumber by remember { mutableStateOf(suggestedBatch) }
    var quantity by remember { mutableStateOf("") }
    var manufactured by remember { mutableStateOf<LocalDate?>(Dates.today()) }
    var expires by remember { mutableStateOf<LocalDate?>(null) }
    var cost by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val qty = quantity.toIntOrNull()
    val madeOn = manufactured
    val expiresOn = expires
    val datesValid = madeOn == null || expiresOn == null || !expiresOn.isBefore(madeOn)
    val valid = productId != null && qty != null && qty > 0 && datesValid && (cost.isBlank() || Money.parse(cost) != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receive delivery") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProductPicker(products, productId) { productId = it }
                OutlinedTextField(batchNumber, { batchNumber = it }, label = { Text("Batch number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField(quantity, { quantity = it }, "Quantity received")
                DateField("Manufacturing date", manufactured, { manufactured = it }, clearable = true)
                DateField("Expiration date", expires, { expires = it }, clearable = true)
                if (!datesValid) Text("Expiry is before the manufacturing date", color = MaterialTheme.colorScheme.error)
                MoneyField(cost, { cost = it }, "Unit cost (optional)", symbol = symbol)
                OutlinedTextField(note, { note = it }, label = { Text("Supplier / note") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val id = productId
                if (id != null && qty != null) {
                    onSave(
                        DeliveryDraft(
                            productId = id,
                            batchNumber = batchNumber,
                            quantity = qty,
                            manufacturedOn = madeOn,
                            expiresOn = expiresOn,
                            unitCostCents = Money.parse(cost),
                            note = note,
                        ),
                    )
                }
            }) { Text("Add to stock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AdjustStockDialog(
    item: ProductStock,
    onSave: (batchId: String, delta: Int, reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val batches = item.batches
    var batchId by remember { mutableStateOf(batches.firstOrNull { it.remaining > 0 }?.batch?.id ?: batches.firstOrNull()?.batch?.id) }
    var delta by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val change = delta.toIntOrNull()
    val selected = batches.firstOrNull { it.batch.id == batchId }
    val valid = selected != null && change != null && change != 0 && selected.remaining + change >= 0 && reason.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust ${item.product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (batches.isEmpty()) {
                    Text("This product has no batches yet. Receive a delivery first.")
                } else {
                    var open by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                        OutlinedTextField(
                            value = selected?.let { "${it.batch.batchNumber} (${it.remaining} left)" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Batch") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                        )
                        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            batches.forEach { b ->
                                val expiry = b.batch.expiresOn?.let { " · exp ${Dates.formatEpochDay(it)}" }.orEmpty()
                                val spoiled = if (b.status == BatchStatus.SPOILED) " · spoiled" else ""
                                DropdownMenuItem(
                                    text = { Text("${b.batch.batchNumber} (${b.remaining} left$expiry$spoiled)") },
                                    onClick = { batchId = b.batch.id; open = false },
                                )
                            }
                        }
                    }
                    NumberField(delta, { delta = it }, "Change (e.g. -2 or +5)", allowNegative = true)
                    if (selected != null && change != null && selected.remaining + change < 0) {
                        Text("Only ${selected.remaining} left in this batch", color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason (count, damage, giveaway…)") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val id = batchId
                if (id != null && change != null) onSave(id, change, reason)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProductPicker(products: List<ProductEntity>, selectedId: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = products.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Product") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            products.forEach { p ->
                DropdownMenuItem(text = { Text(p.name) }, onClick = { onSelect(p.id); open = false })
            }
        }
    }
}
