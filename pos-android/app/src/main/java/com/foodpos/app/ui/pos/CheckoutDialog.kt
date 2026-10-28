@file:OptIn(ExperimentalMaterial3Api::class)

package com.foodpos.app.ui.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foodpos.app.core.Money
import com.foodpos.app.data.local.OrderType
import com.foodpos.app.data.local.OrderWithItems
import com.foodpos.app.data.local.PaymentMethod
import com.foodpos.app.data.repository.AppSettings
import com.foodpos.app.printer.Receipt
import com.foodpos.app.ui.components.InfoRow
import com.foodpos.app.ui.components.MoneyField
import com.foodpos.app.ui.components.ReceiptPreview
import com.foodpos.app.ui.components.rememberBluetoothPermission
import com.foodpos.app.ui.theme.LocalStatusColors

/**
 * Checkout: order type with a dynamic delivery fee, customer details, payment method (Cash, E-Wallet,
 * Bank Transfer) and cash change. The order is only placed when everything required is valid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutDialog(
    subtotalCents: Long,
    itemCount: Int,
    form: CheckoutForm,
    settings: AppSettings,
    saving: Boolean,
    onEdit: ((CheckoutForm) -> CheckoutForm) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (deliveryFeeCents: Long, cashReceivedCents: Long?) -> Unit,
) {
    val symbol = settings.currencySymbol
    val delivery = form.orderType == OrderType.DELIVERY
    val feeCents = if (delivery) Money.parse(form.deliveryFee) else 0L
    val feeInvalid = delivery && form.deliveryFee.isNotBlank() && feeCents == null
    val totalCents = subtotalCents + (feeCents ?: 0L)
    val isCash = form.paymentMethod == PaymentMethod.CASH
    // A blank cash field means the customer paid the exact amount.
    val cashCents = if (isCash) (if (form.cashReceived.isBlank()) totalCents else Money.parse(form.cashReceived)) else null
    val cashShort = isCash && (cashCents == null || cashCents < totalCents)
    val addressMissing = delivery && form.customerAddress.isBlank()
    val canConfirm = !saving && !feeInvalid && !cashShort && !addressMissing

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp).imePadding(),
        ) {
            Column {
                Row(Modifier.padding(start = 24.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Checkout", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !saving) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionLabel("Order type")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        OrderType.entries.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = form.orderType == type,
                                onClick = { onEdit { it.copy(orderType = type) } },
                                shape = SegmentedButtonDefaults.itemShape(index, OrderType.entries.size),
                            ) { Text(type.label, maxLines = 1) }
                        }
                    }
                    if (delivery) {
                        MoneyField(
                            value = form.deliveryFee,
                            onValueChange = { v -> onEdit { it.copy(deliveryFee = v) } },
                            label = "Delivery fee",
                            symbol = symbol,
                            isError = feeInvalid,
                            supportingText = if (feeInvalid) "Enter a valid amount" else null,
                        )
                        if (settings.deliveryFeePresets.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                settings.deliveryFeePresets.forEach { preset ->
                                    AssistChip(
                                        onClick = { onEdit { it.copy(deliveryFee = Money.plain(preset)) } },
                                        label = { Text(Money.format(preset, symbol)) },
                                    )
                                }
                            }
                        }
                    }

                    SectionLabel(if (delivery) "Customer (address required)" else "Customer (optional)")
                    OutlinedTextField(
                        value = form.customerName,
                        onValueChange = { v -> onEdit { it.copy(customerName = v) } },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.customerPhone,
                        onValueChange = { v -> onEdit { it.copy(customerPhone = v) } },
                        label = { Text("Phone") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (delivery) {
                        OutlinedTextField(
                            value = form.customerAddress,
                            onValueChange = { v -> onEdit { it.copy(customerAddress = v) } },
                            label = { Text("Delivery address") },
                            isError = addressMissing,
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = form.notes,
                        onValueChange = { v -> onEdit { it.copy(notes = v) } },
                        label = { Text("Order notes") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    SectionLabel("Payment")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        PaymentMethod.entries.forEachIndexed { index, method ->
                            SegmentedButton(
                                selected = form.paymentMethod == method,
                                onClick = { onEdit { it.copy(paymentMethod = method) } },
                                shape = SegmentedButtonDefaults.itemShape(index, PaymentMethod.entries.size),
                            ) { Text(method.label, maxLines = 1) }
                        }
                    }
                    if (isCash) {
                        MoneyField(
                            value = form.cashReceived,
                            onValueChange = { v -> onEdit { it.copy(cashReceived = v) } },
                            label = "Cash received (blank = exact)",
                            symbol = symbol,
                            isError = cashShort,
                            supportingText = if (cashShort) "Less than the total" else null,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { onEdit { it.copy(cashReceived = "") } }, label = { Text("Exact") })
                            quickCashAmounts(totalCents).forEach { amount ->
                                AssistChip(
                                    onClick = { onEdit { it.copy(cashReceived = Money.plain(amount)) } },
                                    label = { Text(Money.format(amount, symbol)) },
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = form.reference,
                            onValueChange = { v -> onEdit { it.copy(reference = v) } },
                            label = { Text("Reference no. (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(top = 12.dp))
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Subtotal ($itemCount item${if (itemCount == 1) "" else "s"})", Money.format(subtotalCents, symbol))
                    if (delivery) InfoRow("Delivery fee", Money.format(feeCents ?: 0, symbol))
                    InfoRow("Total", Money.format(totalCents, symbol), bold = true)
                    if (isCash && cashCents != null && !cashShort) {
                        InfoRow("Change", Money.format(cashCents - totalCents, symbol), valueColor = LocalStatusColors.current.good)
                    }
                    Button(
                        onClick = { onConfirm(feeCents ?: 0L, cashCents) },
                        enabled = canConfirm,
                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Place order · ${Money.format(totalCents, symbol)}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/** Likely notes a customer hands over: the next 100, 500 and 1000 above the total. */
internal fun quickCashAmounts(totalCents: Long): List<Long> =
    listOf(10_000L, 50_000L, 100_000L)
        .map { step -> ((totalCents + step - 1) / step) * step }
        .filter { it > totalCents }
        .distinct()

/** Shown after checkout: the queue number to call out, change due, and the receipt. */
@Composable
fun OrderCompleteDialog(
    order: OrderWithItems,
    receipt: Receipt?,
    settings: AppSettings,
    printing: Boolean,
    onPrint: () -> Unit,
    onDone: () -> Unit,
) {
    val o = order.order
    val symbol = settings.currencySymbol
    val withPermission = rememberBluetoothPermission()
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(16.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = LocalStatusColors.current.good, modifier = Modifier.size(48.dp))
                Text("Order placed", style = MaterialTheme.typography.titleLarge)
                Text("Queue number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(o.queueNumber, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                InfoRow("Total", Money.format(o.totalCents, symbol), bold = true)
                if (o.paymentMethod == PaymentMethod.CASH) {
                    InfoRow("Change", Money.format(o.changeCents, symbol), bold = true, valueColor = LocalStatusColors.current.good)
                }
                if (receipt != null) {
                    ReceiptPreview(receipt, settings.charsPerLine, Modifier.padding(vertical = 8.dp))
                }
                if (settings.printerAddress == null) {
                    Text(
                        "No receipt printer set up. Owners can choose one in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { withPermission(onPrint) },
                        enabled = !printing && settings.printerAddress != null,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        if (printing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Print, contentDescription = null)
                            Text("  Print")
                        }
                    }
                    Button(onClick = onDone, modifier = Modifier.weight(1f).height(48.dp)) { Text("New order") }
                }
            }
        }
    }
}
