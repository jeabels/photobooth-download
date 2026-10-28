package com.foodpos.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.foodpos.app.core.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.roundToInt

data class AppSettings(
    val businessName: String = "My Food Store",
    val businessAddress: String = "",
    val businessPhone: String = "",
    val receiptFooter: String = "Thank you! Please come again.",
    val currencySymbol: String = Money.DEFAULT_SYMBOL,
    /** Prefix for queue numbers on this device ("A" gives A-001, A-002...). */
    val deviceCode: String = "A",
    val deliveryFeePresets: List<Long> = listOf(5_000, 8_000, 10_000),
    val defaultLowStock: Int = 5,
    val defaultSpoilSoonDays: Int = 3,
    val printerAddress: String? = null,
    val printerName: String? = null,
    val paperWidthMm: Int = 58,
    val autoPrint: Boolean = false,
    /** Shared secret that names this store's data in Firestore. Same key on every device of a store. */
    val syncKey: String = "",
    val lastSyncAt: Long = 0,
    val lastSyncError: String? = null,
) {
    /** Font A characters per line: 32 on 58 mm paper, 48 on 80 mm. */
    val charsPerLine: Int get() = if (paperWidthMm >= 80) 48 else 32

    /** Printable width in dots at 203 dpi: 384 on 58 mm paper, 576 on 80 mm. */
    val paperDots: Int get() = if (paperWidthMm >= 80) 576 else 384
}

/** Small key-value settings in SharedPreferences, exposed as a [StateFlow]. */
class SettingsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("pos_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    /** Bumped whenever the receipt logo changes, so screens and the printer reload it. */
    private val _logoVersion = MutableStateFlow(0)
    val logoVersion: StateFlow<Int> = _logoVersion.asStateFlow()
    val logoFile: File get() = File(context.filesDir, "receipt_logo.png")

    init {
        if (current.syncKey.isBlank()) update { it.copy(syncKey = generateSyncKey()) }
    }

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val before = _settings.value
        val next = transform(before)
        if (next.syncKey != before.syncKey) clearPullCursors()
        write(next)
        _settings.value = next
    }

    fun pullCursor(collection: String): Long = prefs.getLong("cursor_$collection", 0L)

    fun setPullCursor(collection: String, millis: Long) {
        prefs.edit().putLong("cursor_$collection", millis).apply()
    }

    private fun clearPullCursors() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("cursor_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    /** Copies a picked image into app storage, scaled down to the widest paper (576 dots). */
    suspend fun saveLogo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return@withContext false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_LOGO_WIDTH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@withContext false
        val scaled = if (decoded.width > MAX_LOGO_WIDTH) {
            val height = max(1, (decoded.height * MAX_LOGO_WIDTH / decoded.width.toFloat()).roundToInt())
            Bitmap.createScaledBitmap(decoded, MAX_LOGO_WIDTH, height, true)
        } else {
            decoded
        }
        logoFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _logoVersion.value++
        true
    }

    fun removeLogo() {
        logoFile.delete()
        _logoVersion.value++
    }

    fun loadLogo(): Bitmap? = logoFile.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            businessName = prefs.getString("businessName", null) ?: defaults.businessName,
            businessAddress = prefs.getString("businessAddress", null) ?: defaults.businessAddress,
            businessPhone = prefs.getString("businessPhone", null) ?: defaults.businessPhone,
            receiptFooter = prefs.getString("receiptFooter", null) ?: defaults.receiptFooter,
            currencySymbol = prefs.getString("currencySymbol", null) ?: defaults.currencySymbol,
            deviceCode = prefs.getString("deviceCode", null) ?: defaults.deviceCode,
            deliveryFeePresets = prefs.getString("deliveryFeePresets", null)
                ?.split(',')?.mapNotNull { it.trim().toLongOrNull() }
                ?: defaults.deliveryFeePresets,
            defaultLowStock = prefs.getInt("defaultLowStock", defaults.defaultLowStock),
            defaultSpoilSoonDays = prefs.getInt("defaultSpoilSoonDays", defaults.defaultSpoilSoonDays),
            printerAddress = prefs.getString("printerAddress", null),
            printerName = prefs.getString("printerName", null),
            paperWidthMm = prefs.getInt("paperWidthMm", defaults.paperWidthMm),
            autoPrint = prefs.getBoolean("autoPrint", defaults.autoPrint),
            syncKey = prefs.getString("syncKey", null) ?: "",
            lastSyncAt = prefs.getLong("lastSyncAt", 0L),
            lastSyncError = prefs.getString("lastSyncError", null),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putString("businessName", s.businessName)
            .putString("businessAddress", s.businessAddress)
            .putString("businessPhone", s.businessPhone)
            .putString("receiptFooter", s.receiptFooter)
            .putString("currencySymbol", s.currencySymbol)
            .putString("deviceCode", s.deviceCode)
            .putString("deliveryFeePresets", s.deliveryFeePresets.joinToString(","))
            .putInt("defaultLowStock", s.defaultLowStock)
            .putInt("defaultSpoilSoonDays", s.defaultSpoilSoonDays)
            .putString("printerAddress", s.printerAddress)
            .putString("printerName", s.printerName)
            .putInt("paperWidthMm", s.paperWidthMm)
            .putBoolean("autoPrint", s.autoPrint)
            .putString("syncKey", s.syncKey)
            .putLong("lastSyncAt", s.lastSyncAt)
            .putString("lastSyncError", s.lastSyncError)
            .apply()
    }

    private companion object {
        const val MAX_LOGO_WIDTH = 576
        const val KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun generateSyncKey(): String {
            val random = SecureRandom()
            return (1..20).map { KEY_ALPHABET[random.nextInt(KEY_ALPHABET.length)] }
                .chunked(5).joinToString("-") { it.joinToString("") }
        }
    }
}
