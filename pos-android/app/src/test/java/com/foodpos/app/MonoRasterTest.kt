package com.foodpos.app

import com.foodpos.app.printer.MonoRaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonoRasterTest {
    @Test
    fun `threshold maps dark to black and light to white, msb first`() {
        val gray = intArrayOf(0, 255, 0, 255, 255, 255, 255, 0, 10)
        val raster = MonoRaster.fromGray(gray, width = 9, height = 1, dither = false)
        assertEquals(2, raster.bytesPerRow)
        assertEquals(0b1010_0001.toByte(), raster.bits[0])
        assertEquals(0b1000_0000.toByte(), raster.bits[1])
    }

    @Test
    fun `dithering a mid grey gives roughly half black dots`() {
        val size = 32
        val raster = MonoRaster.fromGray(IntArray(size * size) { 128 }, size, size, dither = true)
        var black = 0
        for (y in 0 until size) for (x in 0 until size) if (raster.isBlack(x, y)) black++
        assertTrue(black in (size * size * 0.4).toInt()..(size * size * 0.6).toInt())
    }

    @Test
    fun `centering pads both sides with white`() {
        val raster = MonoRaster.fromGray(IntArray(4) { 0 }, 4, 1, dither = false).centeredOn(12)
        assertEquals(12, raster.width)
        assertFalse(raster.isBlack(3, 0))
        assertTrue((4..7).all { raster.isBlack(it, 0) })
        assertFalse(raster.isBlack(8, 0))
    }
}
