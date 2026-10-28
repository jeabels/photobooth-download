package com.foodpos.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodpos.app.printer.BitmapRaster
import com.foodpos.app.printer.Receipt
import com.foodpos.app.printer.ReceiptLayout
import com.foodpos.app.printer.ReceiptLine

/** Shows a receipt the way the thermal printer will print it: same wrapping, same columns. */
@Composable
fun ReceiptPreview(receipt: Receipt, charsPerLine: Int, modifier: Modifier = Modifier) {
    val base = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = Color.Black)
    Column(
        modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(4.dp))
            .padding(12.dp),
    ) {
        for (line in receipt.lines) {
            when (line) {
                is ReceiptLine.Text -> {
                    val width = if (line.large) charsPerLine / 2 else charsPerLine
                    val style = base.copy(
                        fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (line.large) 18.sp else base.fontSize,
                        lineHeight = if (line.large) 22.sp else base.lineHeight,
                    )
                    ReceiptLayout.wrap(line.text, width).forEach { row ->
                        Text(ReceiptLayout.align(row, width, line.align), style = style, softWrap = false)
                    }
                }
                is ReceiptLine.Columns -> {
                    val style = base.copy(
                        fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (line.tall) 13.sp else base.fontSize,
                        lineHeight = if (line.tall) 18.sp else base.lineHeight,
                    )
                    ReceiptLayout.columns(line.left, line.right, charsPerLine).forEach { row ->
                        Text(row, style = style, softWrap = false)
                    }
                }
                ReceiptLine.Divider -> Text("-".repeat(charsPerLine), style = base, softWrap = false)
                is ReceiptLine.Logo -> {
                    val bitmap = remember(line.image) { BitmapRaster.toBitmap(line.image).asImageBitmap() }
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Receipt logo",
                        contentScale = ContentScale.FillWidth,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }
                is ReceiptLine.Blank -> repeat(line.lines) { Text("", style = base) }
            }
        }
    }
}
