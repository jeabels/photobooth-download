package com.foodpos.app.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodpos.app.data.analytics.DayTotal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/**
 * Interactive monthly sales calendar: each day is shaded by how much it sold (darker = more).
 * Tap a day to select it; arrows move between months.
 */
@Composable
fun SalesCalendar(
    month: YearMonth,
    days: Map<LocalDate, DayTotal>,
    selected: LocalDate?,
    today: LocalDate,
    compactValue: (Long) -> String,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val max = days.values.maxOfOrNull { it.totalCents }?.takeIf { it > 0 } ?: 1L
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month") }
            Text(month.format(monthTitle), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = onNext, enabled = month < YearMonth.from(today)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BoxWithConstraints {
            // Amounts only fit inside the cells when each cell is wide enough.
            val showAmounts = maxWidth / 7 >= 44.dp
            val leading = month.atDay(1).dayOfWeek.value % 7 // Sunday = 0
            val cells = leading + month.lengthOfMonth()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                for (week in 0 until (cells + 6) / 7) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (weekday in 0 until 7) {
                            val dayNumber = week * 7 + weekday - leading + 1
                            if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                val date = month.atDay(dayNumber)
                                val total = days[date]?.totalCents ?: 0
                                val intensity = total.toFloat() / max
                                val background = if (total > 0) primary.copy(alpha = 0.15f + 0.75f * intensity) else MaterialTheme.colorScheme.surfaceContainerHigh
                                val textColor = if (intensity > 0.55f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(if (showAmounts) 0.9f else 1f)
                                        .background(background, RoundedCornerShape(8.dp))
                                        .then(
                                            if (date == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)) else Modifier,
                                        )
                                        .clickable(enabled = !date.isAfter(today)) { onSelect(date) }
                                        .padding(4.dp),
                                ) {
                                    Text(
                                        dayNumber.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                        color = if (date.isAfter(today)) textColor.copy(alpha = 0.4f) else textColor,
                                        modifier = Modifier.align(Alignment.TopStart),
                                    )
                                    if (showAmounts && total > 0) {
                                        Text(
                                            compactValue(total),
                                            fontSize = 9.sp,
                                            color = textColor,
                                            maxLines = 1,
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Less ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0.15f, 0.4f, 0.65f, 0.9f).forEach { a ->
                Box(Modifier.padding(horizontal = 2.dp).background(primary.copy(alpha = a), RoundedCornerShape(3.dp)).padding(6.dp))
            }
            Text(" More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
