package com.foodpos.app.printer

import java.io.ByteArrayOutputStream
import java.text.Normalizer

/** Raw ESC/POS command bytes understood by common 58 mm and 80 mm Bluetooth thermal printers. */
object EscPos {
    private const val ESC = 0x1B
    private const val GS = 0x1D
    const val LF = 0x0A

    /** Rows sent per raster command; small bands keep cheap printers from overflowing their buffer. */
    private const val RASTER_BAND = 128

    /** ESC @: reset the printer to its defaults. */
    fun initialize() = bytes(ESC, 0x40)

    /** ESC a n: 0 left, 1 centre, 2 right. */
    fun align(align: ReceiptAlign) = bytes(ESC, 0x61, align.code)

    /** ESC E n: emphasised (bold) on or off. */
    fun bold(on: Boolean) = bytes(ESC, 0x45, if (on) 1 else 0)

    /** GS ! n: character size. Bits 4-6 scale width, bits 0-2 scale height. */
    fun size(doubleWidth: Boolean, doubleHeight: Boolean) =
        bytes(GS, 0x21, (if (doubleWidth) 0x10 else 0) or (if (doubleHeight) 0x01 else 0))

    /** ESC d n: print and feed n lines. */
    fun feed(lines: Int) = bytes(ESC, 0x64, lines.coerceIn(0, 255))

    /** GS V B 0: feed to the cutter and partial cut (ignored by printers without a cutter). */
    fun cut() = bytes(GS, 0x56, 0x42, 0x00)

    /**
     * GS v 0 m xL xH yL yH d1...dk: print a raster bit image, sent in bands of [RASTER_BAND] rows.
     * xL/xH is the width in bytes and yL/yH the height in dots, both little-endian.
     */
    fun raster(image: MonoRaster): ByteArray {
        val out = ByteArrayOutputStream()
        val widthBytes = image.bytesPerRow
        var y = 0
        while (y < image.height) {
            val band = minOf(RASTER_BAND, image.height - y)
            out.write(bytes(GS, 0x76, 0x30, 0x00, widthBytes and 0xFF, (widthBytes shr 8) and 0xFF, band and 0xFF, (band shr 8) and 0xFF))
            out.write(image.bits, y * widthBytes, band * widthBytes)
            y += band
        }
        return out.toByteArray()
    }

    /**
     * Thermal printers use single-byte code pages, so text is reduced to printable ASCII: the peso
     * sign becomes "P", accents are dropped (Piña -> Pina) and anything else becomes "?".
     */
    fun encode(text: String): ByteArray {
        val replaced = text
            .replace("₱", "P").replace("€", "EUR").replace("£", "GBP")
            .replace('–', '-').replace('—', '-').replace('‘', '\'').replace('’', '\'')
            .replace('“', '"').replace('”', '"').replace('•', '*').replace('×', 'x')
        val plain = Normalizer.normalize(replaced, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return ByteArray(plain.length) { i ->
            val c = plain[i]
            if (c.code in 0x20..0x7E || c == '\n') c.code.toByte() else '?'.code.toByte()
        }
    }

    internal fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
