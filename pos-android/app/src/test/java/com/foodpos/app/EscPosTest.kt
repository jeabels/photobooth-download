package com.foodpos.app

import com.foodpos.app.printer.EscPos
import com.foodpos.app.printer.EscPosRenderer
import com.foodpos.app.printer.MonoRaster
import com.foodpos.app.printer.Receipt
import com.foodpos.app.printer.ReceiptAlign
import com.foodpos.app.printer.ReceiptLayout
import com.foodpos.app.printer.ReceiptLine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosTest {
    @Test
    fun `commands have the documented bytes`() {
        assertArrayEquals(bytes(0x1B, 0x40), EscPos.initialize())
        assertArrayEquals(bytes(0x1B, 0x61, 1), EscPos.align(ReceiptAlign.CENTER))
        assertArrayEquals(bytes(0x1B, 0x45, 1), EscPos.bold(true))
        assertArrayEquals(bytes(0x1D, 0x21, 0x11), EscPos.size(doubleWidth = true, doubleHeight = true))
        assertArrayEquals(bytes(0x1D, 0x21, 0x01), EscPos.size(doubleWidth = false, doubleHeight = true))
        assertArrayEquals(bytes(0x1D, 0x56, 0x42, 0x00), EscPos.cut())
    }

    @Test
    fun `text is reduced to printable ascii`() {
        assertEquals("P120.00 Pina Cafe", String(EscPos.encode("₱120.00 Piña Café")))
        assertEquals("2 x Halo-halo", String(EscPos.encode("2 × Halo–halo")))
        assertEquals("?", String(EscPos.encode("中")))
    }

    @Test
    fun `raster command header has width in bytes and height in dots`() {
        val image = MonoRaster(16, 3, ByteArray(6) { 0xFF.toByte() })
        val data = EscPos.raster(image)
        assertArrayEquals(bytes(0x1D, 0x76, 0x30, 0x00, 2, 0, 3, 0), data.copyOfRange(0, 8))
        assertEquals(8 + 6, data.size)
    }

    @Test
    fun `tall rasters are split into bands`() {
        val image = MonoRaster(8, 300, ByteArray(300))
        val data = EscPos.raster(image)
        // 128 + 128 + 44 rows, each band with an 8-byte header.
        assertEquals(3 * 8 + 300, data.size)
    }

    @Test
    fun `wrap keeps words together and splits long words`() {
        assertEquals(listOf("Chicken", "Adobo with", "rice"), ReceiptLayout.wrap("Chicken Adobo with rice", 10))
        assertEquals(listOf("ABCDEFGHIJ", "KL"), ReceiptLayout.wrap("ABCDEFGHIJKL", 10))
    }

    @Test
    fun `columns right-align the amount to the full width`() {
        val rows = ReceiptLayout.columns("Subtotal", "90.00", 32)
        assertEquals(1, rows.size)
        assertEquals(32, rows[0].length)
        assertTrue(rows[0].startsWith("Subtotal"))
        assertTrue(rows[0].endsWith("90.00"))
    }

    @Test
    fun `renderer bolds totals, resets styles and cuts`() {
        val receipt = Receipt(listOf(ReceiptLine.Columns("TOTAL", "P100.00", bold = true, tall = true)))
        val data = EscPosRenderer.render(receipt, 32).toList()
        val boldOn = bytes(0x1B, 0x45, 1).toList()
        assertTrue(data.windowed(3).contains(boldOn))
        assertEquals(bytes(0x1D, 0x56, 0x42, 0x00).toList(), data.takeLast(4))
        assertFalse(data.isEmpty())
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
