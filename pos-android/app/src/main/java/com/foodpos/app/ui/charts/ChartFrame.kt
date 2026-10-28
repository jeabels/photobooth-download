package com.foodpos.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One labelled value on a bar or line chart. */
data class ChartEntry(val label: String, val value: Long)

/** Vertical layout shared by the y-axis and the plot so their gridlines line up. */
internal object ChartLayout {
    val top = 28.dp      // room for the selected-value label
    val bottom = 28.dp   // room for x-axis labels
    const val GRID_LINES = 4
}

/** Rounds a maximum up to a "nice" axis top: 1, 2, 2.5, 5 or 10 times a power of ten. */
internal fun niceMax(value: Long): Long {
    if (value <= 0) return 100
    val exponent = floor(log10(value.toDouble()))
    val base = 10.0.pow(exponent)
    val fraction = value / base
    val nice = when {
        fraction <= 1 -> 1.0
        fraction <= 2 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5 -> 5.0
        else -> 10.0
    }
    return ceil(nice * base).toLong()
}

/**
 * Responsive chart container. A fixed y-axis sits on the left; the plot area is at least
 * [minItemWidth] per data point and scrolls horizontally when that is wider than the screen, so
 * points never squish together on small phones. It starts scrolled to the most recent data.
 */
@Composable
internal fun ScrollableChart(
    itemCount: Int,
    maxValue: Long,
    axisLabel: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    minItemWidth: Dp = 48.dp,
    plot: @Composable (width: Dp) -> Unit,
) {
    Row(modifier.fillMaxWidth()) {
        YAxis(maxValue, axisLabel, height)
        BoxWithConstraints(Modifier.weight(1f)) {
            val contentWidth = max(maxWidth, minItemWidth * itemCount)
            val scroll = rememberScrollState()
            LaunchedEffect(itemCount, contentWidth) {
                // Jump to the newest data once the real scroll range is known.
                val end = snapshotFlow { scroll.maxValue }.filter { it in 1 until Int.MAX_VALUE }.first()
                scroll.scrollTo(end)
            }
            Box(Modifier.horizontalScroll(scroll)) {
                plot(contentWidth)
            }
        }
    }
}

@Composable
private fun YAxis(maxValue: Long, axisLabel: (Long) -> String, height: Dp) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.width(52.dp).height(height)) {
        val top = ChartLayout.top.toPx()
        val plotHeight = size.height - top - ChartLayout.bottom.toPx()
        for (i in 0..ChartLayout.GRID_LINES) {
            val fraction = i / ChartLayout.GRID_LINES.toFloat()
            val y = top + plotHeight * (1 - fraction)
            val layout = measurer.measure(axisLabel((maxValue * fraction).toLong()), style)
            drawText(layout, topLeft = Offset(size.width - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }
    }
}
