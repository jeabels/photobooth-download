package com.foodpos.app.ui.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.core.Dates
import com.foodpos.app.core.Money
import com.foodpos.app.data.repository.AppSettings
import com.foodpos.app.printer.PairedPrinter
import com.foodpos.app.ui.BaseViewModel
import com.foodpos.app.ui.ShowMessages
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.components.NumberField
import com.foodpos.app.ui.components.SectionCard
import com.foodpos.app.ui.components.rememberBluetoothPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : BaseViewModel() {
    val settings = container.settings.settings
    val logoVersion = container.settings.logoVersion
    val syncRunning = container.sync.running
    val syncConfigured = container.sync.isConfigured

    private val _printers = MutableStateFlow<List<PairedPrinter>>(emptyList())
    val printers: StateFlow<List<PairedPrinter>> = _printers.asStateFlow()

    private val _printing = MutableStateFlow(false)
    val printing: StateFlow<Boolean> = _printing.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) = container.settings.update(transform)

    suspend fun loadLogo(): Bitmap? = withContext(Dispatchers.IO) { container.settings.loadLogo() }

    fun setLogo(uri: Uri) = viewModelScope.launch {
        if (!container.settings.saveLogo(uri)) say("Could not read that image")
    }

    fun removeLogo() = container.settings.removeLogo()

    fun refreshPrinters() {
        val bluetooth = container.bluetooth
        _printers.value = bluetooth.pairedDevices()
        when {
            !bluetooth.isSupported -> say("This device has no Bluetooth")
            _printers.value.isEmpty() -> say("No paired devices. Pair the printer in Android Bluetooth settings first.")
        }
    }

    fun choosePrinter(printer: PairedPrinter) = update { it.copy(printerAddress = printer.address, printerName = printer.name) }

    fun testPrint() {
        if (_printing.value) return
        _printing.value = true
        viewModelScope.launch {
            runCatching { container.receipts.printTestPage() }.report("Test page sent")
            _printing.value = false
        }
    }

    fun syncNow() = viewModelScope.launch {
        container.sync.syncNow().report("Synced")
    }
}

