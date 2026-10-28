package com.foodpos.app.printer

import java.io.ByteArrayOutputStream

enum class ReceiptAlign(val code: Int) { LEFT(0), CENTER(1), RIGHT(2) }

/** A receipt as a list of styled lines. The same model is printed and shown as an on-screen preview. */
sealed interface ReceiptLine {
    /** [large] prints double width and height, so only half as many characters fit on a line. */
    data class Text(
        val text: String,
        val align: ReceiptAlign = ReceiptAlign.LEFT,
        val bold: Boolean = false,
        val large: Boolean = false,
    ) : ReceiptLine

    /** Left text and right-aligned amount on one line. [tall] doubles the height only (for totals). */
    data class Columns(val left: String, val right: String, val bold: Boolean = false, val tall: Boolean = false) : ReceiptLine

    data object Divider : ReceiptLine

    data class Logo(val image: MonoRaster) : ReceiptLine

    data class Blank(val lines: Int = 1) : ReceiptLine
}

data class Receipt(val lines: List<ReceiptLine>)

/** Plain-text layout helpers shared by the printer output and the preview. */
object ReceiptLayout {
    /** Word-wraps [text] to lines of at most [width] characters, breaking long words if needed. */
    fun wrap(text: String, width: Int): List<String> {
        if (width <= 0) return listOf(text)
        val result = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = StringBuilder()
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                var rest = word
                while (rest.length > width) {
                    if (line.isNotEmpty()) {
                        result += line.toString()
                        line = StringBuilder()
                    }
                    result += rest.take(width)
                    rest = rest.drop(width)
                }
                if (rest.isEmpty()) continue
                when {
                    line.isEmpty() -> line.append(rest)
                    line.length + 1 + rest.length <= width -> line.append(' ').append(rest)
                    else -> {
                        result += line.toString()
                        line = StringBuilder(rest)
                    }
                }
            }
            result += line.toString()
        }
        return result
    }

    /** "Subtotal" and "90.00" -> "Subtotal                   90.00"; a long left side wraps above. */
    fun columns(left: String, right: String, width: Int): List<String> {
        val room = width - right.length - 1
        if (room < 4) return wrap(left, width) + right.padStart(width)
        val leftLines = wrap(left, room)
        return leftLines.dropLast(1) + (leftLines.last().padEnd(room) + " " + right)
    }

    fun align(text: String, width: Int, align: ReceiptAlign): String = when (align) {
        ReceiptAlign.LEFT -> text
        ReceiptAlign.RIGHT -> text.padStart(width)
        ReceiptAlign.CENTER -> " ".repeat(((width - text.length) / 2).coerceAtLeast(0)) + text
    }
}

/** Renders a [Receipt] into ESC/POS bytes for a printer with [charsPerLine] Font A characters. */
object EscPosRenderer {
    fun render(receipt: Receipt, charsPerLine: Int, cut: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(EscPos.initialize())
        for (line in receipt.lines) {
            when (line) {
                is ReceiptLine.Text -> {
                    val width = if (line.large) charsPerLine / 2 else charsPerLine
                    out.write(EscPos.align(line.align))
                    out.write(EscPos.bold(line.bold))
                    out.write(EscPos.size(line.large, line.large))
                    for (row in ReceiptLayout.wrap(line.text, width)) {
                        out.write(EscPos.encode(row))
                        out.write(EscPos.LF)
                    }
                    out.write(EscPos.size(doubleWidth = false, doubleHeight = false))
                    out.write(EscPos.bold(false))
                }
                is ReceiptLine.Columns -> {
                    out.write(EscPos.align(ReceiptAlign.LEFT))
                    out.write(EscPos.bold(line.bold))
                    out.write(EscPos.size(doubleWidth = false, doubleHeight = line.tall))
                    for (row in ReceiptLayout.columns(line.left, line.right, charsPerLine)) {
                        out.write(EscPos.encode(row))
                        out.write(EscPos.LF)
                    }
                    out.write(EscPos.size(doubleWidth = false, doubleHeight = false))
                    out.write(EscPos.bold(false))
                }
                ReceiptLine.Divider -> {
                    out.write(EscPos.align(ReceiptAlign.LEFT))
                    out.write(EscPos.encode("-".repeat(charsPerLine)))
                    out.write(EscPos.LF)
                }
                is ReceiptLine.Logo -> {
                    // The raster is already padded to the paper width, so it prints centred.
                    out.write(EscPos.align(ReceiptAlign.LEFT))
                    out.write(EscPos.raster(line.image))
                }
                is ReceiptLine.Blank -> repeat(line.lines) { out.write(EscPos.LF) }
            }
        }
        out.write(EscPos.align(ReceiptAlign.LEFT))
        out.write(EscPos.feed(4))
        if (cut) out.write(EscPos.cut())
        return out.toByteArray()
    }
}
