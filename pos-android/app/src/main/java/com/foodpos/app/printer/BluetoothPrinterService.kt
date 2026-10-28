package com.foodpos.app.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class PairedPrinter(val name: String, val address: String)

class PrinterException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Talks to a paired Bluetooth thermal printer over a classic RFCOMM socket using the Serial Port
 * Profile, the way virtually all ESC/POS receipt printers work. One print job at a time.
 */
class BluetoothPrinterService(private val context: Context) {
    private val mutex = Mutex()

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isSupported: Boolean get() = adapter != null

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Devices already paired in Android's Bluetooth settings. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedPrinter> {
        val adapter = adapter ?: return emptyList()
        if (!hasConnectPermission() || !adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.orEmpty()
            .map { PairedPrinter(it.name ?: it.address, it.address) }
            .sortedBy { it.name.lowercase() }
    }

    @SuppressLint("MissingPermission")
    suspend fun send(address: String, data: ByteArray) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val adapter = adapter ?: throw PrinterException("This device has no Bluetooth")
            if (!hasConnectPermission()) throw PrinterException("Allow the Nearby devices permission to print")
            if (!adapter.isEnabled) throw PrinterException("Turn on Bluetooth to print")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) adapter.cancelDiscovery()

            val device = try {
                adapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                throw PrinterException("Choose the printer again in Settings", e)
            }
            openSocket(device).use { socket ->
                val output = socket.outputStream
                var offset = 0
                while (offset < data.size) {
                    val length = minOf(CHUNK_SIZE, data.size - offset)
                    output.write(data, offset, length)
                    output.flush()
                    offset += length
                    // Cheap printers have small buffers; pacing the stream stops garbled logos.
                    delay(CHUNK_PAUSE_MS)
                }
                // Give the printer time to drain its buffer before the link closes.
                delay(CLOSE_DELAY_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        val secure = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            secure.connect()
            return secure
        } catch (first: IOException) {
            runCatching { secure.close() }
            // Some printers only accept an insecure RFCOMM connection.
            val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            try {
                insecure.connect()
                return insecure
            } catch (second: IOException) {
                runCatching { insecure.close() }
                throw PrinterException("Can't reach ${device.name ?: "the printer"}. Is it on and nearby?", second)
            }
        }
    }

    private companion object {
        /** Standard Serial Port Profile UUID. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CHUNK_SIZE = 512
        const val CHUNK_PAUSE_MS = 15L
        const val CLOSE_DELAY_MS = 400L
    }
}
