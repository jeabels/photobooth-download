package com.foodpos.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

data class PieSlice(val label: String, val value: Long, val color: Color)

/**
 * Donut chart of expense categories drawn on a Compose [Canvas], with a tappable legend. Side by side
 * on wide screens, stacked on phones.
 */
@Composable
fun PieChart(slices: List<PieSlice>, valueLabel: (Long) -> String, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }
    if (total <= 0) return
    var selected by remember(slices) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 520.dp
        val donut = @Composable { Donut(slices, total, selected, valueLabel, onSelect = { selected = if (selected == it) null else it }) }
        val legend = @Composable { m: Modifier -> Legend(slices, total, selected, valueLabel, m) { selected = if (selected == it) null else it } }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                donut()
                Spacer(Modifier.width(24.dp))
                legend(Modifier.weight(1f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                donut()
                legend(Modifier.fillMaxWidth().padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun Donut(slices: List<PieSlice>, total: Long, selected: Int?, valueLabel: (Long) -> String, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val titleStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Canvas(
        Modifier
            .size(200.dp)
            .pointerInput(slices) {
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = tap.x - center.x
                    val dy = tap.y - center.y
                    val radius = min(size.width, size.height) / 2f
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance < radius * 0.45f || distance > radius) return@detectTapGestures
                    // Angle measured clockwise from 12 o'clock, matching the drawing order.
                    val angle = (Math.toDegrees(atan2(dy, dx).toDouble()) + 90 + 360) % 360
                    var start = 0.0
                    slices.forEachIndexed { i, slice ->
                        val sweep = 360.0 * slice.value / total
                        if (angle >= start && angle < start + sweep) {
                            onSelect(i)
                            return@detectTapGestures
                        }
                        start += sweep
                    }
                }
            },
    ) {
        val thickness = size.minDimension * 0.2f
        val inset = thickness / 2 + 6.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        var start = -90f
        slices.forEachIndexed { i, slice ->
            val sweep = 360f * slice.value / total
            val isSelected = selected == i
            drawArc(
                color = if (selected == null || isSelected) slice.color else slice.color.copy(alpha = 0.35f),
                startAngle = start,
                sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = if (isSelected) thickness + 8.dp.toPx() else thickness),
            )
            start += sweep
        }
        val title = selected?.let { slices[it].label } ?: "Total"
        val value = selected?.let { slices[it].value } ?: total
        val titleLayout = measurer.measure(title, titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (size.width * 0.5f).toInt()))
        val valueLayout = measurer.measure(valueLabel(value), valueStyle)
        drawText(titleLayout, topLeft = Offset(center.x - titleLayout.size.width / 2f, center.y - titleLayout.size.height))
        drawText(valueLayout, topLeft = Offset(center.x - valueLayout.size.width / 2f, center.y))
    }
}

@Composable
private fun Legend(
    slices: List<PieSlice>,
    total: Long,
    selected: Int?,
    valueLabel: (Long) -> String,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slices.forEachIndexed { i, slice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected == i) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(12.dp).background(slice.color, CircleShape))
                Text(slice.label, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    String.format(Locale.US, "%.0f%%", 100.0 * slice.value / total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(valueLabel(slice.value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
