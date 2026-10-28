package com.foodpos.app.data.analytics

import com.foodpos.app.data.local.ExpenseEntity
import com.foodpos.app.data.local.SalePoint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class DayTotal(val date: LocalDate, val totalCents: Long, val orders: Int)

data class TrendBucket(val label: String, val start: LocalDate, val totalCents: Long)

enum class TrendRange(val label: String) { WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly") }

data class BusinessSummary(
    val grossRevenueCents: Long,
    val totalOrders: Int,
    val totalExpensesCents: Long,
) {
    val netProfitCents: Long get() = grossRevenueCents - totalExpensesCents
    val averageOrderCents: Long get() = if (totalOrders == 0) 0 else grossRevenueCents / totalOrders
}

/**
 * Pure report maths for the dashboard. Gross revenue is the sum of completed order totals (voided
 * orders are excluded); net profit is gross revenue minus logged operating expenses.
 */
object SalesAnalytics {
    private val dayLabel = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val monthLabel = DateTimeFormatter.ofPattern("MMM yy", Locale.US)

    /** One entry per day from [from] to [to] inclusive; days without sales are zero. */
    fun dailyTotals(points: List<SalePoint>, from: LocalDate, to: LocalDate, zone: ZoneId): List<DayTotal> {
        val grouped = points.groupBy { localDate(it.createdAt, zone) }
        val days = mutableListOf<DayTotal>()
        var day = from
        while (!day.isAfter(to)) {
            val sales = grouped[day].orEmpty()
            days += DayTotal(day, sales.sumOf { it.totalCents }, sales.size)
            day = day.plusDays(1)
        }
        return days
    }

    fun monthTotals(points: List<SalePoint>, month: YearMonth, zone: ZoneId): Map<LocalDate, DayTotal> =
        dailyTotals(points, month.atDay(1), month.atEndOfMonth(), zone).associateBy { it.date }

    /**
     * Sales trend buckets ending at [today]: the last 12 weeks (weeks start Monday), the last 12
     * months, or the last 5 years.
     */
    fun trend(points: List<SalePoint>, range: TrendRange, today: LocalDate, zone: ZoneId): List<TrendBucket> {
        val starts: List<LocalDate> = when (range) {
            TrendRange.WEEKLY -> {
                val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (11 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
            }
            TrendRange.MONTHLY -> {
                val thisMonth = today.withDayOfMonth(1)
                (11 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
            }
            TrendRange.YEARLY -> {
                val thisYear = today.withDayOfYear(1)
                (4 downTo 0).map { thisYear.minusYears(it.toLong()) }
            }
        }
        val totals = LongArray(starts.size)
        for (point in points) {
            val date = localDate(point.createdAt, zone)
            val index = starts.indexOfLast { !date.isBefore(it) }
            if (index < 0) continue
            val end = nextStart(starts[index], range)
            if (date.isBefore(end)) totals[index] += point.totalCents
        }
        return starts.mapIndexed { i, start ->
            val label = when (range) {
                TrendRange.WEEKLY -> start.format(dayLabel)
                TrendRange.MONTHLY -> start.format(monthLabel)
                TrendRange.YEARLY -> start.year.toString()
            }
            TrendBucket(label, start, totals[i])
        }
    }

    /** Totals for orders and expenses in [from]..[to] inclusive; a null [from] means all time. */
    fun summary(
        points: List<SalePoint>,
        expenses: List<ExpenseEntity>,
        from: LocalDate?,
        to: LocalDate,
        zone: ZoneId,
    ): BusinessSummary {
        val sales = points.filter { inRange(localDate(it.createdAt, zone), from, to) }
        val spent = expenses.filter { !it.deleted && inRange(LocalDate.ofEpochDay(it.spentOn), from, to) }
        return BusinessSummary(
            grossRevenueCents = sales.sumOf { it.totalCents },
            totalOrders = sales.size,
            totalExpensesCents = spent.sumOf { it.amountCents },
        )
    }

    /** Expense totals per category in the range, largest first. */
    fun expensesByCategory(expenses: List<ExpenseEntity>, from: LocalDate?, to: LocalDate): List<Pair<String, Long>> =
        expenses.asSequence()
            .filter { !it.deleted && inRange(LocalDate.ofEpochDay(it.spentOn), from, to) }
            .groupBy { it.category.ifBlank { "Other" } }
            .map { (category, list) -> category to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }

    private fun nextStart(start: LocalDate, range: TrendRange): LocalDate = when (range) {
        TrendRange.WEEKLY -> start.plusWeeks(1)
        TrendRange.MONTHLY -> start.plusMonths(1)
        TrendRange.YEARLY -> start.plusYears(1)
    }

    private fun inRange(date: LocalDate, from: LocalDate?, to: LocalDate): Boolean =
        (from == null || !date.isBefore(from)) && !date.isAfter(to)

    private fun localDate(millis: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
