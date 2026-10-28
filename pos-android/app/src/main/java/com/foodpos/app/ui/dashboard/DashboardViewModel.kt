package com.foodpos.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.core.Dates
import com.foodpos.app.data.analytics.BusinessSummary
import com.foodpos.app.data.analytics.DayTotal
import com.foodpos.app.data.analytics.SalesAnalytics
import com.foodpos.app.data.analytics.TrendBucket
import com.foodpos.app.data.analytics.TrendRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

enum class Period(val label: String) {
    TODAY("Today"), WEEK("7 days"), MONTH("This month"), YEAR("This year"), ALL("All time");

    /** First day included, or null for all time. */
    fun start(today: LocalDate): LocalDate? = when (this) {
        TODAY -> today
        WEEK -> today.minusDays(6)
        MONTH -> today.withDayOfMonth(1)
        YEAR -> today.withDayOfYear(1)
        ALL -> null
    }
}

private data class Controls(
    val period: Period = Period.MONTH,
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate? = null,
    val trendRange: TrendRange = TrendRange.WEEKLY,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val period: Period = Period.MONTH,
    val summary: BusinessSummary = BusinessSummary(0, 0, 0),
    val daily: List<DayTotal> = emptyList(),
    val month: YearMonth = YearMonth.now(),
    val monthDays: Map<LocalDate, DayTotal> = emptyMap(),
    val selectedDay: DayTotal? = null,
    val trendRange: TrendRange = TrendRange.WEEKLY,
    val trend: List<TrendBucket> = emptyList(),
    val expenseCategories: List<Pair<String, Long>> = emptyList(),
    val today: LocalDate = LocalDate.now(),
)

/** Business metrics, recomputed live from Room whenever an order or expense changes. */
class DashboardViewModel(container: AppContainer) : ViewModel() {
    private val controls = MutableStateFlow(Controls())
    val settings = container.settings.settings

    val state: StateFlow<DashboardUiState> = combine(
        container.orders.observeSalePoints(),
        container.expenses.observeAll(),
        controls,
    ) { sales, expenses, c ->
        val zone = Dates.zone
        val today = Dates.today()
        val from = c.period.start(today)
        val monthDays = SalesAnalytics.monthTotals(sales, c.month, zone)
        DashboardUiState(
            loading = false,
            period = c.period,
            summary = SalesAnalytics.summary(sales, expenses, from, today, zone),
            daily = SalesAnalytics.dailyTotals(sales, today.minusDays(DAILY_DAYS - 1), today, zone),
            month = c.month,
            monthDays = monthDays,
            selectedDay = c.selectedDay?.let { monthDays[it] },
            trendRange = c.trendRange,
            trend = SalesAnalytics.trend(sales, c.trendRange, today, zone),
            expenseCategories = SalesAnalytics.expensesByCategory(expenses, from, today),
            today = today,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setPeriod(value: Period) = controls.update { it.copy(period = value) }
    fun setTrendRange(value: TrendRange) = controls.update { it.copy(trendRange = value) }
    fun previousMonth() = controls.update { it.copy(month = it.month.minusMonths(1), selectedDay = null) }
    fun nextMonth() = controls.update { it.copy(month = it.month.plusMonths(1), selectedDay = null) }
    fun selectDay(date: LocalDate) = controls.update { it.copy(selectedDay = if (it.selectedDay == date) null else date) }

    private companion object {
        const val DAILY_DAYS = 30L
    }
}
