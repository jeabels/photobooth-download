package com.foodpos.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bar chart drawn on a Compose [Canvas]. Tap a bar to see its exact value; the latest bar is
 * highlighted by default.
 */
@Composable
fun BarChart(
    entries: List<ChartEntry>,
    valueLabel: (Long) -> String,
    axisLabel: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (entries.isEmpty()) return
    val maxValue = niceMax(entries.maxOf { it.value })
    var selected by remember(entries) { mutableStateOf(entries.lastIndex) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    ScrollableChart(entries.size, maxValue, axisLabel, modifier, height) { width ->
        Canvas(
            Modifier
                .width(width)
                .height(height)
                .pointerInput(entries) {
                    detectTapGestures { offset ->
                        val slot = size.width / entries.size.toFloat()
                        selected = (offset.x / slot).toInt().coerceIn(0, entries.lastIndex)
                    }
                },
        ) {
            val top = ChartLayout.top.toPx()
            val plotHeight = size.height - top - ChartLayout.bottom.toPx()
            val baseline = top + plotHeight
            for (i in 0..ChartLayout.GRID_LINES) {
                val y = top + plotHeight * i / ChartLayout.GRID_LINES
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }

            val slot = size.width / entries.size
            val barWidth = (slot * 0.6f).coerceAtMost(40.dp.toPx())
            entries.forEachIndexed { index, entry ->
                val barHeight = plotHeight * entry.value / maxValue.toFloat()
                val x = slot * index + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (index == selected) barColor else barColor.copy(alpha = 0.45f),
                    topLeft = Offset(x, baseline - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
                val label = measurer.measure(entry.label, labelStyle)
                drawText(label, topLeft = Offset(slot * index + (slot - label.size.width) / 2, baseline + 6.dp.toPx()))
                if (index == selected) {
                    val value = measurer.measure(valueLabel(entry.value), valueStyle)
                    val vx = (slot * index + (slot - value.size.width) / 2).coerceIn(0f, size.width - value.size.width)
                    drawText(value, topLeft = Offset(vx, (baseline - barHeight - value.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
                }
            }
        }
    }
}
