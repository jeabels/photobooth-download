package com.foodpos.app.printer

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.roundToInt

/** Android side of logo printing: scales a [Bitmap] and turns it into a centred [MonoRaster]. */
object BitmapRaster {
    /**
     * @param paperDots printable width (384 dots for 58 mm, 576 for 80 mm)
     * @param widthFraction how much of the paper width the logo may use
     */
    fun fromBitmap(source: Bitmap, paperDots: Int, widthFraction: Float = 0.6f, dither: Boolean = true): MonoRaster {
        val maxWidth = (paperDots * widthFraction).roundToInt()
        val targetWidth = minOf(maxWidth, source.width * 2).coerceAtLeast(8)
        val targetHeight = max(1, (source.height * targetWidth / source.width.toFloat()).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)

        val pixels = IntArray(targetWidth * targetHeight)
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        val gray = IntArray(pixels.size) { i ->
            val c = pixels[i]
            val luminance = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
            // Transparent areas print as paper white.
            val alpha = Color.alpha(c) / 255f
            (255f - (255f - luminance) * alpha).roundToInt().coerceIn(0, 255)
        }
        return MonoRaster.fromGray(gray, targetWidth, targetHeight, dither).centeredOn(paperDots)
    }

    /** For the on-screen receipt preview. */
    fun toBitmap(raster: MonoRaster): Bitmap {
        val pixels = IntArray(raster.width * raster.height) { i ->
            if (raster.isBlack(i % raster.width, i / raster.width)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, raster.width, raster.height, Bitmap.Config.ARGB_8888)
    }
}