@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    val printing by vm.printing.collectAsStateWithLifecycle()
    val syncRunning by vm.syncRunning.collectAsStateWithLifecycle()
    val logoVersion by vm.logoVersion.collectAsStateWithLifecycle()
    val withBluetooth = rememberBluetoothPermission()
    ShowMessages(vm)

    var logo by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(logoVersion) { logo = vm.loadLogo() }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::setLogo) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BusinessSection(settings, vm::update)

        SectionCard(title = "Receipt logo") {
            Text(
                "Printed at the top of every receipt, converted to black and white for the thermal printer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            logo?.let {
                Image(it.asImageBitmap(), contentDescription = "Receipt logo", modifier = Modifier.widthIn(max = 200.dp).heightIn(max = 120.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickLogo.launch("image/*") }) { Text(if (logo == null) "Choose image" else "Change") }
                if (logo != null) TextButton(onClick = vm::removeLogo) { Text("Remove") }
            }
        }

        SectionCard(title = "Receipt printer") {
            Text(
                settings.printerName?.let { "Printer: $it" } ?: "No printer chosen",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Paper width", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(58, 80).forEach { mm ->
                    RadioButton(selected = settings.paperWidthMm == mm, onClick = { vm.update { it.copy(paperWidthMm = mm) } })
                    Text("$mm mm", modifier = Modifier.padding(end = 16.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Print receipt automatically after checkout", modifier = Modifier.weight(1f))
                Switch(checked = settings.autoPrint, onCheckedChange = { on -> vm.update { it.copy(autoPrint = on) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { withBluetooth(vm::refreshPrinters) }) {
                    Icon(Icons.Filled.Bluetooth, contentDescription = null)
                    Text(" Paired devices")
                }
                Button(onClick = { withBluetooth(vm::testPrint) }, enabled = settings.printerAddress != null && !printing) {
                    if (printing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Print, contentDescription = null)
                    Text(" Test print")
                }
            }
            printers.forEach { printer ->
                ListItem(
                    headlineContent = { Text(printer.name) },
                    supportingContent = { Text(printer.address, fontFamily = FontFamily.Monospace) },
                    leadingContent = {
                        RadioButton(selected = printer.address == settings.printerAddress, onClick = { vm.choosePrinter(printer) })
                    },
                )
            }
        }

        PosSection(settings, vm::update)

        SectionCard(title = "Cloud sync") {
            if (!vm.syncConfigured) {
                Text(
                    "This build has no Firebase configuration, so the app runs fully offline. Add google-services.json and rebuild to enable background sync (see README).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Every device of this store uses the same sync key. On a new device, choose \"Join an existing store\" on the sign-in screen and enter this key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(settings.syncKey, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                Text(
                    when {
                        settings.lastSyncError != null -> "Waiting to sync: ${settings.lastSyncError}"
                        settings.lastSyncAt > 0 -> "Last synced ${Dates.formatDateTime(settings.lastSyncAt)}"
                        else -> "Not synced yet"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { vm.syncNow() }, enabled = !syncRunning) {
                    if (syncRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sync now")
                }
            }
        }
    }
}

@Composable
private fun BusinessSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var name by remember(settings.businessName) { mutableStateOf(settings.businessName) }
    var address by remember(settings.businessAddress) { mutableStateOf(settings.businessAddress) }
    var phone by remember(settings.businessPhone) { mutableStateOf(settings.businessPhone) }
    var footer by remember(settings.receiptFooter) { mutableStateOf(settings.receiptFooter) }
    val changed = name != settings.businessName || address != settings.businessAddress ||
        phone != settings.businessPhone || footer != settings.receiptFooter
    SectionCard(title = "Business details") {
        OutlinedTextField(name, { name = it }, label = { Text("Business name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(footer, { footer = it }, label = { Text("Receipt footer") }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = changed && name.isNotBlank(),
            onClick = { update { it.copy(businessName = name.trim(), businessAddress = address.trim(), businessPhone = phone.trim(), receiptFooter = footer.trim()) } },
        ) { Text("Save") }
    }
}

@Composable
private fun PosSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    var deviceCode by remember(settings.deviceCode) { mutableStateOf(settings.deviceCode) }
    var currency by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    var fees by remember(settings.deliveryFeePresets) { mutableStateOf(settings.deliveryFeePresets.joinToString(", ") { Money.plain(it) }) }
    var lowStock by remember(settings.defaultLowStock) { mutableStateOf(settings.defaultLowStock.toString()) }
    var spoilDays by remember(settings.defaultSpoilSoonDays) { mutableStateOf(settings.defaultSpoilSoonDays.toString()) }
    val parsedFees = fees.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { Money.parse(it) }
    val valid = deviceCode.isNotBlank() && currency.isNotBlank() && parsedFees.none { it == null } &&
        lowStock.toIntOrNull() != null && spoilDays.toIntOrNull() != null

    SectionCard(title = "Point of sale & inventory") {
        OutlinedTextField(
            deviceCode,
            { deviceCode = it.uppercase().filter(Char::isLetterOrDigit).take(3) },
            label = { Text("Queue number prefix for this device") },
            supportingText = { Text("Orders on this device are numbered ${deviceCode.ifBlank { "A" }}-001, ${deviceCode.ifBlank { "A" }}-002… restarting daily") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(currency, { currency = it.take(4) }, label = { Text("Currency symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            fees,
            { fees = it },
            label = { Text("Delivery fee shortcuts") },
            supportingText = { Text("Comma separated, e.g. 50, 80, 100") },
            isError = parsedFees.any { it == null },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(lowStock, { lowStock = it }, "Default low stock", Modifier.weight(1f))
            NumberField(spoilDays, { spoilDays = it }, "Default spoil days", Modifier.weight(1f))
        }
        Button(
            enabled = valid,
            onClick = {
                update {
                    it.copy(
                        deviceCode = deviceCode,
                        currencySymbol = currency.trim(),
                        deliveryFeePresets = parsedFees.filterNotNull(),
                        defaultLowStock = lowStock.toInt(),
                        defaultSpoilSoonDays = spoilDays.toInt(),
                    )
                }
            },
        ) { Text("Save") }
    }
}
