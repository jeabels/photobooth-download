package com.foodpos.app.printer

import com.foodpos.app.data.local.OrderWithItems
import com.foodpos.app.data.repository.AppSettings
import com.foodpos.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds receipts with the current settings and logo, and sends them to the chosen printer. */
class ReceiptPrinter(
    private val settings: SettingsRepository,
    private val printer: BluetoothPrinterService,
) {
    @Volatile private var cachedLogo: Triple<Int, Int, MonoRaster?>? = null

    val hasPrinter: Boolean get() = settings.current.printerAddress != null

    suspend fun receiptFor(order: OrderWithItems): Receipt = withContext(Dispatchers.Default) {
        val s = settings.current
        ReceiptFormatter.build(order, s, logo(s))
    }

    suspend fun print(order: OrderWithItems) = send(receiptFor(order))

    suspend fun printTestPage() = send(withContext(Dispatchers.Default) {
        val s = settings.current
        ReceiptFormatter.testPage(s, logo(s))
    })

    private suspend fun send(receipt: Receipt) {
        val s = settings.current
        val address = s.printerAddress ?: throw PrinterException("Choose a printer in Settings first")
        val bytes = withContext(Dispatchers.Default) { EscPosRenderer.render(receipt, s.charsPerLine) }
        printer.send(address, bytes)
    }

    /** The logo is converted once per logo/paper-size change, not for every receipt. */
    private fun logo(s: AppSettings): MonoRaster? {
        val version = settings.logoVersion.value
        cachedLogo?.let { (v, dots, raster) -> if (v == version && dots == s.paperDots) return raster }
        val raster = settings.loadLogo()?.let { BitmapRaster.fromBitmap(it, s.paperDots) }
        cachedLogo = Triple(version, s.paperDots, raster)
        return raster
    }
}
