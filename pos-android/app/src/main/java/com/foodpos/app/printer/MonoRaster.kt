package com.foodpos.app.printer

/**
 * A 1-bit image in the layout ESC/POS raster commands expect: rows of bytes, most significant bit is
 * the leftmost pixel, a set bit is a black dot.
 */
class MonoRaster(val width: Int, val height: Int, val bits: ByteArray) {
    val bytesPerRow: Int get() = (width + 7) / 8

    init {
        require(bits.size == bytesPerRow * height) { "Raster data size does not match ${width}x$height" }
    }

    fun isBlack(x: Int, y: Int): Boolean =
        bits[y * bytesPerRow + x / 8].toInt() and (0x80 ushr (x % 8)) != 0

    /** Pads the image with white on both sides so it sits centred on paper [canvasWidth] dots wide. */
    fun centeredOn(canvasWidth: Int): MonoRaster {
        if (width >= canvasWidth) return this
        val offset = (canvasWidth - width) / 2
        val out = MonoRaster(canvasWidth, height, ByteArray(((canvasWidth + 7) / 8) * height))
        for (y in 0 until height) for (x in 0 until width) {
            if (isBlack(x, y)) out.set(x + offset, y)
        }
        return out
    }

    private fun set(x: Int, y: Int) {
        val index = y * bytesPerRow + x / 8
        bits[index] = (bits[index].toInt() or (0x80 ushr (x % 8))).toByte()
    }

    companion object {
        /**
         * Converts greyscale pixels (0 = black, 255 = white, row-major) to black and white.
         * Floyd-Steinberg dithering keeps photos and gradients recognisable on a thermal printer;
         * without it a plain 50% threshold is used, which suits line-art logos.
         */
        fun fromGray(gray: IntArray, width: Int, height: Int, dither: Boolean = true): MonoRaster {
            require(gray.size == width * height) { "Expected ${width * height} pixels" }
            val raster = MonoRaster(width, height, ByteArray(((width + 7) / 8) * height))
            val error = if (dither) FloatArray(width * height) { gray[it].toFloat() } else null
            for (y in 0 until height) for (x in 0 until width) {
                val i = y * width + x
                val value = error?.get(i) ?: gray[i].toFloat()
                val black = value < 128f
                if (black) raster.set(x, y)
                if (error != null) {
                    val diff = value - if (black) 0f else 255f
                    if (x + 1 < width) error[i + 1] += diff * 7 / 16
                    if (y + 1 < height) {
                        if (x > 0) error[i + width - 1] += diff * 3 / 16
                        error[i + width] += diff * 5 / 16
                        if (x + 1 < width) error[i + width + 1] += diff * 1 / 16
                    }
                }
            }
            return raster
        }
    }
}
