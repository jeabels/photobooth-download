package com.foodpos.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodpos.app.core.Dates
import com.foodpos.app.core.Money
import com.foodpos.app.data.analytics.TrendRange
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.charts.BarChart
import com.foodpos.app.ui.charts.ChartEntry
import com.foodpos.app.ui.charts.LineChart
import com.foodpos.app.ui.charts.PieChart
import com.foodpos.app.ui.charts.PieSlice
import com.foodpos.app.ui.charts.SalesCalendar
import com.foodpos.app.ui.components.PillRow
import com.foodpos.app.ui.components.SectionCard
import com.foodpos.app.ui.theme.LocalStatusColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.US)

@Composable
fun DashboardScreen() {
    val vm = appViewModel { DashboardViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val symbol = settings.currencySymbol
    val money = { cents: Long -> Money.format(cents, symbol) }
    val compact = { cents: Long -> Money.compact(cents, symbol) }
    val status = LocalStatusColors.current
    if (state.loading) return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PillRow(
            options = Period.entries,
            selected = state.period,
            label = { it.label },
            onSelect = vm::setPeriod,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(top = 8.dp),
        )

        // KPI cards: 4 across on tablets, 2x2 on phones.
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp)) {
            val s = state.summary
            val cards = listOf(
                Triple("Gross revenue", money(s.grossRevenueCents), Color.Unspecified),
                Triple("Total orders", "${s.totalOrders}", Color.Unspecified),
                Triple("Total expenses", money(s.totalExpensesCents), Color.Unspecified),
                Triple("Net profit", money(s.netProfitCents), if (s.netProfitCents >= 0) status.good else status.danger),
            )
            val perRow = if (maxWidth >= 720.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cards.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, value, color) -> MetricCard(label, value, color, Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (state.summary.totalOrders > 0) {
            Text(
                "Average order ${money(state.summary.averageOrderCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        SectionCard(Modifier.padding(horizontal = 16.dp), title = "Daily sales · last 30 days") {
            BarChart(
                entries = state.daily.map { ChartEntry(it.date.format(shortDay), it.totalCents) },
                valueLabel = money,
                axisLabel = compact,
            )
        }

        SectionCard(Modifier.padding(horizontal = 16.dp), title = "Monthly sales calendar") {
            SalesCalendar(
                month = state.month,
                days = state.monthDays,
                selected = state.selectedDay?.date,
                today = state.today,
                compactValue = compact,
                onSelect = vm::selectDay,
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
            )
            val monthTotal = state.monthDays.values.sumOf { it.totalCents }
            val monthOrders = state.monthDays.values.sumOf { it.orders }
            val day = state.selectedDay
            Text(
                if (day != null) {
                    "${Dates.formatDate(day.date)}: ${money(day.totalCents)} from ${day.orders} order(s)"
                } else {
                    "Month total: ${money(monthTotal)} from $monthOrders order(s). Tap a day for details."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard(Modifier.padding(horizontal = 16.dp), title = "Sales trend") {
            PillRow(
                options = TrendRange.entries,
                selected = state.trendRange,
                label = { it.label },
                onSelect = vm::setTrendRange,
            )
            LineChart(
                entries = state.trend.map { ChartEntry(it.label, it.totalCents) },
                valueLabel = money,
                axisLabel = compact,
            )
        }

        SectionCard(Modifier.padding(horizontal = 16.dp), title = "Expenses by category · ${state.period.label.lowercase()}") {
            if (state.expenseCategories.isEmpty()) {
                Text("No expenses logged in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val palette = status.chart
                PieChart(
                    slices = state.expenseCategories.mapIndexed { i, (category, amount) -> PieSlice(category, amount, palette[i % palette.size]) },
                    valueLabel = money,
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        }
    }
}
