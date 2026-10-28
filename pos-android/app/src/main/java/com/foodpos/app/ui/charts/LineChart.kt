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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Line graph with a soft area fill, drawn on a Compose [Canvas]. Tap near a point to read it. */
@Composable
fun LineChart(
    entries: List<ChartEntry>,
    valueLabel: (Long) -> String,
    axisLabel: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    lineColor: Color = MaterialTheme.colorScheme.secondary,
) {
    if (entries.isEmpty()) return
    val maxValue = niceMax(entries.maxOf { it.value })
    var selected by remember(entries) { mutableStateOf(entries.lastIndex) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface

    ScrollableChart(entries.size, maxValue, axisLabel, modifier, height, minItemWidth = 56.dp) { width ->
        Canvas(
            Modifier
                .width(width)
                .height(height)
                .pointerInput(entries) {
                    detectTapGestures { offset ->
                        val slot = size.width / entries.size.toFloat()
                        selected = entries.indices.minBy { abs(slot * it + slot / 2 - offset.x) }
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
            val points = entries.mapIndexed { i, e ->
                Offset(slot * i + slot / 2, baseline - plotHeight * e.value / maxValue.toFloat())
            }
            val line = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, baseline)
                lineTo(points.first().x, baseline)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f)), startY = top, endY = baseline))
            drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            points.forEachIndexed { i, p ->
                val isSelected = i == selected
                drawCircle(surface, radius = (if (isSelected) 7 else 4).dp.toPx(), center = p)
                drawCircle(lineColor, radius = (if (isSelected) 5 else 3).dp.toPx(), center = p)
                val label = measurer.measure(entries[i].label, labelStyle)
                drawText(label, topLeft = Offset(p.x - label.size.width / 2f, baseline + 6.dp.toPx()))
            }
            val p = points[selected]
            drawLine(lineColor.copy(alpha = 0.4f), Offset(p.x, top), Offset(p.x, baseline), 1.dp.toPx())
            val value = measurer.measure(valueLabel(entries[selected].value), valueStyle)
            val vx = (p.x - value.size.width / 2f).coerceIn(0f, size.width - value.size.width)
            drawText(value, topLeft = Offset(vx, (p.y - value.size.height - 8.dp.toPx()).coerceAtLeast(0f)))
        }
    }
}
