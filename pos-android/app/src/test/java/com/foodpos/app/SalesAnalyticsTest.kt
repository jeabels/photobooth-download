package com.foodpos.app

import com.foodpos.app.data.analytics.SalesAnalytics
import com.foodpos.app.data.analytics.TrendRange
import com.foodpos.app.data.local.ExpenseEntity
import com.foodpos.app.data.local.SalePoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SalesAnalyticsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 28) // a Wednesday

    private fun sale(date: LocalDate, cents: Long, hour: Int = 12) =
        SalePoint(date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), cents)

    private fun expense(date: LocalDate, cents: Long, category: String, deleted: Boolean = false) = ExpenseEntity(
        id = "$date$category$cents", amountCents = cents, spentOn = date.toEpochDay(), category = category,
        description = "", notes = "", createdBy = "", createdAt = 0, deleted = deleted, updatedAt = 0,
    )

    @Test
    fun `daily totals fill empty days with zero`() {
        val days = SalesAnalytics.dailyTotals(
            listOf(sale(today, 1_000), sale(today, 500, hour = 23), sale(today.minusDays(2), 300)),
            today.minusDays(2),
            today,
            zone,
        )
        assertEquals(listOf(300L, 0L, 1_500L), days.map { it.totalCents })
        assertEquals(listOf(1, 0, 2), days.map { it.orders })
    }

    @Test
    fun `summary gives revenue, orders, expenses and net profit for the period`() {
        val sales = listOf(sale(today, 10_000), sale(today.minusDays(40), 99_999))
        val expenses = listOf(
            expense(today, 2_500, "Rent"),
            expense(today, 1_000, "Utilities", deleted = true),
            expense(today.minusDays(40), 50_000, "Rent"),
        )
        val summary = SalesAnalytics.summary(sales, expenses, today.withDayOfMonth(1), today, zone)
        assertEquals(10_000, summary.grossRevenueCents)
        assertEquals(1, summary.totalOrders)
        assertEquals(2_500, summary.totalExpensesCents)
        assertEquals(7_500, summary.netProfitCents)

        val allTime = SalesAnalytics.summary(sales, expenses, null, today, zone)
        assertEquals(109_999, allTime.grossRevenueCents)
        assertEquals(109_999 - 52_500, allTime.netProfitCents)
    }

    @Test
    fun `weekly trend has 12 Monday-based buckets ending this week`() {
        val buckets = SalesAnalytics.trend(
            listOf(sale(today, 100), sale(LocalDate.of(2026, 10, 26), 50), sale(LocalDate.of(2026, 10, 25), 7)),
            TrendRange.WEEKLY,
            today,
            zone,
        )
        assertEquals(12, buckets.size)
        assertEquals(LocalDate.of(2026, 10, 26), buckets.last().start)
        assertEquals(150L, buckets.last().totalCents)
        assertEquals(7L, buckets[10].totalCents)
    }

    @Test
    fun `monthly and yearly trends`() {
        val sales = listOf(sale(today, 100), sale(LocalDate.of(2026, 1, 3), 40), sale(LocalDate.of(2025, 12, 31), 5))
        val months = SalesAnalytics.trend(sales, TrendRange.MONTHLY, today, zone)
        assertEquals(12, months.size)
        assertEquals("Oct 26", months.last().label)
        assertEquals(100L, months.last().totalCents)
        assertEquals(40L, months.first { it.label == "Jan 26" }.totalCents)

        val years = SalesAnalytics.trend(sales, TrendRange.YEARLY, today, zone)
        assertEquals(listOf("2022", "2023", "2024", "2025", "2026"), years.map { it.label })
        assertEquals(listOf(0L, 0L, 0L, 5L, 140L), years.map { it.totalCents })
    }

    @Test
    fun `expenses by category, largest first, deleted ignored`() {
        val result = SalesAnalytics.expensesByCategory(
            listOf(
                expense(today, 100, "Utilities"),
                expense(today, 900, "Rent"),
                expense(today, 50, "Utilities"),
                expense(today, 10_000, "Rent", deleted = true),
                expense(today, 30, ""),
            ),
            null,
            today,
        )
        assertEquals(listOf("Rent" to 900L, "Utilities" to 150L, "Other" to 30L), result)
    }
}
