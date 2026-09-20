package com.snapsstudio.booth

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Everything the booth page can ask the app to do, exposed as window.NativeBooth.
 *
 * Quick calls return a value directly. Slow calls take a callback id as their
 * last argument and answer later through MainActivity.reply(), which resolves
 * the matching promise in the page (see "NATIVE BRIDGE" in index.html).
 *
 * Methods annotated @JavascriptInterface run on a WebView background thread.
 */
class BoothBridge(private val activity: MainActivity) {

    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val printer = BluetoothPrinter(activity.applicationContext)

    /* ---------------- Bluetooth receipt printer ---------------- */

    @JavascriptInterface
    fun printers(): String = printer.pairedPrinters().toString()

    @JavascriptInterface
    fun printerStatus(): String = printer.status().toString()

    @JavascriptInterface
    fun printerConnect(address: String, callbackId: String) = background(callbackId) {
        printer.connect(address)
        "connected"
    }

    @JavascriptInterface
    fun printerWrite(address: String, base64: String, callbackId: String) = background(callbackId) {
        printer.print(address, Base64.decode(base64, Base64.DEFAULT))
        "printed"
    }

    @JavascriptInterface
    fun openBluetoothSettings() = openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    /* ---------------- Photo printer (any Android print service) ---------------- */

    @JavascriptInterface
    fun printPhoto(base64Jpeg: String, copies: Int, paper: String, callbackId: String) {
        worker.execute {
            try {
                val bytes = Base64.decode(base64Jpeg, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalArgumentException("the strip image could not be read")
                activity.runOnUiThread {
                    try {
                        PhotoPrinter.print(activity, bitmap, copies.coerceIn(1, 20), paper)
                        activity.reply(callbackId, true, "sent")
                    } catch (e: Exception) {
                        activity.reply(callbackId, false, e.message ?: "the print dialog could not open")
                    }
                }
            } catch (e: Exception) {
                activity.reply(callbackId, false, e.message ?: "print failed")
            }
        }
    }

    /* ---------------- Saving files (strips, CSV) ---------------- */

    @JavascriptInterface
    fun saveFile(base64: String, name: String, mime: String, callbackId: String) = background(callbackId) {
        FileSaver.save(activity, Base64.decode(base64, Base64.DEFAULT), name, mime)
    }

    /* ---------------- Kiosk ---------------- */

    @JavascriptInterface
    fun kiosk(lock: Boolean) = activity.setKiosk(lock)

    @JavascriptInterface
    fun exitApp() = activity.exit()

    /* ---------------- helpers ---------------- */

    private fun background(callbackId: String, work: () -> String) {
        worker.execute {
            try {
                activity.reply(callbackId, true, work())
            } catch (e: Exception) {
                activity.reply(callbackId, false, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun openSettings(intent: Intent) {
        activity.runOnUiThread {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            } catch (ignored: Exception) {
            }
        }
    }

    fun shutdown() {
        worker.shutdownNow()
        printer.close()
    }
}
